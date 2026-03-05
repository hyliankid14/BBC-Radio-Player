# F-Droid Submission Draft

This folder contains metadata for submitting to F-Droid, following the format
described at https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/

## Structure

```
metadata/
├── com.hyliankid14.bbcradioplayer.yml          # App metadata / build recipe
└── com.hyliankid14.bbcradioplayer/
    └── en-US/
        ├── title.txt                           # App display name
        ├── short_description.txt               # One-line tagline (≤80 chars)
        ├── full_description.txt                # Full store description
        └── images/
            ├── icon.png                        # 512×512 app icon
            └── phoneScreenshots/               # In-app screenshots (1.jpg … 11.jpg)
```

## Notes

- Review `versionName`, `versionCode`, and `commit` before submission.
- Run F-Droid metadata linting in your `fdroiddata` fork before creating a MR.
- Keep F-Droid release notes focused on features available in the F-Droid build.
