package com.redowan

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

class BdixDhakaFlix9Provider : BdixDhakaFlix14Provider() {
    override var mainUrl = "http://172.16.50.9"
    override var name = "(BDIX) DhakaFlix 9"
    override val tvSeriesKeyword: List<String> =
        listOf("Awards", "WWE", "KOREAN", "Documentary", "Anime")
    override val serverName: String = "DHAKA-FLIX-9"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.AnimeMovie,
        TvType.TvSeries
    )
    override val mainPage = mainPageOf(
        "Anime %26 Cartoon TV Series/Anime-TV Series ♥%20 A%20 —%20 F/" to "Anime TV Series",
        "KOREAN TV %26 WEB Series/" to "KOREAN TV & WEB Series",
        "Documentary/" to "Documentary",
        "Awards %26 TV Shows/%23 TV SPECIAL %26 SHOWS/" to "TV SPECIAL & SHOWS",
        "Awards %26 TV Shows/%23 AWARDS/" to "Awards",
        "WWE %26 AEW Wrestling/WWE Wrestling/" to "WWE Wrestling",
        "WWE %26 AEW Wrestling/AEW Wrestling/" to "AEW Wrestling",
    )
}