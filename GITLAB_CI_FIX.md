# 🚨 GitLab CI Build Failed - FIX INSTRUCTIONS

**Problem:** Your metadata file has YAML syntax errors because it includes markdown code fence markers.

**Error:** `found character '`' that cannot start any token`

---

## ✅ Quick Fix (2 minutes)

### Step 1: Go to Your Merge Request

1. Go to: https://gitlab.com/YOUR_USERNAME/fdroiddata
2. Find your merge request (should be visible)
3. Click on it

### Step 2: Edit the Metadata File

1. Click **Files** tab in the merge request
2. Find `metadata/com.hyliankid14.bbcradioplayer.yml`
3. Click on the file name to open it
4. Click the **Edit** button (pencil icon, top right)

### Step 3: Remove the Markdown Markers

**Current content (WRONG):**
```
```yaml              ← DELETE THIS LINE
Categories:
  - Multimedia
...
CurrentVersionCode: 26
```                  ← DELETE THIS LINE
```

**Should be (CORRECT):**
```
Categories:
  - Multimedia
...
CurrentVersionCode: 26
```

### Step 4: The Quick Replace Method

1. **Select all text** in the editor: `Ctrl+A` (or on Mac: `Cmd+A`)
2. **Copy this from below** (starting from "Categories", ending at "26"):

---

Categories:
  - Multimedia
License: GPL-3.0-or-later
AuthorName: shaivure
AuthorWebSite: https://github.com/hyliankid14
SourceCode: https://github.com/hyliankid14/BBC-Radio-Player
IssueTracker: https://github.com/hyliankid14/BBC-Radio-Player/issues
Changelog: https://github.com/hyliankid14/BBC-Radio-Player/releases

AutoName: BBC Radio Player
Summary: Stream 80+ BBC Radio stations with podcast support

Description: |-
  A feature-rich Android app for streaming BBC Radio stations with
  comprehensive podcast support with downloads and subscriptions,
  intelligent playback features, and a modern Material Design 3 interface.

  Features:
  • Standard MediaBrowserService integration
  • 80+ BBC Radio stations across the UK (National, Regional, Local)
  • Podcast support with subscriptions and downloads
  • Episode download management with WiFi-only option
  • Favourites management with drag-and-drop reordering
  • Live metadata and show information
  • Material Design 3 with light and dark themes
  • No tracking, no ads, completely free and open source

  Privacy:
  • No user tracking
  • No analytics by default
  • No Google Play Services in F-Droid build
  • No advertisements
  • Complete source code transparency

  Note: Requires internet connection to stream BBC Radio content.

RepoType: git
Repo: https://github.com/hyliankid14/BBC-Radio-Player.git

Builds:
  - versionName: 1.0.7
    versionCode: 26
    commit: v1.0.7
    subdir: app
    gradle:
      - fdroid

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.7
CurrentVersionCode: 26

---

3. **Paste** into the GitLab editor (this will replace all the old text)
4. Verify the file now starts with `Categories:` and ends with `CurrentVersionCode: 26`
5. Scroll down and click **Commit changes** button

### Step 5: Wait for CI to Re-run

GitLab will automatically re-run the tests:
- ✅ Check source code
- ✅ Schema validation
- ✅ Tools check scripts  
- ✅ fdroid rewritemeta
- ✅ fdroid lint
- ✅ fdroid build

**All should pass now!** ✅

---

## 📸 Visual Comparison

### ❌ WRONG (What you had)

```
Line 1: ```yaml
Line 2: Categories:
Line 3:   - Multimedia
...
Last:   ```
```

### ✅ RIGHT (What it should be)

```
Line 1: Categories:
Line 2:   - Multimedia
...
Last:   CurrentVersionCode: 26
```

---

## 🔑 Key Points

**DO NOT COPY:**
- Triple backticks (` ``` `)
- The word "yaml" 
- Any markdown formatting

**DO COPY:**
- Starting from the "C" in "Categories"
- All content line by line
- Ending with "CurrentVersionCode: 26"

---

## ✨ After You Fix It

1. GitLab CI will automatically test the file again
2. All pipelines should pass ✅
3. F-Droid maintainers can then review your merge request
4. You'll be merged and on F-Droid in ~2 weeks! 🎉

---

## 🆘 Still Stuck?

Check that the file:
- [ ] Starts with `Categories:` (no backticks before it)
- [ ] Ends with `CurrentVersionCode: 26` (no backticks after it)
- [ ] Has proper YAML indentation (2 spaces, no tabs)
- [ ] Has no extra blank lines at start or end

If still failing, post the error message in your GitLab merge request comments and F-Droid team will help!
