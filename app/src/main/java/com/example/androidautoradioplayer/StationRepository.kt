package com.example.androidautoradioplayer

private const val STREAM_BASE = "https://lsn.lv/bbcradio.m3u8"
private const val HQ_BITRATE = "320000"
private const val LQ_BITRATE = "128000"
private const val LOGO_BASE = "https://sounds.files.bbci.co.uk/3.11.0/services"

data class Station(
    val id: String,
    val title: String,
    val serviceId: String,
    val logoUrl: String
) {
    fun getUri(highQuality: Boolean): String {
        val bitrate = if (highQuality) HQ_BITRATE else LQ_BITRATE
        return "$STREAM_BASE?station=$serviceId&bitrate=$bitrate"
    }
}

object StationRepository {
    private fun station(
        id: String,
        title: String,
        serviceId: String,
        logoServiceId: String = serviceId
    ): Station = Station(
        id = id,
        title = title,
        serviceId = serviceId,
        logoUrl = "$LOGO_BASE/$logoServiceId/blocks-colour-black_128x128.png"
    )

    private val stations = listOf(
        // BBC National and Digital Stations
        station("radio1", "BBC Radio 1", "bbc_radio_one"),
        station("1xtra", "BBC Radio 1Xtra", "bbc_1xtra"),
        station("radio1dance", "BBC Radio 1 Dance", "bbc_radio_one_dance"),
        station("radio2", "BBC Radio 2", "bbc_radio_two"),
        station("radio3", "BBC Radio 3", "bbc_radio_three"),
        station("radio4", "BBC Radio 4", "bbc_radio_fourfm"),
        station("radio4extra", "BBC Radio 4 Extra", "bbc_radio_four_extra"),
        station("radio5live", "BBC Radio 5 Live", "bbc_radio_five_live"),
        station("radio6", "BBC Radio 6 Music", "bbc_6music"),
        station("worldservice", "BBC World Service", "bbc_world_service"),
        station("asiannetwork", "BBC Asian Network", "bbc_asian_network"),

        // BBC Nations Stations
        station("radiogaidheal", "BBC Radio nan Gaidheal", "bbc_radio_nan_gaidheal"),
        station("radioscotland", "BBC Radio Scotland", "bbc_radio_scotland_fm"),
        station("radioscotlandmw", "BBC Radio Scotland MW", "bbc_radio_scotland_mw"),
        station("radiowales", "BBC Radio Wales", "bbc_radio_wales_fm"),
        station("radiocymru", "BBC Radio Cymru", "bbc_radio_cymru"),
        station("radiocymru2", "BBC Radio Cymru 2", "bbc_radio_cymru_2"),
        station("radioulster", "BBC Radio Ulster", "bbc_radio_ulster"),
        station("radiofoyle", "BBC Radio Foyle", "bbc_radio_foyle"),

        // BBC Local/Regional Stations (England and Channel Islands)
        station("radioberkshire", "BBC Radio Berkshire", "bbc_radio_berkshire"),
        station("radiobristol", "BBC Radio Bristol", "bbc_radio_bristol"),
        station("radiocambridge", "BBC Radio Cambridgeshire", "bbc_radio_cambridge"),
        station("radiocornwall", "BBC Radio Cornwall", "bbc_radio_cornwall"),
        station("radiocoventrywarwickshire", "BBC Radio Coventry & Warwickshire", "bbc_radio_coventry_warwickshire"),
        station("radiocumbria", "BBC Radio Cumbria", "bbc_radio_cumbria"),
        station("radioderby", "BBC Radio Derby", "bbc_radio_derby"),
        station("radiodevon", "BBC Radio Devon", "bbc_radio_devon"),
        station("radioessex", "BBC Radio Essex", "bbc_radio_essex"),
        station("radioherefordworcester", "BBC Radio Hereford & Worcester", "bbc_radio_hereford_worcester"),
        station("radiogloucestershire", "BBC Radio Gloucestershire", "bbc_radio_gloucestershire"),
        station("radioguernsey", "BBC Radio Guernsey", "bbc_radio_guernsey"),
        station("radiohumberside", "BBC Radio Humberside", "bbc_radio_humberside"),
        station("radiojersey", "BBC Radio Jersey", "bbc_radio_jersey"),
        station("radiokent", "BBC Radio Kent", "bbc_radio_kent"),
        station("radiolancashire", "BBC Radio Lancashire", "bbc_radio_lancashire"),
        station("radioleeds", "BBC Radio Leeds", "bbc_radio_leeds"),
        station("radioleicester", "BBC Radio Leicester", "bbc_radio_leicester"),
        station("radiolincolnshire", "BBC Radio Lincolnshire", "bbc_radio_lincolnshire"),
        station("radiolon", "BBC Radio London", "bbc_london"),
        station("radiomanchester", "BBC Radio Manchester", "bbc_radio_manchester"),
        station("radiomerseyside", "BBC Radio Merseyside", "bbc_radio_merseyside"),
        station("radionewcastle", "BBC Radio Newcastle", "bbc_radio_newcastle"),
        station("radionorfolk", "BBC Radio Norfolk", "bbc_radio_norfolk"),
        station("radionorthampton", "BBC Radio Northampton", "bbc_radio_northampton"),
        station("radionottingham", "BBC Radio Nottingham", "bbc_radio_nottingham"),
        station("radioorkney", "BBC Radio Orkney", "bbc_radio_orkney"),
        station("radiooxford", "BBC Radio Oxford", "bbc_radio_oxford"),
        station("radiosheffield", "BBC Radio Sheffield", "bbc_radio_sheffield"),
        station("radioshetland", "BBC Radio Shetland", "bbc_radio_shetland"),
        station("radioshropshire", "BBC Radio Shropshire", "bbc_radio_shropshire"),
        station("radiosolent", "BBC Radio Solent", "bbc_radio_solent"),
        station("radiosolentwestdorset", "BBC Radio Solent West Dorset", "bbc_radio_solent_west_dorset"),
        station("radiosomerset", "BBC Radio Somerset", "bbc_radio_somerset_sound"),
        station("radiostoke", "BBC Radio Stoke", "bbc_radio_stoke"),
        station("radiosuffolk", "BBC Radio Suffolk", "bbc_radio_suffolk"),
        station("radiosurrey", "BBC Radio Surrey", "bbc_radio_surrey"),
        station("radiosussex", "BBC Radio Sussex", "bbc_radio_sussex"),
        station("radiotees", "BBC Radio Tees", "bbc_tees"),
        station("radiothreecounties", "BBC Three Counties Radio", "bbc_three_counties_radio"),
        station("radiowestmidlands", "BBC Radio West Midlands", "bbc_wm"),
        station("radiowiltshire", "BBC Radio Wiltshire", "bbc_radio_wiltshire"),
        station("radioyork", "BBC Radio York", "bbc_radio_york")
    )

    fun getStations(): List<Station> = stations

    fun getStationById(id: String): Station? = stations.firstOrNull { it.id == id }
}
