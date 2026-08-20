#!/usr/bin/env bash
# ============================================================
#  AtTag build script (offline fallback)
#
#  The canonical build is Maven:  mvn -B clean package
#  (requires network access to Maven Central + repo.papermc.io).
#
#  This script compiles the plugin with the Eclipse Compiler
#  for Java (ECJ) against the bundled compile-only API stubs —
#  no network needed — and packages the final plugin jar with
#  Python. Then it runs the offline smoke tests.
#
#  Requires:
#    - a Java runtime (17+) as `java` on PATH, or set JAVA_BIN
#    - python3
#
#  Output: target/AtTag-1.0.0.jar  (drop into /plugins)
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

VERSION="1.0.0"

# --- locate java -------------------------------------------------------
JAVA_BIN="${JAVA_BIN:-}"
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
    if command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(command -v java)"
    else
        for cand in \
            "$HOME/.cache/toolchain/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java" \
            "$HOME/.cache/gifty-jdk/jdk4py/java-runtime/bin/java" \
            "$HOME/.local/lib/jdk4py/jdk4py/java-runtime/bin/java" \
            "/usr/lib/jvm/java-21-openjdk/bin/java"; do
            if [ -x "$cand" ]; then
                JAVA_BIN="$cand"
                break
            fi
        done
    fi
fi
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
    echo "ERROR: no java runtime found (install a JDK/JRE 17+ or set JAVA_BIN)" >&2
    exit 1
fi
echo "Using java: $JAVA_BIN"
"$JAVA_BIN" -version 2>&1 | head -1

ECJ_JAR="libs/ecj.jar"
if [ ! -f "$ECJ_JAR" ]; then
    echo "ERROR: $ECJ_JAR missing" >&2
    exit 1
fi

# --- compile -----------------------------------------------------------
rm -rf target
mkdir -p target/stub-classes target/classes target/smoke

echo "== compiling API stubs (compile-only, not packaged) =="
find stub-api/src -name '*.java' > target/stub-sources.txt
"$JAVA_BIN" -jar "$ECJ_JAR" -proc:none -nowarn -source 15 -target 15 \
    -encoding UTF-8 -d target/stub-classes @"target/stub-sources.txt"

echo "== compiling plugin =="
find src/main/java -name '*.java' > target/main-sources.txt
"$JAVA_BIN" -jar "$ECJ_JAR" -proc:none -nowarn -source 15 -target 15 \
    -encoding UTF-8 -cp target/stub-classes -d target/classes @"target/main-sources.txt"

echo "== packaging =="
python3 pack.py target/classes src/main/resources "target/AtTag-${VERSION}.jar"

# --- engine test --------------------------------------------------------
echo "== engine test =="
"$JAVA_BIN" -jar "$ECJ_JAR" -proc:none -nowarn -source 15 -target 15 \
    -encoding UTF-8 -cp target/classes -d target/smoke smoke/EngineTest.java
"$JAVA_BIN" -cp "target/classes:target/smoke" \
    dev.superseller.attag.smoke.EngineTest

# --- mock-server flow test ----------------------------------------------
echo "== mock flow test =="
"$JAVA_BIN" -jar "$ECJ_JAR" -proc:none -nowarn -source 15 -target 15 \
    -encoding UTF-8 -cp target/classes:target/stub-classes -d target/smoke smoke/MockFlowTest.java
"$JAVA_BIN" -cp "target/classes:target/stub-classes:target/smoke" \
    dev.superseller.attag.smoke.MockFlowTest

echo ""
echo "BUILD OK -> target/AtTag-${VERSION}.jar"
ls -la "target/AtTag-${VERSION}.jar"
