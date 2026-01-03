# Android Auto Radio Player

Minimal Android app exposing BBC Radio stations to Android Auto and providing seamless audio playback with favorites support.

## Features

- **Android Auto Integration**: Browse and play BBC Radio stations directly from your car's head unit
- **Extensive Station Library**: Access BBC national, regional, and local radio stations
- **Favorites Management**: Save your favorite stations for quick access
- **MediaBrowserService**: Implements `MediaBrowserServiceCompat` for full Android Auto compatibility
- **High-Quality Streaming**: Uses ExoPlayer for reliable HLS audio streaming
- **Playback Controls**: Play, pause, and stop controls in the car interface
- **Favorites Visibility**: See which stations are marked as favorites in the station list with a ★ indicator

## Latest Developments

- **Star Icon Enhancement**: Cleaner, more uniform star outline design for non-favorited stations
- **Android Auto Favorites**: Favorite stations now display with a filled star icon (★) in the Android Auto station list, right-aligned for uniform appearance
- **Improved App Icon**: Modern purple-themed app icon with white radio design, matching the app's primary color scheme (#6200EE)
- **Station List Organization**: Android Auto now shows two browsable categories:
  - **Favorites**: Quick access to saved stations
  - **All Stations**: Complete list of all available BBC Radio stations
- **Mini Player UI**: Enhanced mobile app mini player with custom vector drawables for play, pause, and favorite controls

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
3. Sync Gradle and run on a device or emulator (API 33+ recommended)
4. To test on Android Auto, use Android Auto for Phone Screens or the Android Auto Desktop Head Unit (DHU)

## Architecture

- **RadioService**: Core `MediaBrowserServiceCompat` implementation handling playback and Android Auto integration
- **ExoPlayer**: HLS streaming audio playback
- **FavoritesPreference**: SharedPreferences-based favorites management
- **StationRepository**: Central source for all available BBC Radio stations

## Notes

- This app uses HLS streams from BBC's Akamaized servers for worldwide access
- Playback is handled via ExoPlayer with proper audio focus management
- All network communication is encrypted (HTTPS)
- The app gracefully handles network errors and missing devices

