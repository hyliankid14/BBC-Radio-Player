import Foundation

struct RemoteIndexMeta: Decodable, Equatable {
    let generatedAt: String
    let podcastCount: Int
    let episodeCount: Int

    enum CodingKeys: String, CodingKey {
        case generatedAt = "generated_at"
        case podcastCount = "podcast_count"
        case episodeCount = "episode_count"
    }
}

struct RemoteIndexClient {
    static let indexURL = URL(string: "https://hyliankid14.github.io/BBC-Radio-Player/podcast-index.json.gz")!
    static let metaURL = URL(string: "https://hyliankid14.github.io/BBC-Radio-Player/podcast-index-meta.json")!

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func fetchMeta() async throws -> RemoteIndexMeta {
        let (data, response) = try await session.data(from: Self.metaURL)
        guard let httpResponse = response as? HTTPURLResponse, (200..<300).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(RemoteIndexMeta.self, from: data)
    }
}
