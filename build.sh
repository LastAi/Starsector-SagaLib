#!/usr/bin/env bash
# Builds jars/SagaLib.jar and runs the tests (unit tests + the golden cases shared with Saga Forge).
#
#   bash build.sh            build + tests
#   bash build.sh --no-test  build only
#
# Reads the game's jars from STARSECTOR_CORE (read-only; set it if the game isn't in
# D:\Starsector). Temporary class files go to BUILD_DIR, outside the mod folder so they
# never ship with the mod: the workspace's .cache if there is one, else .build next to
# the mods (gitignored).
set -euo pipefail

MOD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STARSECTOR_CORE="${STARSECTOR_CORE:-/d/Starsector/starsector-core}"
STARSECTOR_MODS="${STARSECTOR_MODS:-$(dirname "$STARSECTOR_CORE")/mods}"
if [ -d "$MOD_DIR/../../.cache" ]; then DEFAULT_BUILD="$MOD_DIR/../../.cache/sagalib-build"; else DEFAULT_BUILD="$MOD_DIR/../.build/sagalib-build"; fi
BUILD_DIR="${BUILD_DIR:-$DEFAULT_BUILD}"

# Optional bridges: <src-optional folder>|<jar glob under STARSECTOR_MODS>[|more]
# None yet (MagicLib bounties come later, PLAN.md §10); see src-optional/README.md.
BRIDGES=()

find_jar() { # newest match of a glob under STARSECTOR_MODS, or nothing
  local matches=()
  local IFS=''
  shopt -s nullglob
  matches=( "$STARSECTOR_MODS"/$1 )
  shopt -u nullglob
  [ ${#matches[@]} -gt 0 ] && printf '%s\n' "${matches[@]}" | sort -V | tail -n 1
  return 0
}
find_tool() {
  local name="$1"
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/$name" -o -x "$JAVA_HOME/bin/$name.exe" ]; then
    echo "$JAVA_HOME/bin/$name"; return
  fi
  for d in "/c/Program Files/Java"/jdk-*/bin "/usr/lib/jvm"/*/bin; do
    if [ -x "$d/$name" ] || [ -x "$d/$name.exe" ]; then echo "$d/$name"; return; fi
  done
  command -v "$name" || { echo "Cannot find '$name'. Install a JDK (17+) or set JAVA_HOME." >&2; exit 1; }
}
JAVAC="$(find_tool javac)"
JAR="$(find_tool jar)"
JAVA="$(find_tool java)"

for j in starfarer.api.jar json.jar log4j-1.2.9.jar lwjgl.jar lwjgl_util.jar; do
  [ -f "$STARSECTOR_CORE/$j" ] || { echo "Missing $STARSECTOR_CORE/$j (set STARSECTOR_CORE)" >&2; exit 1; }
done
SEP=":"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    SEP=";"
    MOD_DIR="$(cygpath -m "$MOD_DIR")"
    mkdir -p "$BUILD_DIR"; BUILD_DIR="$(cd "$BUILD_DIR" && cygpath -m "$(pwd)")"
    STARSECTOR_CORE="$(cygpath -m "$STARSECTOR_CORE")"
    ;;
esac
GAME_CP="$STARSECTOR_CORE/starfarer.api.jar${SEP}$STARSECTOR_CORE/json.jar${SEP}$STARSECTOR_CORE/log4j-1.2.9.jar${SEP}$STARSECTOR_CORE/lwjgl.jar${SEP}$STARSECTOR_CORE/lwjgl_util.jar"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/test-classes" "$MOD_DIR/jars"

echo "== compiling"
find "$MOD_DIR/src" -name '*.java' > "$BUILD_DIR/sources.txt"
# Java 8 bytecode: runs on the game's Java 17 and on any later runtime.
"$JAVAC" --release 8 -Xlint:-options -encoding UTF-8 -cp "$GAME_CP" -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"

echo "== optional bridges"
if [ ${#BRIDGES[@]} -eq 0 ]; then echo "   (none yet)"; fi
for entry in "${BRIDGES[@]+"${BRIDGES[@]}"}"; do
  IFS='|' read -r -a parts <<< "$entry"
  name="${parts[0]}"
  cp_extra=""
  missing=""
  for glob in "${parts[@]:1}"; do
    jar="$(find_jar "$glob")"
    if [ -z "$jar" ]; then missing="$glob"; break; fi
    case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) jar="$(cygpath -m "$jar")" ;; esac
    cp_extra="${cp_extra}${SEP}${jar}"
  done
  if [ -n "$missing" ]; then
    echo "   skip $name (not found: $STARSECTOR_MODS/$missing)"
    continue
  fi
  find "$MOD_DIR/src-optional/$name" -name '*.java' > "$BUILD_DIR/bridge-$name.txt"
  mkdir -p "$BUILD_DIR/no-sources"
  "$JAVAC" --release 8 -nowarn -Xlint:-options -encoding UTF-8 -implicit:none -sourcepath "$BUILD_DIR/no-sources" \
    -cp "$GAME_CP${SEP}$BUILD_DIR/classes$cp_extra" \
    -d "$BUILD_DIR/classes" @"$BUILD_DIR/bridge-$name.txt"
  echo "   built $name"
done

echo "== packaging jars/SagaLib.jar"
rm -f "$MOD_DIR/jars/SagaLib.jar"
"$JAR" cf "$MOD_DIR/jars/SagaLib.jar" -C "$BUILD_DIR/classes" .

if [ "${1:-}" != "--no-test" ]; then
  echo "== testing (Java)"
  find "$MOD_DIR/test" -name '*.java' > "$BUILD_DIR/test-sources.txt"
  "$JAVAC" --release 8 -Xlint:-options -encoding UTF-8 -cp "$GAME_CP${SEP}$BUILD_DIR/classes" -d "$BUILD_DIR/test-classes" @"$BUILD_DIR/test-sources.txt"
  "$JAVA" -Dsagalib.modDir="$MOD_DIR" -Dsagalib.classes="$BUILD_DIR/classes" \
    -cp "$GAME_CP${SEP}$BUILD_DIR/classes${SEP}$BUILD_DIR/test-classes" lastai.sagalib.test.TestMain
fi
echo "== done"
