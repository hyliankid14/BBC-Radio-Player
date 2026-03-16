import SwiftUI

@main
struct BBCRadioPlayerApp: App {
    @StateObject private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .environmentObject(container)
        }
    }
}
