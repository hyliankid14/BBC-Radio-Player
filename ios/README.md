# iOS Port (Initial Implementation)

This directory contains the first implementation slice of a native iOS port for BBC Radio Player.

## Current status

Implemented in this first drop:

- SwiftUI app shell with tabs for Radio and Podcasts
- AVPlayer-based playback service with lock screen command wiring (play/pause)
- Shared domain models (`Station`, `Podcast`, `Episode`)
- Station repository with seed BBC stations and HQ/LQ stream URL logic
- Podcast repository scaffold with OPML and RSS parsing baseline
- Remote index metadata client for `podcast-index-meta.json`

Not implemented yet (planned next):

- Full station catalogue parity with Android
- Podcast index sync and local SQLite FTS search parity
- Download/offline queue, retry rules and limits
- Notification and background refresh parity
- Settings parity (including import/export and advanced preferences)
- Widgets, alarms and deep-link parity

## How to use this code in Xcode

1. Create a new iOS App project in Xcode named `BBCRadioPlayer`.
2. Set deployment target to iOS 16.0 (to include iPhone 8 support).
3. Add all `.swift` files under `ios/BBCRadioPlayer/` to the app target.
4. In Signing & Capabilities, enable:
   - Background Modes -> Audio, AirPlay, and Picture in Picture
5. Build and run on your iPhone 8.

## Immediate next tasks

1. Expand `DefaultStationRepository` to full Android station parity.
2. Replace regex parsing with XMLParser-based OPML/RSS parser for reliability.
3. Add a persistent store (SQLite) and implement FTS-backed search.
4. Add playback state persistence (last station, last podcast position).
5. Add error/retry UX and connectivity-aware quality fallback.
