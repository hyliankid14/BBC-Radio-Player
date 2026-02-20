#!/bin/bash
set -e

# ═══════════════════════════════════════════════════════════════════
# GitHub Release Automation Script
# ═══════════════════════════════════════════════════════════════════
# This script:
# - Builds a release APK
# - Creates a GitHub release with auto-generated changelog
# - Uploads the APK as a release asset
# ═══════════════════════════════════════════════════════════════════

# Configuration
PACKAGE_NAME="com.hyliankid14.bbcradioplayer"
BUILD_GRADLE="app/build.gradle"
APK_OUTPUT_DIR="app/build/outputs/apk/release"
BRANCH="main"

echo "════════════════════════════════════════════════════════════"
echo "🚀 GitHub Release Automation"
echo "════════════════════════════════════════════════════════════"

# Check for GitHub CLI
if ! command -v gh &> /dev/null; then
    echo "❌ Error: GitHub CLI (gh) is not installed."
    echo "   Install it from: https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ Error: Not authenticated with GitHub CLI."
    echo "   Run: gh auth login"
    exit 1
fi

# Function to extract version from build.gradle
extract_version_name() {
    grep -oP 'versionName\s*=\s*"\K[^"]+' "$BUILD_GRADLE" | head -1
}

extract_version_code() {
    grep -oP 'versionCode\s*=\s*\K\d+' "$BUILD_GRADLE" | head -1
}

# Get version information
CURRENT_VERSION=$(extract_version_name)
CURRENT_VERSION_CODE=$(extract_version_code)

if [ -z "$CURRENT_VERSION" ]; then
    echo "❌ Error: Could not extract versionName from $BUILD_GRADLE"
    exit 1
fi

echo "📋 Current Version:"
echo "   Name: $CURRENT_VERSION"
echo "   Code: $CURRENT_VERSION_CODE"
echo ""

# Version selection logic
if [ -n "$USER_VERSION" ]; then
    # User specified version via --version flag
    VERSION_NAME="$USER_VERSION"
    echo "📝 Using specified version: $VERSION_NAME"
else
    # Parse current version
    IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
    MAJOR="${VERSION_PARTS[0]}"
    MINOR="${VERSION_PARTS[1]:-0}"
    PATCH="${VERSION_PARTS[2]:-0}"
    
    # Calculate possible new versions
    PATCH_VERSION="${MAJOR}.${MINOR}.$((PATCH + 1))"
    MINOR_VERSION="${MAJOR}.$((MINOR + 1)).0"
    
    # Special case: if we're at 0.9.x, major could be 1.0.0
    if [ "$MAJOR" = "0" ] && [ "$MINOR" -ge 9 ]; then
        MAJOR_VERSION="1.0.0"
    else
        MAJOR_VERSION="$((MAJOR + 1)).0.0"
    fi
    
    if [ "$AUTO_VERSION" = false ]; then
        echo "════════════════════════════════════════════════════════════"
        echo "🔢 Select Release Type:"
        echo "════════════════════════════════════════════════════════════"
        echo "Current version: v$CURRENT_VERSION"
        echo ""
        echo "1) Patch release   → v${PATCH_VERSION}  (bug fixes)"
        echo "2) Minor release   → v${MINOR_VERSION}  (new features, backward compatible)"
        echo "3) Major release   → v${MAJOR_VERSION}  (breaking changes or stable release)"
        echo "4) Custom version"
        echo ""
        read -p "Select option (1-4):" VERSION_CHOICE
        
        case $VERSION_CHOICE in
            1)
                VERSION_NAME="$PATCH_VERSION"
                RELEASE_DESC="Patch Release"
                ;;
            2)
                VERSION_NAME="$MINOR_VERSION"
                RELEASE_DESC="Minor Release"
                ;;
            3)
                VERSION_NAME="$MAJOR_VERSION"
                RELEASE_DESC="Major Release"
                ;;
            4)
                read -p "Enter custom version (e.g., 1.0.0): " VERSION_NAME
                RELEASE_DESC="Custom Release"
                ;;
            *)
                echo "❌ Invalid selection"
                exit 1
                ;;
        esac
    else
        # Auto mode: default to patch version
        VERSION_NAME="$PATCH_VERSION"
        RELEASE_DESC="Patch Release (auto)"
    fi
