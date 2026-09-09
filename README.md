# Three problems in the `UTF8JsonGenerator` ASCII copy loop

JMH reproducer. Measured on **Jackson 3.1.5, Temurin 25.0.2+10, x86-64**.

```java
// UTF8JsonGenerator._writeStringSegment
while (offset < len) {
    int ch = text.charAt(offset);
    if (ch > 0x7F || escCodes[ch] != 0) break;
    outputBuffer[outputPtr++] = (byte) ch;
    ++offset;
}
```

Three independent problems. All are visible only in the compiled code, and all make the loop
markedly worse when C2 inlines `writeString` into the caller. The third only appears once a
serializer writes more than one String field.

## Problem 1 - the loop does not unroll, and spills

Compiled, the loop does **one character per iteration** with **13 stack loads and stores per
character**. The string, the index, the output buffer and the loop limit all live on the stack and
are re-read every iteration.

Cause: `String.charAt` has a single process-wide MethodData. `StdDateFormat.<clinit>` builds a
`SimpleDateFormat`, which reaches `DecimalFormatSymbols` and calls `String.charAt` on a UTF-16
string. That single call leaves a non-zero count on the non-LATIN1 branch, so in every ASCII
`charAt` loop compiled afterwards C2 cannot prune that branch and keeps an out-of-line call for it.

The LATIN1 case is still inlined to a byte load, and the call **never executes** - it collects no
profiler samples. The damage is indirect: C2 does not unroll a loop containing a call, and the
allocator must keep values live across it.

This is why the inlined case starts out **19.4 % slower** than the not-inlined one
(445.606 vs 373.151 ns/op) - a gap that inlining does not cause and that disappears once this is fixed.

