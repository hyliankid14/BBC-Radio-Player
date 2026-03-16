import SwiftUI

struct RootTabView: View {
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        TabView {
            NavigationStack {
                RadioView(viewModel: container.radioViewModel)
            }
            .tabItem {
                Label("Radio", systemImage: "dot.radiowaves.left.and.right")
            }

            NavigationStack {
                PodcastsView(viewModel: container.podcastsViewModel)
            }
            .tabItem {
                Label("Podcasts", systemImage: "mic")
            }
        }
    }
}