fi

# Calculate new version code (increment by 1)
VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo ""
echo "════════════════════════════════════════════════════════════"
echo "📦 New Version:"
echo "════════════════════════════════════════════════════════════"
echo "   Version: $CURRENT_VERSION → $VERSION_NAME"
echo "   Code:    $CURRENT_VERSION_CODE → $VERSION_CODE"
if [ -n "$RELEASE_DESC" ]; then
    echo "   Type:    $RELEASE_DESC"
fi
echo "════════════════════════════════════════════════════════════"
echo ""

# Update build.gradle with new version
if [ "$CURRENT_VERSION" != "$VERSION_NAME" ] || [ "$CURRENT_VERSION_CODE" != "$VERSION_CODE" ]; then
    echo "✏️  Updating $BUILD_GRADLE..."
    
    # Backup build.gradle
    cp "$BUILD_GRADLE" "${BUILD_GRADLE}.bak"
    
    # Update version name
    sed -i "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"${VERSION_NAME}\"/" "$BUILD_GRADLE"
    
    # Update version code
    sed -i "s/versionCode[[:space:]]*=[[:space:]]*[0-9]*/versionCode = ${VERSION_CODE}/" "$BUILD_GRADLE"
    
    # Verify changes
    NEW_VERSION_CHECK=$(extract_version_name)
    NEW_CODE_CHECK=$(extract_version_code)
    
    if [ "$NEW_VERSION_CHECK" = "$VERSION_NAME" ] && [ "$NEW_CODE_CHECK" = "$VERSION_CODE" ]; then
        echo "✅ Version updated successfully in build.gradle"
        rm "${BUILD_GRADLE}.bak"
        
        # Commit version change
        echo "💾 Committing version change..."
        git add "$BUILD_GRADLE"
        git commit -m "chore: Bump version to $VERSION_NAME" || echo "⚠️  Nothing to commit"
        echo ""
    else
        echo "❌ Failed to update version in build.gradle"
        mv "${BUILD_GRADLE}.bak" "$BUILD_GRADLE"
        exit 1
    fi
    echo ""
fi

# Parse command line arguments
BUILD_RELEASE=true
SKIP_BUILD=false
RELEASE_TYPE="release"
AUTO_VERSION=false
USER_VERSION=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --debug)
            RELEASE_TYPE="debug"
            APK_OUTPUT_DIR="app/build/outputs/apk/debug"
            shift
            ;;
        --version)
            USER_VERSION="$2"
            shift 2
            ;;
        --auto)
            AUTO_VERSION=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--skip-build] [--debug] [--version VERSION] [--auto]"
            exit 1
            ;;
    esac
done

# Ensure ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME="$HOME/android-sdk"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
fi

# Setup QEMU Sysroot for x86_64 emulation (needed on ARM systems like Raspberry Pi)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
export QEMU_LD_PREFIX="$PROJECT_ROOT/scripts/sysroot"

if [ -d "$QEMU_LD_PREFIX" ]; then
    echo "🌍 Using QEMU Sysroot: $QEMU_LD_PREFIX"
    echo ""
fi

# Build APK
if [ "$SKIP_BUILD" = false ]; then
    echo "════════════════════════════════════════════════════════════"
    echo "🔨 Building ${RELEASE_TYPE} APK..."
    echo "════════════════════════════════════════════════════════════"
    
    if [ ! -x "./gradlew" ]; then
        chmod +x ./gradlew
    fi
    
    # Determine processor count
    CORES=$(nproc 2>/dev/null || echo 4)
    echo "⚡ Using $CORES cores for parallel build..."
    
    if [ "$RELEASE_TYPE" = "release" ]; then
        ./gradlew assembleRelease --parallel --max-workers="$CORES"
    else
        ./gradlew assembleDebug --parallel --max-workers="$CORES"
    fi
    
    echo "✅ Build complete!"
    echo ""
