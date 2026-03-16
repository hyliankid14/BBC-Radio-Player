import SwiftUI

struct PodcastsView: View {
    @ObservedObject var viewModel: PodcastsViewModel

    var body: some View {
        Group {
            if let selectedPodcast = viewModel.selectedPodcast {
                episodeList(for: selectedPodcast)
            } else {
                podcastList
            }
        }
        .navigationTitle(viewModel.selectedPodcast == nil ? "Podcasts" : "Episodes")
        .task {
            if viewModel.podcasts.isEmpty {
                await viewModel.loadPodcasts()
            }
        }
    }

    private var podcastList: some View {
        List(viewModel.podcasts) { podcast in
            Button {
                Task {
                    await viewModel.selectPodcast(podcast)
                }
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text(podcast.title)
                        .font(.headline)
                    if !podcast.description.isEmpty {
                        Text(podcast.description)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }
            }
        }
        .overlay {
            if viewModel.isLoading {
                ProgressView("Loading podcasts...")
            }
        }
        .refreshable {
            await viewModel.loadPodcasts()
        }
    }

    private func episodeList(for podcast: Podcast) -> some View {
        List(viewModel.episodes) { episode in
            Button {
                viewModel.play(episode)
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text(episode.title)
                        .font(.headline)
                    if !episode.description.isEmpty {
                        Text(episode.description)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(3)
                    }
                }
            }
        }
        .overlay {
            if viewModel.isLoading {
                ProgressView("Loading episodes...")
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Back") {
                    viewModel.clearSelection()
                }
            }
        }
    }
}
