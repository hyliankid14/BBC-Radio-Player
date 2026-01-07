# BBC Radio Player

A feature-rich Android app for streaming BBC Radio stations with seamless Android Auto integration, intelligent playback features, and a modern Material Design 3 interface.

## Key Features

### 🚗 Android Auto Integration
- **Full MediaBrowserService Integration**: Native Android Auto support with browsable station hierarchy
- **Organized Station Browser**: Two main categories in Android Auto:
  - **Favorites**: Quick access to your saved stations
  - **All Stations**: Complete catalog of BBC Radio stations
- **Rich Metadata Display**: Live show information, episode titles, and artist/track data in your car
- **Favorite Indicators**: Starred icons (★) show which stations are favorited directly in the Android Auto interface
- **Auto-Resume Playback**: Optional setting to automatically resume last station when connecting to Android Auto
- **Seamless Controls**: Full playback control (play, pause, stop, skip, favorite) from your car's head unit

### 📻 Extensive Station Library (80+ Stations)
Access the complete BBC Radio network, organized into three categories:

#### National Stations (11 stations)
- Radio 1, 1Xtra, Radio 1 Dance
- Radio 2, Radio 3
- Radio 4, Radio 4 Extra
- Radio 5 Live, Radio 6 Music
- World Service, Asian Network

#### Regional Stations (9 stations)
- Scotland: Radio Scotland (FM & MW), Radio nan Gàidheal, Radio Orkney, Radio Shetland
- Wales: Radio Wales, Radio Cymru, Radio Cymru 2
- Northern Ireland: Radio Ulster, Radio Foyle

#### Local Stations (60+ stations)
All BBC Local Radio stations across England and the Channel Islands (Berkshire, Bristol, Cambridge, Cornwall, Coventry & Warwickshire, Cumbria, Derby, Devon, Essex, Gloucestershire, Guernsey, Hereford & Worcester, Humberside, Jersey, Kent, Lancashire, Leeds, Leicester, Lincolnshire, London, Manchester, Merseyside, Newcastle, Norfolk, Northampton, Nottingham, Oxford, Sheffield, Shropshire, Solent, Somerset, Stoke, Suffolk, Surrey, Sussex, Tees, Three Counties, West Midlands, Wiltshire, York, and more)

### ⭐ Favorites Management
- **Quick Favorites Access**: Dedicated favorites section in both mobile app and Android Auto
- **Drag-and-Drop Reordering**: Long-press and drag to rearrange your favorite stations
- **Visual Indicators**: Starred icons throughout the app show favorited stations
- **Persistent Storage**: Favorites saved locally and survive app updates

### 🎵 Live Metadata & Show Information
- **Real-Time Show Data**: Displays current show name and episode title
- **Now Playing Artist/Track**: Shows artist and track information when available (powered by BBC RMS API)
- **Album Artwork**: Dynamic artwork loading from BBC Sounds with caching
- **Auto-Scrolling Text**: Long show names and track titles automatically scroll in the mini player
- **Smart Polling**: Efficient 30-second refresh interval aligned with BBC's cache strategy

