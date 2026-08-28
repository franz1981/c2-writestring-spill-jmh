# C2 spills the copy loop of `UTF8JsonGenerator._writeStringSegment` when it is inlined

Writing JSON strings costs about 20 % more when C2 inlines `UTF8JsonGenerator.writeString` into the caller
than when it does not, because the ASCII copy loop inside `_writeStringSegment` loses its registers.

## What is observed

The benchmark serializes a `List<Flat>` with a hand-written serializer that makes one `writeString` call per
element. Two runs, the same code and the same output, differing only in whether `writeString` is inlined.

When it is inlined, the copy loop is **33 instructions, 13 of which address `(%rsp)`**: the loop counter is
written to the stack and read back within the same iteration, and the loop invariants - output buffer, escape
table, end index - are re-loaded from the stack on every iteration.

When it is not, the same loop is **23 instructions with 2 stack operands**, and the counter, buffer and escape
table stay in registers.

The loop gives C2 nothing to struggle with; Jackson already hoists the fields into locals:

```java
int outputPtr = _outputTail;
final byte[] outputBuffer = _outputBuffer;
final int[] escCodes = _outputEscapes;

while (offset < len) {
    int ch = text.charAt(offset);
    if (ch > 0x7F || escCodes[ch] != 0) break;
    outputBuffer[outputPtr++] = (byte) ch;
    ++offset;
}
```

## Run it

```bash
mvn clean package
java -jar target/benchmarks.jar
```

```
Benchmark                                   (size)  Mode  Cnt    Score   Error  Units
SingleBench.serialize                           10  avgt   15  417.158 ± 6.387  ns/op
SingleBench.serializeWriteStringNotInlined      10  avgt   15  342.188 ± 2.836  ns/op
```

The second benchmark adds
`-XX:CompileCommand=dontinline,com/fasterxml/jackson/core/json/UTF8JsonGenerator.writeString` through
`@Fork`. It is the only JVM flag in the project, and it exists only because `writeString` is Jackson's method
and cannot be annotated.

Counters and disassembly:

```bash
java -jar target/benchmarks.jar -prof perfnorm
java -jar target/benchmarks.jar -f 1 -prof 'perfasm:hotThreshold=0.02;tooBigThreshold=4000'
```

## Where the time goes

| per op | inlined | not inlined |
|---|---:|---:|
| cycles | 1790.6 | 1477.5 |
| instructions | 8745.8 | 7907.0 |
| IPC | **4.88** | **5.35** |
| L1-dcache-loads | 3021.7 | 2620.1 |
| L1-icache-load-misses | 0.017 | 0.012 |

The inlined version does not execute much more work, it retires it more slowly. Instruction fetch is not
involved - 0.005 more i-cache misses per operation against a gap of 313 cycles - while data loads are 400
higher per operation, which is the stack traffic in the loop.

## The loop, both ways

Inlined, back edge `0x7fea8c0f6544 -> 0x7fea8c0f64b0`, 33 instructions, 13 touching `(%rsp)`:

```
  0x7fea8c0f64b5:  mov    0x34(%rsp),%ecx
  0x7fea8c0f64c0:  mov    %r10,(%rsp)
  0x7fea8c0f64c8:  mov    %r9d,0x8(%rsp)
  0x7fea8c0f64de:  mov    0x34(%rsp),%r10d
  0x7fea8c0f64f0:  mov    (%rsp),%r8
  0x7fea8c0f64f4:  movzbl 0x10(%r10,%r8,1),%r10d      ; charAt
  0x7fea8c0f650d:  mov    0x50(%rsp),%r8              ; reload the escape table
  0x7fea8c0f6512:  mov    0x10(%r8,%r10,4),%ebp
  0x7fea8c0f651f:  mov    0x48(%rsp),%r8              ; reload the output buffer
  0x7fea8c0f6524:  add    (%rsp),%r8
  0x7fea8c0f6528:  mov    0x38(%rsp),%r9
  0x7fea8c0f652d:  mov    %r10b,0x11(%r9,%r8,1)       ; the one useful store
  0x7fea8c0f6532:  mov    0x34(%rsp),%r8d             ; reload the counter
  0x7fea8c0f6537:  inc    %r8d
  0x7fea8c0f653a:  mov    %r8d,0x34(%rsp)             ; store it back
  0x7fea8c0f653f:  cmp    0x40(%rsp),%r8d             ; limit, from the stack
  0x7fea8c0f6544:  jl     0x00007fea8c0f64b0
```

Not inlined, back edge `0x7f57fc0f69cf -> 0x7f57fc0f6970`, 23 instructions, 2 touching `(%rsp)`:

```
  0x7f57fc0f699d:  movzbl 0x10(%rdx,%rsi,1),%edx      ; charAt
  0x7f57fc0f69a2:  cmp    $0x7f,%edx
  0x7f57fc0f69a5:  jg     0x00007f57fc0f6b5c
  0x7f57fc0f69ab:  cmp    0x48(%rsp),%edx
  0x7f57fc0f69af:  jae    0x00007f57fc0f6ae3
  0x7f57fc0f69b5:  mov    0x10(%rdi,%rdx,4),%ebp      ; escape table, in %rdi
  0x7f57fc0f69b9:  test   %ebp,%ebp
  0x7f57fc0f69bb:  jne    0x00007f57fc0f6b88
  0x7f57fc0f69c1:  lea    (%rbx,%rsi,1),%rbp
  0x7f57fc0f69c5:  mov    %dl,0x11(%rcx,%rbp,1)       ; store, buffer in %rcx
  0x7f57fc0f69c9:  inc    %r9d                        ; counter, in %r9d
  0x7f57fc0f69cc:  cmp    %r11d,%r9d
  0x7f57fc0f69cf:  jl     0x00007f57fc0f6970
```

## Notes

`FlatSer.serialize` is annotated `@CompilerControl(DONT_INLINE)` so that the copy loop appears in that method
instead of inside `CollectionSerializer::serializeContents`, which keeps the perfasm output readable. It does
not change the result: the two benchmarks differ by the same amount with or without it, and the spill is there
either way.

The spill appears with a single `writeString` call site. Adding more string properties to `Flat`, or raising
`-p size=`, makes the difference more pronounced, because more of the run is spent inside the loop.

## Environment

```
JDK 25, Java HotSpot(TM) 64-Bit Server VM, 25+37-LTS-3491
AMD Ryzen 9 7950X 16-Core Processor
hsdis: /usr/lib64/hsdis-amd64.so (default library path, not under $JAVA_HOME)
perf with kernel.perf_event_paranoid = -1; benchmarks pinned with taskset -c 0,1
```

## Files

```
results/results.txt    the two timings
results/perfnorm.txt   instructions, cycles, IPC per op
results/perfasm.txt    the disassembly both loops were read from
```
