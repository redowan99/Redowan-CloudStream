package com.redowan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

import org.jsoup.Jsoup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class CircleFtpProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "http://15.1.1.50:5000/" 
    override var name = "Circle FTP"
    override val supportedTypes = setOf(
        TvType.Movie
    )
    override var lang = "en"


    // enable this when your provider has a main page
    override val hasMainPage = false

    
    private data class QuickSearchResponse(
        val name: String, 
        val id: Int,
        val image: String,
    )

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return app.get(mainUrl+"api/posts?searchTerm="+query+"&order=desc")
            .parsed<Map<String, QuickSearchResponse>>().map {
                val res = it.value
                MovieSearchResponse(
                    res.name,
                    res.id,
                    this.name,
                    TvType.Movie,
                    res.image,
                )
            }

        
        //return listOf<SearchResponse>()
    }
}