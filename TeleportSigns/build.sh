#!/usr/bin/env bash
# Offline compile for environments without Maven Central / PaperMC.
# Canonical build remains: mvn -B clean package  (JDK 25)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

JAVA_BIN="${JAVA_BIN:-${JAVA_HOME:+$JAVA_HOME/bin/java}}"
JAVA_BIN="${JAVA_BIN:-java}"
ECJ="${ECJ:-$ROOT/../AtTag/libs/ecj.jar}"
if [[ ! -f "$ECJ" ]]; then
  ECJ="$ROOT/libs/ecj.jar"
fi

if ! command -v "$JAVA_BIN" >/dev/null 2>&1 && [[ ! -x "$JAVA_BIN" ]]; then
  echo "No Java runtime found. Set JAVA_BIN to a Java 25+ java executable." >&2
  exit 1
fi

python3 stub-api/generate.py

STUBS="${TMPDIR:-/tmp}/ts-build/stubs"
CLASSES="${TMPDIR:-/tmp}/ts-build/classes"
SMOKE="${TMPDIR:-/tmp}/ts-smoke"
rm -rf "${TMPDIR:-/tmp}/ts-build" "$SMOKE"
mkdir -p "$STUBS" "$CLASSES" "$SMOKE"

echo "Compiling API stubs..."
"$JAVA_BIN" -jar "$ECJ" -15 -encoding UTF-8 -d "$STUBS" $(find stub-api/src -name '*.java' | sort)

echo "Compiling TeleportSigns..."
"$JAVA_BIN" -jar "$ECJ" -15 -encoding UTF-8 -cp "$STUBS" -d "$CLASSES" \
  $(find src/main/java -name '*.java' | sort)

echo "Packaging jar..."
python3 pack.py

echo "Running smoke tests..."
"$JAVA_BIN" -jar "$ECJ" -15 -encoding UTF-8 -d "$SMOKE" \
  smoke/SmokeTest.java \
  src/main/java/dev/superseller/teleportsigns/util/Placeholders.java \
  src/main/java/dev/superseller/teleportsigns/util/Numbers.java \
  src/main/java/dev/superseller/teleportsigns/command/DestinationParser.java \
  src/main/java/dev/superseller/teleportsigns/model/SignCodec.java \
  src/main/java/dev/superseller/teleportsigns/model/SignKey.java \
  src/main/java/dev/superseller/teleportsigns/model/TeleportSign.java \
  src/main/java/dev/superseller/teleportsigns/model/WarpDestination.java \
  src/main/java/dev/superseller/teleportsigns/service/SafetyChecker.java
"$JAVA_BIN" -cp "$SMOKE" dev.superseller.teleportsigns.smoke.SmokeTest

echo "OK: $ROOT/target/TeleportSigns-1.0.0.jar"
