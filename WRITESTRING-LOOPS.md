# The `_writeStringSegment` copy loops in three perfasm dumps

Files analysed (all in this directory):

| arm    | file               | Jackson                                | nmethods printed                         |
|--------|--------------------|----------------------------------------|------------------------------------------|
| none   | `perfasm-none.txt` | 3.1.4, no patches                      | `serializeContent` (id 1468), `serializePojo` (id 1437) |
| 6183   | `perfasm-6183.txt` | + databind #6183                       | `serializePojo` (id 1418) only (see §0)  |
| both   | `perfasm-both.txt` | + databind #6183 + core #1681          | `serializePojo` (id 1398) only           |

Everything below was read from the assembly. Grep/awk were used only to locate regions
and, at the end, to sum the per-instruction cycle percentages over address ranges that I had
already delimited by reading; the summation was validated against perfasm's own
`<total for region N>` lines (Appendix A). Syntax is AT&T, source before destination.

---

## 0. What the dumps actually are — four facts that differ from the brief

1. **`strLen = 32`, not 64.** Every dump header says `# Parameters: (strLen = 32)`. (The stray
   file `perfasm-nonext` contains the JMH error `Cannot parse argument '-pstrLen=32'`, so 32 was
   the intended value.) With 32-char strings the 4-wide main loop of the `both` arm runs at most
   7 full iterations per string; this matters for how much of the cycle share lands in the
   peel/drain code.
2. **The VM is not JDK 25 Oracle.** All three headers say
   `# VM version: JDK 28-internal, OpenJDK 64-Bit Server VM, 28-internal-adhoc.forkedfranz.loom`,
   invoked from `/home/forked_franz/CLionProjects/loom/build/linux-x86_64-server-release/images/jdk/bin/java`.
   That is a local loom-repo build. Nothing below depends on the JDK identity, but the report
   would be wrong to call it JDK 25.
3. **perfasm prints only regions above 10 % of cycles, and refuses regions longer than 1000
   lines.** In the 6183 dump the two hottest regions were *not printed*:

   ```
   ....[Hottest Region 1]....
   c2, level 4, bench.paths.sers.MapperUtil::serializePojo, version 2, compile id 1418
    <region is too big to display, has 2309 lines, but threshold is 1000>
     35.40%  <total for region 1>
   ....[Hottest Region 2]....
   c2, level 4, bench.paths.sers.ExtendedPersonSer::serializeContent, version 2, compile id 1450
    <region is too big to display, has 1212 lines, but threshold is 1000>
     21.93%  <total for region 2>
   ```

   So for the 6183 arm 57.3 % of all cycles are in code I cannot see; only Region 3 (10.52 %,
   one loop) is readable. This is the single biggest limitation of the analysis.
4. **The timings do not separate `both` from `6183`.** One fork, 5 × 1 s:

   | arm  | `serialize` (ns/op, 99.9 % CI)  | iterations                                |
   |------|---------------------------------|-------------------------------------------|
   | none | 7889.4 ± 316.6                  | 7829.8 7991.7 7933.2 7907.0 7785.5        |
   | 6183 | 5190.8 ± 448.6                  | 5364.0 5256.0 5085.6 5118.1 5130.3        |
   | both | 5045.4 ± 412.0                  | 4861.8 5089.7 5140.9 5072.1 5062.5        |

   The 6183 and both intervals overlap almost entirely; four of the five `both` measurement
   iterations (5089–5141) lie inside the 6183 range (5086–5364). The mean is 2.8 % lower for
   `both`, but this data does not establish that `both` is faster. §6 says what the *code*
   changed; the code change is real, the timing difference is not established.

---

## 1. Mapping a loop to its `writeString` call site

perfasm interleaves C2's scope annotations (`; - Class::method@bci (line N)`). I resolved the bci
with `javap -c -p -l` on `target/benchmarks.jar`:

| annotation                                    | bytecode                                      | property   |
|-----------------------------------------------|-----------------------------------------------|------------|
| `ExtendedPersonSer::serializeContent@186 (line 71)` | `186: invokevirtual writeString` (line 71 → bci 183) | firstName  |
| `ExtendedPersonSer::serializeContent@222 (line 78)` | `222: invokevirtual writeString` (line 78 → bci 219) | familyName |
| `AddressSer::serializeContent@62 (line 29)`    | `62: invokevirtual writeString` (line 29 → bci 59)   | city       |
| `AddressSer::serializeContent@101 (line 36)`   | `101: invokevirtual writeString` (line 36 → bci 98)  | street     |
| `CarSer::serializeContent@62 (line 29)`        | same layout as AddressSer                     | brand      |
| `CarSer::serializeContent@101 (line 36)`       | same layout as AddressSer                     | model      |

For every loop below the attribution comes from annotations attached to instructions *inside the
loop body* (the `baload`/`charAt`, the `bastore`, the `iinc`), not from the nearest preceding
line, and in each case the surrounding code agrees (e.g. the exit path of a `@62` loop flows into
`writeName` for `@95`, the second property's field name; the exit of a `@101` loop flows into
`writeEndObject`).

---

## 2. Arm `none` (no patches) — three loops printed

