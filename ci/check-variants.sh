#!/usr/bin/env bash
# Every arm must emit byte-identical JSON to the unpatched library.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target/esccheck
cat > target/esccheck/EscCheck.java <<'JAVA'
import tools.jackson.databind.json.JsonMapper;
public class EscCheck {
    public static void main(String[] a) {
        Object[] vals = { "plain", "quote\"here", "back\\slash", "tab\tnl\n", "ctrl\u0001x",
                          "caf\u00e9", "\u20ac euro", "emoji \uD83D\uDE00", "del\u007f", "\u0080high" };
        StringBuilder sb = new StringBuilder();
        for (Object v : vals) { sb.append(JsonMapper.builder().build().writeValueAsString(v)).append((char) 10); }
        System.out.print(sb);
    }
}
JAVA
javac -cp target/benchmarks.jar -d target/esccheck target/esccheck/EscCheck.java
java -cp target/benchmarks.jar:target/esccheck EscCheck > target/esccheck/ref.txt
rc=0
for j in target/bench-*.jar; do
  arm=$(basename "$j" .jar); arm=${arm#bench-}
  java -cp "$j:target/esccheck" EscCheck > "target/esccheck/$arm.txt"
  if diff -q target/esccheck/ref.txt "target/esccheck/$arm.txt" >/dev/null; then
    echo "$arm OK"
  else
    echo "$arm MISMATCH"; diff target/esccheck/ref.txt "target/esccheck/$arm.txt" | head -5; rc=1
  fi
done
exit $rc
