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
            "https://as-hls-ww-live.akamaized.net/pool_904338/live/ww/bbc_radio_one/bbc_radio_one.isml/bbc_radio_one-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904338/live/ww/bbc_radio_one/bbc_radio_one.isml/bbc_radio_one-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_one/blocks-colour-black_128x128.png"
        ),
        Station(
            "1xtra",
            "BBC Radio 1Xtra",
            "https://as-hls-ww-live.akamaized.net/pool_904339/live/ww/bbc_1xtra/bbc_1xtra.isml/bbc_1xtra-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904339/live/ww/bbc_1xtra/bbc_1xtra.isml/bbc_1xtra-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_1xtra/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio2",
            "BBC Radio 2",
            "https://as-hls-ww-live.akamaized.net/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_two/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio3",
            "BBC Radio 3",
            "https://as-hls-ww-live.akamaized.net/pool_904340/live/ww/bbc_radio_three/bbc_radio_three.isml/bbc_radio_three-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904340/live/ww/bbc_radio_three/bbc_radio_three.isml/bbc_radio_three-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_three/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio4",
            "BBC Radio 4",
            "https://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_fourfm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio4extra",
            "BBC Radio 4 Extra",
            "https://as-hls-ww-live.akamaized.net/pool_904341/live/ww/bbc_radio_four_extra/bbc_radio_four_extra.isml/bbc_radio_four_extra-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904341/live/ww/bbc_radio_four_extra/bbc_radio_four_extra.isml/bbc_radio_four_extra-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_four_extra/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio5live",
            "BBC Radio 5 Live",
            "https://as-hls-ww-live.akamaized.net/pool_904342/live/ww/bbc_radio_five_live/bbc_radio_five_live.isml/bbc_radio_five_live-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904342/live/ww/bbc_radio_five_live/bbc_radio_five_live.isml/bbc_radio_five_live-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_five_live/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio5livesportsextra",
            "BBC Radio 5 Sports Extra",
            "https://as-hls-ww-live.akamaized.net/pool_904343/live/ww/bbc_radio_five_live_sports_extra/bbc_radio_five_live_sports_extra.isml/bbc_radio_five_live_sports_extra-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904343/live/ww/bbc_radio_five_live_sports_extra/bbc_radio_five_live_sports_extra.isml/bbc_radio_five_live_sports_extra-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_five_live_sports_extra/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio6",
            "BBC Radio 6 Music",
            "https://as-hls-ww-live.akamaized.net/pool_81827798/live/ww/bbc_6music/bbc_6music.isml/bbc_6music-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_81827798/live/ww/bbc_6music/bbc_6music.isml/bbc_6music-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_6music/blocks-colour-black_128x128.png"
        ),
        Station(
            "asiannetwork",
            "BBC Asian Network",
            "https://as-hls-ww-live.akamaized.net/pool_904344/live/ww/bbc_asian_network/bbc_asian_network.isml/bbc_asian_network-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904344/live/ww/bbc_asian_network/bbc_asian_network.isml/bbc_asian_network-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_asian_network/blocks-colour-black_128x128.png"
        ),
        Station(
            "worldservice",
            "BBC World Service",
            "https://as-hls-ww-live.akamaized.net/pool_904345/live/ww/bbc_world_service/bbc_world_service.isml/bbc_world_service-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904345/live/ww/bbc_world_service/bbc_world_service.isml/bbc_world_service-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_world_service/blocks-colour-black_128x128.png"
        ),
        
        // BBC Local/Regional Stations (England)
        Station(
            "radioberkshire",
            "BBC Radio Berkshire",
            "https://as-hls-ww-live.akamaized.net/pool_904346/live/ww/bbc_radio_berkshire/bbc_radio_berkshire.isml/bbc_radio_berkshire-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904346/live/ww/bbc_radio_berkshire/bbc_radio_berkshire.isml/bbc_radio_berkshire-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_berkshire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiobristol",
            "BBC Radio Bristol",
            "https://as-hls-ww-live.akamaized.net/pool_904347/live/ww/bbc_radio_bristol/bbc_radio_bristol.isml/bbc_radio_bristol-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904347/live/ww/bbc_radio_bristol/bbc_radio_bristol.isml/bbc_radio_bristol-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_bristol/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiocambridgeshire",
            "BBC Radio Cambridgeshire",
            "https://as-hls-ww-live.akamaized.net/pool_904348/live/ww/bbc_radio_cambridge/bbc_radio_cambridge.isml/bbc_radio_cambridge-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904348/live/ww/bbc_radio_cambridge/bbc_radio_cambridge.isml/bbc_radio_cambridge-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_cambridge/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiocornwall",
            "BBC Radio Cornwall",
            "https://as-hls-ww-live.akamaized.net/pool_904349/live/ww/bbc_radio_cornwall/bbc_radio_cornwall.isml/bbc_radio_cornwall-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904349/live/ww/bbc_radio_cornwall/bbc_radio_cornwall.isml/bbc_radio_cornwall-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_cornwall/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiocumbria",
            "BBC Radio Cumbria",
            "https://as-hls-ww-live.akamaized.net/pool_904350/live/ww/bbc_radio_cumbria/bbc_radio_cumbria.isml/bbc_radio_cumbria-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904350/live/ww/bbc_radio_cumbria/bbc_radio_cumbria.isml/bbc_radio_cumbria-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_cumbria/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioderby",
            "BBC Radio Derby",
            "https://as-hls-ww-live.akamaized.net/pool_904351/live/ww/bbc_radio_derby/bbc_radio_derby.isml/bbc_radio_derby-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904351/live/ww/bbc_radio_derby/bbc_radio_derby.isml/bbc_radio_derby-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_derby/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiodevon",
            "BBC Radio Devon",
            "https://as-hls-ww-live.akamaized.net/pool_904352/live/ww/bbc_radio_devon/bbc_radio_devon.isml/bbc_radio_devon-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904352/live/ww/bbc_radio_devon/bbc_radio_devon.isml/bbc_radio_devon-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_devon/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioessex",
            "BBC Radio Essex",
            "https://as-hls-ww-live.akamaized.net/pool_904353/live/ww/bbc_radio_essex/bbc_radio_essex.isml/bbc_radio_essex-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904353/live/ww/bbc_radio_essex/bbc_radio_essex.isml/bbc_radio_essex-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_essex/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiogloucestershire",
            "BBC Radio Gloucestershire",
            "https://as-hls-ww-live.akamaized.net/pool_904354/live/ww/bbc_radio_gloucestershire/bbc_radio_gloucestershire.isml/bbc_radio_gloucestershire-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904354/live/ww/bbc_radio_gloucestershire/bbc_radio_gloucestershire.isml/bbc_radio_gloucestershire-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_gloucestershire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiohumberside",
            "BBC Radio Humberside",
            "https://as-hls-ww-live.akamaized.net/pool_904355/live/ww/bbc_radio_humberside/bbc_radio_humberside.isml/bbc_radio_humberside-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904355/live/ww/bbc_radio_humberside/bbc_radio_humberside.isml/bbc_radio_humberside-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_humberside/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiokent",
            "BBC Radio Kent",
            "https://as-hls-ww-live.akamaized.net/pool_904356/live/ww/bbc_radio_kent/bbc_radio_kent.isml/bbc_radio_kent-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904356/live/ww/bbc_radio_kent/bbc_radio_kent.isml/bbc_radio_kent-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_kent/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiolancashire",
            "BBC Radio Lancashire",
            "https://as-hls-ww-live.akamaized.net/pool_904357/live/ww/bbc_radio_lancashire/bbc_radio_lancashire.isml/bbc_radio_lancashire-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904357/live/ww/bbc_radio_lancashire/bbc_radio_lancashire.isml/bbc_radio_lancashire-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_lancashire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioleeds",
            "BBC Radio Leeds",
            "https://as-hls-ww-live.akamaized.net/pool_904358/live/ww/bbc_radio_leeds/bbc_radio_leeds.isml/bbc_radio_leeds-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904358/live/ww/bbc_radio_leeds/bbc_radio_leeds.isml/bbc_radio_leeds-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_leeds/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioleicester",
            "BBC Radio Leicester",
            "https://as-hls-ww-live.akamaized.net/pool_904359/live/ww/bbc_radio_leicester/bbc_radio_leicester.isml/bbc_radio_leicester-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904359/live/ww/bbc_radio_leicester/bbc_radio_leicester.isml/bbc_radio_leicester-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_leicester/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiolincolnshire",
            "BBC Radio Lincolnshire",
            "https://as-hls-ww-live.akamaized.net/pool_904360/live/ww/bbc_radio_lincolnshire/bbc_radio_lincolnshire.isml/bbc_radio_lincolnshire-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904360/live/ww/bbc_radio_lincolnshire/bbc_radio_lincolnshire.isml/bbc_radio_lincolnshire-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_lincolnshire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiolon",
            "BBC Radio London",
            "https://as-hls-ww-live.akamaized.net/pool_904361/live/ww/bbc_london/bbc_london.isml/bbc_london-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904361/live/ww/bbc_london/bbc_london.isml/bbc_london-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_london/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiomanchester",
            "BBC Radio Manchester",
            "https://as-hls-ww-live.akamaized.net/pool_904362/live/ww/bbc_radio_manchester/bbc_radio_manchester.isml/bbc_radio_manchester-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904362/live/ww/bbc_radio_manchester/bbc_radio_manchester.isml/bbc_radio_manchester-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_manchester/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiomerseyside",
            "BBC Radio Merseyside",
            "https://as-hls-ww-live.akamaized.net/pool_904363/live/ww/bbc_radio_merseyside/bbc_radio_merseyside.isml/bbc_radio_merseyside-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904363/live/ww/bbc_radio_merseyside/bbc_radio_merseyside.isml/bbc_radio_merseyside-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_merseyside/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionewcastle",
            "BBC Radio Newcastle",
            "https://as-hls-ww-live.akamaized.net/pool_904364/live/ww/bbc_radio_newcastle/bbc_radio_newcastle.isml/bbc_radio_newcastle-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904364/live/ww/bbc_radio_newcastle/bbc_radio_newcastle.isml/bbc_radio_newcastle-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_newcastle/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionorfolk",
            "BBC Radio Norfolk",
            "https://as-hls-ww-live.akamaized.net/pool_904365/live/ww/bbc_radio_norfolk/bbc_radio_norfolk.isml/bbc_radio_norfolk-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904365/live/ww/bbc_radio_norfolk/bbc_radio_norfolk.isml/bbc_radio_norfolk-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_norfolk/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionorthampton",
            "BBC Radio Northampton",
            "https://as-hls-ww-live.akamaized.net/pool_904366/live/ww/bbc_radio_northampton/bbc_radio_northampton.isml/bbc_radio_northampton-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904366/live/ww/bbc_radio_northampton/bbc_radio_northampton.isml/bbc_radio_northampton-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_northampton/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionottingham",
            "BBC Radio Nottingham",
            "https://as-hls-ww-live.akamaized.net/pool_904367/live/ww/bbc_radio_nottingham/bbc_radio_nottingham.isml/bbc_radio_nottingham-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904367/live/ww/bbc_radio_nottingham/bbc_radio_nottingham.isml/bbc_radio_nottingham-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_nottingham/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiooxford",
            "BBC Radio Oxford",
            "https://as-hls-ww-live.akamaized.net/pool_904368/live/ww/bbc_radio_oxford/bbc_radio_oxford.isml/bbc_radio_oxford-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904368/live/ww/bbc_radio_oxford/bbc_radio_oxford.isml/bbc_radio_oxford-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_oxford/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosheffield",
            "BBC Radio Sheffield",
            "https://as-hls-ww-live.akamaized.net/pool_904369/live/ww/bbc_radio_sheffield/bbc_radio_sheffield.isml/bbc_radio_sheffield-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904369/live/ww/bbc_radio_sheffield/bbc_radio_sheffield.isml/bbc_radio_sheffield-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_sheffield/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioshropshire",
            "BBC Radio Shropshire",
            "https://as-hls-ww-live.akamaized.net/pool_904370/live/ww/bbc_radio_shropshire/bbc_radio_shropshire.isml/bbc_radio_shropshire-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904370/live/ww/bbc_radio_shropshire/bbc_radio_shropshire.isml/bbc_radio_shropshire-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_shropshire/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosolent",
            "BBC Radio Solent",
            "https://as-hls-ww-live.akamaized.net/pool_904371/live/ww/bbc_radio_solent/bbc_radio_solent.isml/bbc_radio_solent-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904371/live/ww/bbc_radio_solent/bbc_radio_solent.isml/bbc_radio_solent-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_solent/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosomerset",
            "BBC Radio Somerset",
            "https://as-hls-ww-live.akamaized.net/pool_904372/live/ww/bbc_radio_somerset_sound/bbc_radio_somerset_sound.isml/bbc_radio_somerset_sound-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904372/live/ww/bbc_radio_somerset_sound/bbc_radio_somerset_sound.isml/bbc_radio_somerset_sound-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_somerset_sound/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiostoke",
            "BBC Radio Stoke",
            "https://as-hls-ww-live.akamaized.net/pool_904373/live/ww/bbc_radio_stoke/bbc_radio_stoke.isml/bbc_radio_stoke-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904373/live/ww/bbc_radio_stoke/bbc_radio_stoke.isml/bbc_radio_stoke-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_stoke/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosuffolk",
            "BBC Radio Suffolk",
            "https://as-hls-ww-live.akamaized.net/pool_904374/live/ww/bbc_radio_suffolk/bbc_radio_suffolk.isml/bbc_radio_suffolk-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904374/live/ww/bbc_radio_suffolk/bbc_radio_suffolk.isml/bbc_radio_suffolk-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_suffolk/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosurrey",
            "BBC Radio Surrey",
            "https://as-hls-ww-live.akamaized.net/pool_904375/live/ww/bbc_radio_surrey/bbc_radio_surrey.isml/bbc_radio_surrey-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904375/live/ww/bbc_radio_surrey/bbc_radio_surrey.isml/bbc_radio_surrey-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_surrey/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiosussex",
            "BBC Radio Sussex",
            "https://as-hls-ww-live.akamaized.net/pool_904376/live/ww/bbc_radio_sussex/bbc_radio_sussex.isml/bbc_radio_sussex-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904376/live/ww/bbc_radio_sussex/bbc_radio_sussex.isml/bbc_radio_sussex-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_sussex/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiotees",
            "BBC Radio Tees",
            "https://as-hls-ww-live.akamaized.net/pool_904377/live/ww/bbc_tees/bbc_tees.isml/bbc_tees-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904377/live/ww/bbc_tees/bbc_tees.isml/bbc_tees-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_tees/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiowm",
            "BBC Radio WM",
            "https://as-hls-ww-live.akamaized.net/pool_904378/live/ww/bbc_wm/bbc_wm.isml/bbc_wm-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904378/live/ww/bbc_wm/bbc_wm.isml/bbc_wm-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_wm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioyork",
            "BBC Radio York",
            "https://as-hls-ww-live.akamaized.net/pool_904379/live/ww/bbc_radio_york/bbc_radio_york.isml/bbc_radio_york-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904379/live/ww/bbc_radio_york/bbc_radio_york.isml/bbc_radio_york-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_york/blocks-colour-black_128x128.png"
        ),
        
        // BBC Nations Stations
        Station(
            "radioscotland",
            "BBC Radio Scotland",
            "https://as-hls-ww-live.akamaized.net/pool_904380/live/ww/bbc_radio_scotland_fm/bbc_radio_scotland_fm.isml/bbc_radio_scotland_fm-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904380/live/ww/bbc_radio_scotland_fm/bbc_radio_scotland_fm.isml/bbc_radio_scotland_fm-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_scotland_fm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radionanaidheal",
            "BBC Radio nan Gàidheal",
            "https://as-hls-ww-live.akamaized.net/pool_904381/live/ww/bbc_radio_nan_gaidheal/bbc_radio_nan_gaidheal.isml/bbc_radio_nan_gaidheal-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904381/live/ww/bbc_radio_nan_gaidheal/bbc_radio_nan_gaidheal.isml/bbc_radio_nan_gaidheal-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_nan_gaidheal/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiowales",
            "BBC Radio Wales",
            "https://as-hls-ww-live.akamaized.net/pool_904382/live/ww/bbc_radio_wales_fm/bbc_radio_wales_fm.isml/bbc_radio_wales_fm-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904382/live/ww/bbc_radio_wales_fm/bbc_radio_wales_fm.isml/bbc_radio_wales_fm-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_wales_fm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiocymru",
            "BBC Radio Cymru",
            "https://as-hls-ww-live.akamaized.net/pool_904383/live/ww/bbc_radio_cymru/bbc_radio_cymru.isml/bbc_radio_cymru-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904383/live/ww/bbc_radio_cymru/bbc_radio_cymru.isml/bbc_radio_cymru-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_cymru/blocks-colour-black_128x128.png"
        ),
        Station(
            "radioulster",
            "BBC Radio Ulster",
            "https://as-hls-ww-live.akamaized.net/pool_904384/live/ww/bbc_radio_ulster/bbc_radio_ulster.isml/bbc_radio_ulster-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904384/live/ww/bbc_radio_ulster/bbc_radio_ulster.isml/bbc_radio_ulster-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_ulster/blocks-colour-black_128x128.png"
        ),
        Station(
            "radiofoyle",
            "BBC Radio Foyle",
            "https://as-hls-ww-live.akamaized.net/pool_904385/live/ww/bbc_radio_foyle/bbc_radio_foyle.isml/bbc_radio_foyle-audio%3d320000.norewind.m3u8",
            "https://as-hls-ww-live.akamaized.net/pool_904385/live/ww/bbc_radio_foyle/bbc_radio_foyle.isml/bbc_radio_foyle-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_foyle/blocks-colour-black_128x128.png"
        )
    )
}
