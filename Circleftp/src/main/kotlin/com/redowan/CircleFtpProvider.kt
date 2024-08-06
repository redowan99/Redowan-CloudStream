package com.redowan


import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.OkHttpClient
import okhttp3.Request


class CircleFtpProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "http://15.1.1.50"
    override var name = "Circle FTP"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.Cartoon,
        TvType.AsianDrama,
        TvType.Documentary,
        TvType.OVA,
        TvType.Others
    )
    override var lang = "bn"


    // enable this when your provider has a main page
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true


    override val mainPage = mainPageOf(
        "80" to "Featured",
        "6" to "English Movies",
        "9" to "English & Foreign TV Series",
        "22" to "Dubbed TV Series",
        "2" to "Hindi Movies",
        "5" to "Hindi TV Series",
        "3" to "South Indian Dubbed Movie",
        "21" to "Anime Series",
        "1" to "Animation Movies",
        "85" to "Documentary",
        "15" to "WWE"
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val json: String? = getJson("$mainUrl:5000/api/posts?categoryExact=${request.data}&page=$page&order=desc&limit=10")
        val gson = Gson()
        val homeResponse = gson.fromJson(json, PageData::class.java)
        val home = homeResponse.posts.mapNotNull { post ->
            toSearchResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun getJson(url: String): String? {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()
        return if (response.isSuccessful) {
            response.body.string()
        } else {
            null
        }
    }


    private fun toSearchResult(post: Posts): SearchResponse? {
        if (post.type == "singleVideo" || post.type == "series"){
            return newMovieSearchResponse(post.title, "$mainUrl/content/${post.id}", TvType.Movie) {
                this.posterUrl = "$mainUrl:5000/uploads/${post.imageSm}"
                val check = (post.quality).toString().lowercase()
                this.quality = when {
                    (" webrip " in check) -> SearchQuality.WebRip
                    ("web-dl" in check) -> SearchQuality.WebRip
                    ("bluray" in check) -> SearchQuality.BlueRay
                    (" hdts " in check) -> SearchQuality.HdCam
                    ("dvd" in check) -> SearchQuality.DVD
                    (" cam " in check) -> SearchQuality.Cam
                    (" camrip " in check) -> SearchQuality.CamRip
                    (" hdcam " in check) -> SearchQuality.HdCam
                    (" hdtc " in check) -> SearchQuality.HdCam
                    (" hdrip " in check) -> SearchQuality.HD
                    (" hd " in check) -> SearchQuality.HD
                    (" hdtv " in check) -> SearchQuality.HD
                    (" rip " in check) -> SearchQuality.CamRip
                    (" telecine " in check) -> SearchQuality.Telecine
                    (" telesync " in check) -> SearchQuality.Telesync
                    (" fhd " in check) -> SearchQuality.HD
                    (" 4k " in check) -> SearchQuality.FourK
                    (" hdr " in check) -> SearchQuality.HDR
                    ("1080p" in check) -> SearchQuality.HD
                    ("720p" in check) -> SearchQuality.HD
                    else -> null
                }
                this.quality = quality
            }
        }
        return null
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)


    override suspend fun search(query: String): List<SearchResponse> {
        val jsonString: String? = getJson("$mainUrl:5000/api/posts?searchTerm=$query&order=desc")
        val gson = Gson()
        val searchResponse = gson.fromJson<Map<String, List<Posts>>>(jsonString,
            object : TypeToken<Map<String, List<Posts>>>() {}.type)
        return searchResponse["posts"]?.mapNotNull { post ->
            toSearchResult(post)
        }?: listOf()
    }

    private fun extractLinksAndNames(input: String): List<Pair<String, String>> {
        val regex = "\\{(.*?), (.*?)\\}"
        val matches = Regex(regex).findAll(input)

        return matches.map { match ->
            Pair(match.groupValues[1].replace("link=", ""), match.groupValues[2].replace("title=", ""))
        }.toList()
    }

    override suspend fun load(url: String): LoadResponse {
        val jsonString: String? = getJson(url.replace("http://15.1.1.50/content/","$mainUrl:5000/api/posts/"))
        val gson = Gson()
        val type = object : TypeToken<Data>() {}.type
        val loadData = gson.fromJson<Data>(jsonString, type)

        val title = loadData.title
        val poster ="$mainUrl:5000/uploads/${loadData.image}"
        val description = loadData.metaData
        val year = loadData.year?.substring(0, 4)?.toInt()
        when (loadData.content) {
            is List<*> -> {
                val episodesData = mutableListOf<Episode>()
                var seasonNum = 0
                loadData.content.forEach { season ->
                    seasonNum++
                    val episodesList = season as Map<*, *>
                    val linksAndNames = extractLinksAndNames(episodesList["episodes"].toString())
                    var episodeNum = 0
                    for (pair in linksAndNames) {
                        episodeNum ++
                        val episodeUrl = pair.first
                        val episodeName = pair.second
                        episodesData.add(Episode(
                                episodeUrl,
                                episodeName,
                                seasonNum,
                                episodeNum
                            )
                        )
                    }
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                }
            }


            else -> {
                val dataUrl = loadData.content
                return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.backgroundPosterUrl = poster
                } 
            }
        }
    }

    private fun linkToIp(data: String): String {
        val newUrl: String

        if ("index.circleftp.net" in data) newUrl = data.replace("index.circleftp.net","15.1.4.2")
        else if ("index2.circleftp.net" in data) newUrl = data.replace("index2.circleftp.net","15.1.4.5")
        else if ("index1.circleftp.net" in data) newUrl = data.replace("index1.circleftp.net","15.1.4.9")
        else if ("ftp3.circleftp.net" in data) newUrl = data.replace("ftp3.circleftp.net","15.1.4.7")
        else if ("ftp4.circleftp.net" in data) newUrl = data.replace("ftp4.circleftp.net","15.1.1.5")
        else if ("ftp5.circleftp.net" in data) newUrl = data.replace("ftp5.circleftp.net","15.1.1.15")
        else if ("ftp6.circleftp.net" in data) newUrl = data.replace("ftp6.circleftp.net","15.1.2.3")
        else if ("ftp7.circleftp.net" in data) newUrl = data.replace("ftp7.circleftp.net","15.1.4.8")
        else if ("ftp8.circleftp.net" in data) newUrl = data.replace("ftp8.circleftp.net","15.1.2.2")
        else if ("ftp9.circleftp.net" in data) newUrl = data.replace("ftp9.circleftp.net","15.1.2.12")
        else if ("ftp10.circleftp.net" in data) newUrl = data.replace("ftp10.circleftp.net","15.1.4.3")
        else if ("ftp11.circleftp.net" in data) newUrl = data.replace("ftp11.circleftp.net","15.1.2.6")
        else if ("ftp12.circleftp.net" in data) newUrl = data.replace("ftp12.circleftp.net","15.1.2.1")
        else if ("ftp13.circleftp.net" in data) newUrl = data.replace("ftp13.circleftp.net","15.1.1.18")
        else if ("ftp15.circleftp.net" in data) newUrl = data.replace("ftp15.circleftp.net","15.1.4.12")
        else if ("ftp17.circleftp.net" in data) newUrl = data.replace("ftp17.circleftp.net","15.1.3.8")
        else newUrl = data

        return newUrl
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val dataNew = linkToIp(data)
        callback.invoke(
            ExtractorLink(
            mainUrl,
            this.name,
            url = dataNew,
            mainUrl,
            quality = 1080,
            isM3u8 = false,
            isDash = false
            )
        )
        return true
    }


    data class PageData (

        @SerializedName("posts"      ) var posts      : ArrayList<Posts> = arrayListOf(),

    )
    data class Posts (

        @SerializedName("id"         ) var id         : String,
        @SerializedName("title"      ) var title      : String,
        @SerializedName("type"       ) var type       : String?               = null,
        @SerializedName("image"      ) var image      : String?               = null,
        @SerializedName("imageSm"    ) var imageSm    : String?               = null,
        @SerializedName("metaData"   ) var metaData   : String?               = null,
        @SerializedName("name"       ) var name       : String?               = null,
        @SerializedName("quality"    ) var quality    : String?               = null,
        @SerializedName("watchTime"  ) var watchTime  : String?               = null,
        @SerializedName("year"       ) var year       : String?               = null,

    )

    data class Data(
        val title: String,
        val type: String?,
        val image: String?,
        val metaData: String?,
        val content: Any,
        val name: String?,
        val quality: String?,
        val watchTime: String?,
        val year: String?
    )
}