# App vs JMH: the inlined `_writeStringSegment` copy loops, per patch level (rf on)

Every number here was read from full C2 nmethod dumps (`-XX:CompileCommand=print` on the two
compile roots), not from perfasm regions. No profiler was attached. Files: `asm-dumps/`.

## What was dumped

| | Quarkus app (`persons/get-all-extended`, rf on, Quarkus 67076a3) | JMH `ExtendedPersonBench.serialize` |
|---|---|---|
| JDK | Oracle 25+37-LTS-3491 | same |
| strings | the app's own (`John`, `Doe`, `Gotham`, `123 Main St`, `Toyota`, `Camry`) | `strLen=0` = the same six values |
| roots printed | `ExtendedPerson$quarkusjacksonserializer::serializeContent`, `JacksonMapperUtil::serializePojo` | `ExtendedPersonSer::serializeContent`, `MapperUtil::serializePojo` |
| compilations seen | exactly one tier-4 nmethod per root per arm, no recompilation during the 40 s load | one per root per arm |
| run | `RFS=on REPS=1 scripts/collect.sh` with the two print commands in `JAVA_TOOL_OPTIONS` (-c 10, 2026-09-10 11:12–11:14) | `arms/benchmarks-<arm>.jar`, `-f 1 -wi 5 -w 2 -i 5 -r 2`, `numactl --membind=0 taskset -c 0-3` |

The earlier JMH report (`WRITESTRING-LOOPS.md`) was taken on a local JDK 28 build with
`strLen=32` and perfasm dropped the two hottest regions of the 6183 arm. All of that is superseded
by this one: same JDK as the app, same strings, whole nmethods.

## Method

`asm-dumps/locate-loops.py` finds innermost loops (a backward branch whose body has a byte
load, a byte store and an escape compare, and no `call`). Attribution to a property comes from
C2's scope annotations: in the app dumps the body instructions carry `; - …serializeContent@bci`;
in the JMH dumps (I do not know why) only safepoints carry annotations, so a main loop is
attributed from the strip-mine poll right after it and a drain from the annotated instruction
before it — the `none` JMH loops have neither and are matched to the app's by shape only (they
match instruction-for-instruction, including the stack-slot offsets). bci → property from `javap`:

| app `serializeContent@` | JMH | property |
|---|---|---|
| ExtendedPerson @210 / @252 | @186 / @222 | firstName / familyName |
| Address @68 / @113 | @62 / @101 | city / street |
| Car @68 / @113 | @62 / @101 | brand / model |

Every loop was then read (`asm-dumps/*.loops.txt` holds each one verbatim). The checkers were
proven on the loops that visibly contain the pattern before their zeros were believed: the coder
check is `cmpb $0x0,0x10(%reg)` on this JDK (String.coder is at 0x10, not 0xc as on the JDK 28
build), and #1681's range test is `lea -0x20(..); cmp $0x60; jae` (not `$0x5f; ja`).

## Main loops, per property (`insns` = instructions per iteration; `IV` = where the induction variable lives; `rsp` = stack loads+stores per iteration; `xmm` = GPR↔XMM moves per iteration)

### `none` — one 1-char loop per site, coder check and table load in every one

| property | app insns / IV / rsp / xmm | JMH insns / IV / rsp / xmm |
|---|---|---|
| firstName | 33 / `0x4(%rsp)` / 12+3 / 0 | 32 / `0x4(%rsp)` / 9+3 / 0 |
| familyName | 23 / register / 3+1 / 0 | 23 / register / 3+1 / 0 |
| city | 35 / `0x34(%rsp)` / 14+4 / 0 | 35 / `0x34(%rsp)` / 14+4 / 0 |
| street | 43 / `0x50(%rsp)` / 14+10 / 0 | 43 / `0x50(%rsp)` / 14+10 / 0 |
| brand | 34 / `0x4(%rsp)` / 12+3 / 0 | 34 / `0x4(%rsp)` / 12+3 / 0 |
| model | 45 / `(%rsp)` / 14+10 / 0 | 45 / `(%rsp)` / 14+10 / 0 |