### 🎨 Material Design 3 Interface
- **Modern Purple Theme**: Professional purple color scheme (#6200EE primary)
- **Light & Dark Modes**: Full support for both light and dark themes with proper contrast ratios
- **System Theme Support**: Automatically follows system dark mode preferences
- **Material 3 Components**: Proper use of Material buttons, cards, navigation, and typography
- **Responsive Layout**: Optimized for both phones and tablets with adaptive layouts
- **Smooth Animations**: Polished transitions between screens and station categories

### 📱 Mobile App Features
- **Bottom Navigation**: Three main sections - Favorites, All Stations, Settings
- **Categorized Station Browser**: 
  - Tab-based navigation (National, Regions, Local)
  - Swipe gesture support to switch between categories
  - Animated transitions
- **Mini Player**: 
  - Persistent bottom player showing current station
  - Quick playback controls (previous, play/pause, next, stop, favorite)
  - Tap to open full Now Playing screen
  - Auto-scrolling song/show titles
- **Now Playing Screen**:
  - Large album artwork
  - Current show name and episode title
  - Artist and track information
  - Full playback controls
  - Dynamic favorite toggle

### ⚙️ Advanced Settings
- **Audio Quality Options**:
  - **Auto-Detect Quality**: Intelligent quality selection based on network type
    - WiFi: Always high quality (320kbps)
    - 4G/5G: High quality (320kbps)
    - 3G/2G or metered connections: Low quality (128kbps)
  - **Manual Quality Selection**: Choose between 320kbps (HQ) or 128kbps (LQ)
  - **Live Quality Switching**: Stream reloads automatically when quality is changed
- **Theme Selection**: Choose Light, Dark, or System theme
- **Previous/Next Behavior**: 
  - Skip through all stations (default)
  - Skip only through favorites
- **Auto-Resume on Android Auto**: Toggle automatic playback when connecting to car

### 🎧 Robust Playback Engine
- **ExoPlayer Integration**: Industry-standard media player for reliable HLS streaming
- **Audio Focus Management**: Proper handling of audio focus for calls, notifications, etc.
- **Background Playback**: Continues playing when app is minimized
- **Foreground Service**: Persistent notification with playback controls
- **Error Recovery**: Graceful handling of network errors and stream interruptions
- **Skip Controls**: Navigate to previous/next station with configurable behavior

## Technical Highlights

### Architecture & Components
- **RadioService**: `MediaBrowserServiceCompat` for Android Auto and background playback
- **StationRepository**: Centralized station data with category organization
- **ShowInfoFetcher**: Dual-API approach (BBC ESS for schedule, BBC RMS for now playing)
- **PlaybackStateHelper**: Centralized playback state management with observer pattern
- **NetworkQualityDetector**: Smart network condition detection for adaptive quality
- **Custom Views**: ScrollingTextView for auto-scrolling text, SquareImageView for artwork
- **Theme System**: ThemeManager + ThemePreference for persistent theme selection

### Data & APIs
- **BBC ESS API**: Episode schedule and show information
- **BBC RMS API**: Real-time "now playing" artist/track segments
- **HLS Streaming**: High-quality audio via Akamaized CDN (worldwide access)
- **SharedPreferences**: Persistent storage for favorites, settings, and last played station
- **Glide**: Efficient image loading and caching for artwork

### Design Patterns
- Observer pattern for show metadata updates
- Repository pattern for station data
- Service-oriented architecture for background playback
- Preference-based configuration management

## Material Design 3 Color System

### Light Theme
- **Primary**: #6200EE (Purple) - Primary actions and branding
- **On Primary**: #FFFFFF - Text/icons on primary
- **Primary Container**: #EADDFF - Primary container backgrounds
- **Secondary**: #625B71 - Secondary actions
- **Tertiary**: #7D5260 - Accent elements
- **Surface**: #FFFBFE - Card and sheet backgrounds
- **Background**: #FFFBFE - Screen backgrounds

### Dark Theme
- Inverse color tokens with proper contrast ratios
- Surface elevation system for depth
- High emphasis colors for accessibility

## Quick Start

### Build & Run
1. Clone this repository
2. Open in Android Studio Iguana (2023.2.1) or later
3. Sync Gradle dependencies
4. Run on a device or emulator (API 21+ minimum, API 33+ recommended)

### Testing on Android Auto
- **Phone Screens**: Use Android Auto for Phone Screens app
- **Desktop Head Unit**: Use the Android Auto Desktop Head Unit (DHU) simulator
- **Physical Car**: Connect via USB or wireless Android Auto

## Requirements

- **Android SDK**: API 21+ (Android 5.0 Lollipop) minimum
- **Recommended**: API 33+ (Android 13) for best experience
- **Kotlin**: 1.9.23+
- **Material Design 3**: Components Library 1.11.0+
- **ExoPlayer**: 2.18.2+
- **Glide**: 4.12.0+ for image loading
- **Coroutines**: For async operations

## Development & Deployment

### Prerequisites
- **JDK 17**: Required for Gradle builds
- **Android SDK**: Install via Android Studio or command-line tools
- **Android Studio**: Recommended IDE (Iguana 2023.2.1+)

### Local Build (Gradle)
```bash
# Make wrapper executable (Linux/Mac)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug
```

### VS Code Deployment Task
For quick deployment without local Gradle setup:
```bash
# Using the included deploy script
./scripts/deploy.sh "Your commit message"
```
This triggers the GitHub Actions workflow, builds the APK in the cloud, and installs it via `adb`.

### Troubleshooting Gradle (Linux)

**Permissions**:
```bash
chmod +x gradlew
```

**JDK 17 Installation** (Ubuntu/Debian):
```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk

# Verify installation
java -version

# Set JAVA_HOME (optional)
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
export PATH="$JAVA_HOME/bin:$PATH"
```

**Android SDK Setup**:
- Install via Android Studio or command-line tools
- Set `ANDROID_SDK_ROOT` environment variable
- Accept SDK licenses: `yes | sdkmanager --licenses`

**Network Issues**:
- Gradle downloads from services.gradle.org
- Configure proxy if needed: `GRADLE_OPTS="-Dhttps.proxyHost=... -Dhttps.proxyPort=..."`

**Quick Verification**:
```bash
./gradlew -v        # Check Gradle and Java detection
./gradlew tasks     # List available tasks
```

## Technical Notes

- **Streaming**: HLS streams from BBC's Akamaized CDN (worldwide access)
- **Audio Focus**: Proper audio focus management for calls and notifications
- **Network Security**: All communication over HTTPS
- **Error Handling**: Graceful recovery from network errors and stream interruptions
- **Performance**: Efficient metadata polling aligned with BBC's cache strategy (30s)
- **Accessibility**: Follows Material Design 3 accessibility guidelines

## License & Attribution

This is an unofficial third-party app for streaming BBC Radio. BBC Radio, BBC Sounds, and all station logos are trademarks of the British Broadcasting Corporation.

**Stream Sources**: BBC's public HLS streams via Akamaized CDN  
**Metadata APIs**: BBC ESS (schedule) and RMS (now playing) public APIs  
**No Affiliation**: This app is not affiliated with or endorsed by the BBC

## Contributing

Contributions are welcome! Areas for potential enhancement:
- Additional radio networks (other countries)
- Podcast integration
- Sleep timer functionality
- Android Auto custom actions
- Widget support
- Chromecast support

## Changelog

See commit history for detailed changes. Major milestones:
- **v1.0**: Initial release with Android Auto support
- **v1.1**: Material Design 3 implementation
- **v1.2**: Live metadata integration (show names, artist/track)
- **v1.3**: Adaptive quality streaming
- **v1.4**: Drag-and-drop favorites reordering
- **v1.5**: Auto-resume on Android Auto connection

