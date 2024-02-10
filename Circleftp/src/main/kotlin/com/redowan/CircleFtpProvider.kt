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

    private fun Post.toSearchResponse(): SearchResponse? {

        return newMovieSearchResponse(
            title,
            id,
            TvType.Movie,
        ) {
            this.posterUrl = "$mainUrl/uploads/${imageSm}"
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse =
            app.get("$mainUrl/api/posts?searchTerm=$query&order=desc", referer = "$mainUrl/").text
        return tryParseJson<ArrayList<Post>>(searchResponse)?.mapNotNull { post ->
            post.toSearchResponse()
        } ?: throw ErrorLoadingException("Invalid Json reponse")
    }


    data class Post(
        @JsonProperty("imageSm") val imageSm: String?,
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
    )


}