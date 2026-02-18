#!/bin/bash
set -e

# Configuration
ANDROID_HOME="$HOME/android-sdk"
CMD_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
JAVA_VERSION="21"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SYSROOT_DIR="$PROJECT_ROOT/scripts/sysroot"
DEBS_DIR="$PROJECT_ROOT/scripts/debs"

echo "--------------------------------------------------"
echo "🛠️  Setting up Local Android Build Environment"
echo "--------------------------------------------------"

# 1. Install Dependencies
echo "📦 Installing Dependencies (JDK, QEMU)..."
sudo apt-get update
sudo apt-get install -y openjdk-$JAVA_VERSION-jdk-headless unzip wget qemu-user-static binfmt-support

# Verify Java installation
java -version

# 2. Setup Android SDK Directory
if [ -d "$ANDROID_HOME" ]; then
    echo "📂 Android SDK directory already exists at $ANDROID_HOME"
else
    echo "📂 Creating Android SDK directory at $ANDROID_HOME..."
    mkdir -p "$ANDROID_HOME"
fi

# Export environment variables for this session
export ANDROID_HOME="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# 3. Download & Install Command Line Tools
if [ -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "✅ Command Line Tools already installed."
else
    echo "⬇️  Downloading Android Command Line Tools..."
    wget -q "$CMD_TOOLS_URL" -O cmdline-tools.zip
    
    echo "📦 Extracting Command Line Tools..."
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    unzip -q cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
    
    # Rename 'cmdline-tools' to 'latest' as required by sdkmanager
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    
    rm cmdline-tools.zip
    echo "✅ Command Line Tools installed."
fi

# 4. Accept Licenses
echo "📝 Accepting Android SDK Licenses..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true

# 5. Install Required SDK Components
echo "📥 Installing Build Tools and Platforms..."
# Based on project requirement: compileSdk 34, minSdk 21
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 6. Setup QEMU Sysroot for x86_64 emulation (Manual Multiarch)
echo "--------------------------------------------------"
echo "🏗️  Setting up QEMU Sysroot for x86_64 support..."
echo "--------------------------------------------------"

if [ -f "$SYSROOT_DIR/lib/x86_64-linux-gnu/libc.so.6" ]; then
    echo "✅ QEMU Sysroot already set up."
else
    echo "📥 Downloading compatibility libraries for x86_64..."
    mkdir -p "$SYSROOT_DIR" "$DEBS_DIR"
    
    # URLs for Debian Bookworm (Stable) amd64 packages
    # These are needed to run the x86_64 aapt2 build tool via qemu-user-static
    
    LIBC6_URL="http://ftp.de.debian.org/debian/pool/main/g/glibc/libc6_2.36-9+deb12u13_amd64.deb"
    LIBSTDC6_URL="http://ftp.de.debian.org/debian/pool/main/g/gcc-12/libstdc++6_12.2.0-14+deb12u1_amd64.deb"
    LIBGCC1_URL="http://ftp.de.debian.org/debian/pool/main/g/gcc-12/libgcc-s1_12.2.0-14+deb12u1_amd64.deb"
    ZLIB1G_URL="http://ftp.de.debian.org/debian/pool/main/z/zlib/zlib1g_1.2.13.dfsg-1_amd64.deb"

    # Download with fallback for libc6 minor versions if specific version missing (simplified check)
    download_pkg() {
        wget -q "$1" -O "$DEBS_DIR/$2" || echo "⚠️ Failed to download $2 from $1"
    }

    download_pkg "$LIBC6_URL" "libc6.deb"
    download_pkg "$LIBSTDC6_URL" "libstdc++6.deb"
    download_pkg "$LIBGCC1_URL" "libgcc-s1.deb"
    download_pkg "$ZLIB1G_URL" "zlib1g.deb"

    echo "📦 Extracting libraries to sysroot..."
    for f in "$DEBS_DIR"/*.deb; do
        if [ -f "$f" ]; then
             dpkg -x "$f" "$SYSROOT_DIR"
        fi
    done

    # Fix absolute symlinks in sysroot to be relative
    # Specifically for the dynamic linker
    echo "🔧 Fixing symlinks..."
    ln -sf ../lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 "$SYSROOT_DIR/lib64/ld-linux-x86-64.so.2"
    
    echo "✅ QEMU Sysroot ready."
fi

echo "--------------------------------------------------"
echo "✅ Setup Complete!"
echo "--------------------------------------------------"
echo "You can now run ./scripts/local-deploy.sh to build and install the APK."
echo ""
