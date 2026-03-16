import SwiftUI

struct SettingsView: View {
    @ObservedObject var settingsStore: AppSettingsStore
    @ObservedObject var analytics: PrivacyAnalyticsService
    @ObservedObject var podcastsViewModel: PodcastsViewModel
    @ObservedObject var episodeDownloadService: EpisodeDownloadService
    @State private var showPrivacyPolicy = false
    @State private var showDeleteDownloadsConfirmation = false
    @State private var statusMessage: String?

    var body: some View {
        Form {
            Section("Playback") {
                Picker("Quality", selection: $settingsStore.playbackQuality) {
                    ForEach(PlaybackQuality.allCases, id: \.self) { quality in
                        Text(quality.displayName).tag(quality)
                    }
                }

                if settingsStore.playbackQuality == .auto {
                    Text("Automatically selects High quality on Wi-Fi and Low quality on cellular data")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Picker("Next/Previous buttons", selection: $settingsStore.stationSkipMode) {
                    ForEach(StationSkipMode.allCases, id: \.self) { mode in
                        Text(mode.displayName).tag(mode)
                    }
                }

                Picker("Podcast artwork", selection: $settingsStore.podcastArtworkMode) {
                    ForEach(PodcastArtworkMode.allCases, id: \.self) { mode in
                        Text(mode.displayName).tag(mode)
                    }
                }
            }

            Section("Episode Index") {
                HStack {
                    Text("Last updated")
                    Spacer()
                    Text(settingsStore.episodeIndexLastUpdated.map(relativeDateString) ?? "Never")
                        .foregroundStyle(.secondary)
                }

                Button {
                    Task {
                        let didRefresh = await podcastsViewModel.refreshEpisodeIndex(force: true)
                        statusMessage = didRefresh ? "Episode index updated" : "Could not update episode index"
                    }
                } label: {
                    HStack {
                        Text("Update episode index now")
                        Spacer()
                        if podcastsViewModel.isRefreshingEpisodeIndex {
                            ProgressView()
                        }
                    }
                }
                .disabled(podcastsViewModel.isRefreshingEpisodeIndex)

                Toggle("Auto-update daily", isOn: $settingsStore.episodeIndexAutoUpdatesEnabled)
            }

            Section("Downloads") {
                Toggle("Auto-download saved episodes", isOn: $settingsStore.autoDownloadSavedEpisodes)
                Toggle("Auto-download latest subscribed episodes", isOn: $settingsStore.autoDownloadSubscribedPodcasts)

                HStack {
                    Text("Downloaded episodes")
                    Spacer()
                    Text("\(episodeDownloadService.downloadedEpisodeCount)")
                        .foregroundStyle(.secondary)
                }

                Button("Delete all downloads", role: .destructive) {
                    showDeleteDownloadsConfirmation = true
                }
                .disabled(episodeDownloadService.downloadedEpisodeCount == 0)

                Text("Downloads are saved in the app’s Documents folder so they can be accessed from the Files app.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Interface") {
                Picker("Theme", selection: $settingsStore.appTheme) {
                    ForEach(AppTheme.allCases, id: \.self) { theme in
                        Text(theme.displayName).tag(theme)
                    }
                }
            }

            Section("Privacy") {
                Toggle("Share anonymous analytics", isOn: Binding(
                    get: { analytics.isEnabled },
                    set: { analytics.isEnabled = $0 }
                ))

                Button("Privacy policy") {
                    showPrivacyPolicy = true
                }
            }

            Section("About") {
                Text("BBC Radio Player iOS port")
                HStack {
                    Text("Version")
                    Spacer()
                    Text(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "Unknown")
                        .foregroundStyle(.secondary)
                }
                Text("Initial parity target: favourites, stations, podcasts, settings")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Delete all downloads?", isPresented: $showDeleteDownloadsConfirmation) {
            Button("Delete", role: .destructive) {
                episodeDownloadService.deleteAllDownloads()
                statusMessage = "All downloads deleted"
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes all downloaded podcast episodes from local storage.")
        }
        .alert("Status", isPresented: Binding(
            get: { statusMessage != nil },
            set: { if !$0 { statusMessage = nil } }
        )) {
            Button("OK", role: .cancel) {
                statusMessage = nil
            }
        } message: {
            Text(statusMessage ?? "")
        }
        .sheet(isPresented: $showPrivacyPolicy) {
            NavigationStack {
                ScrollView {
                    Text("""
                    BBC Radio Player Analytics Privacy Policy

                    When you enable analytics:
                    • We collect station, podcast and episode play events
                    • We collect the date/time (UTC timestamp) and app version
                    • Data is sent over HTTPS to our private server
                    • IP addresses are not stored in the analytics database
                    • No user identifiers, device IDs or personal info are collected
                    • Data is anonymous and used only for popularity trends

                    When you disable analytics:
                    • No data is collected or sent
                    • You can disable it anytime in settings
                    """)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                }
                .navigationTitle("Privacy Policy")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Done") { showPrivacyPolicy = false }
                    }
                }
            }
        }
    }

    private func relativeDateString(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
