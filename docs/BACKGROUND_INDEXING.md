# Background Indexing

## Overview

The BBC Radio Player app now supports background indexing using Android WorkManager. This allows podcast indexing to:

- **Continue running when you switch apps** - The indexing process won't be interrupted if you switch to another app
- **Run when the app is closed** - Scheduled indexing can run even when the app is not open
- **Show progress notifications** - A notification shows the indexing progress
- **Handle interruptions gracefully** - The system manages battery and network constraints

## Features

### Manual Indexing
When you tap "Index Now" in the settings:
- A background worker starts immediately
- A notification appears showing indexing progress
- You can close the app or switch to other apps
- The indexing continues in the background
- You'll see a completion notification when done

### Scheduled Indexing
Configure automatic indexing in the settings:
- Set an interval (daily, weekly, etc.)
- The system will automatically index new podcasts and episodes
- Only runs when connected to WiFi and battery is not low
- Completely automatic - no manual intervention needed

## Technical Details

### WorkManager
The app uses AndroidX WorkManager for reliable background execution:
- **Persistent** - Survives app restarts and device reboots
- **Battery efficient** - System batches work intelligently
- **Constraint-aware** - Respects network and battery conditions
- **Foreground service** - Shows notification during active indexing

### Permissions
Required permissions for background indexing:
- `INTERNET` - Download podcast data
- `ACCESS_NETWORK_STATE` - Check network connectivity
- `FOREGROUND_SERVICE` - Run foreground service
- `FOREGROUND_SERVICE_DATA_SYNC` - Data sync foreground service type
- `POST_NOTIFICATIONS` - Show progress notifications

### How It Works

1. **Enqueue Work**: When indexing is triggered (manually or scheduled), a WorkRequest is created
2. **Run as Foreground Service**: The work runs as a foreground service with a notification
3. **Progress Updates**: The notification updates to show current progress
4. **Completion**: When done, the index is saved and a completion notification is shown
5. **Automatic Retry**: If it fails (e.g., network issue), WorkManager can retry

### Code Structure

- `BackgroundIndexWorker.kt` - Main WorkManager worker class
- `IndexWorker.kt` - Core indexing logic (reused by background worker)
- `IndexScheduler.kt` - Manages periodic scheduling via WorkManager
- `MainActivity.kt` - Observes work status and updates UI

## User Experience

### While Indexing
- Notification shows: "Indexing Podcasts"
- Progress bar updates as indexing proceeds
- You can continue using your device normally

### After Completion
- Brief notification: "Podcast indexing completed successfully"
- Search becomes faster with the updated index
- All new podcasts and episodes are searchable

## Troubleshooting

**Indexing doesn't start:**
- Check internet connection
- Ensure battery saver is not blocking background work
- Verify app has notification permissions

**Indexing stops unexpectedly:**
- Android may kill background work if memory is low
- Check battery optimization settings for the app
- Try manual indexing when phone is charging

**Scheduled indexing doesn't run:**
- Verify interval is set in settings
- Check that battery optimization allows background work
- Ensure device is connected to WiFi
