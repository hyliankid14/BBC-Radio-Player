import Foundation

@MainActor
final class AppContainer: ObservableObject {
    let stationRepository: StationRepository
    let podcastRepository: PodcastRepository
    let remoteIndexClient: RemoteIndexClient
    let audioPlayerService: AudioPlayerService
    let radioViewModel: RadioViewModel
    let podcastsViewModel: PodcastsViewModel

    init(
        stationRepository: StationRepository = DefaultStationRepository(),
        podcastRepository: PodcastRepository = DefaultPodcastRepository(),
        remoteIndexClient: RemoteIndexClient = RemoteIndexClient(),
        audioPlayerService: AudioPlayerService = AudioPlayerService()
    ) {
        self.stationRepository = stationRepository
        self.podcastRepository = podcastRepository
        self.remoteIndexClient = remoteIndexClient
        self.audioPlayerService = audioPlayerService
        self.radioViewModel = RadioViewModel(
            stationRepository: stationRepository,
            audioPlayerService: audioPlayerService
        )
        self.podcastsViewModel = PodcastsViewModel(
            podcastRepository: podcastRepository,
            audioPlayerService: audioPlayerService
        )
    }
}
