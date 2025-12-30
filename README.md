# Android Auto Radio Player

Minimal Android app exposing BBC Radio stations to Android Auto and providing one-touch play buttons.

Features
- One-touch play for BBC Radio 2, Radio 4, Radio 6 Music
- Implements a MediaBrowserService (`RadioService`) so Android Auto / car head units can browse and play
- Uses ExoPlayer for streaming

Quick start

1. Open this folder in Android Studio.
2. Sync Gradle and run on a device or emulator (API 33+ recommended).
3. To test on Android Auto head unit emulator or a car, use the Android Auto Desktop Head Unit (DHU) or pair a device.

Notes
- This is a minimal starter app. For production you should add proper media metadata, notification handling, audio focus, error handling, and follow Android Auto app quality requirements.
