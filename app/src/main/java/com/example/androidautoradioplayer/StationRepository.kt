package com.example.androidautoradioplayer

data class Station(
    val id: String,
    val title: String,
    val uri: String,
    val logoUrl: String
)

object StationRepository {
    fun getStations(): List<Station> = listOf(
        Station(
            "radio2",
            "BBC Radio 2",
            "https://as-hls-ww-live.akamaized.net/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_two/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio4",
            "BBC Radio 4",
            "https://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d128000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_radio_fourfm/blocks-colour-black_128x128.png"
        ),
        Station(
            "radio6",
            "BBC Radio 6 Music",
            "https://as-hls-ww-live.akamaized.net/pool_81827798/live/ww/bbc_6music/bbc_6music.isml/bbc_6music-audio%3d320000.norewind.m3u8",
            "https://sounds.files.bbci.co.uk/3.11.0/services/bbc_6music/blocks-colour-black_128x128.png"
        )
    )
}
