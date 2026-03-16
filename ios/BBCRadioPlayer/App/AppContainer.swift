import Foundation
import Combine

enum RootTab: Hashable {
    case favourites
    case stations
    case podcasts
    case settings
}

@MainActor
final class AppContainer: ObservableObject {
    @Published var selectedRootTab: RootTab = .favourites

    let stationRepository: StationRepository
    let podcastRepository: PodcastRepository
    let remoteIndexClient: RemoteIndexClient
    let audioPlayerService: AudioPlayerService
    let favoritesStore: FavoritesStore
    let appSettingsStore: AppSettingsStore
    let privacyAnalytics: PrivacyAnalyticsService
    let podcastNotificationService: PodcastNotificationService
    let radioViewModel: RadioViewModel
    let podcastsViewModel: PodcastsViewModel
    private var cancellables: Set<AnyCancellable> = []

    var shouldShowAnalyticsOptInDialog: Bool {
        privacyAnalytics.shouldShowOptInDialog
    }

    func setAnalyticsEnabled(_ enabled: Bool) {
        privacyAnalytics.isEnabled = enabled
    }

    func markAnalyticsOptInDialogShown() {
        privacyAnalytics.markOptInDialogShown()
    }

    func checkEpisodeNotifications() {
        Task {
            await podcastNotificationService.checkForNewEpisodes(
                podcastRepository: podcastRepository,
                favoritesStore: favoritesStore
            )
        }
    }

    func checkEpisodeIndexRefresh() {
        Task {
            await podcastsViewModel.refreshEpisodeIndexIfNeeded()
        }
    }

    init(
        stationRepository: StationRepository = DefaultStationRepository(),
        podcastRepository: PodcastRepository = DefaultPodcastRepository(),
        remoteIndexClient: RemoteIndexClient = RemoteIndexClient(),
        audioPlayerService: AudioPlayerService? = nil,
        favoritesStore: FavoritesStore = FavoritesStore(),
        appSettingsStore: AppSettingsStore = AppSettingsStore(),
        privacyAnalytics: PrivacyAnalyticsService = PrivacyAnalyticsService(),
        podcastNotificationService: PodcastNotificationService = PodcastNotificationService()
    ) {
        let resolvedAudioPlayerService = audioPlayerService ?? AudioPlayerService()

        self.stationRepository = stationRepository
        self.podcastRepository = podcastRepository
        self.remoteIndexClient = remoteIndexClient
        self.audioPlayerService = resolvedAudioPlayerService
        self.favoritesStore = favoritesStore
        self.appSettingsStore = appSettingsStore
        self.privacyAnalytics = privacyAnalytics
        self.podcastNotificationService = podcastNotificationService
        self.radioViewModel = RadioViewModel(
            stationRepository: stationRepository,
            audioPlayerService: resolvedAudioPlayerService,
            favoritesStore: favoritesStore,
            appSettingsStore: appSettingsStore
        )
        self.podcastsViewModel = PodcastsViewModel(
            podcastRepository: podcastRepository,
            remoteIndexClient: remoteIndexClient,
            audioPlayerService: resolvedAudioPlayerService,
            favoritesStore: favoritesStore
        )

        resolvedAudioPlayerService.onNextRequested = { [weak self] in
            self?.radioViewModel.playNextStation()
        }
        resolvedAudioPlayerService.onPreviousRequested = { [weak self] in
            self?.radioViewModel.playPreviousStation()
        }
        resolvedAudioPlayerService.updatePodcastArtworkMode(appSettingsStore.podcastArtworkMode)
        resolvedAudioPlayerService.updateAnalyticsService(privacyAnalytics)

        // Forward nested updates so root environment object refreshes immediately.
        favoritesStore.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)

        appSettingsStore.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)

        appSettingsStore.$podcastArtworkMode
            .removeDuplicates()
            .sink { [resolvedAudioPlayerService] mode in
                resolvedAudioPlayerService.updatePodcastArtworkMode(mode)
            }
            .store(in: &cancellables)

        resolvedAudioPlayerService.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)

        radioViewModel.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)

        podcastsViewModel.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }
}
