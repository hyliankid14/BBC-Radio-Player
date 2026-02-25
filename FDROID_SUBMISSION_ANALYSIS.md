# F-Droid Submission Analysis - BBC Radio Player

**Date:** 22 February 2026  
**App:** BBC Radio Player  
**Repository:** https://github.com/shaivure/Android-Auto-Radio-Player  
**Status:** ⚠️ REQUIRES FIXES BEFORE SUBMISSION

---

## Executive Summary

Your app is **almost ready for F-Droid submission**, but there are **several critical issues** that must be addressed first. The most significant issues involve proprietary Google dependencies and missing licensing information.

---

## ✅ STRENGTHS - What You Have Right

1. **Public Source Repository:** ✅ GitHub repo is public and accessible
2. **Gradle Build System:** ✅ Uses Gradle (fully supported by F-Droid)
3. **No Analytics/Tracking:** ✅ No Firebase, Crashlytics, or Google Analytics
4. **No Advertisements:** ✅ Ad-free application
5. **Open Architecture:** ✅ Kotlin-based Android app with clean codebase
6. **Material Design:** ✅ Modern Material Design 3 UI
7. **Reasonable Permissions:** ✅ Only requests necessary permissions (internet, network state, notifications)

---

## ❌ CRITICAL ISSUES - Must Fix

### 1. **Missing LICENSE File** 🔴 CRITICAL
**Status:** Not found in repository  
**Impact:** F-Droid requires a visible FOSS license file in repository root

**What You Need To Do:**
- Add a `LICENSE` file to the repository root
- Choose an appropriate license (see recommendations below)
- Update README.md to include license statement

**License Recommendation:**
The app appears suitable for **GPL-3.0** or **Apache-2.0** licenses. GPL-3.0 would be appropriate for a media player with community contributions. Example:

```
Copyright (c) 2024-2026 [Your Name/Organization]

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

### 2. **Google Mobile Services (GMS) Dependencies** 🔴 CRITICAL
**Status:** Found in AndroidManifest.xml  
**Issue:** Non-FOSS proprietary Google libraries

**Problematic Uses Found:**

a) **Android Auto/GMS Car Integration**
```xml
<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
```

b) **Google Backup API**
```xml
<meta-data
    android:name="com.google.android.backup.api_key"
    android:value="AEdPqrMQQqCf4X2O9hXN0jEbO/Bc04b2Yb1e6n8Qlbb+2lNMO6lR4xQDWZQ6bnEV" />
