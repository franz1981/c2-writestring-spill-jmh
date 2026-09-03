# Two problems in the `UTF8JsonGenerator` ASCII copy loop

JMH reproducer. Measured on **Jackson 2.22.0, Temurin 25.0.2+10, x86-64**.

```java
// UTF8JsonGenerator._writeStringSegment
while (offset < len) {
    int ch = text.charAt(offset);
    if (ch > 0x7F || escCodes[ch] != 0) break;
    outputBuffer[outputPtr++] = (byte) ch;
    ++offset;
}
```

Two independent problems. Both are visible only in the compiled code, and both make the loop
markedly worse when C2 inlines `writeString` into the caller.

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

This is why the inlined case starts out **22.8 % slower** than the not-inlined one
(429.697 vs 349.969 ns/op) - a gap that inlining does not cause and that disappears once this is fixed.

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

## Results

`SingleBench`, ns/op, 10 forks, lower is better. `serialize` has `writeString` inlined and is the
case to fix; `serializeWriteStringNotInlined` is the control. Raw output in [`results/`](results/).

| | no fix | + #6183 | + #6183 + #1681 |
|---|---:|---:|---:|
| **inlined** | 429.697 ± 13.714 | 335.944 ± 4.299 (-21.8%) | 318.395 ± 2.513 (-5.2%) |
| not inlined | 349.969 ± 3.751 | 332.166 ± 2.334 (-5.1%) | 325.318 ± 2.374 (-2.1%) |

Total on the inlined case **-25.9%**, and nothing regresses at any step. After #6183 the two are within 1.1 % of each other - the inlining gap is gone.

On this branch most of the not-inlined gain comes from #6183; #1681 adds a little more.

## The loop, compiled

Hot method is `bench.flat.FlatSer::serialize` with `writeString` inlined, identified by
`-prof perfasm` sample attribution; loop bounded by the branch that targets its head.

| | chars per iteration | stack accesses per char | `escCodes[]` loads per char | instructions per char |
|---|---:|---:|---:|---:|
| no fix | 1 | 13 | 1 | 33.0 |
| + #6183 | 2 | 2 | 1 | 15.5 |
| + #6183 + #1681 | 4 | 1 | 0 | 13.5 |

## Run it

```bash
mvn clean package
java -jar target/benchmarks.jar                      # released jars = the "no fix" column
java -jar target/benchmarks.jar  -f 10               # fixed builds are bimodal, use 10 forks
java -jar target/benchmarks.jar SingleBench.serialize -f 1 -prof perfasm
```

## Reproducing the fixed builds

The released jars are shaded into `target/benchmarks.jar`, so the simplest route is to patch the
two classes and replace them inside that jar.

```bash
V=2.22.0
mvn -q dependency:copy -Dartifact=com.fasterxml.jackson.core:jackson-core:$V:jar:sources -DoutputDirectory=.
mvn -q dependency:copy -Dartifact=com.fasterxml.jackson.core:jackson-databind:$V:jar:sources -DoutputDirectory=.
unzip -o jackson-core-$V-sources.jar     'com/fasterxml/jackson/core/json/UTF8JsonGenerator.java' -d src
unzip -o jackson-databind-$V-sources.jar 'com/fasterxml/jackson/databind/util/StdDateFormat.java' -d src
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
final boolean stdEsc = (escCodes == com.fasterxml.jackson.core.io.CharTypes.get7BitOutputEscapes());
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

## Not verified

- Other JDK versions, JDK vendors, or non-x86 targets.
- Whether "the table base occupies a register the loop needs" is the reason the unroll factor
  doubles with #1681. The data shows the two together, not one causing the other.
- Whether #6183 still helps an application that does use RFC1123 formatting.
