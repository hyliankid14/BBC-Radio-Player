import Foundation

struct Podcast: Identifiable, Codable, Equatable {
    let id: String
    let title: String
    let description: String
    let rssURL: URL
    let htmlURL: URL?
    let imageURL: URL?
    let genres: [String]
    let typicalDurationMins: Int
}

struct Episode: Identifiable, Codable, Equatable {
    let id: String
    let title: String
    let description: String
    let audioURL: URL
    let imageURL: URL?
    let pubDate: String
    let durationMins: Int
    let podcastID: String
}

struct PodcastFilter: Equatable {
    var genres: Set<String> = []
    var minDuration: Int = 0
    var maxDuration: Int = 300
    var searchQuery: String = ""
}