```

**What F-Droid Requires:**
F-Droid performs automatic cleanup of Google API calls, BUT Google ML Kit and GMS dependencies in code must be removed or made optional via flavors.

**Solutions Available:**

**Option A: Remove GMS Dependencies (RECOMMENDED FOR F-DROID)**
- Remove the two meta-data entries from AndroidManifest.xml
- Remove Google Backup API key
- Android Auto still works without the GMS declarations (they're just optimizations)
- This makes the app F-Droid compliant

**Option B: Create Build Flavors**
- Create separate `fdroid` and `play` flavors
- `fdroid` flavor: removes all GMS
- `play` flavor: includes GMS optimizations
- F-Droid builds the `fdroid` flavor

### 3. **Google ML Kit Language Identification** 🟡 CONCERNING
**Status:** In build.gradle dependencies
```groovy
implementation 'com.google.mlkit:language-id:17.0.6'
```

**Status:** Requires analysis - ML Kit libraries *may* work with F-Droid, but F-Droid should verify they work properly without Google Play Services.

**Action Needed:**
- Test that the app builds and runs without `com.google.android.gms:play-services-*`
- OR: Make this dependency optional/removable for F-Droid builds

---

## 🟡 POTENTIAL ISSUES - Review Required

### 1. **Third-Party API Dependencies**
- **BBC Sounds API:** Verify you have rights/permission to scrape/use BBC API
- **is.gd URL Shortener:** Check if this service is FOSS-compatible
- **Glide Image Library:** ✅ Apache 2.0 licensed (OK)
- **ExoPlayer:** ✅ Apache 2.0 licensed (OK)

### 2. **Cleartext Traffic**
```xml
android:usesCleartextTraffic="true"
```
- This is set in manifest - only needed for HTTP APIs
- Verify BBC APIs don't support HTTPS
- If they do, remove this flag for security

### 3. **Backup Configuration**
Study your backup rules to ensure no sensitive data is backed up:
- `@xml/backup_rules`
- `@xml/data_extraction_rules`
Need to verify these don't include credentials or API keys

----

## 📋 PRE-SUBMISSION CHECKLIST

**Critical (Must Do Before Submission):**
- [ ] Add LICENSE file to repository root (GPL-3.0 recommended)
- [ ] Remove or make optional:
  - [ ] `com.google.android.gms.car.application` meta-data
  - [ ] `com.google.android.backup.api_key` meta-data
- [ ] Update README.md with license header
- [ ] Test app builds and runs without GMS dependencies
- [ ] Verify backup config doesn't expose sensitive data
- [ ] Confirm all dependencies are FOSS-licensed

**Important (Should Do):**
- [ ] Verify BBC API usage is licensed/permitted
- [ ] Add FOSS-compliant metadata (descriptions, screenshots, privacy policy)
- [ ] Create F-Droid metadata folder structure
- [ ] Document build instructions for F-Droid

**Optional (Best Practice):**
- [ ] Add GitHub release tags matching version codes
- [ ] Create build flavors for fdroid vs play
- [ ] Implement reproducible builds
- [ ] Add build instructions in README

---

## 🚀 SUBMISSION PROCESS (After Fixes)

### **Option 1: Submission Queue (Easier, Slower)**
1. Go to https://gitlab.com/fdroid/rfp/issues
2. Create new issue with:
   - App name
   - GitHub repo URL
   - Brief description
   - Any special build notes
3. Wait for F-Droid team to review (can take weeks/months)

**Pros:** Less work, F-Droid team handles metadata  
**Cons:** Slower, lower priority

### **Option 2: Metadata Merge Request (Better, Faster)**
1. Fork https://gitlab.com/fdroid/fdroiddata
2. Write F-Droid metadata file using `fdroid import`
3. Create merge request with:
   - Metadata file
   - Screenshots
   - App description
4. F-Droid CI tests the build automatically
5. Get merged within days (if approved)

**Pros:** Faster inclusion, you control metadata  
**Cons:** Requires more technical knowledge

**Recommended:** Option 2 if you're comfortable with GitLab, Option 1 if not

---

## 📦 F-DROID METADATA STRUCTURE (If Using Option 2)

Create this folder structure in fdroiddata fork:
```
metadata/com.hyliankid14.bbcradioplayer/
├── en-US/
│   ├── name.txt
│   ├── summary.txt
│   ├── description.txt
│   ├── full_description.txt
│   ├── short_description.txt
│   └── images/
│       ├── icon.png (512x512)
│       ├── featureGraphic.png (1024x500)
│       ├── phoneScreenshots/
│       │   ├── 1.png
│       │   ├── 2.png
│       │   ├── 3.png
│       │   └── 4.png
│       └── tenInchScreenshots/
│           └── 1.png
└── FUNDING.yml (optional)

com.hyliankid14.bbcradioplayer.yml (main metadata file)
```

---

## 📝 NEXT STEPS

1. **Immediate (This Week):**
   - ✅ Add LICENSE file
   - ✅ Remove GMS declarations from manifest
   - ✅ Test app builds without GMS

2. **Short Term (This Month):**
   - ✅ Verify all API usage is permitted
   - ✅ Prepare F-Droid metadata
   - ✅ Take app screenshots for metadata

3. **Submission:**
   - ✅ Choose submission option (Queue vs MR)
   - ✅ Submit to F-Droid
   - ✅ Respond to any F-Droid reviewer questions

Expected timeline after fixes:
- **Option 1 (Queue):** 2-12 weeks
- **Option 2 (MR):** 1-2 weeks if approved

---

## ⚠️ IMPORTANT NOTES FOR F-DROID REVIEWERS

When you submit, mention:
- App requires internet for BBC API access
- Uses Android Auto MediaBrowserService (no GMS required)
- Includes podcasts with on-device ML Kit language detection
- Supports API 21+ (Android 5.0)
- No proprietary binaries
- No tracking or analytics

---

## 🆘 ADDITIONAL RESOURCES

- [F-Droid Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy)
- [F-Droid Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference)
- [fdroiddata Contributing Guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md)
- [F-Droid Descriptions Guide](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots)

---

**Status:** Ready to proceed with fixes → F-Droid submission targeting **March/April 2026**
