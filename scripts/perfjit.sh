#!/bin/bash
# Record a JMH benchmark under linux perf with JIT symbols and per-instruction
# annotation of the compiled code. No async-profiler.
#
#   scripts/perfjit.sh <benchmark-jar> [options]
#
# Options:
#   -b <regex>    JMH benchmark selector      (default GenShapeBench.serialize$)
#   -p <k=v>      JMH param, repeatable       (default -p len=128 -p size=20)
#   -s <symbol>   symbol substring to annotate (default the serializer's serializeContent)
#   -j <args>     extra JVM args for the forked JVM
#   -c <cpus>     taskset CPU list            (default 12-15,28-31, one NUMA node)
#   -n <node>     numactl membind node        (default 1)
#   -o <dir>      output directory            (default ./perfjit-out/<jar name>)
#   -F <hz>       perf sampling frequency     (default 3000)
#   -w <n>        warmup iterations           (default 5)
#   -i <n>        measurement iterations      (default 5)
#   -t <sec>      seconds per iteration       (default 3)
#
# Needs: perf, /usr/lib64/libperf-jvmti.so (linux-tools / perf-jvmti package).
# Produces in the output dir:
#   jmh.txt        the JMH score
#   perf.jit.data  perf data with JIT frames resolved
#   annotate.txt   per-instruction cycle percentages of the chosen symbol
#   symbols.txt    hottest symbols, and which jitted object each came from
#
# Read annotate.txt directly. Do not trust a grep of it: the same Java method can
# have several compilations (C1 profiled, C2), each its own jitted-*.so, and they
# share a symbol name. symbols.txt tells you which object holds the hot one.
set -euo pipefail

JAR=${1:?usage: perfjit.sh <benchmark-jar> [options]}; shift || true

BENCH='GenShapeBench.serialize$'
PARAMS=(); JVM=""; CPUS="12-15,28-31"; NODE=1; OUT=""; FREQ=3000
WARM=5; ITER=5; TIME=3
SYMBOL="serializeContent"

while getopts "b:p:s:j:c:n:o:F:w:i:t:" opt; do
  case $opt in
    b) BENCH=$OPTARG ;;
    p) PARAMS+=(-p "$OPTARG") ;;
    s) SYMBOL=$OPTARG ;;
    j) JVM=$OPTARG ;;
    c) CPUS=$OPTARG ;;
    n) NODE=$OPTARG ;;
    o) OUT=$OPTARG ;;
    F) FREQ=$OPTARG ;;
    w) WARM=$OPTARG ;;
    i) ITER=$OPTARG ;;
    t) TIME=$OPTARG ;;
    *) sed -n '2,30p' "$0"; exit 1 ;;
  esac
done
[ ${#PARAMS[@]} -eq 0 ] && PARAMS=(-p len=128 -p size=20)
[ -n "$OUT" ] || OUT="$PWD/perfjit-out/$(basename "$JAR" .jar)"

AGENT=/usr/lib64/libperf-jvmti.so
[ -f "$AGENT" ] || { echo "missing $AGENT (install perf-jvmti / linux-tools)"; exit 1; }
command -v perf >/dev/null || { echo "perf not found"; exit 1; }

JAR=$(readlink -f "$JAR")
rm -rf "$OUT"; mkdir -p "$OUT"; cd "$OUT"
rm -f /tmp/jmh.lock
export JITDUMPDIR="$OUT"

# -k mono is required: jitdump timestamps must share perf's clock.
# PreserveFramePointer gives perf usable stacks through JIT frames.
perf record -k mono -F "$FREQ" -o "$OUT/perf.data" -- \
  numactl --membind="$NODE" taskset -c "$CPUS" \
  java -jar "$JAR" "$BENCH" "${PARAMS[@]}" -f 1 -wi "$WARM" -i "$ITER" -w "${TIME}s" -r "${TIME}s" \
    -jvmArgsAppend "-Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+PreserveFramePointer -XX:+UnlockDiagnosticVMOptions -agentpath:$AGENT $JVM" \
  > "$OUT/jmh.txt" 2>&1 || { echo "run failed, see $OUT/jmh.txt"; tail -20 "$OUT/jmh.txt"; exit 1; }

grep -E '^Benchmark|^[A-Za-z].*avgt|thrpt' "$OUT/jmh.txt" | head -4 || true

DUMP=$(find "$OUT/.debug" -name 'jit-*.dump' 2>/dev/null | head -1)
[ -n "$DUMP" ] || { echo "no jitdump produced - did the agent load? see jmh.txt"; exit 1; }
perf inject --jit -i "$OUT/perf.data" -o "$OUT/perf.jit.data" 2>"$OUT/inject.err"

perf report -i "$OUT/perf.jit.data" --stdio --no-children -F overhead,dso,symbol \
  > "$OUT/symbols.txt" 2>/dev/null || true

FULL=$(grep -oP '(?<=\] )\S.*' "$OUT/symbols.txt" | grep -F "$SYMBOL" | head -1 || true)
if [ -z "$FULL" ]; then
  echo "symbol '$SYMBOL' not in the profile; see $OUT/symbols.txt"
else
  perf annotate -i "$OUT/perf.jit.data" --stdio -s "$FULL" > "$OUT/annotate.txt" 2>/dev/null || true
  echo
  echo "hottest symbols:"; grep -vE '^#|^$' "$OUT/symbols.txt" | head -5
  echo
  echo "annotated: $OUT/annotate.txt   ($(wc -l < "$OUT/annotate.txt") lines)"
fi
echo "output in $OUT"
