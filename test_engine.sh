#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
JDK_BIN="${JDK_BIN:-/tmp/toolchain/jre_full/package/jre/bin}"
JAVA="$JDK_BIN/java"
JAVAC="$JDK_BIN/javac"
ANDROID_JAR="${ANDROID_JAR:-/tmp/toolchain/minapk/package/tools/android.jar}"

rm -rf "$ROOT/build/test-classes"
mkdir -p "$ROOT/build/test-classes"

# Compile engine + test against host JDK first (ensures pure Java engine works identically on JVM & Android)
"$JAVAC" --release 8 -encoding UTF-8 \
  -d "$ROOT/build/test-classes" \
  "$ROOT"/app/src/main/java/com/belopus/life/engine/*.java \
  "$ROOT"/app/src/test/java/com/belopus/life/LifeEngineTest.java

"$JAVA" -cp "$ROOT/build/test-classes" com.belopus.life.LifeEngineTest

# Also verify clean compilation against Android API 34 android.jar
mkdir -p "$ROOT/build/test-android-classes"
"$JAVAC" -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$ROOT/build/test-android-classes" \
  "$ROOT"/app/src/main/java/com/belopus/life/engine/*.java
