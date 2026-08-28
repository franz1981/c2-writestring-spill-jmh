# C2 spills a hot copy loop when it is inlined into a large method

Serializing the same object graph to JSON costs **42 % more** when C2 inlines
`UTF8JsonGenerator.writeString` into the caller than when it does not. The extra cost is register
allocation inside `UTF8JsonGenerator._writeStringSegment`'s ASCII copy loop.

## What is observed

Two benchmarks run identical Java code over identical data and produce identical bytes. They differ
only in one JVM flag: the second excludes `UTF8JsonGenerator.writeString` from inlining.

In the first, C2 inlines the whole serializer tree - the collection loop, the three bean serializers,
`writeString` and `_writeStringSegment` - into a single ~50 KB nmethod
(`CollectionSerializer::serializeContents`), which ends up holding one copy of the ASCII copy loop per
string property. In those copies the loop is **33-35 instructions with 14-15 stack operands per
iteration**: the loop counter is written to the stack and read back within the same iteration, and the
loop invariants (output buffer, escape table, end index) are re-loaded from the stack on every
iteration.

Compiled on its own, the same loop is **22 instructions with 2 stack operands** and keeps those values
in registers.

The loop's own live set does not change between the two: it is the same Java, with the same locals,
already written so that the fields are hoisted by hand:

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

## Results

Time for one serialization of a `List<Person>` (20 objects, 9 properties each, 3021 bytes of JSON),
3 forks x 5 iterations, no profiler attached:

| benchmark | ns/op |
|---|---:|
| `serializer` — `writeString` inlined | **4210.6 ± 37.9** |
| `serializerNotInlinedWriteString` — `writeString` not inlined | **2973.5 ± 59.9** |

Excluding the method from inlining is worth **1237 ns/op**.

The instruction count barely moves; the retirement rate does:

| benchmark | instructions/op | cycles/op | IPC |
|---|---:|---:|---:|
| `serializer` | 80,546 | 18,166 | **4.44** |
| `serializerNotInlinedWriteString` | 68,591 | 12,639 | **5.43** |

## The loop, both ways

Inlined into `CollectionSerializer::serializeContents` (~50 KB nmethod), back edge
`0x7f65f40f8f79 -> 0x7f65f40f8ec0`, 35 instructions of which 15 address `(%rsp)` (elisions marked):

```
  0x7f65f40f8ec6:  add    0x24(%rsp),%r10d
  0x7f65f40f8ecb:  mov    %r10d,0x74(%rsp)
  0x7f65f40f8ed3:  mov    %r10,0x88(%rsp)
  0x7f65f40f8edb:  mov    0x74(%rsp),%r11d
  0x7f65f40f8ee3:  mov    %r11d,0x90(%rsp)
  ...
  0x7f65f40f8f13:  mov    0x88(%rsp),%r11
  0x7f65f40f8f1b:  movzbl 0x10(%r10,%r11,1),%eax      ; charAt
  0x7f65f40f8f21:  mov    %r8d,0x94(%rsp)             ; store the counter
  ...
  0x7f65f40f8f3c:  mov    0x80(%rsp),%r10             ; reload the escape table
  0x7f65f40f8f44:  mov    0x10(%r10,%rax,4),%r8d
  0x7f65f40f8f52:  mov    0x78(%rsp),%r10             ; reload the output buffer
  0x7f65f40f8f57:  add    0x88(%rsp),%r10             ; reload the offset
  0x7f65f40f8f5f:  mov    0x10(%rsp),%r11
  0x7f65f40f8f64:  mov    %al,0x11(%r11,%r10,1)       ; the one useful store
  0x7f65f40f8f69:  mov    0x94(%rsp),%r9d             ; reload the counter, same iteration
  0x7f65f40f8f71:  inc    %r9d
  0x7f65f40f8f74:  cmp    0x20(%rsp),%r9d             ; limit, from the stack
  0x7f65f40f8f79:  jl     0x00007f65f40f8ec0
```

