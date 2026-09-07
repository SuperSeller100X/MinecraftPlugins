#!/usr/bin/env bash
# Offline build + test for PlayerBank.
#
# No network and no JDK? This script still does everything possible locally:
#   1. resource consistency checks (message keys, config paths, permissions)
#   2. compiles the pure-logic classes (Amount, AmountParser, ChestLayout)
#      plus the smoke tests against ../AtTag/libs/ecj.jar on any JVM 17+
#   3. runs the smoke tests
#
# The full `mvn clean package` against the real paper-api runs in CI
# (.github/workflows/build.yml).
#
# Usage: ./build.sh            (auto-detects a java executable)
#        JAVA_BIN=/path/java ./build.sh
set -euo pipefail

cd "$(dirname "$0")"

PYTHON_BIN="${PYTHON_BIN:-python3}"
if [ -z "${JAVA_BIN:-}" ]; then
  JAVA_BIN="$(command -v java || true)"
fi
ECJ_JAR="../AtTag/libs/ecj.jar"

echo "== PlayerBank offline checks =="
"$PYTHON_BIN" tools/check_consistency.py

echo
echo "== Pure-logic smoke tests =="
if [ -z "$JAVA_BIN" ]; then
  echo "no java executable found — skipping smoke tests (they run in CI)"
  exit 0
fi
if [ ! -f "$ECJ_JAR" ]; then
  echo "$ECJ_JAR not found — skipping smoke tests (they run in CI)"
  exit 0
fi

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

"$JAVA_BIN" -jar "$ECJ_JAR" -source 11 -target 11 -warn:none -d "$OUT" \
  src/main/java/dev/superseller/playerbank/gui/Amount.java \
  src/main/java/dev/superseller/playerbank/gui/AmountParser.java \
  src/main/java/dev/superseller/playerbank/gui/ChestLayout.java \
  smoke/AmountParserTest.java \
  smoke/ChestLayoutTest.java

"$JAVA_BIN" -cp "$OUT" dev.superseller.playerbank.smoke.AmountParserTest
"$JAVA_BIN" -cp "$OUT" dev.superseller.playerbank.smoke.ChestLayoutTest
echo "all offline checks passed"