Printed regions: R1 15.92 % (`serializeContent` id 1468), R2 14.59 % (`serializePojo` id 1437),
R3 12.18 % (`serializePojo` id 1437). Each region is exactly one copy loop plus its exit.
There is **no peel, no unrolled main loop and no drain** in this arm: each call site has a single
one-character loop whose induction variable starts at 0 (R2: `xor %ecx,%ecx; mov %ecx,0x2c(%rsp)`
at `0x...3e8e9/0x...3e908`; R3: `xor %edi,%edi; mov %edi,0x4(%rsp)` at `0x...3f273/0x...3f291`;
R1's entry is outside the printed region).

### 2.1 `none` / firstName — `serializeContent@186`, R1

- **Range** `0x00007f39dbe442e0`–`0x00007f39dbe44381`, **37 instructions**, **15.69 %** of cycles
  (of the region's 15.92 %).
- **Attribution:** every scope inside the body ends in
  `; - bench.paths.sers.ExtendedPersonSer::serializeContent@186 (line 71)`.
- **Characters per iteration: 1** — one `movzbl 0xc(%r13,%r8,1),%r9d` and one
  `mov %r9b,0xd(%r10,%r11,1)`.
- **Loop-closing sequence, verbatim:**

  ```
  0x00007f39dbe44379:   inc    %r8d                         ;*iinc  _writeStringSegment@63
  0x00007f39dbe4437c:   cmp    0x2c(%rsp),%r8d
  0x00007f39dbe44381:   jl     0x00007f39dbe442e0           ;*if_icmpge  _writeStringSegment@24
  ```
  Induction variable **`%r8d`, in a register**. The loop bound (`len`) is in **`0x2c(%rsp)`**
  and is read from the stack every iteration.
- **Coder check present (pollution):**

  ```
  0x00007f39dbe442fa:   mov    (%rsp),%r10d
  0x00007f39dbe442fe:   mov    0x10(%r10),%r13d             ;*getfield value   String::charAt@8
  0x00007f39dbe44302:   cmpb   $0x0,0xc(%r10)
  0x00007f39dbe44307:   jne    0x00007f39dbe4494f           ;*ifeq  String::charAt@4
  0x00007f39dbe4432c:   mov    0x8(%r13),%ebp               ;*arraylength  StringLatin1::charAt@2
  0x00007f39dbe44330:   cmp    %ebp,%r8d
  0x00007f39dbe44333:   jae    0x00007f39dbe44a68
  ```
  The String reference is reloaded from `(%rsp)`, `String.value`, `String.coder` and
  `value.length` are reloaded, and the index is bounds-checked, every character.
- **Escape test = table load:**

  ```
  0x00007f39dbe4433f:   cmp    $0x7f,%r9d
  0x00007f39dbe44343:   jg     0x00007f39dbe44afc           ;*if_icmpgt  _writeStringSegment@38
  0x00007f39dbe44349:   cmp    %ebx,%r9d
  0x00007f39dbe4434c:   jae    0x00007f39dbe44a28
  0x00007f39dbe44352:   mov    0x30(%rsp),%r13d
  0x00007f39dbe44357:   mov    0xc(%r13,%r9,4),%ebp         ;*iaload  _writeStringSegment@45
  0x00007f39dbe4435c:   test   %ebp,%ebp
  0x00007f39dbe4435e:   jne    0x00007f39dbe44b3c           ;*ifeq  _writeStringSegment@46
  ```
  The table length is in a register here (`%ebx`); the table reference is reloaded from
  `0x30(%rsp)`.
- **Every stack reference in the body (17 per character: 10 loads, 7 stores):**

  | instruction                         | what the slot is                                                                 | kind |
  |-------------------------------------|----------------------------------------------------------------------------------|------|
  | `mov 0x4(%rsp),%r11d` … `mov %r11d,0x4(%rsp)` | five values (`0x4,0x8,0xc,0x10,0x14`) loaded into `%r11d,%edi,%ecx,%edx,%ebp` at the top and **stored back unchanged** at `0x...4430d–0x...44327`; none of the five is used in the loop (`%ebp` and `%r11` are overwritten only *after* the store-back). They are consumed on the exit path (`0x...44392–0x...4439f` reload `0x8,0xc,0x10,0x14`). I do not know what they hold. | round-trip of loop-invariant values, 5 loads + 5 stores |
  | `mov (%rsp),%r10d`                  | the String reference (source of `getfield value`/`coder`)                       | invariant reload |
  | `mov %rsi,0x38(%rsp)` then `mov 0x38(%rsp),%rsi` | `%rsi` is the loop-invariant output-pointer delta (`buffer index = offset + %rsi`); stored and reloaded *within the same iteration* with no intervening write to `%rsi` | store + reload of an invariant |
  | `mov %r9d,0x34(%rsp)`               | `%r9d = %r8d + %rax + 1` (the `lea` at `0x...442f5`), the output pointer after this character; read only on exit (`0x...44387: mov 0x34(%rsp),%ecx; inc %ecx` → `_outputTail`) | exit value, stored every iteration |
  | `mov 0x30(%rsp),%r13d`              | escape-table reference                                                          | invariant reload |
  | `mov 0x28(%rsp),%r10d`              | output-buffer reference                                                         | invariant reload |
  | `cmp 0x2c(%rsp),%r8d`               | loop bound `len`                                                                | invariant, compared from memory |

  The induction variable itself (`%r8d`) is **not** spilled in this loop.

### 2.2 `none` / brand — `CarSer::serializeContent@62`, R2

- **Range** `0x00007f39dbe3e910`–`0x00007f39dbe3e9aa`, **34 instructions**, **14.14 %**.
- **Attribution:** body scopes end in `; - bench.paths.sers.CarSer::serializeContent@62 (line 29)`;
  the exit writes `_outputTail` and the next code is `writeName` for `CarSer::serializeContent@95`.
- **1 character per iteration:** `movzbl 0xc(%r9,%r11,1),%r11d` / `mov %r11b,0xd(%r8,%r10,1)`.
- **Loop-closing sequence, verbatim — the induction variable lives in `0x2c(%rsp)`:**

  ```
  0x00007f39dbe3e998:   mov    0x2c(%rsp),%r11d
  0x00007f39dbe3e99d:   inc    %r11d                        ;*iinc  _writeStringSegment@63
  0x00007f39dbe3e9a0:   mov    %r11d,0x2c(%rsp)
  0x00007f39dbe3e9a5:   cmp    0x18(%rsp),%r11d
  0x00007f39dbe3e9aa:   jl     0x00007f39dbe3e910           ;*if_icmpge  _writeStringSegment@24
  ```
  `0x2c(%rsp)` is read **four times** per iteration (`0x...3e918`, `0x...3e946`, `0x...3e981`,
  `0x...3e998`) and written once. This is a spilled induction variable, not an invariant reload.
- **Coder check present:** `mov (%rsp),%r10d; mov 0x10(%r10),%r9d` (`0x...3e910/3e914`), then
  again `mov (%rsp),%r10d; cmpb $0x0,0xc(%r10); jne 0x00007f39dbe3fff8` (`0x...3e933–3e93c`),
  then `mov 0x8(%r9),%ebp` + `cmp %ebp,%r11d; jae` bounds check.
- **Escape test = table load**, with the table *length* also on the stack:

  ```
  0x00007f39dbe3e964:   cmp    0x5c(%rsp),%r11d
  0x00007f39dbe3e969:   jae    0x00007f39dbe40158
  0x00007f39dbe3e96f:   mov    0x20(%rsp),%r8d
  0x00007f39dbe3e974:   mov    0xc(%r8,%r11,4),%ebp         ;*iaload  _writeStringSegment@45
  0x00007f39dbe3e979:   test   %ebp,%ebp
  0x00007f39dbe3e97b:   jne    0x00007f39dbe40310
  ```
- **Stack references (15 per character: 12 loads, 3 stores):**

  | slot | what it is | evidence |
  |------|-----------|----------|
  | `0x2c(%rsp)` ×4 loads, 1 store | **induction variable** | closing sequence above; also indexes the char load via `%r11d` |
  | `(%rsp)` ×2 | String reference | feeds `getfield value` and `cmpb $0x0,0xc(%r10)` |
  | `0x1c(%rsp)` | index of the opening quote (output base) | set pre-loop at `0x...3e880: mov %r11d,0x1c(%rsp)` right after the quote store `mov %al,0xc(%r14,%r11,1)`; in the loop `add %r8d,%r10d` builds outputPtr |
  | `0x24(%rsp)`, `0x28(%rsp)` stores | outputPtr and outputPtr+1 for the exit path (`0x...3e9b0: mov 0x24(%rsp),%edi; add $0x2,%edi` → `_outputTail`) | exit values, stored every iteration |
  | `0x5c(%rsp)` | escape-table length (bounds check) | pre-loop `mov 0x8(%r11),%r10d; mov %r10d,0x5c(%rsp)` after `getfield _outputEscapes` |
  | `0x20(%rsp)` | escape-table reference | pre-loop `mov %r11d,0x20(%rsp)` after `getfield _outputEscapes` |
  | `0x60(%rsp)` | 64-bit sign-extended output base | pre-loop `movslq 0x1c(%rsp),%rdi; … mov %rdi,0x60(%rsp)` |
  | `0x4(%rsp)` | output-buffer reference | pre-loop `mov %r14d,0x4(%rsp)`; `%r14` was the buffer in the quote store |
  | `0x18(%rsp)` | loop bound `len` | pre-loop `mov %ebx,0x18(%rsp)` after `test %ebx,%ebx; jle` (the `len > 0` check) |

  Aside (not part of the loop): the pre-loop block `0x...3e8d2–3e908` materialises the constants
  `0x3a, 2, 3, 1, 0` into registers and spills them to `0x48–0x58(%rsp)`; they are reloaded on the
  exit path (`0x...3e9b7–3e9c4`). I do not know what they are for.

### 2.3 `none` / city — `AddressSer::serializeContent@62`, R3

- **Range** `0x00007f39dbe3f2a0`–`0x00007f39dbe3f329`, **32 instructions**, **11.07 %**.
- **Attribution:** body scopes end in `; - bench.paths.sers.AddressSer::serializeContent@62 (line 29)`;
  after the exit the code reads `getfield street` (`AddressSer::serializeContent@68`) and runs
  `writeName` for `@95`.
- **1 character per iteration:** `movzbl 0xc(%r10,%rbx,1),%r11d` / `mov %r11b,0xd(%rdi,%r10,1)`.
- **Loop-closing sequence, verbatim — induction variable in `0x4(%rsp)`:**

  ```
  0x00007f39dbe3f317:   mov    0x4(%rsp),%r10d
  0x00007f39dbe3f31c:   inc    %r10d                        ;*iinc  _writeStringSegment@63
  0x00007f39dbe3f31f:   mov    %r10d,0x4(%rsp)
  0x00007f39dbe3f324:   cmp    0x2c(%rsp),%r10d
  0x00007f39dbe3f329:   jl     0x00007f39dbe3f2a0           ;*if_icmpge  _writeStringSegment@24
  ```
  `0x4(%rsp)` is read four times (`0x...3f2a8`, `0x...3f2ca`, `0x...3f301`, `0x...3f317`) and
  written once per iteration.
- **Coder check present:** `mov (%rsp),%r10d; mov 0x10(%r10),%r10d` then
  `mov (%rsp),%ebx; cmpb $0x0,0xc(%rbx); jne 0x00007f39dbe3ff41`, then
  `mov 0x8(%r10),%ebp; mov 0x4(%rsp),%ebx; cmp %ebp,%ebx; jae`.
- **Escape test = table load**, table length in a register this time:

  ```
  0x00007f39dbe3f2e6:   cmp    %ecx,%r11d
  0x00007f39dbe3f2e9:   jae    0x00007f39dbe40094
  0x00007f39dbe3f2ef:   mov    0x30(%rsp),%r10d
  0x00007f39dbe3f2f4:   mov    0xc(%r10,%r11,4),%ebp        ;*iaload  _writeStringSegment@45
  0x00007f39dbe3f2f9:   test   %ebp,%ebp
  0x00007f39dbe3f2fb:   jne    0x00007f39dbe40264
  ```
- **Stack references (12 per character: 10 loads, 2 stores):** `0x4(%rsp)` ×4 loads + 1 store =
  **induction variable**; `(%rsp)` ×2 = String reference; `0x30(%rsp)` = escape table;
  `0x58(%rsp)` = 64-bit output base (pre-loop `movslq %r8d,%rsi; mov %rsi,0x58(%rsp)`; `%r8d`
  itself stays in a register and is also used as `add %r8d,%r9d`); `0x28(%rsp)` = output buffer;
  `0x2c(%rsp)` = loop bound `len`; `0x8(%rsp)` = store of outputPtr+1 for the exit path.

**Summary for `none`:** three single, non-unrolled, one-char loops; all three carry the coder
check and the escape-table load; two of the three (brand, city) keep the induction variable in a
stack slot, the third (firstName) keeps it in `%r8d` but round-trips five unrelated values through
the stack instead. 32–37 instructions and 12–17 stack accesses per character.

---

## 3. Arm `6183` — one loop visible (brand), 2-wide, no stack traffic in the main loop

Only Region 3 (10.52 %, `serializePojo` id 1418, `0x00007fab0fe3c6aa`–`0x00007fab0fe3ca98`) is
printed. It contains the complete `writeString(brand)` expansion: prologue, peel, strip-mined
main loop, drain, exit, then `writeName` for `model`. Every scope in it ends in
`; - bench.paths.sers.CarSer::serializeContent@62 (line 29)` (peel, main and drain alike); the
exit reads `getfield model` (`CarSer::serializeContent@68`).

**Structure (one call site = four pieces):**

| piece | range | insns | cycles | notes |
|-------|-------|-------|--------|-------|
| peel (char 0) | `0x...3c741`–`0x...3c76d` | 11 | 0.18 % | `movsbl 0xc(%rbx),%ecx` — no coder check; the check was done once before (`test %r11d,%r11d; jne 0x...3ed4e` at `0x...3c6f7`, `%r11d` = coder) |
| strip-mine header | `0x...3c7d1`–`0x...3c7ff` | 12 | 0.20 % | computes inner limit `min(remaining, 0x7d0)` |
| **main loop** | `0x...3c800`–`0x...3c87e` | **30** | **7.30 %** | 2 chars/iteration |
| strip-mine close (safepoint poll) | `0x...3c884`–`0x...3c896` | 6 | 0.06 % | `test %eax,(%r8)` poll, `cmp 0x10(%rsp),%ecx; jl 0x...3c7d1` |
| drain (1 char) | `0x...3c8ac`–`0x...3c8ee` | 17 | 0.28 % | |

**Main loop, verbatim (comments trimmed):**

```
0x00007fab0fe3c800:   lea    (%rcx,%r14,1),%edx
0x00007fab0fe3c804:   movslq %ecx,%r11
0x00007fab0fe3c807:   lea    0x1(%rdx),%edi
0x00007fab0fe3c80a:   movsbl 0xc(%rbx,%r11,1),%eax        ;*baload  StringLatin1::charAt@8
0x00007fab0fe3c810:   movzbl %al,%r9d                     ;*iand
0x00007fab0fe3c814:   cmp    $0x7f,%r9d
0x00007fab0fe3c818:   jg     0x00007fab0fe3e3c0           ;*if_icmpgt  _writeStringSegment@38
0x00007fab0fe3c81e:   cmp    %esi,%r9d
0x00007fab0fe3c821:   jae    0x00007fab0fe3e29c
0x00007fab0fe3c827:   mov    0xc(%rbp,%r9,4),%r9d         ;*iaload  _writeStringSegment@45
0x00007fab0fe3c82c:   test   %r9d,%r9d
0x00007fab0fe3c82f:   jne    0x00007fab0fe3e3f0           ;*ifeq  _writeStringSegment@46
0x00007fab0fe3c835:   add    $0x2,%edx
0x00007fab0fe3c838:   movslq %ecx,%r13
0x00007fab0fe3c83b:   vmovq  %xmm4,%r9
0x00007fab0fe3c840:   add    %r9,%r13
0x00007fab0fe3c843:   mov    %al,0xd(%r10,%r13,1)         ;*bastore  _writeStringSegment@62
0x00007fab0fe3c848:   movsbl 0xd(%rbx,%r11,1),%eax        ;*baload
0x00007fab0fe3c84e:   movzbl %al,%r9d
0x00007fab0fe3c852:   cmp    $0x7f,%r9d
0x00007fab0fe3c856:   jg     0x00007fab0fe3e3c4
0x00007fab0fe3c85c:   cmp    %esi,%r9d
0x00007fab0fe3c85f:   jae    0x00007fab0fe3e2a0
0x00007fab0fe3c865:   mov    0xc(%rbp,%r9,4),%r9d         ;*iaload
0x00007fab0fe3c86a:   test   %r9d,%r9d
0x00007fab0fe3c86d:   jne    0x00007fab0fe3e3f4
0x00007fab0fe3c873:   mov    %al,0xe(%r10,%r13,1)         ;*bastore
0x00007fab0fe3c878:   add    $0x2,%ecx                    ;*iinc  _writeStringSegment@63
0x00007fab0fe3c87b:   cmp    %r8d,%ecx
0x00007fab0fe3c87e:   jl     0x00007fab0fe3c800           ;*goto  _writeStringSegment@66
```

- **Characters per iteration: 2** (`movsbl 0xc(…)`/`movsbl 0xd(…)`, stores to `0xd(…)`/`0xe(…)`).
- **Loop-closing sequence:** `add $0x2,%ecx; cmp %r8d,%ecx; jl` — induction variable **`%ecx`
  in a register**, inner limit `%r8d` in a register.
- **Stack references in the main loop: none.** `%rbx` = `String.value`, `%rbp` = escape table,
  `%esi` = table length, `%r10` = output buffer, `%r14d` = output base; the 64-bit output base is
  parked in an XMM register and moved back with `vmovq %xmm4,%r9` each iteration (a register
  move, not a memory access).
- **Coder check: absent** from the loop (pollution gone). No `cmpb $0x0,0xc(…)`, no reload of
  the String reference, no `arraylength`; the `baload` is a plain `movsbl` off `%rbx`.
- **Escape test: table load** (`cmp %esi,%r9d; jae` bounds check + `mov 0xc(%rbp,%r9,4),%r9d` +
  `test/jne`), as expected without #1681.
- **Strip-mine header/close** are the only stack users: `mov 0x2c(%rsp),%r8d` (= `len`, stored at
  `0x...3c6e0` region from `String.length()`), `mov 0x10(%rsp),%r9d` / `cmp 0x10(%rsp),%ecx`
  (= `len-1`, the main-loop limit, stored at `0x...3c77c: mov %r11d,0x10(%rsp)` after
  `mov 0x2c(%rsp),%r11d; dec %r11d`). These are loop-invariant reads outside the inner loop.
- **Drain loop** `0x...3c8ac`–`0x...3c8ee`: one char, `inc %ecx; cmp 0x2c(%rsp),%ecx; jl` —
  bound read from the stack, induction variable in `%ecx`, still the table load.

Whether the other three `serializePojo` loops (city, street, model) and the two
`serializeContent` loops look like this in the 6183 arm **cannot be determined from this dump**
(the regions were not printed, §0.3).

---

## 4. Arm `both` — three main loops visible (city, brand, street), 4-wide, constant escape test

All three printed regions are in `serializePojo` (id 1398). The regions are contiguous pieces
of one nmethod (`R3 0x...3d4d0–3d86a`, `R2 0x...3d855–3ddca`, `R1 0x...3ddbf–3e288`; R3/R2
overlap by 21 bytes) and C2 has laid the four call sites out interleaved, so a region can hold
the *peel* of one site and the *main loop* of another:

| piece | site | range | insns | cycles |
|-------|------|-------|-------|--------|
| city strip-mine header | `AddressSer@62` | `0x...3d529`–`0x...3d579` | 20 | 0.40 % |
| **city main** | `AddressSer@62` | `0x...3d580`–`0x...3d659` | **52** | **6.96 %** |
| city close (poll) | | `0x...3d65f`–`0x...3d670` | 6 | 0.10 % |
| city drain | `AddressSer@62` | `0x...3d688`–`0x...3d6cb` | 17 | 1.14 % |
| street peel | `AddressSer@101` | `0x...3da18`–`0x...3da46` | 11 | 0.22 % → `jmp 0x...3dfde` |
| brand strip-mine header | `CarSer@62` | `0x...3da96`–`0x...3dad8` | 17 | 0.16 % |
| **brand main** | `CarSer@62` | `0x...3dae0`–`0x...3dbb7` | **52** | **6.29 %** |
| brand close (poll) | | `0x...3dbbd`–`0x...3dbce` | 6 | 0.10 % |
| brand drain | `CarSer@62` | `0x...3dbe4`–`0x...3dc27` | 17 | 0.85 % |
| model peel | `CarSer@101` | `0x...3df78`–`0x...3dfa5` | 11 | 0.06 % → `jmp 0x...3e296` (**not printed**) |
| street strip-mine header | `AddressSer@101` | `0x...3dfcd`–`0x...3e02a` | 23 | 0.65 % |
| **street main** | `AddressSer@101` | `0x...3e030`–`0x...3e123` | **55** | **7.43 %** |
| street close (poll) | | `0x...3e129`–`0x...3e13d` | 7 | 0.18 % |
| street drain | `AddressSer@101` | `0x...3e168`–`0x...3e1bd` | 20 | 1.20 % |

The city and brand peels are not inside any printed region (the city header is entered by a
`jmp 0x...3d53b` from a block at `0x...3d4d0` whose predecessor is not printed; the brand header
is entered at `0x...3daa3` from code not printed).

### 4.1 `both` / city — `AddressSer@62`, main loop `0x...3d580`–`0x...3d659`

Verbatim (comments trimmed; every scope in this range ends in
`; - bench.paths.sers.AddressSer::serializeContent@62 (line 29)`):

```
0x00007fed37e3d580:   lea    (%r9,%r14,1),%r11d
0x00007fed37e3d584:   movslq %r9d,%rdi
0x00007fed37e3d587:   lea    0x1(%r11),%r13d
0x00007fed37e3d58b:   movsbl 0xc(%rcx,%rdi,1),%esi        ;*baload
0x00007fed37e3d590:   movzbl %sil,%ebx
0x00007fed37e3d594:   lea    -0x20(%rbx),%edx
0x00007fed37e3d597:   cmp    $0x5f,%edx
0x00007fed37e3d59a:   ja     0x00007fed37e3e9f1           ;*if_icmpgt  _writeStringSegment@65
0x00007fed37e3d5a0:   cmp    $0x22,%ebx
0x00007fed37e3d5a3:   je     0x00007fed37e3ea30           ;*if_icmpeq  _writeStringSegment@72
0x00007fed37e3d5a9:   cmp    $0x5c,%ebx
0x00007fed37e3d5ac:   je     0x00007fed37e3ea6c           ;*if_icmpne  _writeStringSegment@79
0x00007fed37e3d5b2:   lea    0x2(%r11),%edx
0x00007fed37e3d5b6:   movslq %r9d,%rbp
0x00007fed37e3d5b9:   add    %r8,%rbp
0x00007fed37e3d5bc:   mov    %sil,0xd(%r10,%rbp,1)        ;*bastore  _writeStringSegment@113
0x00007fed37e3d5c1:   movsbl 0xd(%rcx,%rdi,1),%r13d
      … same 8-instruction test, then …
0x00007fed37e3d5e9:   mov    %r13b,0xe(%r10,%rbp,1)
0x00007fed37e3d5ee:   lea    0x3(%r11),%r13d
0x00007fed37e3d5f2:   movsbl 0xe(%rcx,%rdi,1),%esi
      … same test …
0x00007fed37e3d619:   lea    0x4(%r11),%edx
0x00007fed37e3d61d:   movslq %r11d,%rbp
0x00007fed37e3d620:   mov    %sil,0xf(%r10,%rbp,1)
0x00007fed37e3d625:   movsbl 0xf(%rcx,%rdi,1),%r11d
      … same test …
0x00007fed37e3d64d:   mov    %r11b,0x10(%r10,%rbp,1)
0x00007fed37e3d652:   add    $0x4,%r9d                    ;*iinc  _writeStringSegment@114
0x00007fed37e3d656:   cmp    %eax,%r9d
0x00007fed37e3d659:   jl     0x00007fed37e3d580           ;*goto  _writeStringSegment@117
```

- **4 characters per iteration** (loads at `0xc,0xd,0xe,0xf(%rcx,%rdi,1)`, stores at
  `0xd,0xe,0xf,0x10(%r10,%rbp,1)`), 52 instructions = 13 per character.
- **Closing:** `add $0x4,%r9d; cmp %eax,%r9d; jl` — IV **`%r9d` in a register**, inner limit
  `%eax` in a register.
- **Stack references in the main loop: none** (checked instruction by instruction; the awk
  count agrees: 0).
- **Coder check: absent.** **Escape test: constants** — `lea -0x20(%rbx),%edx; cmp $0x5f,%edx;
  ja` (unsigned `ch-0x20 > 0x5f`, i.e. `ch < 0x20 || ch > 0x7f`), `cmp $0x22` (quote),
  `cmp $0x5c` (backslash). No `iaload`, no bounds check. Note the constant is `$0x5f` with `ja`,
  not `$0x60`.
- Registers: `%rcx` = `String.value`, `%r10` = output buffer, `%r8` = 64-bit output base,
  `%r14d` = 32-bit output base (feeds the exit value `%r11d/%r13d`), `%eax` = strip-mine limit.
- Header/close/drain stack use: header stores `mov %edx,(%rsp)` and close does
  `cmp (%rsp),%r9d; jl 0x...3d529` — `(%rsp)` = main-loop limit (`len-3`); `len` itself is
  parked in `%xmm1` (`vmovd %xmm1,%r11d` on re-entry and after the loop). The drain
  `0x...3d688–3d6cb` is 17 instructions, 1 char, `inc %r9d; cmp %r11d,%r9d; jl` — IV and bound
  both in registers, **no stack access**.

### 4.2 `both` / brand — `CarSer@62`, main loop `0x...3dae0`–`0x...3dbb7`

Same shape as city (52 instructions, 4 chars, constants test, no coder check). Scopes end in
`; - bench.paths.sers.CarSer::serializeContent@62 (line 29)`; exit reads `getfield model`.

```
0x00007fed37e3dae0:   lea    (%rbx,%r14,1),%edx
0x00007fed37e3dae4:   movslq %ebx,%rbp
0x00007fed37e3dae7:   lea    0x1(%rdx),%ecx
0x00007fed37e3daea:   movsbl 0xc(%rax,%rbp,1),%esi        ;*baload
0x00007fed37e3daef:   movzbl %sil,%edi
0x00007fed37e3daf3:   lea    -0x20(%rdi),%r8d
0x00007fed37e3daf7:   cmp    $0x5f,%r8d
0x00007fed37e3dafb:   ja     0x00007fed37e3eaa7
0x00007fed37e3db01:   cmp    $0x22,%edi
0x00007fed37e3db04:   je     0x00007fed37e3eadb
0x00007fed37e3db0a:   cmp    $0x5c,%edi
0x00007fed37e3db0d:   je     0x00007fed37e3eb07
0x00007fed37e3db13:   lea    0x2(%rdx),%r8d
0x00007fed37e3db17:   movslq %ebx,%r13
0x00007fed37e3db1a:   add    %r11,%r13
0x00007fed37e3db1d:   mov    %sil,0xd(%r10,%r13,1)        ;*bastore
      … chars 1..3 at 0xd/0xe/0xf(%rax,%rbp,1) → 0xe/0xf/0x10(%r10,%r13,1) …
0x00007fed37e3dbb1:   add    $0x4,%ebx                    ;*iinc
0x00007fed37e3dbb4:   cmp    %r9d,%ebx
0x00007fed37e3dbb7:   jl     0x00007fed37e3dae0
```

- **Closing:** `add $0x4,%ebx; cmp %r9d,%ebx; jl` — IV **`%ebx` in a register**.
- **Stack references in the main loop: none.** `%rax` = `String.value`, `%r10` = buffer,
  `%r11` = 64-bit base, `%r14d` = 32-bit base, `%r9d` = strip limit.
- Header: `mov (%rsp),%edi` … `mov %edi,(%rsp)` (main limit round-trip), close:
  `cmp (%rsp),%ebx; jl 0x...3da96`. **Drain** `0x...3dbe4–3dc27`, 17 instructions:
  `inc %ebx; cmp 0x1c(%rsp),%ebx; jl` — the drain bound `len` is read from `0x1c(%rsp)`
  (one stack load per drained character); IV in `%ebx`.

### 4.3 `both` / street — `AddressSer@101`, main loop `0x...3e030`–`0x...3e123`: **the spilled one**

Scopes inside the body end in `; - bench.paths.sers.AddressSer::serializeContent@101 (line 36)`
(e.g. `0x...3e034 iadd writeString@65`, `0x...3e03f baload`, `0x...3e077 bastore`, `0x...3e11a iinc`,
`0x...3e123 goto`). The exit path writes `_outputTail`, the closing quote, then goes into
`writeEndObject` (`GeneratedSer::serialize@13`) — consistent with `street` being the last
property of `Address`. The loop is entered from the street peel in R2 via `jmp 0x00007fed37e3dfde`
(`0x...3da69`), and the strip-mine close jumps back to `0x...3dfcd`.

```
0x00007fed37e3e030:   mov    0xc(%rsp),%ecx
0x00007fed37e3e034:   add    %r14d,%ecx                   ;*iadd  writeString@65
0x00007fed37e3e037:   movslq 0xc(%rsp),%rdx
0x00007fed37e3e03c:   lea    0x1(%rcx),%edi
0x00007fed37e3e03f:   movsbl 0xc(%r8,%rdx,1),%ebx         ;*baload
0x00007fed37e3e045:   movzbl %bl,%r10d
0x00007fed37e3e049:   lea    -0x20(%r10),%r11d
0x00007fed37e3e04d:   cmp    $0x5f,%r11d
0x00007fed37e3e051:   ja     0x00007fed37e3eb30
0x00007fed37e3e057:   cmp    $0x22,%r10d
0x00007fed37e3e05b:   je     0x00007fed37e3eb88
0x00007fed37e3e061:   cmp    $0x5c,%r10d
0x00007fed37e3e065:   je     0x00007fed37e3ebe4
0x00007fed37e3e06b:   lea    0x2(%rcx),%r11d
0x00007fed37e3e06f:   movslq 0xc(%rsp),%rdi
0x00007fed37e3e074:   add    %rax,%rdi
0x00007fed37e3e077:   mov    %bl,0xd(%r9,%rdi,1)          ;*bastore
0x00007fed37e3e07c:   movsbl 0xd(%r8,%rdx,1),%ebx
      … chars 1..3, same constants test, stores to 0xe(%r9,%rdi,1), 0xf(%r9,%rcx,1), 0x10(%r9,%rcx,1) …
0x00007fed37e3e0da:   lea    0x4(%rcx),%r11d
0x00007fed37e3e0de:   movslq %ecx,%rcx
0x00007fed37e3e0e1:   mov    %bl,0xf(%r9,%rcx,1)
      …
0x00007fed37e3e111:   mov    %bl,0x10(%r9,%rcx,1)
0x00007fed37e3e116:   mov    0xc(%rsp),%ebx
0x00007fed37e3e11a:   add    $0x4,%ebx                    ;*iinc  _writeStringSegment@114
0x00007fed37e3e11d:   mov    %ebx,0xc(%rsp)
0x00007fed37e3e121:   cmp    %esi,%ebx
0x00007fed37e3e123:   jl     0x00007fed37e3e030           ;*goto  _writeStringSegment@117
```

- **4 characters per iteration**, **55 instructions** (13.75 per character).
- **Loop-closing sequence:** `mov 0xc(%rsp),%ebx; add $0x4,%ebx; mov %ebx,0xc(%rsp); cmp %esi,%ebx; jl`
  — the induction variable **lives in `0xc(%rsp)`**. It is loaded **four times** per iteration
  (`0x...3e030`, `0x...3e037`, `0x...3e06f`, `0x...3e116`) and stored once (`0x...3e11d`). The
  three extra loads are not invariants: `0x...3e037/3e06f` sign-extend the IV to index the
  string and the buffer, `0x...3e030` derives the output pointer (`IV + %r14d`). This is the
  "some copies keep the counter in a register, others in a stack slot" case from the README,
  observed directly.
- **Stack references in the main loop: 5, all the induction variable.** No other slot is
  touched; the String (`%r8`), buffer (`%r9`), 64-bit base (`%rax`), 32-bit base (`%r14d`) and
  limit (`%esi`) are all in registers. Nothing else in the loop differs from city/brand.
- **Coder check absent; escape test = constants** (same `lea -0x20 / cmp $0x5f / ja`,
  `cmp $0x22`, `cmp $0x5c`).
- **Drain** `0x...3e168–3e1bd`, 20 instructions, also spilled: `mov 0xc(%rsp),%r13d` (×1),
  `mov 0xc(%rsp),%edi`, `movslq 0xc(%rsp),%r10`, then `inc %r13d; mov %r13d,0xc(%rsp);
  cmp 0x10(%rsp),%r13d; jl` — 4 loads + 1 store of the IV and a load of the bound
  `0x10(%rsp)` (= `len`, stored at `0x...3d97f: mov %esi,0x10(%rsp)` from `String.length()`).
- Two curiosities in the surrounding code, observed, not explained:
  `0x...3e15b: mov 0xc(%rsp),%r10d` immediately followed by `0x...3e160: mov %r10d,0xc(%rsp)`
  (a load and store of the same slot with nothing in between); and the only write of the value
  `1` to `0xc(%rsp)` on the printed path is `0x...3d4d0: mov $0x1,%esi … 0x...3d4de: mov %esi,0xc(%rsp)`,
  which sits *before the city loop*. Between there and the street loop no printed instruction
  writes `0xc(%rsp)` (grep over the three regions finds writes only at `0x...3d4de`, `0x...3e11d`,
  `0x...3e160`, `0x...3e1b3`). I infer, and did not prove, that C2 assigned the street loop's
  offset phi to a stack slot and hoisted its initial store far up the method.

### 4.4 `both` / model — `CarSer@101`: peel printed, loop not

R1 contains the `writeString(model)` prologue and peel (scopes `CarSer::serializeContent@101
(line 36)`), which ends with

```
0x00007fed37e3dfa2:   movslq %r14d,%r10
0x00007fed37e3dfa5:   mov    %sil,0xd(%r11,%r10,1)        ;*bastore  _writeStringSegment@113
0x00007fed37e3dfaa:   mov    0x10(%rsp),%r10d
0x00007fed37e3dfaf:   add    $0xfffffffd,%r10d
0x00007fed37e3dfb3:   vmovd  %r10d,%xmm8
0x00007fed37e3dfb8:   cmp    $0x1,%r10d
0x00007fed37e3dfbc:   jle    0x00007fed37e3f650
0x00007fed37e3dfc2:   movslq %r14d,%rax
0x00007fed37e3dfc5:   mov    %r11,%rbp
0x00007fed37e3dfc8:   jmp    0x00007fed37e3e296
```

This is the same prologue shape as the street site's (`len-3` into `%xmm8`, `jmp` to the main
loop header). The target `0x00007fed37e3e296` is 14 bytes past the end of R1 (`0x...3e288`) and
does not occur anywhere in the printed text. The model main loop is therefore **not visible**.
The hottest-regions list has a fourth `serializePojo` region at **9.79 %** — just under the 10 %
print threshold; I infer that is where the model loop is, but I have not seen it and cannot say
whether its induction variable is spilled.

---

## 5. Cross-arm comparison of the same loop

Only `brand` (`CarSer@62`) is printed in all three arms; `city` is printed in `none` and `both`;
`firstName` only in `none`; `street` only in `both`.

| | none / brand | 6183 / brand | both / brand |
|---|---|---|---|
| range | `0x...3e910–3e9aa` | `0x...3c800–3c87e` (main) | `0x...3dae0–3dbb7` (main) |
| structure | single loop | peel + 2-wide main + drain | (peel unprinted) + 4-wide main + drain |
| chars / iteration | 1 | 2 | 4 |
| insns / iteration (per char) | 34 (34) | 30 (15) | 52 (13) |
| cycle share (main only) | 14.14 % | 7.30 % | 6.29 % |
| IV location | `0x2c(%rsp)` (4 ld + 1 st / char) | `%ecx` | `%ebx` |
| loop close | `mov 0x2c(%rsp),%r11d; inc %r11d; mov %r11d,0x2c(%rsp); cmp 0x18(%rsp),%r11d; jl` | `add $0x2,%ecx; cmp %r8d,%ecx; jl` | `add $0x4,%ebx; cmp %r9d,%ebx; jl` |
| stack refs in main body | 15 (IV, String ×2, base, table, table len, buffer, bound, 2 exit stores) | 0 | 0 |
| coder check in loop | yes (`mov (%rsp),%r10d; cmpb $0x0,0xc(%r10); jne`) | no | no |
| escape test | `cmp 0x5c(%rsp),%r11d; jae; mov 0x20(%rsp),%r8d; mov 0xc(%r8,%r11,4),%ebp; test; jne` | `cmp %esi,%r9d; jae; mov 0xc(%rbp,%r9,4),%r9d; test; jne` | `lea -0x20(%rdi),%r8d; cmp $0x5f,%r8d; ja; cmp $0x22,%edi; je; cmp $0x5c,%edi; je` |
| memory loads / char | 1 char + 1 table + ~12 stack | 1 char + 1 table | 1 char |

| | none / city | both / city | both / street (for contrast) |
|---|---|---|---|
| chars / iteration | 1 | 4 | 4 |
| insns (per char) | 32 (32) | 52 (13) | 55 (13.75) |
| IV | `0x4(%rsp)` | `%r9d` | `0xc(%rsp)` (4 ld + 1 st per 4 chars) |
| stack refs / iteration | 12 | 0 | 5 |
| coder check | yes | no | no |
| escape test | table (`mov 0x30(%rsp),%r10d; mov 0xc(%r10,%r11,4),%ebp`) | constants | constants |
| main-loop cycle share | 11.07 % | 6.96 % | 7.43 % |

What each patch changed, as visible in the brand loop:

- **#6183 (none → 6183):** the coder check, the String-reference reload, the `arraylength`
  reload and the index bounds check leave the loop; the loop unrolls ×2 and strip-mines; the
  induction variable and every invariant move into registers. 34 → 15 instructions per
  character, 15 → 0 stack accesses per character. This is by far the larger change.
- **#1681 (6183 → both):** the 5-instruction table test (`cmp/jae/mov iaload/test/jne`, one
  memory load) becomes a 7-instruction constant test (`lea/cmp/ja/cmp/je/cmp/je`, no memory
  load); the unroll factor goes from 2 to 4. 15 → 13 instructions per character. The unroll
  change is an observation; I did not verify the mechanism (a plausible reading is that the
  smaller body fits C2's unroll budget twice more, but I have no `-XX:+PrintOptoLoop`-style
  evidence for that).

