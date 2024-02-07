package com.redowan

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

class CircleFtpProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "http://15.1.1.50:5000/" 
    override var name = "Circle FTP"
    override val supportedTypes = setOf(
        "TvSeries",
        "Movie",
        "AnimeMovie",
        "Anime",
        "OVA",
        "Documentary",
    )

    override var lang = "bn"

    // enable this when your provider has a main page
    override val hasMainPage = false

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }
}