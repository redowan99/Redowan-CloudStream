package com.redowan

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

class BdixDhakaFlix7Provider : BdixDhakaFlix14Provider()  {
    override var mainUrl = "http://172.16.50.7"
    override var name = "(BDIX) DhakaFlix 7"
    override val tvSeriesKeyword: List<String> = emptyList()
    override val serverName: String = "DHAKA-FLIX-7"
    override val supportedTypes = setOf(TvType.Movie)
    override val mainPage= mainPageOf(
        "English Movies/($year)/" to "English Movies",
        "English Movies (1080p)/($year) 1080p/" to "English Movies (1080p)",
        "3D Movies/" to "3D Movies",
        "Foreign Language Movies/Japanese Language/" to "Japanese Movies",
        "Foreign Language Movies/Korean Language/" to "Korean Movies",
        "Foreign Language Movies/Bangla Dubbing Movies/" to "Bangla Dubbing Movies",
        "Foreign Language Movies/Pakistani Movie/" to "Pakistani Movies",
        "Kolkata Bangla Movies/(2022)/" to "Kolkata Bangla Movies",
        "Foreign Language Movies/Chinese Language/" to "Chinese Movies"
    )
}