import Foundation

@MainActor
final class RadioViewModel: ObservableObject {
    @Published private(set) var stations: [Station] = []
    @Published var selectedQuality: PlaybackQuality = .high

    private let stationRepository: StationRepository
    private let audioPlayerService: AudioPlayerService

    init(stationRepository: StationRepository, audioPlayerService: AudioPlayerService) {
        self.stationRepository = stationRepository
        self.audioPlayerService = audioPlayerService
        stations = stationRepository.allStations()
    }

    func play(_ station: Station) {
        audioPlayerService.play(station: station, quality: selectedQuality)
    }

    func togglePlayback() {
        if audioPlayerService.isPlaying {
            audioPlayerService.pause()
        } else {
            audioPlayerService.resume()
        }
    }

    var isPlaying: Bool {
        audioPlayerService.isPlaying
    }

    var currentStationID: String? {
        audioPlayerService.currentStation?.id
    }
}