---

## 6. Does `both` look better than `6183`?

**Timing:** not established — 5045 ± 412 vs 5191 ± 449 ns/op, one fork, overlapping intervals
(§0.4).

**Code:** yes, in the one loop that can be compared, and the difference is exactly two things:

1. Per character, the table probe

   ```
   cmp    %esi,%r9d
   jae    0x00007fab0fe3e29c
   mov    0xc(%rbp,%r9,4),%r9d         ;*iaload
   test   %r9d,%r9d
   jne    0x00007fab0fe3e3f0
   ```
   is replaced by

   ```
   lea    -0x20(%rdi),%r8d
   cmp    $0x5f,%r8d
   ja     0x00007fed37e3eaa7
   cmp    $0x22,%edi
   je     0x00007fed37e3eadb
   cmp    $0x5c,%edi
   je     0x00007fed37e3eb07
   ```
   — two more instructions but one fewer load and one fewer bounds check.
2. The main loop is 4-wide instead of 2-wide, so the per-iteration overhead (`lea`, `movslq`,
   `add`, `cmp`, `jl`, plus the `vmovq %xmm4,%r9` register shuffle in 6183) is amortised over
   twice as many characters.

Net: 15 → 13 instructions/char and one fewer memory load/char in the brand main loop. The
main loop's cycle share went 7.30 % → 6.29 %. That is consistent with a small improvement,
but at 32 characters per string the non-loop code (prologue, peel, drain, `writeName`,
`_verifyValueWrite`, `writeEndObject`) dominates each `writeString`, and I cannot see the
other five loops of the 6183 arm, so I cannot say whether `both` is better *overall* from the
code. What the `both` dump does show is that #1681 did **not** remove the spill problem: the
street loop (§4.3) carries a stack-resident induction variable with both patches applied.

