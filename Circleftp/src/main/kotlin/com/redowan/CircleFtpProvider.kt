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
        TvType.Movie
    )
    override var lang = "en"


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