import Foundation

protocol PodcastRepository {
    func fetchPodcasts(forceRefresh: Bool) async throws -> [Podcast]
    func fetchEpisodes(for podcast: Podcast) async throws -> [Episode]
}

struct DefaultPodcastRepository: PodcastRepository {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func fetchPodcasts(forceRefresh: Bool = false) async throws -> [Podcast] {
        // Initial implementation: read OPML and produce seed podcast data.
        // Full parity implementation will add disk caching, language filtering and FTS sync.
        guard let url = URL(string: "https://www.bbc.co.uk/radio/opml/bbc_podcast_opml.xml") else {
            return []
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = 20

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200..<300).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }

        return OPMLPodcastParser.parse(data: data)
    }

    func fetchEpisodes(for podcast: Podcast) async throws -> [Episode] {
        var request = URLRequest(url: podcast.rssURL)
        request.timeoutInterval = 20

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200..<300).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }

        return try RSSPodcastParser.parseEpisodes(data: data, podcastID: podcast.id)
    }
}

enum OPMLPodcastParser {
    static func parse(data: Data) -> [Podcast] {
        guard let content = String(data: data, encoding: .utf8) else {
            return []
        }

        let pattern = #"xmlUrl=\"([^\"]+)\"[^>]*text=\"([^\"]+)\""#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else {
            return []
        }

        let nsRange = NSRange(content.startIndex..<content.endIndex, in: content)
        let matches = regex.matches(in: content, options: [], range: nsRange)

        return matches.compactMap { match in
            guard
                let rssRange = Range(match.range(at: 1), in: content),
                let titleRange = Range(match.range(at: 2), in: content),
                let rssURL = URL(string: String(content[rssRange]))
            else {
                return nil
            }

            let title = String(content[titleRange])
            return Podcast(
                id: title.lowercased().replacingOccurrences(of: " ", with: "-"),
                title: title,
                description: "",
                rssURL: rssURL,
                htmlURL: nil,
                imageURL: nil,
                genres: [],
                typicalDurationMins: 0
            )
        }
    }
}

enum RSSPodcastParser {
    static func parseEpisodes(data: Data, podcastID: String) throws -> [Episode] {
        guard let content = String(data: data, encoding: .utf8) else {
            return []
        }

        let itemPattern = #"<item>([\s\S]*?)</item>"#
        let titlePattern = #"<title><!\[CDATA\[(.*?)\]\]></title>|<title>(.*?)</title>"#
        let enclosurePattern = #"<enclosure[^>]*url=\"([^\"]+)\""#

        let itemRegex = try NSRegularExpression(pattern: itemPattern, options: [.caseInsensitive])
        let titleRegex = try NSRegularExpression(pattern: titlePattern, options: [.caseInsensitive])
        let enclosureRegex = try NSRegularExpression(pattern: enclosurePattern, options: [.caseInsensitive])

        let itemMatches = itemRegex.matches(in: content, options: [], range: NSRange(content.startIndex..<content.endIndex, in: content))

        return itemMatches.compactMap { itemMatch in
            guard let itemRange = Range(itemMatch.range(at: 1), in: content) else {
                return nil
            }
            let itemContent = String(content[itemRange])
            let itemNSRange = NSRange(itemContent.startIndex..<itemContent.endIndex, in: itemContent)

            guard
                let enclosureMatch = enclosureRegex.firstMatch(in: itemContent, options: [], range: itemNSRange),
                let enclosureRange = Range(enclosureMatch.range(at: 1), in: itemContent),
                let audioURL = URL(string: String(itemContent[enclosureRange]))
            else {
                return nil
            }

            var title = "Episode"
            if let titleMatch = titleRegex.firstMatch(in: itemContent, options: [], range: itemNSRange) {
                let titleCapture = titleMatch.range(at: 1).location != NSNotFound ? titleMatch.range(at: 1) : titleMatch.range(at: 2)
                if let titleRange = Range(titleCapture, in: itemContent) {
                    title = String(itemContent[titleRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }

            return Episode(
                id: UUID().uuidString,
                title: title,
                description: "",
                audioURL: audioURL,
                imageURL: nil,
                pubDate: "",
                durationMins: 0,
                podcastID: podcastID
            )
        }
    }
}
