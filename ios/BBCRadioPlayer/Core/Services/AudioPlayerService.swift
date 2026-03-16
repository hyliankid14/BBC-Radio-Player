import AVFoundation
import Foundation
import MediaPlayer

@MainActor
final class AudioPlayerService: ObservableObject {
    @Published private(set) var isPlaying = false
    @Published private(set) var currentStation: Station?
    @Published private(set) var currentEpisode: Episode?

    private var player: AVPlayer?
    private var timeObserver: Any?

    deinit {
        if let timeObserver, let player {
            player.removeTimeObserver(timeObserver)
        }
    }

    func play(station: Station, quality: PlaybackQuality) {
        guard let url = station.streamURL(quality: quality) else {
            return
        }

        currentStation = station
        currentEpisode = nil
        configureAudioSession()
        startPlayback(url: url)
        configureNowPlaying(title: station.title, subtitle: "Live radio")
    }

    func play(episode: Episode) {
        currentEpisode = episode
        currentStation = nil
        configureAudioSession()
        startPlayback(url: episode.audioURL)
        configureNowPlaying(title: episode.title, subtitle: "Podcast")
    }

    func pause() {
        player?.pause()
        isPlaying = false
    }

    func resume() {
        player?.play()
        isPlaying = true
    }

    private func startPlayback(url: URL) {
        if player == nil {
            player = AVPlayer(playerItem: AVPlayerItem(url: url))
            configureRemoteCommands()
        } else {
            let item = AVPlayerItem(url: url)
            player?.replaceCurrentItem(with: item)
        }

        player?.play()
        isPlaying = true
    }

    private func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.allowAirPlay])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Audio session error: \(error)")
        }
    }

    private func configureNowPlaying(title: String, subtitle: String) {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: subtitle
        ]
    }

    private func configureRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                self?.resume()
            }
            return .success
        }
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                self?.pause()
            }
            return .success
        }
    }
}
