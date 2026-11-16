#!/usr/bin/env bash
# 无 Gradle 构建：aapt2 -> javac -> d8 -> 注入 dex -> zipalign -> apksigner
#
# 用法：
#   bash build.sh
#
# 需要的环境变量（二选一）：
#   ANDROID_HOME / ANDROID_SDK_ROOT  —— Android SDK 根目录
#   TC                               —— 工具链根目录（若含 sdk/ 子目录会自动识别）
# 可选：
#   JAVA_HOME  指定 JDK（不设则用 PATH 里的 java/javac）
#   KS         签名用 keystore（默认 ~/.android/debug.keystore，不存在会自动生成）
#   PY         python 解释器（默认 python3 / python）
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
# Windows 上 pwd 给的是 /d/xxx，Windows 工具链（javac/aapt2）不认，转成 d:/xxx
if command -v cygpath >/dev/null 2>&1; then
    ROOT="$(cygpath -m "$ROOT")"
fi
SRC="$ROOT/src"
OUT="$ROOT/build/out-$(date +%s)"
DIST="$ROOT/dist"

# 版本号可覆盖：VERSION_CODE=18 VERSION_NAME=1.9.1 bash build.sh
VERSION_CODE="${VERSION_CODE:-18}"
VERSION_NAME="${VERSION_NAME:-1.9.1}"
APK="$DIST/ClockWidget-$VERSION_NAME.apk"

# ---------- 定位工具链 ----------
TC="${TC:-}"
if [ -n "$TC" ] && [ -d "$TC/sdk" ]; then
    SDK="$TC/sdk"
elif [ -n "$TC" ]; then
    SDK="$TC"
elif [ -n "$ANDROID_HOME" ]; then
    SDK="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    SDK="$ANDROID_SDK_ROOT"
else
    echo "错误：请设置 ANDROID_HOME 或 TC（指向 Android SDK 目录）" >&2
    exit 1
fi

# Windows 下工具带 .exe 后缀
maybe_exe() { if [ -f "$1.exe" ]; then echo "$1.exe"; else echo "$1"; fi; }

BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
BT="${BT%/}"
[ -n "$BT" ] && [ -d "$BT" ] || { echo "错误：找不到 $SDK/build-tools/*" >&2; exit 1; }

PLATFORM="$(ls -d "$SDK"/platforms/android-*/ 2>/dev/null | sort -V | tail -1)"
PLATFORM="${PLATFORM%/}"
[ -n "$PLATFORM" ] && [ -d "$PLATFORM" ] || { echo "错误：找不到 $SDK/platforms/android-*" >&2; exit 1; }

AAPT2="$(maybe_exe "$BT/aapt2")"
ZIPALIGN="$(maybe_exe "$BT/zipalign")"
AJAR="$PLATFORM/android.jar"
D8_JAR="$BT/lib/d8.jar"
SIGNER_JAR="$BT/lib/apksigner.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVA="$(maybe_exe "$JAVA_HOME/bin/java")"
    JAVAC="$(maybe_exe "$JAVA_HOME/bin/javac")"
    KEYTOOL="$(maybe_exe "$JAVA_HOME/bin/keytool")"
else
    JAVA="$(command -v java || true)"
    JAVAC="$(command -v javac || true)"
    KEYTOOL="$(command -v keytool || true)"
fi
[ -n "$JAVA" ] && [ -n "$JAVAC" ] || { echo "错误：找不到 java/javac，请设置 JAVA_HOME" >&2; exit 1; }

PY="${PY:-$(command -v python3 || command -v python || true)}"
[ -n "$PY" ] || { echo "错误：找不到 python" >&2; exit 1; }

KS="${KS:-$HOME/.android/debug.keystore}"
if [ ! -f "$KS" ]; then
    echo "-- 未找到 keystore，自动生成 debug keystore: $KS"
    mkdir -p "$(dirname "$KS")"
    "$KEYTOOL" -genkeypair -v -keystore "$KS" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

echo "工具链: build-tools=$(basename "$BT")  platform=$(basename "$PLATFORM")"
"$JAVAC" -version 2>&1 | sed 's/^/        /'

# ---------- 构建 ----------
# 每次用新的输出目录：整目录递归删除会被安全策略拦（文件数超阈值）
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen" "$DIST"

echo "[1/6] compile resources"
"$AAPT2" compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "[2/6] link + generate R.java"
"$AAPT2" link -o "$OUT/base.apk" -I "$AJAR" \
    --manifest "$SRC/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version 30 --target-sdk-version 36 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    -A "$SRC/assets" \
    "$OUT/res.zip"

echo "[3/6] compile java"
# Windows 的 classpath 分隔符是分号
CP_SEP=":"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) CP_SEP=";" ;;
esac
# Xposed API 编译桩：只参与编译，不打包（运行时由 LSPosed 提供）
mkdir -p "$OUT/stubs"
find "$ROOT/tools/xposed-stubs" -name "*.java" > "$OUT/stubsrc.txt"
"$JAVAC" -encoding UTF-8 --release 8 -nowarn -d "$OUT/stubs" @"$OUT/stubsrc.txt" \
    || { echo "错误：Xposed 桩编译失败" >&2; exit 1; }
find "$SRC/java" "$OUT/gen" -name "*.java" > "$OUT/src.txt"
"$JAVAC" -encoding UTF-8 --release 8 -nowarn -cp "$AJAR${CP_SEP}$OUT/stubs" -d "$OUT/classes" @"$OUT/src.txt" \
    || { echo "错误：javac 编译失败，停止构建" >&2; exit 1; }

echo "[4/6] dex"
find "$OUT/classes" -name "*.class" > "$OUT/cls.txt"
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 --min-api 30 --lib "$AJAR" \
    --output "$OUT/dex" @"$OUT/cls.txt" \
    || { echo "错误：d8 失败，停止构建" >&2; exit 1; }

echo "[5/6] inject dex"
"$PY" "$ROOT/tools/inject.py" "$OUT/base.apk" "$OUT/dex/classes.dex" "$SRC/META-INF" "$OUT/merged.apk"

echo "[6/6] align + sign"
"$ZIPALIGN" -f -p 4 "$OUT/merged.apk" "$OUT/aligned.apk"
"$JAVA" -jar "$SIGNER_JAR" sign \
    --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$APK" "$OUT/aligned.apk"

"$JAVA" -jar "$SIGNER_JAR" verify --print-certs "$APK" | head -3
ls -lh "$APK"
echo "OK"
