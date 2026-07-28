#!/bin/sh
# Maven Wrapper launcher for this sample. Prefer an installed Maven; otherwise use wrapper JAR.
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
JAVA_CMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "Maven is unavailable and $JAR has not been downloaded." >&2
  exit 1
fi
exec "$JAVA_CMD" -jar "$JAR" "$@"
