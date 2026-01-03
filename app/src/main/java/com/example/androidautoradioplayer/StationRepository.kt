package com.example.androidautoradioplayer

data class Station(
    val id: String,
    val title: String,
    val uriHQ: String,
    val uriLQ: String,
    val logoUrl: String
) {
    fun getUri(highQuality: Boolean): String = if (highQuality) uriHQ else uriLQ
}

object StationRepository {
    fun getStations(): List<Station> = listOf(
        // BBC National Stations
        Station(
            "radio1",
            "BBC Radio 1",
            "http://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/cf/bbc_radio_one.m3u8",
            "http://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/cf/bbc_radio_one.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_one/blocks-colour-black_128x128.png"
        ),
        Station(
            "1xtra",
            "BBC Radio 1Xtra",
            "http://as-hls-ww-live.akamaized.net/pool_92079267/live/ww/bbc_1xtra/bbc_1xtra.isml/bbc_1xtra-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_92079267/live/ww/bbc_1xtra/bbc_1xtra.isml/bbc_1xtra-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_1xtra/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio2",
            "BBC Radio 2",
            "http://as-hls-ww-live.akamaized.net/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_two/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio3",
            "BBC Radio 3",
            "http://as-hls-ww-live.akamaized.net/pool_23461179/live/ww/bbc_radio_three/bbc_radio_three.isml/bbc_radio_three-audio=320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_23461179/live/ww/bbc_radio_three/bbc_radio_three.isml/bbc_radio_three-audio=128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_three/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio4",
            "BBC Radio 4",
            "http://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_fourfm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio4extra",
            "BBC Radio 4 Extra",
            "http://as-hls-ww-live.akamaized.net/pool_26173715/live/ww/bbc_radio_four_extra/bbc_radio_four_extra.isml/bbc_radio_four_extra-audio=320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_26173715/live/ww/bbc_radio_four_extra/bbc_radio_four_extra.isml/bbc_radio_four_extra-audio=128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_four_extra/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio5live",
            "BBC Radio 5 Live",
            "http://as-hls-ww-live.akamaized.net/pool_89021708/live/ww/bbc_radio_five_live/bbc_radio_five_live.isml/bbc_radio_five_live-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_89021708/live/ww/bbc_radio_five_live/bbc_radio_five_live.isml/bbc_radio_five_live-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_five_live/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio6",
            "BBC Radio 6 Music",
            "http://as-hls-ww-live.akamaized.net/pool_81827798/live/ww/bbc_6music/bbc_6music.isml/bbc_6music-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_81827798/live/ww/bbc_6music/bbc_6music.isml/bbc_6music-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_6music/blocks-colour-black_128x128.png"
        ),
        Station(
            "asiannetwork",
            "BBC Asian Network",
            "http://as-hls-ww-live.akamaized.net/pool_22108647/live/ww/bbc_asian_network/bbc_asian_network.isml/bbc_asian_network-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_22108647/live/ww/bbc_asian_network/bbc_asian_network.isml/bbc_asian_network-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_asian_network/blocks-colour-black_128x128.png"
        ),
        
        // BBC Local/Regional Stations (England)
        Station(
            "radiobristol",
            "BBC Radio Bristol",
            "http://as-hls-ww-live.akamaized.net/pool_41858929/live/ww/bbc_radio_bristol/bbc_radio_bristol.isml/bbc_radio_bristol-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_41858929/live/ww/bbc_radio_bristol/bbc_radio_bristol.isml/bbc_radio_bristol-audio%3d96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_bristol/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiodevon",
            "BBC Radio Devon",
            "http://as-hls-ww-live.akamaized.net/pool_08856933/live/ww/bbc_radio_devon/bbc_radio_devon.isml/bbc_radio_devon-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_08856933/live/ww/bbc_radio_devon/bbc_radio_devon.isml/bbc_radio_devon-audio%3d96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_devon/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioleeds",
            "BBC Radio Leeds",
            "https://as-hls-ww-live.akamaized.net/pool_50115440/live/ww/bbc_radio_leeds/bbc_radio_leeds.isml/bbc_radio_leeds-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_50115440/live/ww/bbc_radio_leeds/bbc_radio_leeds.isml/bbc_radio_leeds-audio%3d96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_leeds/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiolon",
            "BBC Radio London",
            "http://as-hls-ww-live.akamaized.net/pool_98137350/live/ww/bbc_london/bbc_london.isml/bbc_london-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_98137350/live/ww/bbc_london/bbc_london.isml/bbc_london-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_london/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionorthampton",
            "BBC Radio Northampton",
            "http://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/cfs/bbc_radio_northampton.m3u8",
            "http://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/cfs/bbc_radio_northampton.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_northampton/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionottingham",
            "BBC Radio Nottingham",
            "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/audio_syndication_low_sbr_v1/aks/bbc_radio_nottingham.m3u8",
            "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/audio_syndication_low_sbr_v1/aks/bbc_radio_nottingham.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_nottingham/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosolent",
            "BBC Radio Solent",
            "http://as-hls-ww-live.akamaized.net/pool_11685351/live/ww/bbc_radio_solent/bbc_radio_solent.isml/bbc_radio_solent-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_11685351/live/ww/bbc_radio_solent/bbc_radio_solent.isml/bbc_radio_solent-audio%3D96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_solent/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiotees",
            "BBC Radio Tees",
            "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/aks/bbc_tees.m3u8",
            "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/aks/bbc_tees.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_tees/blocks-colour-black_128x128.png"
        ),
        
        // BBC Nations Stations
        Station(
            "radioscotland",
            "BBC Radio Scotland",
            "http://as-hls-ww-live.akamaized.net/pool_43322914/live/ww/bbc_radio_scotland_fm/bbc_radio_scotland_fm.isml/bbc_radio_scotland_fm-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_43322914/live/ww/bbc_radio_scotland_fm/bbc_radio_scotland_fm.isml/bbc_radio_scotland_fm-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_scotland_fm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiowales",
            "BBC Radio Wales",
            "http://as-hls-ww-live.akamaized.net/pool_97517794/live/ww/bbc_radio_wales_fm/bbc_radio_wales_fm.isml/bbc_radio_wales_fm-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_97517794/live/ww/bbc_radio_wales_fm/bbc_radio_wales_fm.isml/bbc_radio_wales_fm-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_wales_fm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiocymru",
            "BBC Radio Cymru",
            "http://as-hls-ww-live.akamaized.net/pool_24792333/live/ww/bbc_radio_cymru/bbc_radio_cymru.isml/bbc_radio_cymru-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_24792333/live/ww/bbc_radio_cymru/bbc_radio_cymru.isml/bbc_radio_cymru-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_cymru/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioulster",
            "BBC Radio Ulster",
            "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/audio_syndication_low_sbr_v1/aks/bbc_radio_ulster.m3u8",
            "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/audio_syndication_low_sbr_v1/aks/bbc_radio_ulster.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_ulster/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiofoyle",
            "BBC Radio Foyle",
            "http://as-hls-ww-live.akamaized.net/pool_43178797/live/ww/bbc_radio_foyle/bbc_radio_foyle.isml/bbc_radio_foyle-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_43178797/live/ww/bbc_radio_foyle/bbc_radio_foyle.isml/bbc_radio_foyle-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_foyle/blocks-colour-black_128x128.png"
        )
    )
}
