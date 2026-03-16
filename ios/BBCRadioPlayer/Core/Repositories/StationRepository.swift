import Foundation

protocol StationRepository {
    func allStations() -> [Station]
    func stations(for category: StationCategory) -> [Station]
    func station(id: String) -> Station?
}

struct DefaultStationRepository: StationRepository {
    private let stations: [Station] = [
        Station(id: "radio1", title: "BBC Radio 1", serviceId: "bbc_radio_one", directStreamURL: nil, category: .national),
        Station(id: "1xtra", title: "BBC Radio 1Xtra", serviceId: "bbc_1xtra", directStreamURL: nil, category: .national),
        Station(id: "radio2", title: "BBC Radio 2", serviceId: "bbc_radio_two", directStreamURL: nil, category: .national),
        Station(id: "radio3", title: "BBC Radio 3", serviceId: "bbc_radio_three", directStreamURL: nil, category: .national),
        Station(id: "radio4", title: "BBC Radio 4", serviceId: "bbc_radio_fourfm", directStreamURL: nil, category: .national),
        Station(id: "radio5live", title: "BBC Radio 5 Live", serviceId: "bbc_radio_five_live", directStreamURL: URL(string: "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/uk/audio_syndication_high_sbr_v1/ak/bbc_radio_five_live.m3u8"), category: .national),
        Station(id: "radio6", title: "BBC Radio 6 Music", serviceId: "bbc_6music", directStreamURL: nil, category: .national),
        Station(id: "worldservice", title: "BBC World Service", serviceId: "bbc_world_service", directStreamURL: nil, category: .national),
        Station(id: "radiocymru", title: "BBC Radio Cymru", serviceId: "bbc_radio_cymru", directStreamURL: nil, category: .regions),
        Station(id: "radioulster", title: "BBC Radio Ulster", serviceId: "bbc_radio_ulster", directStreamURL: nil, category: .regions),
        Station(id: "radiowales", title: "BBC Radio Wales", serviceId: "bbc_radio_wales_fm", directStreamURL: nil, category: .regions),
        Station(id: "radiolondon", title: "BBC Radio London", serviceId: "bbc_london", directStreamURL: nil, category: .local),
        Station(id: "radiomanchester", title: "BBC Radio Manchester", serviceId: "bbc_radio_manchester", directStreamURL: nil, category: .local),
        Station(id: "radionewcastle", title: "BBC Radio Newcastle", serviceId: "bbc_radio_newcastle", directStreamURL: nil, category: .local),
        Station(id: "radioscotland", title: "BBC Radio Scotland", serviceId: "bbc_radio_scotland_fm", directStreamURL: nil, category: .regions)
    ]

    func allStations() -> [Station] {
        stations
    }

    func stations(for category: StationCategory) -> [Station] {
        stations.filter { $0.category == category }
    }

    func station(id: String) -> Station? {
        stations.first { $0.id == id }
    }
}
