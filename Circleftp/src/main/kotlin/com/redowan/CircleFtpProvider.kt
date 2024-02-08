package com.redowan

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

    @Serializable
    data class Post(
    val id: Int,
    val image: String,
    val name: String,
    )

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        var jsonString = Jsoup.connect(mainUrl+"api/posts?searchTerm="+query+"&order=desc").get()

        val parsedJson = Json.parseToJsonElement(jsonString)

        for (item in parsedJson.orEmpty()) {
            val postJson = item.jsonObject
            val href =mainUrl+"api/posts/" + postJson["id"].parseInt()
            val title = postJson["name"].toString()
            val posterUrl = mainUrl+"uploads/" + postJson["image"].toString()
            return (title, href, posterUrl)
        }

        
        //return listOf<SearchResponse>()
    }
}