# GitHub Release Automation Guide

## Overview

The `github-release.sh` script automates the process of creating GitHub releases for the BBC Radio Player app. It handles:

- ✅ Building release APKs
- ✅ Auto-generating changelogs from git commits
- ✅ Creating GitHub releases with proper versioning
- ✅ Uploading APK files as release assets

## Prerequisites

Before using this script, ensure you have:

1. **GitHub CLI (`gh`)** installed and authenticated
   ```bash
   # Install (if not already installed)
   sudo apt install gh
   
   # Authenticate
   gh auth login
   ```

2. **Android SDK** properly configured
   - `ANDROID_HOME` environment variable set
   - Or the script will use `$HOME/android-sdk` by default

3. **Git** with a properly configured repository
   - All changes committed
   - On the correct branch (default: `main`)

## Basic Usage

### Standard Release (Interactive Version Selection)

```bash
./scripts/github-release.sh
```

This will:
1. Display the current version from `app/build.gradle`
2. **Prompt you to select release type:**
   - **Patch** (e.g., 0.9.2 → 0.9.3) - For bug fixes
   - **Minor** (e.g., 0.9.2 → 0.10.0) - For new features (backward compatible)
   - **Major** (e.g., 0.9.2 → 1.0.0) - For breaking changes or stable release
   - **Custom** - Enter any version number
3. Update version in `app/build.gradle` and commit the change
4. Build a release APK
5. Generate a changelog from commits since the last release
6. Create a GitHub release with the new version tag
7. Upload the APK

**Example interaction:**
```
════════════════════════════════════════════════════════════
🔢 Select Release Type:
════════════════════════════════════════════════════════════
Current version: v0.9.2

1) Patch release   → v0.9.3  (bug fixes)
2) Minor release   → v0.10.0  (new features, backward compatible)
3) Major release   → v1.0.0  (breaking changes or stable release)
4) Custom version

Select option (1-4):
```

### Quick Release (skip build, use existing APK)

If you've already built the APK:

```bash
./scripts/github-release.sh --skip-build
```

### Debug Release

To create a release with a debug APK:

```bash
./scripts/github-release.sh --debug
```

### Custom Version

Override the version and skip the interactive prompt:

```bash
./scripts/github-release.sh --version 1.2.3
```

### Automated Patch Release

For CI/CD or quick patch releases, use `--auto` to automatically increment the patch version:

```bash
./scripts/github-release.sh --auto
```

This will automatically bump the patch version (e.g., 0.9.2 → 0.9.3) without prompting.

## Changelog Generation

The script automatically generates changelogs by categorizing commits:

- **✨ Features**: Commits with `feat`, `feature`, or `add`
- **🐛 Bug Fixes**: Commits with `fix` or `bug`
- **📈 Improvements**: Commits with `improve`, `enhance`, `update`, or `refactor`
- **📚 Documentation**: Commits with `doc` or `readme`
- **🔧 Other Changes**: Everything else

### Writing Good Commit Messages

For best results, use conventional commit format:

```bash
git commit -m "feat: Add support for BBC Radio 6"
git commit -m "fix: Resolve playback pause issue"
git commit -m "improve: Enhance station loading performance"
git commit -m "doc: Update README with installation steps"
```

## Version Management

The script automatically reads and updates version information in `app/build.gradle`:

```groovy
versionCode = 9
versionName = "0.9.2"
```

### Semantic Versioning

