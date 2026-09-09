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

## Problem 3 - with several String fields, only one copy keeps its counter in a register

**Everything in this section is measured with Problems 1 and 2 already fixed** - Jackson 3.1.4 with
[#1681](https://github.com/FasterXML/jackson-core/pull/1681) and
[#6183](https://github.com/FasterXML/jackson-databind/pull/6183) applied. This is a problem that
remains after both patches, not one they mask.

The first two problems are about one copy loop. This one only appears when a serializer writes
**more than one String field**, which is the normal case for a bean.

C2 inlines `writeString` **once per call site**, so a bean with two String properties gets two
independent copies of the loop in one method - and it allocates them differently. Measured on a
serializer in the shape Quarkus generates for a 4-field `Person` (`String firstName, String
lastName, int age, double height`), with `firstName` left at the application's `"John"` and
`lastName` at 256 characters, so only the second copy does real work:

| inlined copy | loop counter | stack refs in the loop |
|---|---|---|
| first (`writeString(firstName)`, bci 59) | **register `ebx`** | 6 |
| second (`writeString(lastName)`, bci 122) | **memory, `[rsp+0x8]`** | 13 |

The first copy ends its iteration with `add ebx,0x2 / cmp ebx,eax / jl`. The second has to do:

```asm
mov    r10d,DWORD PTR [rsp+0x8]     ; load the counter
add    r10d,0x2
mov    DWORD PTR [rsp+0x8],r10d     ; store it back
cmp    r10d,edx
jl     ...                          ; and it is loaded again at the loop head
```

Both loops touch the stack for other values - the output buffer base and the output pointer - so
neither is free of stack traffic. The difference that matters is the counter itself, and the second
copy carries roughly twice the stack references overall.

`perfasm` puts **57.8 % of all cycles** in the second copy's loop.

### What C2 is doing

Under a fastdebug JVM, `-XX:+PrintOptoAssembly` labels the allocator's spill code directly. Both
copy loops use **the same 13 registers**; x86-64 offers 14 allocatable here, since `RSP` is the
stack pointer and `R12` is pinned as the compressed-oop heap base (visible in the addressing,
`[R12 + R11 << 3 + #16]`). The loops sit right at the limit.

That suggests being one register short, but the test does not support it. With the unroll factor
pinned at 2x in both arms so only the register count changes:

| | first copy | second copy |
|---|---|---|
| `-XX:+UseCompressedOops` | 50 insns, 6 stack refs | 53 insns, 13 stack refs |
| `-XX:-UseCompressedOops` (frees `R12`) | 49 insns, 4 stack refs | 33 insns, 7 stack refs |

Handing the allocator one more register roughly halves the second copy's stack traffic but does not
get its counter back into a register. So the shortfall is larger than one register, and **why the
later copy is the one that loses its counter is not established here.**

*The `PrintOptoAssembly` register census comes from a fastdebug VM, whose allocation for this method
is not identical to the release build's - treat it as indicative of the pressure, not as a
description of the release code. The shapes and stack-ref counts in the tables above are from
release builds.*

### What it costs

`GenShapeBench`, 20 beans per op, `firstName` fixed at the application's `"John"` so only the
*second* copy does real work, `lastName` grown to 256 characters. `serialize` has `writeString`
inlined and is the case to fix; `serializeWriteStringNotInlined` is the control:

| | ns/op |
|---|---:|
| `serialize` (inlined, second copy spills) | 5350.6 ± 60.3 |
| `serializeWriteStringNotInlined` (control) | 5051.2 ± 11.2 |
| | **-5.6 %** |

The control is also far more reproducible (±11.2 vs ±60.3): with one out-of-line copy there is no
second allocation to get wrong.

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

## Profiling with linux perf instead of async-profiler

`scripts/perfjit.sh` records a benchmark under `perf` with JIT symbols resolved and produces a
per-instruction cycle annotation of the compiled code, so the assembly and the profile come from
the same run and can be read side by side:

```
scripts/perfjit.sh target/benchmarks.jar -p len=128 -p size=20 -s serializeContent
```

It sets `-XX:+PreserveFramePointer`, loads `libperf-jvmti.so` so the JVM writes a jitdump, records
with `perf record -k mono` (the jitdump timestamps must share perf's clock), then runs
`perf inject --jit` and `perf annotate`. Output lands in `perfjit-out/<jar>/`:

| file | |
|---|---|
| `jmh.txt` | the JMH score for the run that was profiled |
| `symbols.txt` | hottest symbols, and which jitted object each came from |
| `annotate.txt` | per-instruction cycle percentages of the chosen symbol |
| `perf.jit.data` | for your own `perf report` / `perf annotate` |

Two things to know when reading it. **A Java method has several compilations** - a profiled C1 one
and a C2 one - and they share a symbol name while living in different `jitted-*.so` objects. Check
`symbols.txt` for which object carries the cycles before drawing anything from `annotate.txt`; the
C1 one is recognisable by its MDO counter bumps (`addq $0x1,0x198(%rdi)`). And **read the
annotation rather than grepping it** - loops here are unrolled, peeled and strip-mined, so a method
holds several copies of the same source loop and a pattern match cannot tell you which is which.

Give the strings enough length (`-p len=128` and up) that the copy loop dominates; at the
application's own 3-4 character values it is a small fraction of the profile and nothing separates.

Needs `perf` and `/usr/lib64/libperf-jvmti.so` (the `perf-jvmti` / `linux-tools` package).

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