---

## 7. Which of the six call sites appear, and why the others do not

| site | nmethod | none | 6183 | both |
|------|---------|------|------|------|
| firstName `serializeContent@186` | serializeContent | **printed** (R1, 15.69 %) | not printed (R2, 21.93 %, too big) | not printed (below 10 %) |
| familyName `serializeContent@222` | serializeContent | not printed | not printed (in R2, too big) | not printed |
| city `AddressSer@62` | serializePojo | **printed** (R3, 11.07 %) | not printed (in R1, too big) | **printed** (R3, main 6.96 %) |
| street `AddressSer@101` | serializePojo | not printed | not printed (in R1, too big) | **printed** (R1, main 7.43 %) |
| brand `CarSer@62` | serializePojo | **printed** (R2, 14.14 %) | **printed** (R3, main 7.30 %) | **printed** (R2, main 6.29 %) |
| model `CarSer@101` | serializePojo | not printed | not printed (in R1, too big) | peel printed, loop not (`jmp 0x...3e296` leaves R1) |

Evidence for the explanations:

- **The dumps are truncated by perfasm, not by C2.** Every dump lists more hot regions in
  `[Hottest Regions]` than it prints. `none`: after the three printed regions come
  `7.76 % serializePojo`, `7.59 % serializePojo`, `7.59 % serializeContent`,
  `3.45 % serializeContent`. `both`: `9.79 % serializePojo`, `9.14 % serializeContent`,
  `9.05 % serializeContent`, `5.12 %`, `2.82 %` (both serializeContent). The header says
  `Hottest code regions (>10.00% "cycles" events)`. I infer that the missing loops are in
  those regions (in `both`, the two `serializeContent` regions at 9.14 % and 9.05 % are the
  only sizeable hot regions of an nmethod whose printed hot code, in the `none` arm, was a
  `writeString` loop), but their contents are not in the file, so this is an inference, not an
  observation. In `6183` the regions were suppressed by the 1000-line cap instead (§0.3).
- **No hot separate compile of `writeString`.** `[Hottest Methods (after inlining)]` shows
  two nmethods carrying the hot code in every arm: `serializePojo` (52.6 / 54.1 / 54.5 %) and
  `serializeContent` (32.8 / 28.6 / 30.0 %). No `UTF8JsonGenerator::writeString` or
  `_writeStringSegment` nmethod appears in the top-20 list of the `serialize` benchmark (one
  does appear, as expected, in the `serializeWriteStringNotInlined` half of each file, which
  runs with `-XX:CompileCommand=dontinline,...writeString`). The list is truncated
  (`<...other 144 warm methods...>`, each below ~0.12 %), so a separate `writeString` nmethod
  that is essentially never executed cannot be excluded; what can be said is that no hot copy
  loop lives outside those two nmethods, and every printed loop carries a
  `writeString@78 → serializeContent@bci` inlining scope.
- **Peel/main/drain do not create phantom "loops" in these dumps.** Each printed site in
  6183/both is one strip-mined main loop plus a peel and a drain, all annotated with the same
  bci (§3, §4). Each printed site in `none` is one loop. Counting main loops gives the numbers
  in the table above.
- **The `none` arm shows the first String of each serializer and not the second.** firstName,
  city and brand are printed; familyName, street and model are not. I observe that the three
  printed loops have 11–16 % each and the unprinted regions have ≤ 7.8 %. I do not know why the
  second site of each pair is cooler; the strings are the same object and the same length.
  (This is a guess, I have no data for it: C2 may have shared code between the two sites'
  cold paths or laid the second loop out in a region that perfasm split, but I have not seen
  either.)

---

## 8. What I could not determine

- The shape of firstName, familyName, city, street and model in the **6183** arm (not
  printed; 57 % of cycles are in the two suppressed regions).
- The shape of firstName and familyName in the **both** arm (below the 10 % threshold).
- The shape of the **model** main loop in the `both` arm (its peel jumps to `0x...3e296`,
  outside the printed text), and therefore whether the spill in `both` affects one or two of
  the four `serializePojo` loops.
