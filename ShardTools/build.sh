#!/usr/bin/env bash
# ============================================================
#  ShardTools offline build (no Maven Central access needed).
#
#  Compiles the full plugin source against the compile-only API
#  stubs in stub-api/ using the Eclipse compiler (ECJ) running on
#  a Java 25 runtime (jdk4py, or any installed JDK), runs the
#  pure-logic smoke tests and packages target/ShardTools-1.0.0.jar.
#
#  The canonical build is Maven (pom.xml) on JDK 25 - see the CI
#  workflow and README. This script exists for offline/sandboxed
#  environments and produces an equivalent jar.
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

# --- locate a Java 25+ runtime -------------------------------------------
find_java() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME/bin/java"; return
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java; return
  fi
  if command -v python3 >/dev/null 2>&1; then
    local candidate
    candidate="$(python3 - <<'PY'
try:
    import jdk4py, pathlib
    print(pathlib.Path(jdk4py.__file__).parent / 'java-runtime' / 'bin' / 'java')
except Exception:
    pass
PY
)" || true
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then
      echo "$candidate"; return
    fi
  fi
  echo ""
}

JAVA_BIN="$(find_java)"
if [ -z "$JAVA_BIN" ]; then
  echo "error: no Java runtime found (install JDK 25 or 'pip install jdk4py')" >&2
  exit 1
fi
echo "Using java: $JAVA_BIN"
"$JAVA_BIN" -version

# --- locate the Eclipse batch compiler ------------------------------------
find_ecj() {
  if [ -n "${ECJ_JAR:-}" ] && [ -f "$ECJ_JAR" ]; then echo "$ECJ_JAR"; return; fi
  if [ -f "libs/ecj.jar" ]; then echo "libs/ecj.jar"; return; fi
  if [ -f "../AtTag/libs/ecj.jar" ]; then echo "../AtTag/libs/ecj.jar"; return; fi
  echo ""
}
ECJ="$(find_ecj)"
if [ -z "$ECJ" ]; then
  echo "error: ECJ compiler jar not found (expected libs/ecj.jar or ../AtTag/libs/ecj.jar, or set ECJ_JAR)" >&2
  exit 1
fi
echo "Using ECJ: $ECJ"

# --- generate stubs --------------------------------------------------------
python3 stub-api/generate.py

# --- compile plugin + stubs ------------------------------------------------
rm -rf build/classes build/smoke target
mkdir -p build/classes build/smoke
# shellcheck disable=SC2046
"$JAVA_BIN" -jar "$ECJ" -source 15 -target 15 -encoding UTF-8 -proc:none -nowarn \
  -d build/classes \
  $(find src/main/java stub-api/src -name '*.java')
echo "Plugin compiled against stub API."

# --- compile + run smoke tests (pure logic only, no stubs on classpath) ----
PURE="src/main/java/dev/superseller/shardtools/util/Numbers.java \
src/main/java/dev/superseller/shardtools/util/TimeWords.java \
src/main/java/dev/superseller/shardtools/expiry/ExpiryMath.java \
src/main/java/dev/superseller/shardtools/tools/AreaPlane.java \
src/main/java/dev/superseller/shardtools/tools/TreeFeller.java \
src/main/java/dev/superseller/shardtools/shop/PriceBook.java"
# shellcheck disable=SC2086
"$JAVA_BIN" -jar "$ECJ" -source 15 -target 15 -encoding UTF-8 -proc:none -nowarn \
  -d build/smoke smoke/SmokeTest.java $PURE
"$JAVA_BIN" -cp build/smoke dev.superseller.shardtools.smoke.SmokeTest

# --- package jar -----------------------------------------------------------
mkdir -p target
VERSION="$(grep -m1 '<version>' pom.xml | sed -e 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d ' \r')"
python3 - "$VERSION" <<'PY'
import pathlib, sys, zipfile
version = sys.argv[1] if len(sys.argv) > 1 else "1.0.0"
root = pathlib.Path(".")
out = root / "target" / f"ShardTools-{version}.jar"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as jar:
    jar.writestr("META-INF/MANIFEST.MF",
                 "Manifest-Version: 1.0\r\n"
                 "Implementation-Title: ShardTools\r\n"
                 f"Implementation-Version: {version}\r\n")
    # Only plugin classes go into the jar - never the compile-only stubs
    # (org/bukkit, net/kyori, io/papermc), which would shadow the server API.
    skip = ("org/", "net/", "io/")
    for path in sorted((root / "build" / "classes").rglob("*.class")):
        rel = path.relative_to(root / "build" / "classes").as_posix()
        if rel.startswith(skip):
            continue
        jar.write(path, rel)
    for path in sorted((root / "src" / "main" / "resources").iterdir()):
        if path.is_file():
            content = path.read_text(encoding="utf-8").replace("${project.version}", version)
            jar.writestr(path.name, content)
print(f"Packaged {out}")
PY

echo
echo "Offline build OK: target/ShardTools-$VERSION.jar"