The script follows [semantic versioning](https://semver.org/):

- **Patch** (X.Y.**Z**): Bug fixes and minor changes (0.9.2 → 0.9.3)
- **Minor** (X.**Y**.0): New features, backward compatible (0.9.2 → 0.10.0)
- **Major** (**X**.0.0): Breaking changes or milestone releases (0.9.2 → 1.0.0)

### Special Version Logic

When you're on version **0.9.x**, the script intelligently suggests **1.0.0** for a major release (indicating the app is ready for stable release).

For other versions, major releases follow standard semantic versioning (e.g., 1.5.3 → 2.0.0).

### Version Code

The script automatically increments `versionCode` by 1 with each release. This is required by Android for app updates.

## Workflow Example

### 1. Complete Your Development Work

```bash
# Make changes to the app
git add .
git commit -m "feat: Add new radio station feature"
git commit -m "fix: Resolve stream buffering issue"
git commit -m "improve: Update UI for better accessibility"
```

### 2. Run the Release Script

```bash
./scripts/github-release.sh
```

The script will show you the current version and ask what type of release to create:

```
📋 Current Version:
   Name: 0.9.2
   Code: 9

════════════════════════════════════════════════════════════
🔢 Select Release Type:
════════════════════════════════════════════════════════════
Current version: v0.9.2

1) Patch release   → v0.9.3  (bug fixes)
2) Minor release   → v0.10.0  (new features, backward compatible)
3) Major release   → v1.0.0  (breaking changes or stable release)
4) Custom version

Select option (1-4): 3
```

### 3. Version Update

The script automatically updates [app/build.gradle](app/build.gradle) and commits the change:

```
📦 New Version:
════════════════════════════════════════════════════════════
   Version: 0.9.2 → 1.0.0
   Code:    9 → 10
   Type:    Major Release
════════════════════════════════════════════════════════════

✏️  Updating app/build.gradle...
✅ Version updated successfully in build.gradle
💾 Committing version change...
```

### 4. Build and Confirm

The script builds the APK and shows a summary:

```
════════════════════════════════════════════════════════════
📦 Release Summary:
════════════════════════════════════════════════════════════
Tag:      v1.0.0
Title:    BBC Radio Player 1.0.0
APK:      app-release.apk (8.2M)
Branch:   main
Version:  0.9.2 → 1.0.0
════════════════════════════════════════════════════════════

🚀 Create this release? (y/n):
```

Type `y` to confirm.

### 5. Share the Release

Once created, users can download the APK from:
```
https://github.com/YOUR_USERNAME/BBC-Radio-Player/releases/latest
```

## Troubleshooting

### "GitHub CLI (gh) is not installed"

Install the GitHub CLI:
```bash
sudo apt install gh
# or
brew install gh
```

### "Not authenticated with GitHub CLI"

Run:
```bash
gh auth login
```

### "No APK found"

Make sure you've built the app first, or check that your gradle build succeeded:
```bash
./gradlew assembleRelease
```

### "Tag already exists"

The script now prevents creating duplicate tags. If you see this error:

```
❌ Error: Tag v0.9.3 already exists
```

Either:
- The script will have already prompted you to select a different version
- Delete the existing tag if you need to recreate it:
  ```bash
  git tag -d v0.9.3
  git push origin :refs/tags/v0.9.3
  gh release delete v0.9.3 --yes
  ```
- Run the script again and choose a different version

## Script Options

| Option | Description |
|--------|-------------|
| `--skip-build` | Use existing APK, don't rebuild |
| `--debug` | Create release with debug APK |
| `--version VERSION` | Override version number (skips interactive prompt) |
| `--auto` | Automatically increment patch version without prompting |

## Integration with CI/CD

You can also integrate this into your CI/CD pipeline:

```yaml
# .github/workflows/release.yml
name: Create Release
on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build and Release
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: ./scripts/github-release.sh --skip-build
```

## Tips

1. **Choose the Right Release Type**:
   - **Patch**: Bug fixes only, no new features (0.9.2 → 0.9.3)
   - **Minor**: New features that don't break existing functionality (0.9.2 → 0.10.0)
   - **Major**: Breaking changes or ready for production (0.9.2 → 1.0.0)

2. **Regular Releases**: Create releases after significant features or fixes - don't wait too long

3. **Clear Commit Messages**: Use conventional commits to generate meaningful changelogs:
   ```bash
   git commit -m "feat: Add new feature"
   git commit -m "fix: Resolve bug"
   git commit -m "improve: Enhance performance"
   ```

4. **Test Before Release**: Always test the APK before creating a public release

5. **Version 1.0.0**: Consider releasing 1.0.0 when your app is stable and ready for production use

## Support

For issues or questions, check the main repository documentation or open an issue on GitHub.