- Why C2 put the street loop's induction variable in `0xc(%rsp)` and the city/brand ones in
  registers. The dump shows the outcome, not the allocator's reasons.
- What the five values in `0x4..0x14(%rsp)` are that the `none`/firstName loop loads and
  stores back unchanged every character, and what the constants `0x3a,2,3,1,0` spilled before
  the `none`/brand loop are.
- Why the 6183 main loop is 2-wide and the `both` main loops 4-wide (observed; mechanism not
  verified).
- Whether `both` is actually faster than `6183` (timings overlap; single fork).
- The whole analysis is at `strLen=32` on a JDK 28 loom build, not at 64 on JDK 25 as the
  brief states; nothing was re-run.

---

## Appendix A. How the cycle shares were computed and checked

For each loop I first delimited the address range by reading the code (the `↗`/`╰` arrows and
the backward `jl`), then summed the leading percentage of every instruction line in that range
with a small awk script. To make sure the script measures what I think it measures I ran it on
each *whole printed region* and compared with perfasm's own `<total for region N>`:

| region | perfasm | script | note |
|--------|---------|--------|------|
| none R1 | 15.92 % | 15.93 % | |
| none R2 | 14.59 % | 14.60 % | |
| none R3 | 12.18 % | 12.19 % | |
| 6183 R3 | 10.52 % | 10.54 % | |
| both R1 | 12.30 % | 12.14 % | short by 0.16 % |
| both R2 | 11.21 % | 11.03 % | short by 0.18 %; R2 overlaps R3 by 21 bytes |
| both R3 | 10.46 % | 10.44 % | |

The two `both` regions come out 0.16–0.18 % low; I do not know why (the file has no percentage
lines without an address in those regions, so it is not a parsing miss). All per-loop shares
in this report should therefore be read as ±0.2 %. Instruction counts and `(%rsp)` operand
counts from the script matched my hand counts for every loop quoted above.

---

## Follow-up questions

Same files, same method: grep/awk only to locate and to sum, every claim below read from the
instructions. AT&T syntax. Cycle sums use the awk from Appendix A; re-validated on the three
dontinline nmethods (`none` 61.94 vs 62.07 %, `6183` 57.18 vs 57.37 %, `both` 56.75 vs 56.90 %),
so per-loop shares are again ±0.2 %.

Bytecode resolved with `javap -c -p -cp arms/benchmarks-{none,both}.jar tools.jackson.core.json.UTF8JsonGenerator`
(the per-arm jars are in `arms/`). Unpatched `_writeStringSegment(String,int,int)`:
local 2 = `offset`, local 3 = `end`, local 4 = `outputPtr`, local 5 = `outputBuffer`, local 6 = `escCodes`;
`@29 invokevirtual charAt`, `@38 if_icmpgt` (`ch > 0x7F`), `@45 iaload` (`escCodes[ch]`), `@46 ifeq`,
**`@56 iinc 4,1` = `outputPtr++`**, `@62 bastore`, **`@63 iinc 2,1` = `++offset`**, `@66 goto`.
With #1681 (`both` jar): `@22–27 aload 6; invokestatic CharTypes.get7BitOutputEscapes; if_acmpne`
(is the table the default one?), `@56/@65/@72/@79` the constant compares (`<0x20`, `>0x7F`, `==0x22`, `==0x5C`),
`@107 iinc 4,1`, `@113 bastore`, `@114 iinc 2,1`.

Layout facts used throughout (read from the code, all arms): the opening quote is stored at
`0xc(buf,B,1)` = `buf[B]` where `B` = `_outputTail` before the quote (e.g. 6183 R3
`0x...3c6af: mov 0x38(%r8),%r14d ;*getfield _outputTail` … `0x...3c6d8: mov %r9b,0xc(%r10,%r14,1) ;*bastore writeString@73`);
`_outputTail` is then set to `B+1`, so `outputPtr` at loop entry is `B+1`, and character `i` is
stored at `buf[B+1+i]` = `0xd(buf, B+i, 1)`. Byte-array length is at `0x8(base)`; byte element
scale is `,1)`, the `int[]` escape table's is `,4)`.

### Q1. Why does the 6183 main loop contain two `add $0x2`?

**Answer: one loop, unrolled ×2; the two `add $0x2` advance two different Java locals, each
by the two increments of the unrolled body folded into one add.** `add $0x2,%ecx` is
`++offset` (local 2, `iinc @63`) — the induction variable. `add $0x2,%edx` is the `outputPtr`
local (local 4, `iinc @56`). It is not a strip-mine outer increment, not a peeled iteration, and
not two adjacent loops.

The loop is `writeString(brand)` = `CarSer::serializeContent@62`, in `serializePojo` id 1418,
Region 3, main loop `0x00007fab0fe3c800`–`0x00007fab0fe3c87e` (30 instructions, 7.30 %).
Body verbatim (innermost scope kept):

```
0x00007fab0fe3c800:   lea    (%rcx,%r14,1),%edx           ; edx = i + B            (= outputPtr - 1)
0x00007fab0fe3c804:   movslq %ecx,%r11                    ; r11 = i (64-bit)
0x00007fab0fe3c807:   lea    0x1(%rdx),%edi               ; edi = i + B + 1        (= outputPtr at iteration entry)
0x00007fab0fe3c80a:   movsbl 0xc(%rbx,%r11,1),%eax        ;*baload  StringLatin1::charAt@8      value[i]
0x00007fab0fe3c810:   movzbl %al,%r9d                     ;*iand    StringLatin1::charAt@12
0x00007fab0fe3c814:   cmp    $0x7f,%r9d
0x00007fab0fe3c818:   jg     0x00007fab0fe3e3c0           ;*if_icmpgt  _writeStringSegment@38
0x00007fab0fe3c81e:   cmp    %esi,%r9d                    ; esi = escCodes.length
0x00007fab0fe3c821:   jae    0x00007fab0fe3e29c
0x00007fab0fe3c827:   mov    0xc(%rbp,%r9,4),%r9d         ;*iaload  _writeStringSegment@45      escCodes[ch]
0x00007fab0fe3c82c:   test   %r9d,%r9d
0x00007fab0fe3c82f:   jne    0x00007fab0fe3e3f0           ;*ifeq    _writeStringSegment@46
0x00007fab0fe3c835:   add    $0x2,%edx                    ; edx = i + B + 2        (= outputPtr after the 1st iinc 4,1)
0x00007fab0fe3c838:   movslq %ecx,%r13
0x00007fab0fe3c83b:   vmovq  %xmm4,%r9                    ; xmm4 = (long) B
0x00007fab0fe3c840:   add    %r9,%r13                     ; r13 = i + B
0x00007fab0fe3c843:   mov    %al,0xd(%r10,%r13,1)         ;*bastore _writeStringSegment@62      buf[B+1+i]
0x00007fab0fe3c848:   movsbl 0xd(%rbx,%r11,1),%eax        ;*baload                              value[i+1]
0x00007fab0fe3c84e:   movzbl %al,%r9d
0x00007fab0fe3c852:   cmp    $0x7f,%r9d
0x00007fab0fe3c856:   jg     0x00007fab0fe3e3c4
0x00007fab0fe3c85c:   cmp    %esi,%r9d
0x00007fab0fe3c85f:   jae    0x00007fab0fe3e2a0
0x00007fab0fe3c865:   mov    0xc(%rbp,%r9,4),%r9d         ;*iaload
0x00007fab0fe3c86a:   test   %r9d,%r9d
0x00007fab0fe3c86d:   jne    0x00007fab0fe3e3f4
0x00007fab0fe3c873:   mov    %al,0xe(%r10,%r13,1)         ;*bastore                             buf[B+1+i+1]
0x00007fab0fe3c878:   add    $0x2,%ecx                    ;*iinc    _writeStringSegment@63      offset += 2
0x00007fab0fe3c87b:   cmp    %r8d,%ecx
0x00007fab0fe3c87e:   jl     0x00007fab0fe3c800           ;*goto    _writeStringSegment@66
```

How each `add $0x2` was identified:

- `%ecx` is the induction variable: it indexes both `baload`s (through `%r11`), both stores
  (through `%r13`), and is the operand of the loop-closing `cmp %r8d,%ecx; jl`. Its increment
  carries the `iinc @63` (`++offset`) annotation.
- `%edx` is `outputPtr`: with `%rcx = i` and `%r14d = B` (the quote index, see layout facts),
  `lea (%rcx,%r14,1),%edx` gives `i+B = outputPtr-1`, `lea 0x1(%rdx),%edi` gives `outputPtr`,
  and `add $0x2,%edx` gives `outputPtr+1`, which is the value of local 4 after the first
  `iinc 4,1` of the unrolled body, i.e. the store index of the second character. The stores
  themselves do **not** use `%edx`/`%edi`: they address through `%r13 = i + B` with
  displacements `0xd`/`0xe`, so `outputPtr` is only carried as a value, not used as a pointer.
  The perfasm annotation on the `add` (`;*aload_1 @27`) is the loop-head scope, not `iinc @56`;
  the identification rests on the arithmetic, not on the annotation.
- Consumers: `%edi` (outputPtr at entry) feeds the strip-mine close
  `0x...3c884: mov %edi,%r11d; 0x...3c887: add $0x2,%r11d` right before the safepoint poll
  (`0x...3c88b–3c88f`, scope `(reexecute) _writeStringSegment@66`), and the no-drain exit
  `0x...3c8f0: add $0x2,%edi` → `0x...3c8f8: mov %edi,0x38(%r11) ;*putfield _outputTail`
  (`i+B+3 = B+1+(i+2)` = `outputPtr` after two characters — consistent). `%edx` after the
  `add` has **no consumer in the printed region** (grep over `0x...3c835–3ca98` for `%edx`/`%rdx`
  finds only its redefinition at `0x...3c8b5` and unrelated later uses); the only code that
  can consume it is the second character's three out-of-region exits (`jg 0x...3e3c4`,
  `jae 0x...3e2a0`, `jne 0x...3e3f4`), which need `outputPtr` for `_outputTail = outputPtr`
  at bci 69 / the deopt state. That it is kept for those exits is an inference; that it *is*
  `outputPtr+1` is arithmetic.
- The same construct is in the dontinline 6183 nmethod (id 1369), where the register **is**
  visibly consumed as `outputPtr`: `0x...36432: add $0x2,%r11d` in the loop, then after the
  loop `0x...36476: mov %r11d,%ebx; 0x...36479: inc %ebx ;*iinc _writeStringSegment@56` and
  (no-drain path) `0x...364d9: inc %r11d` → `0x...364ea: mov %r11d,0x38(%r10) ;*putfield _outputTail`.
  `@56` is `iinc 4,1` = `outputPtr++` (javap above).

Ruling out the other candidates from the code:

- *Strip-mined outer loop with its own increment:* the outer loop is `0x...3c7d1`–`0x...3c896`.
  Its header recomputes the inner limit — `mov 0x2c(%rsp),%r8d` (len); `sub %ecx,%r8d; dec %r8d`
  (len-1-i); `cmp %ecx,%r9d; cmovl %r11d,%r8d` (0 if `len-1 < i`); `cmp $0x7d0,%r8d; cmova %r9d,%r8d`
  (min with 2000); `add %ecx,%r8d` — and its close is `cmp 0x10(%rsp),%ecx; jl 0x...3c7d1`. It
  has no increment of its own; `%ecx` simply carries over. No `add $0x2` there.
- *Peeled iteration:* the peel (`0x...3c741`–`0x...3c76d`) handles character 0 with a constant
  index (`movsbl 0xc(%rbx),%ecx`, store `0xd(%r10,%r9,1)` with `%r9 = B`) and contains no add.
- *Two adjacent loops misread as one:* the only other loop is the drain (`0x...3c8ac`–`0x...3c8ee`),
  which advances by `inc %ecx` (1, not 2); the `add $0x2,%edi` at `0x...3c8f0` is straight-line
  exit code (see above), outside any loop.

Not determinable: the contents of `0x...3e3c0/3e3c4/3e3c6` and `0x...3e3f0/3e3f4/3e3f6` (the
per-character exits; the addresses are 4 and 2 bytes apart, so they are tiny stubs, but they are
not printed). Whether the other five 6183 call sites have the same shape (unprinted regions, §0.3).

### Q2. Where did `String`'s coder check go, and is there a deoptimisation?

