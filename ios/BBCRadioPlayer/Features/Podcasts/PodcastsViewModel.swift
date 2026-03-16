import Foundation

@MainActor
final class PodcastsViewModel: ObservableObject {
    @Published private(set) var podcasts: [Podcast] = []
    @Published private(set) var episodes: [Episode] = []
    @Published private(set) var selectedPodcast: Podcast?
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private let podcastRepository: PodcastRepository
    private let audioPlayerService: AudioPlayerService

    init(podcastRepository: PodcastRepository, audioPlayerService: AudioPlayerService) {
        self.podcastRepository = podcastRepository
        self.audioPlayerService = audioPlayerService
    }

    func loadPodcasts() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            podcasts = try await podcastRepository.fetchPodcasts(forceRefresh: false)
        } catch {
            errorMessage = "Could not load podcasts. Please try again."
        }
    }

    func selectPodcast(_ podcast: Podcast) async {
        selectedPodcast = podcast
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            episodes = try await podcastRepository.fetchEpisodes(for: podcast)
        } catch {
            errorMessage = "Could not load episodes for \(podcast.title)."
            episodes = []
        }
    }

    func play(_ episode: Episode) {
        audioPlayerService.play(episode: episode)
    }

    func clearSelection() {
        selectedPodcast = nil
        episodes = []
    }
}