Compiled as its own nmethod (~1.6 KB), back edge `0x7f9d800f0faf -> 0x7f9d800f0f50`, 22 instructions of
which 2 address `(%rsp)` - the counter, the buffer and the escape table stay in registers:

```
  0x7f9d800f0f7d:  movzbl 0x10(%rdx,%rsi,1),%edx      ; charAt
  0x7f9d800f0f82:  cmp    $0x7f,%edx
  0x7f9d800f0f85:  jg     0x00007f9d800f1154
  0x7f9d800f0f8b:  cmp    0x48(%rsp),%edx
  0x7f9d800f0f8f:  jae    0x00007f9d800f10db
  0x7f9d800f0f95:  mov    0x10(%rdi,%rdx,4),%ebp      ; escape table, in %rdi
  0x7f9d800f0f99:  test   %ebp,%ebp
  0x7f9d800f0f9b:  jne    0x00007f9d800f1180
  0x7f9d800f0fa1:  lea    (%rbx,%rsi,1),%rbp
  0x7f9d800f0fa5:  mov    %dl,0x11(%rcx,%rbp,1)       ; store, buffer in %rcx
  0x7f9d800f0fa9:  inc    %r9d                        ; counter, in %r9d
  0x7f9d800f0fac:  cmp    %r11d,%r9d
  0x7f9d800f0faf:  jl     0x00007f9d800f0f50
```

Three copies of the loop are hot enough to appear in the dump, at 33/14, 34/15 and 35/15
instructions / `(%rsp)` operands.

## It is the loop body, not instruction fetch

`perfasm` attributes 11.11 % of all cycles to this single copy of the loop, and 6.04 % of all cycles to
instructions inside it that address `(%rsp)`. The hottest instruction of the body is the reload of the
induction variable:

```
   0.40%   0x…8f21:  mov    %r8d,0x94(%rsp)     ; store the counter
   4.53%   0x…8f69:  mov    0x94(%rsp),%r9d     ; reload it, same iteration
```

The frontend is not where the difference is. Per op, against a gap of 5,526 cycles:

| | `serializer` | `serializerNotInlinedWriteString` | delta |
|---|---:|---:|---:|
| L1-icache-load-misses | 1.61 | 0.20 | +1.4 |
| stalled-cycles-frontend | 473 | 195 | +278 |
| L1-dcache-loads | 27,570 | 20,597 | **+6,973** |

Instruction-fetch effects account for a few percent of the difference; the extra work is data loads. The
count matches the loop: about 7 more loads per iteration, and about 1,000 iterations per operation
(~1,000 string characters per 3021-byte document).

## Build and run

```bash
mvn clean package

java -jar target/benchmarks.jar                                                  # results.txt
java -jar target/benchmarks.jar -prof perfnorm                                   # perfnorm.txt
java -jar target/benchmarks.jar -f 1 -prof 'perfasm:hotThreshold=0.02;tooBigThreshold=4000'
```

`tooBigThreshold` matters: with the default, the ~50 KB nmethod is skipped and the spilling loop never
appears in the output. Run the two benchmarks separately for the perfasm one - dumping both in a single
JVM crashed it here (`<forked VM failed with exit code 134>`).

The only dependency is `jackson-databind`. `bench.proto.ProtoSerializers` is hand-written, in the shape
a per-bean serializer generator produces; nothing here is generated at build time.

## Environment

```
JDK 25, Java HotSpot(TM) 64-Bit Server VM, 25+37-LTS-3491
AMD Ryzen 9 7950X 16-Core Processor, 2 NUMA nodes
hsdis: /usr/lib64/hsdis-amd64.so (on the default library path, not under $JAVA_HOME)
perf with kernel.perf_event_paranoid = -1; benchmarks pinned with taskset -c 0,1
```

## Files

```
results/results.txt    the two timings, no profiler
results/perfnorm.txt   instructions, cycles, IPC per op
results/perfasm.txt    the disassembly both loops were read from
```