(Per the corrected brief: `ch > 0x7F` is Jackson's data-dependent break test and is treated
separately at the end; the question is about `String.charAt`'s `isLatin1()` test.)

**Answer: with #6183 the coder check is hoisted out of the loop into a single `test coder,coder;
jne` per `writeString` call, placed before the peel, and its taken branch goes to an
uncommon-trap block — i.e. a non-LATIN1 string deoptimises. It is not gone: it is executed once
per call.** The evidence that the target is an uncommon trap is that, in the 6183 arm, the same
target address is also the `implicit exception: dispatches to` address of a null check, and C2
only emits an implicit null check when the null path contains an uncommon-trap call. The trap
blocks themselves are cold and not printed in any dump; no `uncommon_trap`/`Deoptimization`
annotation appears anywhere in the three files (the only `{runtime_call}` annotations are
`ic_miss_blob` on each nmethod entry and `Stub::jbyte_arraycopy_stub` in `writeName`), which is a
statement about what perfasm printed, not about the nmethods.

**6183, dontinline nmethod (id 1369) — the one place where load, test and null-check dispatch are
all visible, verbatim:**

```
0x00007f5e23e362c0:   mov    0x10(%rdx),%r13d             ; implicit exception: dispatches to 0x00007f5e23e3686c
                                                          ;*getfield value   String::length@1
0x00007f5e23e362c4:   mov    0x8(%r13),%ecx               ; implicit exception: dispatches to 0x00007f5e23e36824
                                                          ;*arraylength      String::length@4     ecx = value.length
0x00007f5e23e362c8:   movsbl 0xc(%rdx),%esi               ;*getfield coder   String::coder@7      esi = coder
0x00007f5e23e362cc:   mov    0x40(%r10),%ebp              ;*getfield _outputMaxContiguous
0x00007f5e23e362d0:   sarx   %esi,%ecx,%r11d              ;*ishr             String::length@9     len = value.length >> coder
   ...
0x00007f5e23e36329:   mov    0x1c(%r10),%edx              ;*getfield _outputEscapes
0x00007f5e23e3632d:   test   %esi,%esi                    ; coder != LATIN1(0) ?
0x00007f5e23e3632f:   jne    0x00007f5e23e366ae
0x00007f5e23e36335:   test   %ecx,%ecx                    ; value.length == 0 ?
0x00007f5e23e36337:   jbe    0x00007f5e23e366ae
0x00007f5e23e3633d:   mov    0xc(%rsp),%esi               ; len
0x00007f5e23e36341:   dec    %esi
0x00007f5e23e36343:   mov    %esi,0x4(%rsp)               ; len-1
0x00007f5e23e36347:   cmp    %ecx,%esi                    ; len-1 >= value.length ?
0x00007f5e23e36349:   jae    0x00007f5e23e366ae
0x00007f5e23e3634f:   mov    0x8(%rdx),%ecx               ; implicit exception: dispatches to 0x00007f5e23e366ae
                                                          ;*iaload  _writeStringSegment@45       ecx = escCodes.length
0x00007f5e23e36352:   cmp    (%rsp),%r11d                 ; outputPtr >= buf.length ?
0x00007f5e23e36356:   jae    0x00007f5e23e366ae
   ...
0x00007f5e23e36384:   movsbl 0xc(%r13),%r10d              ;*baload  StringLatin1::charAt@8       peel: value[0], no coder test
```

The coder is loaded once (`0x...362c8`), used by `String.length()` (`sarx`), and tested once
(`0x...3632d`). Its taken branch, the empty-array guard, the hoisted input range check and the
first output range check all go to `0x00007f5e23e366ae`, and so does the implicit null check
on `_outputEscapes` at `0x...3634f`. From the loom tree this JDK was built from,
`src/hotspot/share/opto/lcm.cpp` (`PhaseCFG::implicit_null_check`):

```
  // Search the exception block for an uncommon trap.
  // (See Parse::do_if and Parse::do_ifnull for the reason
  // we need an uncommon trap.  Briefly, we need a way to
  // detect failure of this optimization, as in 6366351.)
  ...
      if (nn->is_MachCall() &&
          nn->as_MachCall()->entry_point() == OptoRuntime::uncommon_trap_blob()->entry_point()) {
  ...
    if (!found_trap) {
      // We did not find an uncommon trap.
      return;
    }
```

So the block at `0x...366ae` contains a call to the uncommon-trap blob, and the `jne` from the
coder test lands in that same block. The loop itself (`0x...36400`–`0x...36474`, quoted in Q3)
contains no coder access, no `String` reload and no `arraylength`.

**6183, inlined (`serializePojo` id 1418, R3, brand):** same guard sequence, same shared target,
but the loads of `%r11d` and `%ecx` are before the region start (`0x...3c6aa`):

```
0x00007fab0fe3c6f3:   mov    0x1c(%r9),%ebp               ;*getfield _outputEscapes
0x00007fab0fe3c6f7:   test   %r11d,%r11d
0x00007fab0fe3c6fa:   jne    0x00007fab0fe3ed4e
0x00007fab0fe3c700:   test   %ecx,%ecx
0x00007fab0fe3c702:   jbe    0x00007fab0fe3ed4e
0x00007fab0fe3c708:   mov    0x2c(%rsp),%r11d
0x00007fab0fe3c70d:   dec    %r11d
0x00007fab0fe3c710:   cmp    %ecx,%r11d
0x00007fab0fe3c713:   jae    0x00007fab0fe3ed4e
0x00007fab0fe3c719:   mov    0x8(%rbp),%esi               ; implicit exception: dispatches to 0x00007fab0fe3ed4e
                                                          ;*iaload  _writeStringSegment@45
0x00007fab0fe3c71c:   cmp    0x4(%rsp),%edi
0x00007fab0fe3c720:   jae    0x00007fab0fe3ed4e
   ...
0x00007fab0fe3c741:   movsbl 0xc(%rbx),%ecx               ;*baload  StringLatin1::charAt@8       peel
```

That `%r11d` is the coder and `%ecx` is `value.length` is **inferred** from the guard sequence
being instruction-for-instruction the same as the two cases where the loads are visible (the
dontinline nmethod above and `both` below); I did not see the loads. (§3 of the original report
stated `%r11d` = coder as fact; it should have been labelled an inference.) The implicit
null-check dispatch sharing `0x...3ed4e` makes that block an uncommon trap by the same lcm.cpp
argument. Region 3 also shows the peel/main/drain loops with no coder access.

**both, inlined (`serializePojo` id 1398):** the preheaders of the model (`CarSer@101`, R1) and
street (`AddressSer@101`, R2) sites are printed. Model, verbatim:

```
0x00007fed37e3debf:   mov    0x10(%r13),%ecx              ; implicit exception: dispatches to 0x00007fed37e40c90
                                                          ;*getfield value   String::length@1
0x00007fed37e3dec8:   mov    0x8(%rcx),%r8d               ; implicit exception: dispatches to 0x00007fed37e40a40
                                                          ;*arraylength      String::length@4     r8d = value.length
0x00007fed37e3decc:   movsbl 0xc(%r13),%r10d              ;*getfield coder   String::coder@7      r10d = coder
0x00007fed37e3ded9:   sarx   %r10d,%r8d,%esi              ;*ishr             String::length@9
   ...
0x00007fed37e3df1e:   mov    0x1c(%rsi),%ebp              ;*getfield _outputEscapes
0x00007fed37e3df21:   cmp    $0xff190830,%ebp             ;   {oop([I{0x00000000ff190830})}     #1681: table == default table?
0x00007fed37e3df27:   jne    0x00007fed37e3f9d0           ;*if_acmpne  _writeStringSegment@27
0x00007fed37e3df2d:   mov    0x10(%rsp),%esi
0x00007fed37e3df31:   test   %esi,%esi
0x00007fed37e3df33:   jle    0x00007fed37e3e909           ;*if_icmpge  _writeStringSegment@39     len > 0
0x00007fed37e3df39:   test   %r10d,%r10d                  ; coder != 0 ?
0x00007fed37e3df3c:   jne    0x00007fed37e3f628
0x00007fed37e3df42:   test   %r8d,%r8d
0x00007fed37e3df45:   jbe    0x00007fed37e3f628
0x00007fed37e3df4b:   dec    %esi
0x00007fed37e3df4d:   cmp    %r8d,%esi
0x00007fed37e3df50:   jae    0x00007fed37e3f628
0x00007fed37e3df56:   cmp    %r9d,%edx
0x00007fed37e3df59:   jae    0x00007fed37e3f628
0x00007fed37e3df5f:   movslq %edx,%r10
0x00007fed37e3df62:   movslq %r9d,%r8
0x00007fed37e3df65:   movslq 0x10(%rsp),%rsi
0x00007fed37e3df6a:   lea    -0x1(%r10,%rsi,1),%r10
0x00007fed37e3df6f:   cmp    %r8,%r10
0x00007fed37e3df72:   jae    0x00007fed37e3f628
0x00007fed37e3df78:   movsbl 0xc(%rcx),%esi               ;*baload  StringLatin1::charAt@8       peel
```

Street (`0x...3d96d: movsbl 0xc(%r14),%r10d ;*getfield coder` … `0x...3d9da: test %r10d,%r10d;
jne 0x00007fed37e3f5f8`, then the same four guards to `0x...3f5f8`) is identical in shape. Here
there is no implicit-null-check dispatch to the shared target (#1681 tests the table by identity
against a constant oop, so its length is never loaded and no null check is needed), so the
lcm.cpp argument does not apply directly; what the code shows is five semantically different
failures (UTF-16 string, empty `value`, input index range, two output index ranges) jumping to
**one** address. Five distinct in-method continuations cannot share one block; a block that
discards the compiled frame can. I take `0x...3f628` / `0x...3f5f8` to be uncommon traps on that
basis plus the 6183 evidence, and label it an inference. The city/brand/street main loops and
drains (§4) contain no coder access; the both-arm dontinline nmethod (id 1371) has the same
preheader (`0x...364c6: movsbl 0xc(%r11),%esi ;*getfield coder` … `0x...36530: test %esi,%esi;
jne 0x00007ff497e36938`, four more guards to `0x...36938`) and no coder access in its loops.

**`none`, for contrast:** the check is inside every loop, per character, and its taken branch
goes to an in-method address: dontinline `0x...37449: cmpb $0x0,0xc(%r10); 0x...3744e: jne
0x00007fd0a3e37537 ;*ifeq String::charAt@4`; inlined `jne 0x...4494f` (R1), `jne 0x...3fff8`
(R2), `jne 0x...3ff41` (R3). None of those targets is printed. Since the polluted profile
records both `charAt` branches as taken, the `StringUTF16.charAt` path has to be compiled
in-method; I did not see it.

**The `ch > 0x7F` / escape test (Jackson's own break):** `none`/`6183`: `cmp $0x7f,%r9d; jg <addr>`
per character, then the table probe `cmp %esi,%r9d; jae` / `mov 0xc(%rbp,%r9,4),%r9d; test; jne`.
`both`: `lea -0x20(%r10),%r11d; cmp $0x5f,%r11d; ja <addr>` — bytecodes `@58 if_icmplt 0x20` and
`@65 if_icmpgt 0x7F` folded into one unsigned compare (`(ch-0x20) >u 0x5f`) — then
`cmp $0x22; je`, `cmp $0x5c; je`. All taken targets are in-method, cold, and unprinted
(6183: `0x...3e3c0/3e3c4/3e3c6` and `0x...3e3f0/3e3f4/3e3f6`, `0x...3fa9a/3faa3` for the peel;
both/city: `0x...3e9f1/3ea30/3ea6c`; both/dontinline: `0x...36800/36846/3688a` etc.). No trap
annotation near any of them, and none is expected: these branches are valid control flow to
bci 69/120 (`_outputTail = outputPtr` and the slow `_writeStringSegment2` path).

Not determinable: the deopt reason/action of the trap blocks (the `mov $trap_request` +
call is not printed); whether a trap ever fired during the runs; the contents of every
out-of-region target listed above; the coder load in the 6183 inlined region.

### Q3. `serializeWriteStringNotInlined`: `none` vs `6183`

**Answer: the two out-of-line `writeString` compilations are not nearly identical. #6183 changed
the not-inlined nmethod in the same way it changed the inlined loops: the coder test, the
`String.value` reload and the `arraylength` reload leave the loop, the input range check is
hoisted to the preheader, the loop unrolls ×2 and is strip-mined. What #6183 did *not* have to
fix here is spilling: the `none` not-inlined loop already keeps its induction variable in a
register and touches the stack zero times per character.** The timings are
`none` 5853.1 ± 585.7 vs `6183` 5087.3 ± 386.8 ns/op (one fork, 5 × 1 s); the 99.9 % intervals
overlap, but the raw iterations do not (`none` 5718.7–6080.1, `6183` 5018.2–5259.9). The brief's
premise that the timings "suggest it changed little" is not what the file shows: −13 % on the
mean, versus −34 % for the inlined `serialize` (7889 → 5191).

`none`, `UTF8JsonGenerator::writeString` id 1380, the only loop, `0x00007fd0a3e37440`–`0x00007fd0a3e37496`,
**21 instructions, 1 character, 54.65 % of cycles** (region 62.07 %), verbatim:

```
0x00007fd0a3e37440:   mov    0x10(%r10),%r9d              ;*getfield value   String::charAt@8     r10 = String, reloaded value
0x00007fd0a3e37444:   lea    0x1(%rcx,%rdi,1),%r8d        ; outputPtr = i + B + 1
0x00007fd0a3e37449:   cmpb   $0x0,0xc(%r10)               ; coder check, per character
0x00007fd0a3e3744e:   jne    0x00007fd0a3e37537           ;*ifeq   String::charAt@4
0x00007fd0a3e37454:   mov    0x8(%r9),%ebp                ; implicit exception: dispatches to 0x00007fd0a3e3781c
                                                          ;*arraylength  StringLatin1::charAt@2   value.length, per character
0x00007fd0a3e37458:   cmp    %ebp,%ecx
0x00007fd0a3e3745a:   jae    0x00007fd0a3e375dc           ; input range check, per character
0x00007fd0a3e37460:   movzbl 0xc(%r9,%rcx,1),%r9d         ;*invokevirtual charAt  _writeStringSegment@29
0x00007fd0a3e37466:   cmp    $0x7f,%r9d
0x00007fd0a3e3746a:   jg     0x00007fd0a3e37618           ;*if_icmpgt  _writeStringSegment@38
0x00007fd0a3e37470:   cmp    %esi,%r9d                    ; esi = escCodes.length (loaded pre-loop at 0x...37413)
0x00007fd0a3e37473:   jae    0x00007fd0a3e375a8           ; table range check, per character
0x00007fd0a3e37479:   mov    0xc(%rdx,%r9,4),%ebp         ;*iaload  _writeStringSegment@45
0x00007fd0a3e3747e:   test   %ebp,%ebp
0x00007fd0a3e37480:   jne    0x00007fd0a3e37644           ;*ifeq    _writeStringSegment@46
0x00007fd0a3e37486:   movslq %ecx,%rbp
0x00007fd0a3e37489:   add    %r13,%rbp                    ; r13 = (long) B
0x00007fd0a3e3748c:   mov    %r9b,0xd(%rbx,%rbp,1)        ;*bastore _writeStringSegment@62      rbx = outputBuffer
0x00007fd0a3e37491:   inc    %ecx                         ;*iinc    _writeStringSegment@63
0x00007fd0a3e37493:   cmp    %r11d,%ecx                   ; r11d = len
0x00007fd0a3e37496:   jl     0x00007fd0a3e37440
```

`6183`, `writeString` id 1369: peel `0x...36384`–`0x...363b5` (13 insns, 1.00 %), strip-mine header
`0x...363ce`–`0x...363f8` (12 insns, 0.89 %), **main loop `0x00007f5e23e36400`–`0x00007f5e23e36474`,
29 instructions, 2 characters, 41.23 %**, close+poll `0x...36476`–`0x...36487` (6 insns, 0.79 %),
drain `0x...36498`–`0x...364d7` (16 insns, 1.33 %), prologue up to the peel 9.15 %, exit 2.13 %.
Main loop verbatim:

```
0x00007f5e23e36400:   lea    (%r9,%r14,1),%r11d           ; r11d = i + B  (outputPtr - 1)
0x00007f5e23e36404:   movslq %r9d,%r10
0x00007f5e23e36407:   lea    0x1(%r11),%edi               ; edi = outputPtr
0x00007f5e23e3640b:   movsbl 0xc(%r13,%r10,1),%esi        ;*baload  StringLatin1::charAt@8       r13 = value (hoisted)
0x00007f5e23e36411:   movzbl %sil,%ebx                    ;*iand
0x00007f5e23e36415:   cmp    $0x7f,%ebx
0x00007f5e23e36418:   jg     0x00007f5e23e365c4           ;*if_icmpgt
0x00007f5e23e3641e:   cmp    %ecx,%ebx                    ; ecx = escCodes.length
0x00007f5e23e36420:   jae    0x00007f5e23e3657d
0x00007f5e23e36426:   mov    0xc(%rdx,%rbx,4),%ebx        ;*iaload
0x00007f5e23e3642a:   test   %ebx,%ebx
0x00007f5e23e3642c:   jne    0x00007f5e23e365f8           ;*ifeq
0x00007f5e23e36432:   add    $0x2,%r11d                   ; outputPtr + 1 (see Q1)
0x00007f5e23e36436:   movslq %r9d,%rdi
0x00007f5e23e36439:   add    %rbp,%rdi                    ; rbp = (long) B
0x00007f5e23e3643c:   mov    %sil,0xd(%r8,%rdi,1)         ;*bastore                             r8 = outputBuffer
0x00007f5e23e36441:   movsbl 0xd(%r13,%r10,1),%r10d       ;*baload
0x00007f5e23e36447:   movzbl %r10b,%ebx
0x00007f5e23e3644b:   cmp    $0x7f,%ebx
0x00007f5e23e3644e:   jg     0x00007f5e23e365c9
0x00007f5e23e36454:   cmp    %ecx,%ebx
0x00007f5e23e36456:   jae    0x00007f5e23e36582
0x00007f5e23e3645c:   mov    0xc(%rdx,%rbx,4),%ebx        ;*iaload
0x00007f5e23e36460:   test   %ebx,%ebx
0x00007f5e23e36462:   jne    0x00007f5e23e365fd
0x00007f5e23e36468:   mov    %r10b,0xe(%r8,%rdi,1)        ;*bastore
0x00007f5e23e3646d:   add    $0x2,%r9d                    ;*iinc    _writeStringSegment@63
0x00007f5e23e36471:   cmp    %eax,%r9d                    ; eax = strip-mine limit
0x00007f5e23e36474:   jl     0x00007f5e23e36400
```

Side by side:

| | `none` id 1380 | `6183` id 1369 |
|---|---|---|
| structure | single loop | peel + strip-mined main + drain |
| unroll | 1 char / iteration | 2 chars / iteration |
| insns per character | 21 | 14.5 (29 / 2); drain 16 |
| main-loop cycle share | 54.65 % | 41.23 % (+ peel/header/close/drain 4.0 %) |
| coder check | per character, `cmpb $0x0,0xc(%r10); jne 0x...37537` (in-method target) | once, pre-peel, `test %esi,%esi; jne 0x...366ae` (uncommon-trap block, Q2) |
| `String.value` load | per character (`mov 0x10(%r10),%r9d`) | once (`0x...362c0`), kept in `%r13` |
| `value.length` load + input range check | per character (`mov 0x8(%r9),%ebp; cmp %ebp,%ecx; jae`) | once, pre-peel (`test %ecx,%ecx; jbe` + `dec; cmp %ecx,%esi; jae`), Q4 |
| escape test | table: `cmp %esi,%r9d; jae; mov 0xc(%rdx,%r9,4),%ebp; test; jne` | identical form: `cmp %ecx,%ebx; jae; mov 0xc(%rdx,%rbx,4),%ebx; test; jne` |
| output range check | hoisted (pre-loop `cmp %ebp,%r8d; jae 0x...376e0` + 64-bit form), Q4 | hoisted (`cmp (%rsp),%r11d; jae 0x...366ae` + 64-bit form to `0x...366a4`) |
| induction variable | `%ecx`, register (`inc %ecx; cmp %r11d,%ecx; jl`) | `%r9d`, register (`add $0x2,%r9d; cmp %eax,%r9d; jl`); drain `inc %r9d; cmp 0xc(%rsp),%r9d; jl` |
| loop bound | `%r11d` register (= `len`) | main: `%eax` register (strip limit); drain: `0xc(%rsp)` (= `len`) |
| `(%rsp)` operands in the loop body | **0** | **0** (main); 1 in the drain (`cmp 0xc(%rsp),%r9d`), 1 in the close (`cmp 0x4(%rsp),%r9d`), 2 in the strip header (`mov 0xc(%rsp),%eax`, `mov 0x4(%rsp),%r10d`) |
| `(%rsp)` operands in the whole printed nmethod | 1 (the stack bang `mov %eax,-0x14000(%rsp)`) | 16 |
| frame | `sub $0x50,%rsp` | `sub $0x50,%rsp` |

What the 6183 stack slots are (all written in the prologue, all loop-invariant):
`0xc(%rsp)` = `len` (`0x...362ee: mov %r11d,0xc(%rsp)` after `sarx`), `(%rsp)` = `outputBuffer.length`
(`0x...36304: mov 0x8(%r8),%edx; 0x...36308: mov %edx,(%rsp)`), `0x4(%rsp)` = `len-1`
(`0x...36343`). Six values are parked in XMM registers instead of the stack:
`vmovd %edi,%xmm3` (`_outputEnd`), `vmovq %rbx,%xmm1` (the String), `vmovq %r10,%xmm0` (`this`),
`vmovd %edx,%xmm4` (quote char), `vmovq %r10,%xmm2` (buffer), `vmovq %r10,%xmm5` (table); they
are moved back on exit (`0x...364dc–364e6`). None of this is inside the main loop.

The `none` loop is what a `none` inlined loop looks like *without* the spills: compare §2.2
(brand, 34 instructions and 15 stack operands per character, induction variable in `0x2c(%rsp)`)
with the 21-instruction, 0-stack-operand loop above. That is an observation about the two
compilations; I did not measure what share of the 7889 → 5853 ns/op difference between
`serialize` and `serializeWriteStringNotInlined` in the `none` arm it accounts for.

For completeness, `both` dontinline (id 1371): 4-wide main loop `0x...36610`–`0x...366eb`
(52 insns, 36.87 %), drain `0x...36724`–`0x...36762` (16 insns, 5.06 %, one stack read of `len`
at `0x8(%rsp)`), constant escape test, no coder access, IV `%ebx` in a register, 0 stack operands
in the main loop; 5182.3 ± 354.7 ns/op, overlapping `6183`'s 5087.3 ± 386.8.

Not determinable: the in-method targets of the `none` coder branch (`0x...37537`) and of the
per-character exits; whether the −13 % is attributable to the loop change alone (single fork,
no counters).

### Q4. Bounds checks: which array, what they look like, when they run, where they went

Three arrays can be checked in this loop: the **input** `String.value` (`byte[]`, read by
`StringLatin1.charAt`: `movzbl/movsbl 0xc(value, i, 1)`), the **output** `outputBuffer`
(`byte[]`, written `mov %xx, 0xd(buf, i+B, 1)`), and the **escape table** `escCodes`
(`int[]`, read `mov 0xc(table, ch, 4)`, only in arms without #1681). Each is recognised by the
base register of the access it guards and the length load `mov 0x8(base),reg` (`arraylength`)
or the register/slot that length was copied to. Every taken branch listed below goes to an
address outside the printed regions; where that address is shared with an
`implicit exception: dispatches to` I say "uncommon trap" on the lcm.cpp basis from Q2,
otherwise "not seen".

Two C2 preconditions matter for what could be hoisted, read from
`src/hotspot/share/opto/loopTransform.cpp`, `PhaseIdealLoop::do_range_check`, in the loom tree:
the check's limit (the array length) must be loop-invariant (`// Both inputs are loop varying;
cannot RCE`), and the index must be `is_scaled_iv_plus_offset(rc_exp, trip_counter, …)`, i.e.
linear in the induction variable.

#### `none`

*Input `String.value` — per character, in every loop, not eliminated.* The String is
reloaded from the stack, `value` reloaded from the String, and `value.length` reloaded from the
array on every character, so the limit is not loop-invariant and RCE cannot apply. Verbatim:

```
R1 firstName (0x...442fa): mov (%rsp),%r10d ; mov 0x10(%r10),%r13d ;*getfield value
   0x00007f39dbe4432c:   mov    0x8(%r13),%ebp               ;*arraylength  StringLatin1::charAt@2
   0x00007f39dbe44330:   cmp    %ebp,%r8d                    ; i vs value.length
   0x00007f39dbe44333:   jae    0x00007f39dbe44a68           ; not seen
   0x00007f39dbe44339:   movzbl 0xc(%r13,%r8,1),%r9d
R2 brand (0x...3e910): mov (%rsp),%r10d ; mov 0x10(%r10),%r9d
   0x00007f39dbe3e942:   mov    0x8(%r9),%ebp                ;*arraylength
   0x00007f39dbe3e946:   mov    0x2c(%rsp),%r11d             ; i (spilled IV)
   0x00007f39dbe3e94b:   cmp    %ebp,%r11d
   0x00007f39dbe3e94e:   jae    0x00007f39dbe4018c           ; not seen
R3 city (0x...3f2a0): mov (%rsp),%r10d ; mov 0x10(%r10),%r10d
   0x00007f39dbe3f2c6:   mov    0x8(%r10),%ebp                ;*arraylength
   0x00007f39dbe3f2ca:   mov    0x4(%rsp),%ebx                ; i (spilled IV)
   0x00007f39dbe3f2ce:   cmp    %ebp,%ebx
   0x00007f39dbe3f2d0:   jae    0x00007f39dbe400c4           ; not seen
dontinline (0x...37440): mov 0x10(%r10),%r9d ;*getfield value
   0x00007fd0a3e37454:   mov    0x8(%r9),%ebp                ;*arraylength
   0x00007fd0a3e37458:   cmp    %ebp,%ecx
   0x00007fd0a3e3745a:   jae    0x00007fd0a3e375dc           ; not seen
```

I would expect this check to be hoisted for a loop over a final field of an invariant object,
and it is not; the visible reason is that the `value`/`length` loads sit inside the loop next
to the per-character coder test. Whether the polluted `charAt` profile (the merged UTF-16
path) is what keeps those loads in the loop I did not verify — the 6183 arm removes the
pollution and the loads leave the loop together, which is consistent with that but is not a
measurement of the mechanism.

*Escape table — per character, in every loop, not eliminable:* the index is `ch`, a data
value, not a function of the induction variable (`is_scaled_iv_plus_offset` fails).

```
R1:  0x00007f39dbe44349:   cmp    %ebx,%r9d       ; ebx = escCodes.length (loaded before the region)
     0x00007f39dbe4434c:   jae    0x00007f39dbe44a28
     0x00007f39dbe44352:   mov    0x30(%rsp),%r13d ; table ref
     0x00007f39dbe44357:   mov    0xc(%r13,%r9,4),%ebp   ;*iaload
R2:  0x00007f39dbe3e964:   cmp    0x5c(%rsp),%r11d ; 0x5c(%rsp) = escCodes.length, stored pre-loop:
                                                   ;   0x...3e8a1: mov 0x8(%r11),%r10d ; 0x...3e8a5: mov %r10d,0x5c(%rsp)
     0x00007f39dbe3e969:   jae    0x00007f39dbe40158
     0x00007f39dbe3e96f:   mov    0x20(%rsp),%r8d  ; table ref
     0x00007f39dbe3e974:   mov    0xc(%r8,%r11,4),%ebp
R3:  0x00007f39dbe3f2e6:   cmp    %ecx,%r11d
     0x00007f39dbe3f2e9:   jae    0x00007f39dbe40094
     0x00007f39dbe3f2ef:   mov    0x30(%rsp),%r10d
     0x00007f39dbe3f2f4:   mov    0xc(%r10,%r11,4),%ebp
dontinline: 0x00007fd0a3e37470: cmp %esi,%r9d ; 0x...37473: jae 0x00007fd0a3e375a8   (esi from 0x...37413: mov 0x8(%rdx),%esi)
```

*Output `outputBuffer` — already hoisted in `none`.* No store in any `none` loop is guarded
(`mov %r9b,0xd(%r10,%r11,1)` R1, `mov %r11b,0xd(%r8,%r10,1)` R2, `mov %r11b,0xd(%rdi,%r10,1)` R3,
`mov %r9b,0xd(%rbx,%rbp,1)` dontinline). The check is done once in the preheader as a pair of
guards — first store index `< buf.length` and last store index `< buf.length` — that jump to
an uncommon-trap block. R2 preheader, verbatim (`%r9d` = `buf.length`: it also guards the
quote store `0x...3e867: cmp %r9d,%r11d; jae 0x...406d0; 0x...3e870: mov %al,0xc(%r14,%r11,1)`;
`%ebx` = `len`, the value of `test %ebx,%ebx; jle` and of the loop bound `0x18(%rsp)`):

```
0x00007f39dbe3e8a1:   mov    0x8(%r11),%r10d              ; implicit exception: dispatches to 0x00007f39dbe40ad4   (escCodes null check)
0x00007f39dbe3e8aa:   cmp    %r9d,%edi                    ; outputPtr vs buf.length   (edi = outputPtr: inferred, set before the region)
0x00007f39dbe3e8ad:   jae    0x00007f39dbe40ad4
0x00007f39dbe3e8b3:   movslq %edi,%r10
0x00007f39dbe3e8b6:   movslq %r9d,%r11
0x00007f39dbe3e8b9:   movslq %ebx,%r8
0x00007f39dbe3e8bc:   lea    -0x1(%r10,%r8,1),%r10        ; outputPtr + len - 1
0x00007f39dbe3e8c1:   cmp    %r11,%r10                    ; vs buf.length (64-bit)
0x00007f39dbe3e8c4:   jae    0x00007f39dbe40ad4           ; same block as the implicit null check -> uncommon trap
```

dontinline preheader, same shape, with every operand visible (`%rbx` = buffer from
`0x...3737d: mov 0x50(%rsi),%ebx ;*getfield _outputBuffer`, `%ebp` = its length from
`0x...373f8: mov 0x8(%rbx),%ebp`, `%r8d` = `outputPtr` from `0x...373f0: lea 0x1(%rdi),%r8d`
which is also stored to `_outputTail`, `%r11d` = `len`):

```
0x00007fd0a3e37413:   mov    0x8(%rdx),%esi               ; implicit exception: dispatches to 0x00007fd0a3e376e0
0x00007fd0a3e37416:   cmp    %ebp,%r8d
0x00007fd0a3e37419:   jae    0x00007fd0a3e376e0
0x00007fd0a3e3741f:   movslq %r11d,%r9
0x00007fd0a3e37422:   movslq %r8d,%rcx
0x00007fd0a3e37425:   lea    -0x1(%rcx,%r9,1),%r9
0x00007fd0a3e3742a:   movslq %ebp,%rcx
0x00007fd0a3e3742d:   cmp    %rcx,%r9
0x00007fd0a3e37430:   jae    0x00007fd0a3e376e0           ; -> uncommon trap
```

R3's preheader is cut by the region boundary (only its last `jae 0x00007f39dbe40a88` at
`0x...3f251` is printed); R1's is not printed at all. Their loops store unguarded, so the same
hoisting is inferred for them, not seen.

#### `6183`

*Input — hoisted to the preheader, as two guards, then absent from peel, main and drain.*
Guard 1: `value.length != 0` (the peel reads `value[0]`); guard 2: `len-1 < value.length`
(the last index). Both jump to the uncommon-trap block. dontinline, verbatim (`%ecx` =
`value.length` from `0x...362c4: mov 0x8(%r13),%ecx ;*arraylength`, `0xc(%rsp)` = `len`):

```
0x00007f5e23e36335:   test   %ecx,%ecx
0x00007f5e23e36337:   jbe    0x00007f5e23e366ae
0x00007f5e23e3633d:   mov    0xc(%rsp),%esi
0x00007f5e23e36341:   dec    %esi
0x00007f5e23e36343:   mov    %esi,0x4(%rsp)
0x00007f5e23e36347:   cmp    %ecx,%esi
0x00007f5e23e36349:   jae    0x00007f5e23e366ae           ; -> uncommon trap (shared with the implicit null check at 0x...3634f)
```

Inlined R3 brand: `0x...3c700: test %ecx,%ecx; jbe 0x...3ed4e` and
`0x...3c708: mov 0x2c(%rsp),%r11d; dec %r11d; cmp %ecx,%r11d; jae 0x...3ed4e` (`%ecx` =
`value.length` inferred, load not printed; `0x2c(%rsp)` = `len`). In the loops: the `baload`s
are plain `movsbl 0xc(%rbx,%r11,1)` / `movsbl 0xd(%rbx,%r11,1)` (inlined) and
`movsbl 0xc(%r13,%r10,1)` / `0xd(...)` (dontinline) with no compare before them; the drains
(`0x...3c8b0`, `0x...36498`) likewise.

*Output — hoisted, same two-guard shape as in `none`.* Inlined R3 (`0x4(%rsp)` = `buf.length`:
it guards the quote store `0x...3c6c8: cmp 0x4(%rsp),%r14d; jae 0x...3e7d8; … 0x...3c6d8: mov %r9b,0xc(%r10,%r14,1)`;
`%edi` = `outputPtr` = `B+1` from `0x...3c6c0: lea 0x1(%r14),%edi` stored to `_outputTail`):

```
0x00007fab0fe3c71c:   cmp    0x4(%rsp),%edi
0x00007fab0fe3c720:   jae    0x00007fab0fe3ed4e
0x00007fab0fe3c726:   movslq %edi,%r9
0x00007fab0fe3c729:   movslq 0x4(%rsp),%rcx
0x00007fab0fe3c72e:   movslq 0x2c(%rsp),%rdx
0x00007fab0fe3c733:   lea    -0x1(%r9,%rdx,1),%r9
0x00007fab0fe3c738:   cmp    %rcx,%r9
0x00007fab0fe3c73b:   jae    0x00007fab0fe3ed4e           ; -> uncommon trap
```

dontinline: `0x...36352: cmp (%rsp),%r11d; jae 0x...366ae` and
`0x...3636a: movslq 0xc(%rsp),%r10; movslq %r11d,%rbx; lea -0x1(%rbx,%r10,1),%r10; movslq (%rsp),%rbx;
cmp %rbx,%r10; jae 0x00007f5e23e366a4` — note the second guard's target `0x...366a4` is 10 bytes
before `0x...366ae`; I do not know whether it is a separate trap or a stub that falls into the
same one. Stores in peel/main/drain are unguarded.

*Escape table — per character, unchanged from `none`:* inlined `cmp %esi,%r9d; jae 0x...3e29c`
/ `0x...3e2a0` (main), `0x...3e2a2` (drain), `0x...3ed78` (peel), with `%esi` =
`escCodes.length` from `0x...3c719: mov 0x8(%rbp),%esi`; dontinline `cmp %ecx,%ebx; jae`
with `%ecx` from `0x...3634f`. Same data-dependent index, same reason it cannot be hoisted.

The other five 6183 inlined sites are in the unprinted regions; nothing can be said about them.

#### `both`

*No range check of any kind inside any printed loop or drain* (city, brand, street main loops
§4.1–4.3; both-dontinline main `0x...36610`–`0x...366eb` and drain `0x...36724`–`0x...36762`).
Input and output are hoisted exactly as in 6183 — model preheader `0x...3df42: test %r8d,%r8d;
jbe 0x...3f628` / `0x...3df4b: dec %esi; cmp %r8d,%esi; jae 0x...3f628` (input;
`%r8d` = `value.length` from `0x...3dec8: mov 0x8(%rcx),%r8d`) and `0x...3df56: cmp %r9d,%edx;
jae 0x...3f628` / `0x...3df5f–3df72` 64-bit form (output; `%r9d` = `buf.length`: it guards the
quote store `0x...3df0c: cmp %r9d,%r14d; jae 0x...3f104; 0x...3df19: mov %al,0xc(%r11,%r14,1)`);
street preheader `0x...3d9e3`–`0x...3da12` identical in shape; dontinline
`0x...36538`–`0x...36567` identical with `%edx` = `value.length`, `(%rsp)` = `buf.length`,
`0x8(%rsp)` = `len`. The escape table is never indexed on the fast path — #1681's
`if_acmpne` (`cmp $0xff190830,%ebp {oop([I…)}; jne`, once per call) selects the constant-compare
branch — so there is no table check to hoist or keep.

#### Summary

| check | `none` inlined | `none` dontinline | `6183` inlined (brand) | `6183` dontinline | `both` (all printed) |
|---|---|---|---|---|---|
| input `value[i]` | per char, length reloaded per char | per char, length reloaded per char | preheader, 2 guards → trap | preheader, 2 guards → trap | preheader, 2 guards → trap |
| output `buf[B+1+i]` | preheader, 2 guards → trap (R2 seen; R1/R3 inferred) | preheader, 2 guards → trap | preheader, 2 guards → trap | preheader, 2 guards → trap | preheader, 2 guards → trap |
| table `escCodes[ch]` | per char | per char | per char | per char | none (table not indexed) |

Not determinable: the contents of any taken-branch target (all outside the printed regions);
the mechanism that keeps the `value`/`length` loads inside the `none` loops; whether
`0x...366a4` and `0x...366ae` in the 6183 dontinline nmethod are one trap or two; the
input/output guards of the `none` R1 and R3 sites and of every unprinted 6183 site.
