#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# RapidHoppers offline build.
#
# The canonical build is:   mvn -B clean package
# It compiles against io.papermc.paper:paper-api for Minecraft 26.2 and needs
# network access to https://repo.papermc.io.
#
# This script is the fallback for sandboxes / CI runners without that access:
# it compiles the plugin against the bundled compile-only API stubs in
# stub-api/ using the Eclipse batch compiler (libs/ecj.jar, ECJ 3.45 which supports --release 25), then runs
# the smoke tests and assembles a jar with the plugin resources.
#
# Usage:  JAVA_BIN=/path/to/java ./build.sh
# ---------------------------------------------------------------------------
set -euo pipefail

cd "$(dirname "$0")"

JAVA_BIN="${JAVA_BIN:-java}"
ECJ="${ECJ:-libs/ecj.jar}"
OUT="target/offline-classes"
SMOKE_OUT="target/smoke-classes"

if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "error: java not found. Set JAVA_BIN=/path/to/java (Java 25 required)." >&2
  exit 1
fi

if [[ ! -f "$ECJ" ]]; then
  echo "error: Eclipse compiler not found at $ECJ (set ECJ=/path/to/ecj.jar)." >&2
  exit 1
fi

echo "==> regenerating API stubs"
python3 stub-api/generate.py

echo "==> compiling plugin sources (release 25)"
rm -rf "$OUT" "$SMOKE_OUT"
mkdir -p "$OUT" "$SMOKE_OUT"
find stub-api/src src/main/java -name '*.java' >target/sources.txt
"$JAVA_BIN" -jar "$ECJ" -nowarn --release 25 -encoding UTF-8 -d "$OUT" @target/sources.txt

echo "==> compiling and running smoke tests"
find smoke -name '*.java' >target/smoke-sources.txt
"$JAVA_BIN" -jar "$ECJ" -nowarn --release 25 -encoding UTF-8 \
  -cp "$OUT" -d "$SMOKE_OUT" @target/smoke-sources.txt
"$JAVA_BIN" -cp "$OUT:$SMOKE_OUT" dev.superseller.rapidhoppers.smoke.EngineTest
"$JAVA_BIN" -cp "$OUT:$SMOKE_OUT" dev.superseller.rapidhoppers.smoke.CommandTest

echo "==> assembling target/RapidHoppers-offline.jar"
rm -rf target/jar
mkdir -p target/jar
cp -r "$OUT"/dev target/jar/
cp src/main/resources/*.yml target/jar/
sed -i.bak 's/\${project.version}/1.0.0/' target/jar/plugin.yml && rm -f target/jar/plugin.yml.bak
(cd target/jar && zip -qr ../RapidHoppers-offline.jar .)

echo "OK: target/RapidHoppers-offline.jar"
