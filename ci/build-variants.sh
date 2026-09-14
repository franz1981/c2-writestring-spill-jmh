#!/usr/bin/env bash
# Build one benchmarks jar per escape-filter variant, by patching Jackson's UTF8JsonGenerator.
#
#   ./ci/build-variants.sh            # all arms
#   ./ci/build-variants.sh t64k gc    # just those
#
# Each jar is target/bench-<arm>.jar: the shaded benchmarks jar with the variant's
# UTF8JsonGenerator and the #6183 StdDateFormat compiled over it. See ci/gen-variants.py.
set -euo pipefail
cd "$(dirname "$0")/.."

V=$(sed -n 's/.*<jackson.version>\(.*\)<\/jackson.version>.*/\1/p' pom.xml)
[ -n "$V" ] || { echo "cannot read jackson.version from pom.xml" >&2; exit 1; }
echo "==> jackson $V"

ARMS=("$@")
[ ${#ARMS[@]} -eq 0 ] && ARMS=(base const t64k gc gct64k)

mvn -q -DskipTests package
mvn -q dependency:copy -Dartifact=tools.jackson.core:jackson-core:$V:jar:sources -DoutputDirectory=target/srcjars
mvn -q dependency:copy -Dartifact=tools.jackson.core:jackson-databind:$V:jar:sources -DoutputDirectory=target/srcjars

rm -rf target/pristine target/variants
mkdir -p target/pristine
unzip -oq target/srcjars/jackson-core-$V-sources.jar     'tools/jackson/core/json/UTF8JsonGenerator.java' -d target/pristine
unzip -oq target/srcjars/jackson-databind-$V-sources.jar 'tools/jackson/databind/util/StdDateFormat.java' -d target/pristine

python3 ci/gen-variants.py \
  target/pristine/tools/jackson/core/json/UTF8JsonGenerator.java \
  target/pristine/tools/jackson/databind/util/StdDateFormat.java \
  target/variants

SHARED=target/variants/shared/tools/jackson/databind/util/StdDateFormat.java
for arm in "${ARMS[@]}"; do
  src="target/variants/$arm/tools/jackson/core/json/UTF8JsonGenerator.java"
  [ -f "$src" ] || { echo "no such arm: $arm" >&2; exit 1; }
  rm -rf "target/out-$arm"; mkdir -p "target/out-$arm"
  cp target/benchmarks.jar "target/bench-$arm.jar"
  javac -nowarn -cp target/benchmarks.jar -d "target/out-$arm" "$src" "$SHARED"
  (cd "target/out-$arm" && jar uf "../bench-$arm.jar" .)
  echo "==> target/bench-$arm.jar"
done
