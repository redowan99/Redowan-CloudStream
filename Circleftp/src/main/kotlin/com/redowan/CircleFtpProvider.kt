package com.redowan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.ArrayList


import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CircleFtpProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "http://15.1.1.50:5000" 
    override var name = "Circle FTP"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )
    override var lang = "bn"


    // enable this when your provider has a main page
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "80" to "Featured",
        "6" to "English Movies",
        "9" to "English & Foreign TV Series",
        "2" to "Hindi Movies",
        "5" to "Hindi TV Series",
    )


    private fun getJson(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()
        return response.body.string()
    }

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val jsonString = getJson("$mainUrl/api/posts?categoryExact="+request.data+"&page=1&order=desc&limit=50")
        val gson = Gson()
        val jtype = object : TypeToken<Map<String, List<Post>>>() {}.type
        val homeResponse = gson.fromJson<Map<String, List<Post>>>(jsonString, jtype)
        //val home: List<String?> = 
        //}
        //
        //println(home)

        return newHomePageResponse(request.name, homeResponse["posts"]?.mapNotNull { post ->
            if (post.type == "singleVideo" || post.type == "series"){
                it.toSearchResult()
                }
            }?: listOf()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        return newMovieSearchResponse(this.title, this.id.toString(), TvType.Movie) {
            this.posterUrl = "$mainUrl/uploads/"+ this.imageSm
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val jsonString = getJson("$mainUrl/api/posts?searchTerm=$query&order=desc")
        val gson = Gson()
        val type = object : TypeToken<Map<String, List<Post>>>() {}.type
        val searchResponse = gson.fromJson<Map<String, List<Post>>>(jsonString, type)
        return searchResponse["posts"]?.map { post ->
            it.toSearchResult()
        }?: listOf()
    }

    data class Post(
        val id: String,
        val title: String,
        val imageSm: String?,
        val type: String?
    )
}