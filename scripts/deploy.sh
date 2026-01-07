#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status.

# Configuration Variables - Change these for other projects
WORKFLOW_FILE="android-build.yml"
PACKAGE_NAME="com.example.bbcradioplayer"
ARTIFACT_NAME="app-debug-apk"
BRANCH="main"

# Get commit message from argument
COMMIT_MSG="$1"

if [ -z "$COMMIT_MSG" ]; then
  echo "Error: Commit message is required."
  echo "Usage: $0 \"Commit message\""
  exit 1
fi

echo "--------------------------------------------------"
echo "🚀 Starting Deployment Script"
echo "--------------------------------------------------"

echo "📦 Staging all changes..."
git add -A

# Check if there are changes to commit
if ! git diff-index --quiet HEAD; then
    echo "💾 Committing with message: \"$COMMIT_MSG\""
    git commit -m "$COMMIT_MSG"
    
    echo "⬆️ Pushing to $BRANCH..."
    git push origin "$BRANCH"
else
    echo "⚠️ No changes to commit. Proceeding with existing code..."
fi

echo "triggering GitHub Workflow: $WORKFLOW_FILE..."
gh workflow run "$WORKFLOW_FILE" --ref "$BRANCH"

echo "⏳ Waiting for workflow to initialize..."
sleep 10

# Get the latest run ID for this workflow
BUILD_ID=$(gh run list --workflow="$WORKFLOW_FILE" --limit=1 --json databaseId --jq '.[0].databaseId')

if [ -z "$BUILD_ID" ]; then
    echo "❌ Could not find build ID."
    exit 1
fi

echo "👀 Watching build ID: $BUILD_ID"
gh run watch "$BUILD_ID"

echo "🧹 Cleaning up old artifacts..."
rm -rf artifacts

echo "⬇️ Downloading artifact..."
gh run download "$BUILD_ID" --name "$ARTIFACT_NAME" -D artifacts

echo "🧐 Searching for APK file..."
APK_FILE=$(find artifacts -name "*.apk" -type f | head -1)

if [ -z "$APK_FILE" ]; then
    echo "❌ No APK file found in artifacts"
    echo "Artifact contents:"
    find artifacts -type f
    exit 1
fi

echo "Found APK: $APK_FILE"

echo "🗑️ Uninstalling old app ($PACKAGE_NAME)..."
adb uninstall "$PACKAGE_NAME" || echo "⚠️ App was not installed, skipping uninstall"

echo "📲 Installing new APK..."
if adb install "$APK_FILE"; then
    echo "✅ Deployment Complete!"
else
    echo "❌ Failed to install APK"
    exit 1
fi

# Create GitHub Release (optional)
read -p "📦 Create a GitHub release with this APK? (y/n): " CREATE_RELEASE
if [ "$CREATE_RELEASE" = "y" ]; then
    read -p "Enter release tag (e.g., v1.0.0): " TAG
    read -p "Enter release title: " TITLE
    
    echo "🏷️ Creating release $TAG..."
    gh release create "$TAG" "$APK_FILE" \
        --title "$TITLE" \
        --notes "Auto-generated release from commit: $COMMIT_MSG" \
        --target "$BRANCH"
    
    echo "✅ Release created! Users can download the APK directly from:"
    echo "   https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/tag/$TAG"
fi