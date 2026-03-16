import SwiftUI

struct RadioView: View {
    @ObservedObject var viewModel: RadioViewModel

    var body: some View {
        List {
            Section {
                Picker("Quality", selection: $viewModel.selectedQuality) {
                    Text("High").tag(PlaybackQuality.high)
                    Text("Low").tag(PlaybackQuality.low)
                }
                .pickerStyle(.segmented)
            }

            ForEach(viewModel.stations) { station in
                Button {
                    viewModel.play(station)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(station.title)
                            Text(station.category.displayName)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        if station.id == viewModel.currentStationID && viewModel.isPlaying {
                            Image(systemName: "speaker.wave.2.fill")
                                .foregroundStyle(.tint)
                        }
                    }
                }
            }
        }
        .navigationTitle("Radio")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(viewModel.isPlaying ? "Pause" : "Play") {
                    viewModel.togglePlayback()
                }
            }
        }
    }
}