### `6183` — peel + 2-wide strip-mined main + 1-char drain per site; no coder check; table load stays

| property | app main insns / IV / rsp / xmm | JMH main insns / IV / rsp / xmm | drain IV (app / JMH) |
|---|---|---|---|
| firstName | 32 / register / 0+0 / 4 | 29 / register / 1+0 / 0 | reg / reg |
| familyName | 44 / **`0x10(%rsp)`** / 3+2 / 10 | 31 / **`0x8(%rsp)`** / 3+1 / 0 | reg (jge/jmp form) / reg |
| city | 40 / register / 1+0 / 7 | 30 / register / 1+0 / 0 | reg / reg |
| street | 43 / **`0x10(%rsp)`** / 4+1 / 10 | 31 / **`0xc(%rsp)`** / 3+1 / 0 | **spilled** / **spilled** |
| brand | 33 / register / 1+0 / 4 | 29 / register / 1+0 / 0 | reg / reg |
| model | 42 / **`0x18(%rsp)`** / 4+1 / 9 | 31 / **`0x8(%rsp)`** / 3+1 / 0 | **spilled** / **spilled** |

### `both` — 4-wide main + 1-char drain; constant escape test (`lea -0x20; cmp $0x60; jae` / `cmp $0x22` / `cmp $0x5c`), no table, no bounds check on the table

| property | app main insns / IV / rsp / xmm | JMH main insns / IV / rsp / xmm | drain IV (app / JMH) |
|---|---|---|---|
| firstName | 55 / register / 0+0 / 4 | 51 / register / 0+0 / 0 | reg / reg |
| familyName | 62 / **`0x8(%rsp)`** / 3+1 / 8 | 54 / **`0x8(%rsp)`** / 3+1 / 0 | reg / reg |
| city | 65 / register / 1+0 / 7 | 51 / register / 0+0 / 0 | reg / reg |
| street | 69 / **`0x10(%rsp)`** / 4+1 / 13 | 54 / **`0x8(%rsp)`** / 3+1 / 0 | **spilled** / **spilled** |
| brand | 55 / register / 0+0 / 4 | 51 / register / 0+0 / 0 | reg / reg |
| model | 67 / **`0x18(%rsp)`** / 4+1 / 11 | 54 / **`0x8(%rsp)`** / 3+1 / 0 | **spilled** / **spilled** |

## What the comparison shows

1. **Loop shape per arm is the same in the app and in JMH**: `none` = 1 char/iteration with the
   `String.coder` check, the String reload and the table probe inside the loop; `6183` = the coder
   check leaves the loop (an uncommon trap before it), peel + 2-wide main + drain; `both` = 4-wide
   main with the constant test. The unroll factor moved 1 → 2 → 4 identically on both sides.

2. **The spill pattern is the same in the app and in JMH, for both patched arms.** In every
   serializer the *first* `writeString` (firstName, city, brand) keeps its induction variable in a
   register; the *second* (familyName, street, model) keeps it in a stack slot, reloaded 3–4 times
   and stored once per iteration, in the main loop and (for street/model) in the drain too. This is
   the JMH README's "Problem 3", now seen in the application's own code with the same six call
   sites. #1681 did not change it (both arms spill the same three sites).

   The `none` arm spills more (5 of 6 sites, familyName being the exception), and there app and
   JMH agree down to the stack-slot offsets.