else
    echo "⏭️  Skipping build (using existing APK)..."
    echo ""
fi

# Find the APK
echo "🔍 Locating APK file..."
APK_FILE=$(find "$APK_OUTPUT_DIR" -name "*.apk" -type f | head -1)

if [ -z "$APK_FILE" ]; then
    echo "❌ Error: No APK found in $APK_OUTPUT_DIR"
    echo "   Available files:"
    find "$APK_OUTPUT_DIR" -type f 2>/dev/null || echo "   Directory not found"
    exit 1
fi

APK_FILENAME=$(basename "$APK_FILE")
APK_SIZE=$(du -h "$APK_FILE" | cut -f1)

echo "✅ Found: $APK_FILENAME ($APK_SIZE)"

# Rename APK to bbc-radio-player.apk for release
RELEASE_APK_NAME="bbc-radio-player.apk"
RELEASE_APK_PATH="$(dirname "$APK_FILE")/$RELEASE_APK_NAME"

if [ "$APK_FILENAME" != "$RELEASE_APK_NAME" ]; then
    echo "📝 Renaming to $RELEASE_APK_NAME..."
    cp "$APK_FILE" "$RELEASE_APK_PATH"
    APK_FILE="$RELEASE_APK_PATH"
    APK_FILENAME="$RELEASE_APK_NAME"
fi
echo ""

# Determine release tag
TAG="v${VERSION_NAME}"

# Check if tag already exists
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "❌ Error: Tag $TAG already exists"
    echo "   Either:"
    echo "   - Delete the tag: git tag -d $TAG && git push origin :refs/tags/$TAG"
    echo "   - Choose a different version when running the script"
    exit 1
fi

# Get the last release tag
echo "════════════════════════════════════════════════════════════"
echo "📝 Generating Changelog..."
echo "════════════════════════════════════════════════════════════"

LAST_TAG=$(gh release list --limit 1 --json tagName --jq '.[0].tagName' 2>/dev/null || echo "")

if [ -z "$LAST_TAG" ]; then
    echo "ℹ️  No previous releases found. Creating initial release."
    CHANGELOG="🎉 Initial release of BBC Radio Player v${VERSION_NAME}"
    echo ""
    echo "$CHANGELOG"
    echo ""
