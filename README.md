# BBC Radio Player

A feature-rich Android app for streaming BBC Radio stations with seamless Android Auto integration, comprehensive podcast support with downloads and subscriptions, intelligent playback features, and a modern Material Design 3 interface.

## Key Features

### 🚗 Android Auto Integration
- **Full MediaBrowserService Integration**: Native Android Auto support with browsable station hierarchy
- **Organized Browser**: Multiple categories in Android Auto:
  - **Favorites**: Quick access to your saved stations, episodes, and podcasts
  - **All Stations**: Complete catalog of BBC Radio stations
  - **Saved Episodes**: Your bookmarked podcast episodes
  - **Subscriptions**: Podcasts you're subscribed to
- **Rich Metadata Display**: Live show information, episode titles, and artist/track data in your car
- **Favorite Indicators**: Starred icons (★) show which stations are favorited directly in the Android Auto interface
- **Auto-Resume Playback**: Optional setting to automatically resume last station or podcast when connecting to Android Auto
- **Seamless Controls**: Full playback control (play, pause, stop, skip, seek, favorite) from your car's head unit
- **Podcast Playback**: Full podcast support with seekbar, progress tracking, and episode information

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
### 🎙 Podcast Support ✅
- **Full podcast playback**: Subscribe to shows, play individual episodes, and save episodes to your saved list for quick access.
- **Accurate metadata**: Episode title, series (podcast) title, description and episode artwork are shown in-app, in the notification shade, and on Android Auto.
- **Notification & Android Auto fixes**: Podcast series name is now always shown as the notification and Android Auto title (fixes cases that previously displayed a generic "Podcast"). The service passes series metadata through all resume/autoplay fallbacks and resolves missing metadata in the background when needed.
- **Playback resume & smart replay**: Episode progress is persisted; replaying an episode marked as completed starts from 0 but preserves the played flag; progress is periodically saved to minimize writes.
- **Autoplay next episode**: When an episode ends, the service will try to autoplay the next chronologically newer episode in the same podcast.
- **Save & subscription management**: Save/bookmark episodes and manage podcast subscriptions; saved episodes and podcast subscriptions are accessible from the Favorites section and exposed to Android Auto browsers.
- **Per-podcast notifications**: Toggle notifications on/off for individual podcasts with visual indicators
- **Robust fallbacks**: Episodes are resolved via subscriptions, saved entries, or the local index when auto-resuming (Android Auto) so the correct series/title is displayed even in edge cases.
- **Automatic episode indexing**: Background indexing system keeps track of new episodes from BBC podcasts with configurable intervals (daily, weekly, bi-weekly, or manual)
- **Subscription notifications**: Get notified of new episodes with configurable refresh intervals (15 minutes to 24 hours, or disabled)

### 📥 Episode Download Management
- **Auto-Download Episodes**: Automatically download new episodes from subscribed podcasts
- **Configurable Download Limits**: Choose to download 1, 2, 3, 5, or 10 latest episodes per podcast
- **WiFi-Only Downloads**: Option to restrict downloads to WiFi connections to save mobile data
- **Auto-Delete on Completion**: Optionally delete downloaded episodes after playing to completion to save storage
- **Batch Management**: Delete all downloaded episodes with one tap
- **Offline Playback**: Play downloaded episodes without internet connection

### 📜 Playback History
- **Recent Episodes Tracking**: Automatically tracks your last 20 played podcast episodes
- **Quick Access**: View your play history in the Favorites section
- **Progress Display**: See how much of each episode you've listened to
- **Replay Episodes**: Easily replay episodes from your history