3. **The app's loops are 3–15 instructions longer than JMH's, and the difference is GPR↔XMM
   traffic that JMH does not have.** The JMH main loops contain no `vmov*` at all; the app's
   contain 4–13 per iteration. Read from `app-both` street (69 insns): `%xmm2` holds the output
   buffer reference (`vmovd %xmm2,%ecx` before every `bastore`), `%xmm4` receives the character
   just loaded (`vmovd %ecx,%xmm4`) and hands it back after the range test (`vmovd %xmm4,%ecx`),
   `%xmm5` receives the running output pointer for the exit path. In `app-6183` familyName (44 insns)
   `%xmm4` additionally holds the escape-table reference and `%xmm3` the buffer. So in the
   application the same loops run under higher register pressure than in JMH: everything JMH
   keeps in GPRs is there, plus values parked in XMM registers. I do not know which extra live
   values in the app's compile units cause this; nothing in these dumps names them.

4. **What #1681 does to the app's loops, per character** (main-loop instructions / chars):
   firstName 16 → 13.75, familyName 22 → 15.5, city 20 → 16.25, street 21.5 → 17.25,
   brand 16.5 → 13.75, model 21 → 16.75. JMH: 14.5 → 12.75 (first properties), 15.5 → 13.5
   (second). The table probe (`cmp len; jae; mov (table,ch,4); test; jne` = one load + one bounds
   check) becomes `lea; cmp; jae; cmp; je; cmp; je` (no load), and the unroll goes 2 → 4.

5. **Throughput context** (separate runs, no profiler, no print): app rf-on medians 6183 150,112
   vs both 151,097 req/s, min–max ranges overlapping (n=3) — not separable. JMH strLen=0, one fork,
   with the print commands active: none 3785 ± 31, 6183 2909 ± 31, both 2864 ± 32 ns/op
   (99.9 % CI, 5 × 2 s) — 6183 and both differ by 1.5 % with non-overlapping intervals in this
   single fork; that is one fork, not a measurement to quote.

## Not established

- Why the JMH dumps carry scope annotations only at safepoints while the app dumps annotate
  every bytecode-bearing instruction (same JDK, same flags).
- Which live values in the app's `serializeContent`/`serializePojo` are the ones parked in XMM
  registers. Needs `-XX:+PrintOptoAssembly` on a debug build or reading the register allocation;
  not done.
- Whether the string lengths change the shape: these dumps are for the app's short strings only.

---

# Part 2 - the reflective arm (`ReflectiveBench`, rf off)

`ReflectiveBench` writes the same 20 beans through Jackson's own bean serializers, with the
mapper/writer built as Quarkus builds them (`QuarkusMapper`: `ConfigurationCustomizer` defaults,
`createDefaultWriter`, `forType(List<ExtendedPerson>)`). Nothing is annotated in Jackson: the
compile roots are C2's own choice. `ExtendedPersonBench` now uses the same `QuarkusMapper`, so the
generated arm changed too (feature flags only). JSON: 2821 bytes on both paths, identical to the
application's; the reflective path orders `familyName` before `firstName` (Jackson 3 sorts
alphabetically), the generated one the other way round - each matches its application arm.
Known difference: Quarkus's `HybridJacksonPool` recycler pool is not installed here.

## Timings, app strings, one fork × 5 × 2 s, ns/op (99.9 % CI)

| arm | reflective | generated | reflective / generated | app rf off / rf on (req/s, medians, n=3) |
|---|---:|---:|---:|---:|
| none | 3499 ± 243 | 3733 ± 20 | 0.94 (reflective faster) | 148,426 / 141,024 = rf off faster by 5.2 % |
| 1681 | 3331 ± 91 | 3064 ± 10 | 1.09 | rf off not measured |
| 6183 | 3406 ± 177 | 2855 ± 23 | 1.19 | 146,530 / 150,112 = rf on faster by 2.4 % |
| both | 3364 ± 172 | 2894 ± 307 | 1.16 | 146,032 / 151,097 = rf on faster by 3.5 % |

Direction matches the application in every arm that has both sides: unpatched, reflection wins;
patched, the generated serializers win. The reflective arm barely moves with the patches (3499 →
3364, CIs overlapping), as in the app (148k → 146k, ranges overlapping). Magnitudes are not
comparable one-to-one: JMH times the serializer alone, the app number has the HTTP stack in the
denominator. The reflective forks are noisy (± 170–240 on one fork); why is not known.

