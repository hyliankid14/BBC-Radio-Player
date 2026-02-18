#!/bin/bash
set -e

# Ensure ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME="$HOME/android-sdk"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
fi

if [ ! -d "$ANDROID_HOME" ]; then
    echo "❌ Error: ANDROID_HOME not found at $ANDROID_HOME"
    echo "Please run scripts/setup-local-build.sh first."
    exit 1
fi

echo "--------------------------------------------------"
echo "🔨 Starting Local Build"
echo "--------------------------------------------------"

# Setup QEMU Sysroot for x86_64 emulation
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
export QEMU_LD_PREFIX="$PROJECT_ROOT/scripts/sysroot"

if [ -d "$QEMU_LD_PREFIX" ]; then
    echo "🌍 Using QEMU Sysroot: $QEMU_LD_PREFIX"
fi

# Determine processor count for parallel execution
CORES=$(nproc)
echo "⚡ Building with $CORES cores..."

# Build Debug APK
# Using --offline if possible to save bandwidth, but for now we'll stick to standard
./gradlew assembleDebug --parallel --max-workers="$CORES"

echo "--------------------------------------------------"
echo "📲 Deploying to Device"
echo "--------------------------------------------------"

# Find the APK
APK_FILE=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)

if [ -z "$APK_FILE" ]; then
    echo "❌ Build failed: No APK found."
    exit 1
fi

echo "Found APK: $APK_FILE"

# Check for connected device
if ! adb get-state 1>/dev/null 2>&1; then
    echo "⚠️  No device connected via ADB."
    echo "    APK is ready at: $APK_FILE"
    exit 0
fi

echo "🗑️  Uninstalling old version..."
adb uninstall com.hyliankid14.bbcradioplayer || true

echo "🚀 Installing new version..."
adb install -r "$APK_FILE"

echo "✅ App installed successfully!"
