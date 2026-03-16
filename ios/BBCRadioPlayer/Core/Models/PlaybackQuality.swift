import Foundation

enum PlaybackQuality: String, CaseIterable, Codable {
    case high
    case low

    var bitrate: String {
        switch self {
        case .high:
            return "320000"
        case .low:
            return "128000"
        }
    }
}