## Inlining decisions, OOTB, JMH vs app (`-XX:CompileCommand=PrintInlining` on the roots)

Identical in JMH and in the application, `none` and `both`:

- `BeanPropertyWriter.serializeAsProperty@190  ValueSerializer::serialize  failed to inline: virtual call`
  - the per-property serializer call is megamorphic (String / Integer / UnrolledBean), so
  **`StringSerializer.serialize` is compiled once, as its own C2 root**, and `writeString` +
  `_writeStringSegment` + `String.charAt` are inlined into *that* (10-byte) method. One copy loop,
  shared by all six properties - the shape of the `serializeWriteStringNotInlined` control.
- `UnrolledBeanSerializer.serialize@218 ... failed to inline: already compiled into a big method`
  (the nested Address/Car serializer) - same message both sides.
- `Invokers$Holder.invokeExact_MT` force-inlined, `UnreflectHandleSupplier.get` inlined,
  `Invokers.maybeCustomize` "don't inline by annotation" - same on both sides; the getter
  `MethodHandle` chain itself is not visible in these trees (the app flamegraph shows
  `MethodHandle.invokeBasic` as a separate compiled root at ~3 %).
- The six `serializeAsProperty` sites (bci 63…133) show a mix of `inline (hot)` and
  `callee is too large` across successive compilations on both sides; the app's rf-off flamegraph
  has them inlined in the final code.

## The one reflective copy loop (`StringSerializer::serialize` nmethod)

| | JMH none | app none | JMH both | app both |
|---|---|---|---|---|
| nmethod size (insns) | 379 | 378 | 454 | 455 |
| out-of-line `charAt` calls | 1 | 1 | 0 | 0 |
| main loop | 21 insns, 1 char, coder check + table, **IV in register, 0 stack refs** | same, 21 / 0 | 51 insns, 4-wide, constants, 0 stack refs | 56 insns, 4-wide, 1 stack ref |
| drain | - | - | 16 insns, IV reg, 1 stack ref | 17 insns, 1 stack ref |

So the reflective `none` loop is the same 1-wide polluted loop as rf-on's, but compiled in a
tiny method it keeps everything in registers (21 instructions vs 33–45 and 0 stack refs vs
12–24 for the six inlined copies). That is what the numbers show: unpatched, rf off beats rf on;
the patches remove most of what made the inlined copies expensive, and then rf on wins.

---

# Part 3 - why the app's loops carried XMM traffic and JMH's did not: loop strip mining

Same jar, same inlining tree (verified with PrintInlining, identical callee sets), same 32-bit
zero-based compressed oops, same 14 GPRs in use. The variable is the collector's *side effect*:
G1/ZGC/Shenandoah enable `UseCountedLoopSafepoints` + `LoopStripMiningIter=1000`, ParallelGC and
SerialGC leave both off (JDK 25 `-XX:+PrintFlagsFinal`).

| JMH `both`, x4 main loops | insns | vmovd |
|---|---|---|
| G1 default | 51/54 | 0 |
| ParallelGC default | 55/60/68/69 | 4-15 |
| ParallelGC + `-XX:+UseCountedLoopSafepoints -XX:LoopStripMiningIter=1000` | 51/54 | 0 |
| G1 + `-XX:-UseCountedLoopSafepoints -XX:LoopStripMiningIter=0` | 55/61/67/69 | 4-15 |
| SerialGC / ZGC | as ParallelGC / as G1 | |

Allocator-level cause (fastdebug `-XX:+TraceSpilling`, plus a temporary print of the spill pick in
`PhaseChaitin::Simplify`, since reverted): with strip mining the copy-loop blocks have C2 block
frequency 39.8, without it 5.8. Chaitin's spill score subtracts area (∝ frequency) from cost, so at
5.8 the loop's live ranges are ~7x cheaper to spill; the `_outputBuffer` load feeding the string
sites (node 556) is in the spilled set under ParallelGC (18 reaching-definition mentions) and not
under G1 (0). ParallelGC needed 3 allocation rounds vs 2. Why strip mining changes the frequency
estimate 7x was not traced.

