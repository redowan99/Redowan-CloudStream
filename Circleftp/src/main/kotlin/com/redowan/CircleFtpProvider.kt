package com.redowan

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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

    @Serializable
    data class Post(
    val id: Int,
    val title: String,
    val type: String,
    val categories: List<Category>,
    val tags: String,
    val metaData: String?,
    val createdAt: String,
    val updatedAt: String,
    val image: String,
    val imageSm: String,
    val cover: String?,
    val name: String,
    val quality: String?,
    val watchTime: String?,
    val year: String,
    val createdBy: CreatedBy
    )

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val jsonString = app.get("$mainUrlapi/posts?searchTerm=$query&order=desc", referer = "$mainUrl").document
        jsondata = jsondata.trimIndent()

        val parsedJson = Json.parseToJsonElement(jsonString)
        for (item in parsedArray) {
            val postJson = item.jsonObject
            val url ="$mainUrlapi/posts/" + postJson["id"].parseInt()
            val name = postJson["name"].toString()
            val posterUrl = "$mainUrluploads/" + postJson["image"].toString()
            extractedData.add(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            }
        }

        return extractedData
        //return listOf<SearchResponse>()
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("main article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }


    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("header > h1")?.text() ?: return null
        val link = document.selectFirst("article meta[itemprop=embedUrl]")?.attr("content")?.split("/")?.last()?.let{
            "https://www.youtube.com/watch?v=$it"
        } ?: throw ErrorLoadingException("No link found")

        return newMovieLoadResponse(title, url, TvType.Movie, link) {
            this.posterUrl = document.select("div.player-content > img").attr("src")
            this.year = document.selectFirst("div.meta-bar.meta-single")?.ownText()?.filter { it.isDigit() }?.toIntOrNull()
            this.plot = document.select("div[itemprop=reviewBody] > p").text().trim()
            this.tags = document.select("div.meta-bar.meta-single > a").map { it.text() }
            this.rating = document.selectFirst("div.module div.star")?.text()?.toRatingInt()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        loadExtractor(data, data, subtitleCallback, callback)

        return true
    }
}