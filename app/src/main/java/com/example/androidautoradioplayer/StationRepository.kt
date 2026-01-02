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
            "radio5livesportsextra",
            "BBC Radio 5 Sports Extra",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_five_live_sports_extra",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_five_live_sports_extra",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_five_live_sports_extra/blocks-colour-black_128x128.png"
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
        Station(
            "worldservice",
            "BBC World Service",
            "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service_west_africa",
            "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service_west_africa",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_world_service/blocks-colour-black_128x128.png"
        ),
        
        // BBC Local/Regional Stations (England)
        Station(
            "radioberkshire",
            "BBC Radio Berkshire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_berkshire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_berkshire",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_berkshire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiobristol",
            "BBC Radio Bristol",
            "http://as-hls-ww-live.akamaized.net/pool_41858929/live/ww/bbc_radio_bristol/bbc_radio_bristol.isml/bbc_radio_bristol-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_41858929/live/ww/bbc_radio_bristol/bbc_radio_bristol.isml/bbc_radio_bristol-audio%3d96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_bristol/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiocambridgeshire",
            "BBC Radio Cambridgeshire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_cambridge",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_cambridge",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_cambridge/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiocornwall",
            "BBC Radio Cornwall",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_cornwall",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_cornwall",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_cornwall/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiocumbria",
            "BBC Radio Cumbria",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_cumbria",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_cumbria",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_cumbria/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioderby",
            "BBC Radio Derby",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_derby",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_derby",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_derby/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiodevon",
            "BBC Radio Devon",
            "http://as-hls-ww-live.akamaized.net/pool_08856933/live/ww/bbc_radio_devon/bbc_radio_devon.isml/bbc_radio_devon-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_08856933/live/ww/bbc_radio_devon/bbc_radio_devon.isml/bbc_radio_devon-audio%3d96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_devon/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioessex",
            "BBC Radio Essex",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_essex",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_essex",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_essex/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiogloucestershire",
            "BBC Radio Gloucestershire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_gloucestershire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_gloucestershire",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_gloucestershire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiohumberside",
            "BBC Radio Humberside",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_humberside",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_humberside",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_humberside/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiokent",
            "BBC Radio Kent",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_kent",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_kent",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_kent/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiolancashire",
            "BBC Radio Lancashire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_lancashire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_lancashire",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_lancashire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioleeds",
            "BBC Radio Leeds",
            "https://as-hls-ww-live.akamaized.net/pool_50115440/live/ww/bbc_radio_leeds/bbc_radio_leeds.isml/bbc_radio_leeds-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_50115440/live/ww/bbc_radio_leeds/bbc_radio_leeds.isml/bbc_radio_leeds-audio%3d96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_leeds/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioleicester",
            "BBC Radio Leicester",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_leicester",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_leicester",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_leicester/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiolincolnshire",
            "BBC Radio Lincolnshire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_lincolnshire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_lincolnshire",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_lincolnshire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiolon",
            "BBC Radio London",
            "http://as-hls-ww-live.akamaized.net/pool_98137350/live/ww/bbc_london/bbc_london.isml/bbc_london-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_98137350/live/ww/bbc_london/bbc_london.isml/bbc_london-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_london/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiomanchester",
            "BBC Radio Manchester",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_manchester",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_manchester",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_manchester/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiomerseyside",
            "BBC Radio Merseyside",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_merseyside",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_merseyside",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_merseyside/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionewcastle",
            "BBC Radio Newcastle",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_newcastle",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_newcastle",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_newcastle/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionorfolk",
            "BBC Radio Norfolk",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_norfolk",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_norfolk",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_norfolk/blocks-colour-black_128x128.png"
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
            "radiooxford",
            "BBC Radio Oxford",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_oxford",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_oxford",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_oxford/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosheffield",
            "BBC Radio Sheffield",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_sheffield",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_sheffield",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_sheffield/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioshropshire",
            "BBC Radio Shropshire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_shropshire",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_shropshire",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_shropshire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosolent",
            "BBC Radio Solent",
            "http://as-hls-ww-live.akamaized.net/pool_11685351/live/ww/bbc_radio_solent/bbc_radio_solent.isml/bbc_radio_solent-audio%3d320000.norewind.m3u8",
            "http://as-hls-ww-live.akamaized.net/pool_11685351/live/ww/bbc_radio_solent/bbc_radio_solent.isml/bbc_radio_solent-audio%3D96000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_solent/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosomerset",
            "BBC Radio Somerset",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_somerset_sound",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_somerset_sound",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_somerset_sound/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiostoke",
            "BBC Radio Stoke",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_stoke",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_stoke",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_stoke/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosuffolk",
            "BBC Radio Suffolk",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_suffolk",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_suffolk",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_suffolk/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosurrey",
            "BBC Radio Surrey",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_surrey",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_surrey",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_surrey/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosussex",
            "BBC Radio Sussex",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_sussex",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_sussex",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_sussex/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiotees",
            "BBC Radio Tees",
            "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/aks/bbc_tees.m3u8",
            "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/aks/bbc_tees.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_tees/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiowm",
            "BBC Radio WM",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_wm",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_wm",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_wm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioyork",
            "BBC Radio York",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_york",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_york",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_york/blocks-colour-black_128x128.png"
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
            "radionanaidheal",
            "BBC Radio nan Gàidheal",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_nan_gaidheal",
            "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_nan_gaidheal",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_nan_gaidheal/blocks-colour-black_128x128.png"
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