Cost: 64-char strings, `6183`: 11,203 (ParallelGC) vs 7,780 (G1) vs 7,705 (ParallelGC + the two
flags); `both`: 8,151 / 7,443 / 7,425. App strings: within noise everywhere. App end-to-end with the
two flags (rf on, 3 rounds): none 137,603, both 145,066 - same as without (138,837 / 146,779).
Per-arm JMH numbers were re-taken under the ParallelGC fork (2 forks): app strings none 3,627,
6183 2,884, both 2,723; 64 chars 19,195 / 11,203 / 8,151.

---

# Part 4 - the per-bean accessor design (perf/pb-accessor-on-main, 7bab6dfe1e9)

App (Quarkus 999-19efffc vs its base 999-9e4ec7a, rf on, 3 rounds, no profiler):
none 139,327 vs 141,467 (overlap) - 6183 148,567 vs 149,396 (overlap) - both 147,993 vs 153,662
(ranges do NOT overlap, accessors -3.7%). #1681 does nothing for the accessor design.

Compile shape, identical in app and JMH (PrintInlining + print, no CompileCommand needed):
- StringWriter.serializeAsProperty = one C2 root (720-795 insns app / 659-743 JMH) with ONE copy
  loop: none 22 insns 1-wide coder+table, both 55 insns 4-wide constants + 16-insn drain, rsp 0-1.
- accessor.stringGetter(bean, index): `failed to inline: virtual call` (megamorphic: 3 accessor
  classes at one site); each bean's stringGetter is its own 52-insn nmethod (checkcast + if-chain).
- UnrolledBeanSerializer.serializeNonFiltered -> StringWriter.serializeAsProperty: inlined in `none`
  (monomorphic profile), `already compiled into a big method` in `both` (writer nmethod > InlineSmallCode).

Why it loses to the generated serializers once the loop is fixed (JFR, both, ParallelGC, app
strings, ns/op): its loop is cheaper (_writeStringSegment incl 631 vs 738; writeString 819 vs 1088)
but it pays stringGetter 199 + vtable stub 146 + serializeNonFiltered self 177 + serializeAsProperty
self 154 ~= 680 ns/op of dispatch the generated code does not have -> 2,983 vs 2,786 total.
JMH timings (2 forks, ParallelGC): app strings none/6183/both 3,745 / 3,009 / 2,944;
64 chars 10,238 / 10,509 / 8,595 (generated: 3,627 / 2,884 / 2,723 and 19,195 / 11,203 / 8,151).

## Part 4b - one writer class with an int-kind switch (KindWriter, JMH only)

Hypothesis: with one writer class the `prop.serializeAsProperty` site in UnrolledBeanSerializer is
monomorphic and C2 inlines the writer into the bean serializer. Two variants, `-p writers=kind`:
- big switch in serializeAsProperty: `failed to inline: hot method too big` (over FreqInlineSize).
- tiny 87-byte dispatcher + static per-kind helpers: `failed to inline: already compiled into a big
  method` - the writer is compiled first as its own root and inlines the nested-bean path
  (ObjectWriter -> writeValue -> UnrolledBeanSerializer.serialize -> ... -> serializeAsProperty),
  growing to 9,5k instructions with THREE copies of the string loop inside it (both: 4x55[0,4],
  4x67[1,15], 4x68[5,12] - spills and XMM parking are back), so the bean serializer cannot inline it.
Neither variant reaches the bean serializer. Timings (2 forks, ParallelGC, ns/op):
app strings none/both: typed 3,745/2,944 - big switch 3,150/3,172 - tiny 3,184/3,202 (generated 3,627/2,723);
64 chars: typed 10,238/8,595 - big 10,008/8,728 - tiny 9,746/8,663 (generated 19,195/8,151).
