package com.redowan

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

class BdixDhakaFlix12Provider : BdixDhakaFlix14Provider()  {
    override var mainUrl = "http://172.16.50.12"
    override var name = "(BDIX) DhakaFlix 12"
    override val tvSeriesKeyword: List<String> = listOf("TV-WEB-Series")
    override val serverName: String = "DHAKA-FLIX-12"
    override val supportedTypes = setOf(TvType.TvSeries)
    override val mainPage= mainPageOf(
        "TV-WEB-Series/TV Series ★%20 0%20 —%20 9/" to "TV Series ★ 0 — 9",
        "TV-WEB-Series/TV Series ♥%20 A%20 —%20 L/" to "TV Series ♥ A — L",
        "TV-WEB-Series/TV Series ♦%20 M%20 —%20 R/" to "TV Series ♦ M — R",
        "TV-WEB-Series/TV Series ♦%20 S%20 —%20 Z/" to "TV Series ♦ S — Z"
    )
}