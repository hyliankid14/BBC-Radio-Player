# Podcasts Feature Implementation

This document describes the podcasts feature added to the BBC Radio Player app.

## Overview

The podcasts section allows users to browse and filter BBC podcasts from the official BBC podcast OPML feed at `https://www.bbc.co.uk/radio/opml/bbc_podcast_opml.xml`.

## Features

### 1. Podcast Browsing
- Displays a list of all BBC podcasts with their artwork and genre
- Fetches podcasts from the BBC OPML feed
- Caches podcasts to avoid repeated network calls

### 2. Genre Filtering
- Automatically extracts genre categories from podcast metadata
- Supports filtering by genres including:
  - News
  - Sport
  - Comedy
  - Drama
  - Documentary
  - Music
  - Science
  - History
  - Arts
  - Culture
  - Politics
  - Business
  - Technology
  - Health
  - Education
  - Entertainment
  - Kids
  - Other (for uncategorized podcasts)
- "All" option to show all podcasts

### 3. Episode Duration Filtering
- Three duration categories:
  - **Short**: 0-15 minutes
  - **Medium**: 15-45 minutes
  - **Long**: 45+ minutes
- "All Durations" option to show all episodes
- Duration is parsed from iTunes RSS tags

## Architecture

### Data Models
- **Podcast**: Represents a podcast show with title, description, RSS feed URL, image, and genre
- **PodcastEpisode**: Represents an individual episode with title, description, audio URL, duration, and publication date
- **DurationCategory**: Enum for episode duration filtering

### Repository
- **PodcastRepository**: Handles fetching and parsing of OPML and RSS feeds
  - `fetchPodcasts()`: Fetches and parses the BBC OPML feed
  - `fetchEpisodes(podcast)`: Fetches and parses a podcast's RSS feed for episodes
  - `getUniqueGenres()`: Extracts unique genres from podcasts
  - Caching support to minimize network requests

### UI Components
- **PodcastAdapter**: RecyclerView adapter for displaying podcasts with genre filtering
- **EpisodeAdapter**: RecyclerView adapter for displaying episodes with duration filtering
- **PodcastDetailActivity**: Shows podcast details and episode list with duration filter

### Layouts
- `podcast_list_item.xml`: List item for podcasts
- `episode_list_item.xml`: List item for episodes
- `activity_podcast_detail.xml`: Podcast detail screen

## Navigation

The podcasts section is accessible via a new tab in the bottom navigation menu, represented by a podcast icon.

## Technical Details

### Dependencies
- **OkHttp**: HTTP client for fetching OPML and RSS feeds
- **Kotlinx Coroutines**: Asynchronous operations for network requests
- **Glide**: Image loading for podcast artwork

### XML Parsing
- Uses Java's built-in `DocumentBuilderFactory` for parsing OPML and RSS XML
- Supports iTunes podcast tags:
  - `<itunes:duration>`: Episode duration in HH:MM:SS, MM:SS, or seconds format
  - `<itunes:image>`: Podcast and episode artwork
  - `<itunes:category>`: Genre categories (used for extraction)

### Network Requests
- All network operations are performed on background threads using Kotlin coroutines
- Progress indicators shown during loading
- Error handling with user-friendly error messages

## Limitations

1. **Audio Playback**: Episode playback is not yet implemented. Clicking an episode shows a toast message. Full implementation would require:
   - Integration with existing RadioService
   - Or creation of a new PodcastPlayerService
   - Support for seek, pause, resume, and playback progress

2. **Offline Support**: Podcasts are not cached for offline access

3. **Episode Downloads**: No support for downloading episodes for offline listening

4. **Playback Queue**: No support for episode queues or playlists

## Future Enhancements

- Implement actual podcast playback
- Add episode download functionality
- Support for marking episodes as played
- Podcast subscription management
- Search functionality
- Sort options (by date, popularity, etc.)
- Episode descriptions and show notes
- Share podcast/episode functionality
