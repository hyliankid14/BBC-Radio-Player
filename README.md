# Android Auto Radio Player

Minimal Android app exposing BBC Radio stations to Android Auto and providing seamless audio playback with favorites support.

## Design Philosophy

This app follows **Material Design 3** guidelines with a focus on:
- **Accessibility**: Proper contrast ratios and color tokens for all themes
- **Adaptive Color**: Support for dynamic color based on system theme (Android 12+)
- **Responsive Design**: Optimized for both light and dark modes
- **User-Centric**: Intuitive navigation and playback controls

## Features

- **Android Auto Integration**: Browse and play BBC Radio stations directly from your car's head unit
- **Extensive Station Library**: Access BBC national, regional, and local radio stations
- **Favorites Management**: Save your favorite stations for quick access
- **MediaBrowserService**: Implements `MediaBrowserServiceCompat` for full Android Auto compatibility
- **High-Quality Streaming**: Uses ExoPlayer for reliable HLS audio streaming
- **Playback Controls**: Play, pause, and stop controls in the car interface
- **Favorites Visibility**: See which stations are marked as favorites in the station list with a ★ indicator
- **Material Design 3**: Modern UI with dynamic color, proper spacing, and elevation

## Latest Developments

- **Material Design 3 Implementation**: Full M3 theme with color system, proper color tokens, and dynamic color support
- **Light & Dark Modes**: Proper color schemes for both light and dark themes with accessible contrast ratios
- **Star Icon Enhancement**: Cleaner, more uniform star outline design for non-favorited stations
- **Android Auto Favorites**: Favorite stations now display with a filled star icon (★) in the Android Auto station list, right-aligned for uniform appearance
- **Improved App Icon**: Modern purple-themed app icon with white radio design, matching the app's primary color scheme (#6200EE)
- **Station List Organization**: Android Auto now shows two browsable categories:
  - **Favorites**: Quick access to saved stations
  - **All Stations**: Complete list of all available BBC Radio stations
- **Mini Player UI**: Enhanced mobile app mini player with custom vector drawables for play, pause, and favorite controls

## Material Design 3 Implementation

The app implements Material Design 3 best practices:

### Color System
- **Primary Color**: #6200EE (Purple) - Used for primary actions and branding
- **Secondary Color**: #03DAC6 (Cyan) - Used for secondary actions
- **Tertiary Color**: #018786 (Teal) - Used for accent elements
- **Surface & Background**: Proper tone-based surface colors for both light and dark themes
- **Error Color**: #B3261E - Used for error states with proper contrast

### Components
- Proper use of Material Design 3 components (buttons, cards, lists, navigation)
- Correct spacing and padding following Material Guidelines
- Proper elevation and shadow effects
- Dynamic color support for Android 12+

### Typography & Spacing
- Follows Material Design 3 typography scale
- Consistent spacing using 4dp and 8dp grids
- Proper text sizing for readability

## Supported Stations

### BBC National Stations
- Radio 1
- Radio 1Xtra
- Radio 2
- Radio 3
- Radio 4
- Radio 4 Extra
- Radio 5 Live
- Radio 6 Music
- Asian Network

### BBC Regional Stations (England)
- Radio Bristol
- Radio Devon
- Radio Leeds
- Radio London
- Radio Northampton
- Radio Nottingham
- Radio Solent
- Radio Tees

### BBC National (Nations)
- Radio Scotland
- Radio Wales
- Radio Cymru
- Radio Ulster
- Radio Foyle

## Quick Start

1. Clone this repository
2. Open this folder in Android Studio
3. Sync Gradle and run on a device or emulator (API 21+ minimum, API 33+ recommended)
4. To test on Android Auto, use Android Auto for Phone Screens or the Android Auto Desktop Head Unit (DHU)

## Requirements

- Android API 21+
- Material Design 3 Components Library 1.11.0+
- ExoPlayer 2.18.2+
- Kotlin 1.9.23+

## Architecture

- **RadioService**: Core `MediaBrowserServiceCompat` implementation handling playback and Android Auto integration
- **ExoPlayer**: HLS streaming audio playback with proper audio focus management
- **FavoritesPreference**: SharedPreferences-based favorites management
- **StationRepository**: Central source for all available BBC Radio stations
- **Material 3 Theming**: Dynamic theme system with proper color tokens and dark mode support

## Notes

- This app uses HLS streams from BBC's Akamaized servers for worldwide access
- Playback is handled via ExoPlayer with proper audio focus management
- All network communication is encrypted (HTTPS)
- The app gracefully handles network errors and missing devices
- Follows Material Design 3 guidelines at https://m3.material.io/

## Troubleshooting: Using Gradle Wrapper (Linux)

If `./gradlew` fails locally, try these steps:

- Permissions: make the wrapper executable
  - `chmod +x gradlew`

- JDK 17: ensure Java 17 is installed and active
  - Install (Ubuntu/Debian):
    - `sudo apt-get update`
    - `sudo apt-get install -y openjdk-17-jdk`
  - Verify: `java -version` should report 17
  - Optional: set `JAVA_HOME` and `PATH`
    - `export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))`
    - `export PATH="$JAVA_HOME/bin:$PATH"`

- Android SDK: install via Android Studio or command-line tools
  - Set `ANDROID_SDK_ROOT` and add `platform-tools` to `PATH`
  - Accept licenses: `yes | sdkmanager --licenses`

- Network/Proxy: wrapper downloads Gradle from services.gradle.org
  - If blocked, configure proxy (`GRADLE_OPTS`), or build via CI (below)

- Quick checks
  - `./gradlew -v` — confirms Gradle + Java detection
  - `./gradlew tasks` — basic connectivity test

### Build without local Gradle

Use CI to build and deploy without a local Gradle/SDK setup:

- VS Code Task: Deploy to Device (runs `scripts/deploy.sh`)
  - Triggers [.github/workflows/android-build.yml](.github/workflows/android-build.yml)
  - Downloads the APK artifact and installs via `adb`

- Manual run:
  - `./scripts/deploy.sh "Your commit message"`

If problems persist, share the exact error output so we can tailor fixes (permissions, env vars, proxy, or missing SDK components).

