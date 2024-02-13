package com.redowan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.ArrayList


import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CircleFtpProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "http://15.1.1.50:5000" 
    override var name = "Circle FTP"
    override val supportedTypes = setOf(
        TvType.Movie
    )
    override var lang = "bn"


    // enable this when your provider has a main page
    override val hasMainPage = false


    override suspend fun search(query: String): List<SearchResponse> {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://15.1.1.50:5000/api/posts?searchTerm=batman&order=desc")
            .build()
        val response = client.newCall(request).execute()
        val jsonString = response.body.string()
        val gson = Gson()
        val type = object : TypeToken<Map<String, List<Post>>>() {}.type
        val searchResponse = gson.fromJson<Map<String, List<Post>>>(jsonString, type)
        return searchResponse["posts"]?.map { post ->
            val title = post.title
            val poster = post.imageSm
            val href = post.id
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }

    }


    data class Post(
    val id: Int,
    val title: String,
    val imageSm: String?
    )


}