else
    echo "📌 Last release: $LAST_TAG"
    
    # Get the date of the last release
    LAST_RELEASE_DATE=$(gh release view "$LAST_TAG" --json publishedAt --jq '.publishedAt' 2>/dev/null || echo "")
    
    if [ -n "$LAST_RELEASE_DATE" ]; then
        echo "🕒 Released: $LAST_RELEASE_DATE"
    fi
    
    echo "📊 Commits since $LAST_TAG:"
    echo ""
    
    # Generate changelog from git log
    CHANGELOG_HEADER="## 🚀 What's Changed\n\n"
    
    # Get commits since last tag
    COMMITS=$(git log "$LAST_TAG"..HEAD --pretty=format:"- %s (%h)" --no-merges 2>/dev/null || echo "")
    
    if [ -z "$COMMITS" ]; then
        echo "⚠️  No commits found since last release"
        CHANGELOG="${CHANGELOG_HEADER}No changes (re-release of existing code)"
    else
        # Count changes by category
        FEATURES=$(echo "$COMMITS" | grep -i "feat\|feature\|add" || echo "")
        FIXES=$(echo "$COMMITS" | grep -i "fix\|bug" || echo "")
        IMPROVEMENTS=$(echo "$COMMITS" | grep -i "improve\|enhance\|update\|refactor" || echo "")
        DOCS=$(echo "$COMMITS" | grep -i "doc\|readme" || echo "")
        OTHER=$(echo "$COMMITS" | grep -iv "feat\|feature\|add\|fix\|bug\|improve\|enhance\|update\|refactor\|doc\|readme" || echo "")
        
        CHANGELOG="${CHANGELOG_HEADER}"
        
        # Add features
        if [ -n "$FEATURES" ]; then
            CHANGELOG="${CHANGELOG}### ✨ Features\n${FEATURES}\n\n"
        fi
        
        # Add fixes
        if [ -n "$FIXES" ]; then
            CHANGELOG="${CHANGELOG}### 🐛 Bug Fixes\n${FIXES}\n\n"
        fi
        
        # Add improvements
        if [ -n "$IMPROVEMENTS" ]; then
            CHANGELOG="${CHANGELOG}### 📈 Improvements\n${IMPROVEMENTS}\n\n"
        fi
        
        # Add documentation
        if [ -n "$DOCS" ]; then
            CHANGELOG="${CHANGELOG}### 📚 Documentation\n${DOCS}\n\n"
        fi
        
        # Add other changes
        if [ -n "$OTHER" ]; then
            CHANGELOG="${CHANGELOG}### 🔧 Other Changes\n${OTHER}\n\n"
        fi
        
        # Add statistics
        COMMIT_COUNT=$(echo "$COMMITS" | wc -l)
        CHANGELOG="${CHANGELOG}---\n\n**Full Changelog**: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/compare/${LAST_TAG}...${TAG}"
    fi
    
    # Display changelog preview
    echo -e "$CHANGELOG" | head -20
    echo ""
    
    if [ "$(echo -e "$CHANGELOG" | wc -l)" -gt 20 ]; then
        echo "[... changelog truncated for preview ...]"
        echo ""
    fi
fi

# Push version bump commit if needed
if [ "$CURRENT_VERSION" != "$VERSION_NAME" ]; then
    echo "════════════════════════════════════════════════════════════"
    echo "⬆️  Pushing version bump to $BRANCH..."
    echo "════════════════════════════════════════════════════════════"
    
    if git push origin "$BRANCH"; then
        echo "✅ Version bump pushed successfully"
    else
        echo "❌ Failed to push version bump"
        echo "   You may need to manually push before creating the release"
    fi
    echo ""
fi

# Confirmation prompt
echo "════════════════════════════════════════════════════════════"
echo "📦 Release Summary:"
echo "════════════════════════════════════════════════════════════"
echo "Tag:      $TAG"
echo "Title:    BBC Radio Player $VERSION_NAME"
echo "APK:      $APK_FILENAME ($APK_SIZE)"
echo "Branch:   $BRANCH"
if [ "$CURRENT_VERSION" != "$VERSION_NAME" ]; then
    echo "Version:  $CURRENT_VERSION → $VERSION_NAME"
fi
echo "════════════════════════════════════════════════════════════"
echo ""

read -p "🚀 Create this release? (y/n): " CONFIRM

if [ "$CONFIRM" != "y" ]; then
    echo "❌ Release cancelled"
    exit 0
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "🏷️  Creating GitHub Release..."
echo "════════════════════════════════════════════════════════════"

# Create the release
if gh release create "$TAG" "$APK_FILE" \
    --title "BBC Radio Player $VERSION_NAME" \
    --notes "$(echo -e "$CHANGELOG")" \
    --target "$BRANCH"; then
    
    echo ""
    echo "════════════════════════════════════════════════════════════"
    echo "✅ Release Created Successfully!"
    echo "════════════════════════════════════════════════════════════"
    
    RELEASE_URL=$(gh release view "$TAG" --json url --jq '.url')
    echo ""
    echo "🔗 Release URL:"
    echo "   $RELEASE_URL"
    echo ""
    echo "📥 Download URL:"
    echo "   ${RELEASE_URL/tag/download}/$APK_FILENAME"
    echo ""
    echo "✨ Users can now download the APK directly from GitHub!"
    echo "════════════════════════════════════════════════════════════"
else
    echo ""
    echo "❌ Failed to create release"
    exit 1
fi