**Fixed by** [jackson-databind#6182](https://github.com/FasterXML/jackson-databind/issues/6182) / [PR #6183](https://github.com/FasterXML/jackson-databind/pull/6183): build the RFC1123
blueprint lazily instead of in `<clinit>`.

*Scope:* this defers the `SimpleDateFormat` construction rather than removing it. An application
that actually parses or formats an RFC1123 date will build it, and by the mechanism above should
re-pollute the profile. Not measured here.

## Problem 2 - a table load per character

`escCodes[ch]` is a heap load plus a bounds check for every character.

**Fixed by** [jackson-core#1680](https://github.com/FasterXML/jackson-core/issues/1680) / [PR #1681](https://github.com/FasterXML/jackson-core/pull/1681): for the standard
escape table the test is expressible with constants
(`ch < 0x20 || ch > 0x7F || ch == '"' || ch == '\\'`), so both the load and the bounds check go.

## Problem 3 - with several String fields, only one copy is allocated well

**Everything in this section is measured with Problems 1 and 2 already fixed** - Jackson 3.1.4 with
[#1681](https://github.com/FasterXML/jackson-core/pull/1681) and
[#6183](https://github.com/FasterXML/jackson-databind/pull/6183) applied. This is a problem that
remains after both patches, not one they mask.

The first two problems are about one copy loop. This one only appears when a serializer writes
**more than one String field**, which is the normal case for a bean.

C2 inlines `writeString` **once per call site**, so a bean with two String properties gets two
independent copies of the loop in one method - and it allocates them differently. Measured on the
serializer Quarkus generates for a 4-field `Person` (`String firstName, String lastName, int age,
double height`):

| inlined copy | compiled shape, hand-written `GenPersonSer` | real generated serializer |
|---|---|---|
| first (`firstName`) | 29 insns, 2x unrolled, **no stack traffic** | 28 insns, 2x, no stack traffic |
| second (`lastName`) | 31 insns, 2x unrolled, **counter in `[rsp+0x18]`** | 31 insns, 2x, counter in `[rsp+0x8]` |

Both copies unroll identically and have identical escape checks. The difference is purely register
allocation: the second reloads its induction variable three times and stores it back once per
iteration, where the first keeps it in `edx`.

With longer strings the second copy degrades further - the output buffer base and a loop-invariant
offset end up on the stack as well, reloaded on **every character**:

```asm
0x15410:  mov    edx,DWORD PTR [rsp]         ; induction variable
0x15413:  add    edx,DWORD PTR [rsp+0x3c]    ; loop-invariant offset, reloaded
0x15417:  movsxd r8,DWORD PTR [rsp]          ; induction variable again
   ...
0x15453:  mov    ebx,DWORD PTR [rsp+0x4]     ; output buffer base, reloaded per character
0x154fd:  mov    r8d,DWORD PTR [rsp]         ; induction variable a third time
```

`perfasm` puts **57.8 % of all cycles** in exactly that loop, annotated `serializeContent@209` -
the static reading and the sampled profile agree on which copy is the bad one.

### What it costs

`GenShapeBench`, 20 beans per op, `firstName` fixed at the application's `"John"` so only the
*second* copy does real work, `lastName` grown to 256 characters. `serialize` has `writeString`
inlined and is the case to fix; `serializeWriteStringNotInlined` is the control:

| | ns/op |
|---|---:|
| `serialize` (inlined, second copy spills) | 5350.6 ± 60.3 |
| `serializeWriteStringNotInlined` (control) | 5051.2 ± 11.2 |
| | **-5.6 %** |

The same measurement against the *real* serializer Quarkus generates, rather than the hand-written
stand-in, gives 5392.7 ± 140.0 and 5065.8 ± 7.0 - **-6.1 %**. See `GenSerBench` and the
`quarkus-gen` profile.

The control is also far more reproducible (±11.2 vs ±60.3, and ±7.0 vs ±140.0 on the generated
serializer): with one out-of-line copy there is no second allocation to get wrong.

Which copy gets the bad allocation, and why, is not established here.

### Reproducing

Self-contained - `GenPersonSer` is hand-written in the shape Quarkus generates, so this needs no
generated classes and builds from a clean clone:

```
mvn clean package
java -jar target/benchmarks.jar GenShapeBench -p len=256
```

`serializeContent` carries `@CompilerControl(DONT_INLINE)`. That is the 1:1 counterpart of the same
annotation on `FlatSer.serialize` on `master`, and it is what keeps the two copy loops in the
serializer's own method instead of letting C2 bury them in `CollectionSerializer`. Quarkus splits
its generated serializers the same way - `GeneratedSerializer.serialize` writes the braces and calls
an abstract `serializeContent` holding the property writes - so `serializeContent`, not `serialize`,
is the method that matters.

To cross-check against the real generated serializer instead of the stand-in, install the classes
dumped from a built app and use the opt-in profile:

```
mvn install:install-file -Dfile=quarkus-gen-person.jar \
    -DgroupId=bench.local -DartifactId=quarkus-gen-person -Dversion=1.0 -Dpackaging=jar
mvn -Pquarkus-gen clean package
java -jar target/benchmarks.jar GenSerBench.genser -p len=256 -f 5 \
  -jvmArgsAppend "-XX:CompileCommand=dontinline,org.quarkus.metaprogramming.Person\$quarkusjacksonserializer::serializeContent"
```

To read the compiled loops, add `-XX:+UnlockDiagnosticVMOptions -XX:-BackgroundCompilation` and
`-XX:CompileCommand=print,...::serializeContent` - print one method only, because with a global
`-XX:+PrintAssembly` the compiler threads interleave and truncate each other's output. No unrolling
flag is needed: with both patches applied the loop unrolls 2x on its own.

*Do not pass `-jvmArgsAppend` on the command line when running `GenShapeBench`: it replaces the
`@Fork` annotation's arguments rather than adding to them, which silently disables the control arm's
`dontinline` and makes both arms identical.*

## Results

`SingleBench`, ns/op, 10 forks, lower is better. `serialize` has `writeString` inlined and is the
case to fix; `serializeWriteStringNotInlined` is the control. Raw output in [`results/`](results/).

| | no fix | + #6183 | + #6183 + #1681 |
|---|---:|---:|---:|
| **inlined** | 445.606 ± 3.295 | 356.893 ± 6.613 (-19.9%) | 346.744 ± 3.685 (-2.8%) |
| not inlined | 373.151 ± 4.255 | 371.751 ± 22.68 (-0.4%) | 350.375 ± 7.309 (-5.8%) |

Total on the inlined case **-22.2%**, and nothing regresses at any step. After #6183 the inlined case is 4.0 % **faster** than the control - the gap does not just close, it reverses.

On this branch #6183 does not measurably move the not-inlined case (373.151 -> 371.751, and that run has a ±22.68 error), while #1681 does (-5.8%). On the Jackson 2 branch it is the other way round. Unexplained.

## The loop, compiled

Hot method is `bench.flat.FlatSer::serialize` with `writeString` inlined, identified by
`-prof perfasm` sample attribution; loop bounded by the branch that targets its head.

| | chars per iteration | stack accesses per char | `escCodes[]` loads per char | instructions per char |
|---|---:|---:|---:|---:|
| no fix | 1 | 13 | 1 | 33.0 |
| + #6183 | 2 | 2 | 1 | 15.5 |
| + #6183 + #1681 | 4 | 0 | 0 | 13.0 |

## Run it

```bash
mvn clean package
java -jar target/benchmarks.jar SingleBench                     # released jars = the "no fix" column
java -jar target/benchmarks.jar SingleBench -f 10               # fixed builds are bimodal, use 10 forks
java -jar target/benchmarks.jar SingleBench.serialize -f 1 -prof perfasm
```

## Reproducing the fixed builds

The released jars are shaded into `target/benchmarks.jar`, so the simplest route is to patch the
two classes and replace them inside that jar.

```bash
V=3.1.5
mvn -q dependency:copy -Dartifact=tools.jackson.core:jackson-core:$V:jar:sources -DoutputDirectory=.
mvn -q dependency:copy -Dartifact=tools.jackson.core:jackson-databind:$V:jar:sources -DoutputDirectory=.
unzip -o jackson-core-$V-sources.jar     'tools/jackson/core/json/UTF8JsonGenerator.java' -d src
unzip -o jackson-databind-$V-sources.jar 'tools/jackson/databind/util/StdDateFormat.java' -d src
# apply the two edits below to src/, then:
javac -cp target/benchmarks.jar -d out $(find src -name '*.java')
(cd out && jar uf ../target/benchmarks.jar .)
```

**`StdDateFormat`** (#6183) - delete the `protected final static DateFormat DATE_FORMAT_RFC1123`
field and its `static { }` initialiser, add a holder, and change both
`_cloneFormat(DATE_FORMAT_RFC1123, ...)` call sites to `RFC1123Holder.DATE_FORMAT_RFC1123`:

```java
private static final class RFC1123Holder {
    static final DateFormat DATE_FORMAT_RFC1123;
    static {
        DATE_FORMAT_RFC1123 = new SimpleDateFormat(DATE_FORMAT_STR_RFC1123, DEFAULT_LOCALE);
        DATE_FORMAT_RFC1123.setTimeZone(DEFAULT_TIMEZONE);
    }
}
```

**`UTF8JsonGenerator`** (#1681) - in **both** `_writeStringSegment` overloads (one reads
`cbuf[offset]`, the other `text.charAt(offset)`; leave that line as it is):

```java
final boolean stdEsc = (escCodes == tools.jackson.core.io.CharTypes.get7BitOutputEscapes());
while (offset < len) {
    int ch = /* unchanged */;
    if (stdEsc ? (ch < 0x20 || ch > 0x7F || ch == '"' || ch == '\\')
               : (ch > 0x7F || escCodes[ch] != 0)) {
        break;
    }
    ...
}
```

These are equivalent to the linked PRs, not cherry-picked from them.

## Notes

- **The fixed builds are bimodal across forks.** At 3 forks one unlucky fork moves the mean by
  ~10 %; all numbers here use 10. The baseline is stable, the fixed builds are not. Why, is not
  understood.
- The `dontinline` benchmark is a control, not a proposed fix - `writeString` is Jackson's method
  and cannot be annotated.
- This branch also carries `AccessorBench` and `CallPathBench`, unrelated to the two problems
  documented here; pass `SingleBench` to run only this reproducer.

## Not verified

- Other JDK versions, JDK vendors, or non-x86 targets.
- Whether "the table base occupies a register the loop needs" is the reason the unroll factor
  doubles with #1681. The data shows the two together, not one causing the other.
- Whether #6183 still helps an application that does use RFC1123 formatting.