### 🔗 Sharing Features
- **Share Podcasts**: Share entire podcast series with friends via Android share sheet
- **Share Episodes**: Share individual episodes with rich metadata
- **Smart URLs**: Automatically shortened URLs for cleaner sharing (via is.gd)
- **Cross-Platform**: Non-app users get web player links, app users get deep links
- **Rich Metadata**: Shared content includes title, description, and artwork
- **Web Player Integration**: Hosted at GitHub Pages for users without the app
### 🎨 Material Design 3 Interface
- **Modern Purple Theme**: Professional purple color scheme (#6200EE primary)
- **Light & Dark Modes**: Full support for both light and dark themes with proper contrast ratios
- **System Theme Support**: Automatically follows system dark mode preferences
- **Material 3 Components**: Proper use of Material buttons, cards, navigation, and typography
- **Responsive Layout**: Optimized for both phones and tablets with adaptive layouts
- **Smooth Animations**: Polished transitions between screens and station categories

### 📱 Mobile App Features
- **Bottom Navigation**: Three main sections - Favorites, All Stations, Settings
- **Enhanced Favorites Section**:
  - **Tab-based Navigation**: Switch between Stations, Saved Episodes, Podcasts, and History
  - **Drag-and-Drop**: Reorder favorite stations with long-press and drag (with haptic feedback)
  - **Quick Access**: One-tap access to all your favorited content
- **Categorized Station Browser**: 
  - Tab-based navigation (National, Regions, Local)
  - Swipe gesture support to switch between categories
  - Animated transitions
- **Podcast Discovery**:
  - Browse all BBC podcasts
  - Search functionality
  - Filter by language (English/all languages)
  - Subscribe to shows and episodes
  - Visual indicators for subscribed podcasts
  - Per-podcast notification toggles
- **Mini Player**: 
  - Persistent bottom player showing current station or episode
  - Quick playback controls (previous, play/pause, next, stop, favorite)
  - Episode progress bar for podcasts
  - Tap to open full Now Playing screen
  - Auto-scrolling song/show titles
- **Now Playing Screen**:
  - Large album artwork
  - Current show name and episode title (radio) or episode details (podcast)
  - Artist and track information
  - Seekbar for podcast episodes
  - Playback progress and time remaining
  - Full playback controls
  - Dynamic favorite toggle
  - Share button for podcasts and episodes
  - Episode description viewer
  - Manual mark-as-played button for podcasts

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
- **Android Auto Settings**:
  - **Auto-Resume**: Toggle automatic playback when connecting to Android Auto
- **Podcast Subscription Settings**:
  - **Refresh Interval**: Configure how often to check for new episodes (15 min to 24 hours, or disabled)
  - **Language Filtering**: Option to exclude non-English podcasts from search
- **Episode Download Settings**:
  - **Auto-Download**: Enable automatic downloading of new episodes
  - **Download Limits**: Configure how many recent episodes to keep (1-10)
  - **WiFi-Only Mode**: Restrict downloads to WiFi connections
  - **Auto-Delete**: Delete episodes after playing to completion
- **Podcast Indexing**:
  - **Background Indexing**: Configure automatic index rebuilding (daily, weekly, bi-weekly, or manual)
  - **Index Management**: Manual index rebuild and progress tracking
- **Backup & Restore**:
  - **Export Settings**: Save all preferences, favorites, subscriptions, and saved episodes to a file
  - **Import Settings**: Restore from a backup file

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
- **PodcastsViewModel**: MVVM architecture for podcast data management
- **IndexStore**: SQLite-based episode indexing for fast podcast search
- **EpisodeDownloadManager**: Background download manager with progress tracking and retry logic
- **PlayedHistoryPreference**: Persistent playback history tracking
- **SubscriptionRefreshScheduler**: WorkManager-based background job for checking new episodes
- **BackgroundIndexWorker**: Periodic indexing of BBC podcast episodes
- **ShareUtil**: Cross-platform sharing with URL shortening and deep linking
- **Custom Views**: ScrollingTextView for auto-scrolling text, SquareImageView for artwork
- **Theme System**: ThemeManager + ThemePreference for persistent theme selection

### Data & APIs
- **BBC ESS API**: Episode schedule and show information
- **BBC RMS API**: Real-time "now playing" artist/track segments
- **BBC Sounds RSS**: Podcast feed discovery and episode metadata
- **HLS Streaming**: High-quality audio via Akamaized CDN (worldwide access)
- **SharedPreferences**: Persistent storage for favorites, settings, and last played station
- **SQLite Database**: Local episode index for fast search and filtering
- **Glide**: Efficient image loading and caching for artwork
- **WorkManager**: Background task scheduling for downloads and indexing

### Design Patterns
- Observer pattern for show metadata updates
- Repository pattern for station and podcast data
- Service-oriented architecture for background playback
- Preference-based configuration management
- MVVM architecture for UI components
- Worker pattern for background tasks

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
- **WorkManager**: For background tasks (episode indexing, subscription checks)

## Development & Deployment

### Prerequisites
- **JDK 21**: Required for Gradle builds
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

**JDK 21 Installation** (Ubuntu/Debian):
```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk

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
- **Background Processing**: WorkManager for reliable background tasks (indexing, downloads, notifications)
- **Offline Playback**: Downloaded episodes available without internet connection
- **Storage Management**: Efficient caching and optional auto-deletion of played content
- **Deep Linking**: Support for app:// scheme for cross-platform sharing

## License & Attribution

This is an unofficial third-party app for streaming BBC Radio. BBC Radio, BBC Sounds, and all station logos are trademarks of the British Broadcasting Corporation.

**Stream Sources**: BBC's public HLS streams via Akamaized CDN  
**Metadata APIs**: BBC ESS (schedule) and RMS (now playing) public APIs  
**No Affiliation**: This app is not affiliated with or endorsed by the BBC

## Contributing

Contributions are welcome! Areas for potential enhancement:
- Additional radio networks (other countries)
- Sleep timer functionality
- Android Auto custom actions
- Widget support
- Chromecast support
- CarPlay support (if porting to iOS)

## Changelog

See commit history for detailed changes. Major releases:

- **v0.10.0** (Latest - Feb 2026): Episode download system with auto-download, WiFi-only mode, configurable limits (1-10 episodes), and auto-delete on completion; comprehensive settings backup and restore
- **v0.9.7**: APK signing configuration and build improvements
- **v0.9.6**: GitHub release automation, JDK 21 requirement, audio focus handling improvements, podcast UI enhancements
- **v0.9.2**: Improved podcast search functionality
- **v0.9.1**: Podcast search UI improvements
- **v0.9**: Episode sharing with URL shortening (via is.gd) and web player integration
- **v0.8**: Subscription notifications with configurable refresh intervals
- **v0.7**: Playback history tracking (last 20 episodes)
- **v0.6**: Full BBC Podcasts integration with subscribe/play/save episodes, persistent progress, smart replay, autoplay next episode, and improved Android Auto/notification metadata
- **v0.5**: First public release with Android Auto support, Material Design 3 interface, 80+ BBC Radio stations, live metadata, adaptive quality streaming, drag-and-drop favorites, and auto-resume on Android Auto connection

