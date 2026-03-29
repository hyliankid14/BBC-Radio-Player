#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null || (cd "$SCRIPT_DIR/../../.." && pwd))"

cd "$PROJECT_ROOT"

# -------------------------------------------------------
# Check signing config
# -------------------------------------------------------
GRADLE_PROPS="$HOME/.gradle/gradle.properties"
if [ ! -f "$GRADLE_PROPS" ]; then
    echo "❌ Error: ~/.gradle/gradle.properties not found."
    echo "   Run scripts/setup-release-signing.sh to configure signing."
    exit 1
fi

for key in RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
    if ! grep -q "^${key}=" "$GRADLE_PROPS"; then
        echo "❌ Error: $key missing from ~/.gradle/gradle.properties."
        echo "   Run scripts/setup-release-signing.sh to configure signing."
        exit 1
    fi
done

KEYSTORE_FILE=$(grep "^RELEASE_STORE_FILE=" "$GRADLE_PROPS" | cut -d'=' -f2-)
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "❌ Error: Keystore not found at $KEYSTORE_FILE"
    exit 1
fi

if [ ! -x "./gradlew" ]; then
    chmod +x ./gradlew
fi

echo "--------------------------------------------------"
echo "🔨 Building Release AABs for Google Play (phone + wear)"
echo "--------------------------------------------------"

./gradlew :app:bundlePlayRelease :wear:bundleRelease

# -------------------------------------------------------
# Locate the AABs
# -------------------------------------------------------
PHONE_AAB_FILE=$(find app/build/outputs/bundle/playRelease -name "*.aab" | sort | head -1)
WEAR_AAB_FILE=$(find wear/build/outputs/bundle/release -name "*.aab" | sort | head -1)

PHONE_MAPPING_FILE="app/build/outputs/mapping/playRelease/mapping.txt"
PHONE_SYMBOLS_FILE="app/build/outputs/native-debug-symbols/playRelease/native-debug-symbols.zip"
PHONE_NATIVE_LIBS_ROOT="app/build/intermediates/merged_native_libs/playRelease/mergePlayReleaseNativeLibs/out"

WEAR_MAPPING_FILE="wear/build/outputs/mapping/release/mapping.txt"
WEAR_SYMBOLS_FILE="wear/build/outputs/native-debug-symbols/release/native-debug-symbols.zip"
WEAR_NATIVE_LIBS_ROOT="wear/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out"

if [ -z "$PHONE_AAB_FILE" ]; then
    echo "❌ Build failed: No AAB found in app/build/outputs/bundle/playRelease/"
    exit 1
fi

if [ -z "$WEAR_AAB_FILE" ]; then
    echo "❌ Build failed: No AAB found in wear/build/outputs/bundle/release/"
    exit 1
fi

# Always generate a Play-compatible symbols archive from merged native libs.
# Play Console expects ABI folders at zip root (arm64-v8a/, x86_64/, ...), not lib/.
if [ -d "$PHONE_NATIVE_LIBS_ROOT/lib" ] && find "$PHONE_NATIVE_LIBS_ROOT/lib" -name "*.so" | grep -q .; then
    PHONE_SYMBOLS_FILE="app/build/outputs/native-debug-symbols/playRelease/native-debug-symbols.zip"
    mkdir -p "$(dirname "$PHONE_SYMBOLS_FILE")"
    rm -f "$PHONE_SYMBOLS_FILE"

    if command -v zip >/dev/null 2>&1; then
        (
            cd "$PHONE_NATIVE_LIBS_ROOT/lib"
            zip -rq "$PROJECT_ROOT/$PHONE_SYMBOLS_FILE" .
        )
    else
        # macOS fallback if zip is unavailable.
        (
            cd "$PHONE_NATIVE_LIBS_ROOT/lib"
            ditto -c -k --sequesterRsrc . "$PROJECT_ROOT/$PHONE_SYMBOLS_FILE"
        )
    fi
else
    PHONE_SYMBOLS_FILE=""
fi

if [ -d "$WEAR_NATIVE_LIBS_ROOT/lib" ] && find "$WEAR_NATIVE_LIBS_ROOT/lib" -name "*.so" | grep -q .; then
    WEAR_SYMBOLS_FILE="wear/build/outputs/native-debug-symbols/release/native-debug-symbols.zip"
    mkdir -p "$(dirname "$WEAR_SYMBOLS_FILE")"
    rm -f "$WEAR_SYMBOLS_FILE"

    if command -v zip >/dev/null 2>&1; then
        (
            cd "$WEAR_NATIVE_LIBS_ROOT/lib"
            zip -rq "$PROJECT_ROOT/$WEAR_SYMBOLS_FILE" .
        )
    else
        (
            cd "$WEAR_NATIVE_LIBS_ROOT/lib"
            ditto -c -k --sequesterRsrc . "$PROJECT_ROOT/$WEAR_SYMBOLS_FILE"
        )
    fi
else
    WEAR_SYMBOLS_FILE=""
fi

# -------------------------------------------------------
# Verify signature
# -------------------------------------------------------
echo ""
echo "--------------------------------------------------"
echo "🔏 Verifying signature"
echo "--------------------------------------------------"

if jarsigner -verify -verbose "$PHONE_AAB_FILE" 2>&1 | grep -q "jar verified"; then
    echo "✅ Phone AAB is correctly signed."
else
    echo "⚠️  Warning: jarsigner could not verify the phone AAB signature."
fi

if jarsigner -verify -verbose "$WEAR_AAB_FILE" 2>&1 | grep -q "jar verified"; then
    echo "✅ Wear AAB is correctly signed."
else
    echo "⚠️  Warning: jarsigner could not verify the Wear AAB signature."
fi

# -------------------------------------------------------
# Summary
# -------------------------------------------------------
PHONE_AAB_SIZE=$(du -sh "$PHONE_AAB_FILE" | cut -f1)
WEAR_AAB_SIZE=$(du -sh "$WEAR_AAB_FILE" | cut -f1)

echo ""
echo "=================================================="
echo "✅ Release AABs ready for Google Play"
echo ""
echo "📱 Phone"
echo "   Path : $PHONE_AAB_FILE"
echo "   Size : $PHONE_AAB_SIZE"
if [ -f "$PHONE_MAPPING_FILE" ]; then
    echo "   Mapping : $PHONE_MAPPING_FILE"
    echo "   Upload mapping.txt in Play Console deobfuscation section"
else
    echo "   Mapping : Not generated"
fi
if [ -n "${PHONE_SYMBOLS_FILE:-}" ] && [ -f "$PHONE_SYMBOLS_FILE" ]; then
    echo "   Native symbols : $PHONE_SYMBOLS_FILE"
    echo "   Upload native-debug-symbols.zip in Play Console to resolve native crash/ANR symbols"
else
    echo "   Native symbols : Not generated"
fi

echo ""
echo "⌚ Wear"
echo "   Path : $WEAR_AAB_FILE"
echo "   Size : $WEAR_AAB_SIZE"
if [ -f "$WEAR_MAPPING_FILE" ]; then
    echo "   Mapping : $WEAR_MAPPING_FILE"
    echo "   Upload mapping.txt in Play Console deobfuscation section"
else
    echo "   Mapping : Not generated"
fi
if [ -n "${WEAR_SYMBOLS_FILE:-}" ] && [ -f "$WEAR_SYMBOLS_FILE" ]; then
    echo "   Native symbols : $WEAR_SYMBOLS_FILE"
    echo "   Upload native-debug-symbols.zip in Play Console to resolve native crash/ANR symbols"
else
    echo "   Native symbols : Not generated"
fi
echo "=================================================="
