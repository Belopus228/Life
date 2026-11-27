#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-/tmp/toolchain}"
if [ ! -x "$TOOLCHAIN_DIR/jre_full/package/jre/bin/java" ] || [ ! -x "$TOOLCHAIN_DIR/aapt/package/bin/x64/linux/aapt2" ] || [ ! -f "$TOOLCHAIN_DIR/minapk/package/tools/d8.jar" ]; then
  TOOLCHAIN_DIR="$ROOT/.cache/toolchain"
  if [ ! -x "$TOOLCHAIN_DIR/jre_full/package/jre/bin/java" ]; then
    echo "==> Downloading OpenJDK 17 toolchain from npm..."
    mkdir -p "$TOOLCHAIN_DIR/jre_full"
    curl -sL https://registry.npmjs.org/javajre-linux-64/-/javajre-linux-64-17.0.8.tgz | tar -xz -C "$TOOLCHAIN_DIR/jre_full"
  fi
  if [ ! -f "$TOOLCHAIN_DIR/aapt/package/bin/x64/linux/aapt2" ]; then
    echo "==> Downloading Android aapt2 from npm..."
    mkdir -p "$TOOLCHAIN_DIR/aapt"
    curl -sL https://registry.npmjs.org/aaptjs3/-/aaptjs3-2.0.2.tgz | tar -xz -C "$TOOLCHAIN_DIR/aapt"
  fi
  if [ ! -f "$TOOLCHAIN_DIR/minapk/package/tools/d8.jar" ]; then
    echo "==> Downloading Android D8 / android.jar / apksigner from npm..."
    mkdir -p "$TOOLCHAIN_DIR/minapk"
    curl -sL https://registry.npmjs.org/@drxiaozhi/minapk/-/minapk-0.5.1.tgz | tar -xz -C "$TOOLCHAIN_DIR/minapk"
  fi
fi

JDK_BIN="$TOOLCHAIN_DIR/jre_full/package/jre/bin"
JAVA="$JDK_BIN/java"
JAVAC="$JDK_BIN/javac"
KEYTOOL="$JDK_BIN/keytool"
AAPT2="$TOOLCHAIN_DIR/aapt/package/bin/x64/linux/aapt2"
TOOLS="$TOOLCHAIN_DIR/minapk/package/tools"
ANDROID_JAR="$TOOLS/android.jar"
D8_JAR="$TOOLS/d8.jar"
APKSIGNER_JAR="$TOOLS/apksigner.jar"

chmod +x "$AAPT2" "$JAVA" "$JAVAC" "$KEYTOOL"

echo "==> Running Engine Unit Tests..."
JDK_BIN="$JDK_BIN" ANDROID_JAR="$ANDROID_JAR" "$ROOT/test_engine.sh"

BUILD="$ROOT/build/apk_build"
rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/out"

PROJ="$ROOT/app/src/main"
OUT_APK="$ROOT/Life-GameOfLife.apk"

echo "==> 1. Compiling Android resources (aapt2 compile)..."
"$AAPT2" compile --dir "$PROJ/res" -o "$BUILD/res/compiled.zip"

echo "==> 2. Linking manifest & resources (aapt2 link)..."
"$AAPT2" link \
  -o "$BUILD/out/resources.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$PROJ/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --auto-add-overlay \
  "$BUILD/res/compiled.zip"

echo "==> 3. Compiling Java sources (javac Java 8 target)..."
find "$BUILD/gen" "$PROJ/java" -name "*.java" > "$BUILD/sources.list"
"$JAVAC" -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.list"

echo "==> 4. Converting bytecode to Dalvik DEX (D8 release)..."
find "$BUILD/classes" -name "*.class" > "$BUILD/classes.list"
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 \
  --release \
  --min-api 26 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" \
  $(cat "$BUILD/classes.list")

echo "==> 5. Packaging unsigned APK..."
cp "$BUILD/out/resources.apk" "$BUILD/out/unsigned.apk"
(cd "$BUILD/dex" && zip -q -u "$BUILD/out/unsigned.apk" classes.dex)

echo "==> 6. Zipaligning (4-byte alignment)..."
python3 - "$BUILD/out/unsigned.apk" "$BUILD/out/aligned.apk" << 'PY'
import sys, struct, zipfile, zlib

src_path, dst_path = sys.argv[1], sys.argv[2]
align = 4

with zipfile.ZipFile(src_path, 'r') as zin:
    entries = [(info, zin.read(info.filename)) for info in zin.infolist()]

offset = 0
central = []
with open(dst_path, 'wb') as out:
    for info, data in entries:
        fname = info.filename.encode('utf-8')
        comp_type = info.compress_type
        if comp_type == zipfile.ZIP_DEFLATED:
            c = zlib.compressobj(9, zlib.DEFLATED, -zlib.MAX_WBITS)
            payload = c.compress(data) + c.flush()
        else:
            comp_type = zipfile.ZIP_STORED
            payload = data
        crc = zlib.crc32(data) & 0xffffffff
        base_len = 30 + len(fname)
        pad = 0
        if comp_type == zipfile.ZIP_STORED:
            cur_data_off = offset + base_len
            pad = (align - (cur_data_off % align)) % align
        extra = b'\x00' * pad
        dos_time = (info.date_time[3] << 11) | (info.date_time[4] << 5) | (info.date_time[5] // 2)
        dos_date = ((info.date_time[0] - 1980) << 9) | (info.date_time[1] << 5) | info.date_time[2]
        lvh = struct.pack('<IHHHHHIIIHH',
            0x04034b50, 20, 0x0800, comp_type, dos_time, dos_date,
            crc, len(payload), len(data), len(fname), len(extra)
        )
        local_off = offset
        data_off = local_off + len(lvh) + len(fname) + len(extra)
        if comp_type == zipfile.ZIP_STORED:
            assert data_off % align == 0, f"Misaligned STORED entry {info.filename} at {data_off}"
        out.write(lvh + fname + extra + payload)
        offset += len(lvh) + len(fname) + len(extra) + len(payload)
        cdh = struct.pack('<IHHHHHHIIIHHHHHII',
            0x02014b50, 20, 20, 0x0800, comp_type, dos_time, dos_date,
            crc, len(payload), len(data), len(fname), 0, 0, 0, 0,
            info.external_attr, local_off
        )
        central.append(cdh + fname)
    cd_off = offset
    cd_bytes = b''.join(central)
    out.write(cd_bytes)
    eocd = struct.pack('<IHHHHIIH',
        0x06054b50, 0, 0, len(central), len(central), len(cd_bytes), cd_off, 0
    )
    out.write(eocd)
print("Zipalign verified OK.")
PY

echo "==> 7. Signing APK (v1 + v2 + v3)..."
KEYSTORE="$ROOT/build/life-release.jks"
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkeypair \
    -alias life_release \
    -keyalg RSA -keysize 2048 -sigalg SHA256withRSA \
    -validity 10000 \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -storepass life2026 \
    -keypass life2026 \
    -dname "CN=Belopus Life,OU=Android,O=Belopus,C=NL"
fi

"$JAVA" -jar "$APKSIGNER_JAR" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias life_release \
  --ks-pass pass:life2026 \
  --key-pass pass:life2026 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$OUT_APK" \
  "$BUILD/out/aligned.apk"

echo "==> 8. Verifying APK signature & metadata..."
"$JAVA" -jar "$APKSIGNER_JAR" verify -Werr --verbose --print-certs "$OUT_APK"
"$AAPT2" dump badging "$OUT_APK" | head -n 30

ls -lh "$OUT_APK"
echo "==> DONE: $OUT_APK"
