package com.redowan


import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass

class CircleFtpProvider : MainAPI() {
//    override var mainUrl = "http://new.circleftp.net"
//    private val apiUrl = "https://new.circleftp.net:5000"
    override var mainUrl = "http://15.1.1.50"
    private val apiUrl = "https://15.1.1.50:5000"
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


    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val instantLinkLoading = true


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
        request: MainPageRequest
    ): HomePageResponse {
        val requests = Requests(responseParser = parser)
        val json = requests.get(
            "$apiUrl/api/posts?categoryExact=${request.data}&page=$page&order=desc&limit=10",
            verify = false
        ).parsed<PageData>()
        val home = json.posts.mapNotNull { post ->
            toSearchResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toSearchResult(post: Post): SearchResponse? {
        if (post.type == "singleVideo" || post.type == "series") {
            return newMovieSearchResponse(post.title, "$mainUrl/content/${post.id}", TvType.Movie) {
                this.posterUrl = "$apiUrl/uploads/${post.imageSm}"
                val check = post.title.lowercase()
                this.quality = when {
                    " webrip " in check -> SearchQuality.WebRip
                    "web-dl" in check -> SearchQuality.WebRip
                    "bluray" in check -> SearchQuality.BlueRay
                    " hdts " in check -> SearchQuality.HdCam
                    "dvd" in check -> SearchQuality.DVD
                    " cam " in check -> SearchQuality.Cam
                    " camrip " in check -> SearchQuality.CamRip
                    " hdcam " in check -> SearchQuality.HdCam
                    " hdtc " in check -> SearchQuality.HdCam
                    " hdrip " in check -> SearchQuality.HD
                    " hd " in check -> SearchQuality.HD
                    " hdtv " in check -> SearchQuality.HD
                    " rip " in check -> SearchQuality.CamRip
                    " telecine " in check -> SearchQuality.Telecine
                    " telesync " in check -> SearchQuality.Telesync
                    " fhd " in check -> SearchQuality.HD
                    " 4k " in check -> SearchQuality.FourK
                    " hdr " in check -> SearchQuality.HDR
                    "1080p" in check -> SearchQuality.HD
                    "720p" in check -> SearchQuality.HD
                    else -> null
                }
            }
        }
        return null
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)


    override suspend fun search(query: String): List<SearchResponse> {
        val requests = Requests(responseParser = parser)
        val searchResponse = requests.get(
            "$apiUrl/api/posts?searchTerm=$query&order=desc",
            verify = false
        ).parsed<PageData>()
        return searchResponse.posts.mapNotNull { post ->
            toSearchResult(post)
        }
    }

    private val parser = object : ResponseParser {
        val mapper: ObjectMapper = jacksonObjectMapper().configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )

        override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
            return mapper.readValue(text, kClass.java)
        }

        override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
            return try {
                mapper.readValue(text, kClass.java)
            } catch (e: Exception) {
                null
            }
        }

        override fun writeValueAsString(obj: Any): String {
            return mapper.writeValueAsString(obj)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val requests = Requests(responseParser = parser)
        val json =
            requests.get(url.replace("$mainUrl/content/", "$apiUrl/api/posts/"), verify = false)
        val loadData = json.parsed<Data>()
        val title = loadData.name
        val poster = "$apiUrl/uploads/${loadData.image}"
        val description = loadData.metaData
        val year = loadData.year?.substring(0, 4)?.toInt()
        if (loadData.type == "singleVideo") {
            val movieUrl = json.parsed<Movies>()
            val duration =
                getDurationFromString(loadData.watchTime/*?.replace("h", "hour")?.replace("m","min")*/)
            return newMovieLoadResponse(title, url, TvType.Movie, movieUrl.content) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.duration = duration
            }
        } else {
            val tvData = json.parsed<TvSeries>()
            val episodesData = mutableListOf<Episode>()
            var seasonNum = 0
            tvData.content.forEach { season ->
                seasonNum++
                var episodeNum = 0
                season.episodes.forEach {
                    episodeNum++
                    episodesData.add(
                        Episode(
                            it.link,
                            name = null,
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
    }

    private fun linkToIp(data: String): String {
        return when {
            "index.circleftp.net" in data -> data.replace("index.circleftp.net", "15.1.4.2")
            "index2.circleftp.net" in data -> data.replace("index2.circleftp.net", "15.1.4.5")
            "index1.circleftp.net" in data -> data.replace("index1.circleftp.net", "15.1.4.9")
            "ftp3.circleftp.net" in data -> data.replace("ftp3.circleftp.net", "15.1.4.7")
            "ftp4.circleftp.net" in data -> data.replace("ftp4.circleftp.net", "15.1.1.5")
            "ftp5.circleftp.net" in data -> data.replace("ftp5.circleftp.net", "15.1.1.15")
            "ftp6.circleftp.net" in data -> data.replace("ftp6.circleftp.net", "15.1.2.3")
            "ftp7.circleftp.net" in data -> data.replace("ftp7.circleftp.net", "15.1.4.8")
            "ftp8.circleftp.net" in data -> data.replace("ftp8.circleftp.net", "15.1.2.2")
            "ftp9.circleftp.net" in data -> data.replace("ftp9.circleftp.net", "15.1.2.12")
            "ftp10.circleftp.net" in data -> data.replace("ftp10.circleftp.net", "15.1.4.3")
            "ftp11.circleftp.net" in data -> data.replace("ftp11.circleftp.net", "15.1.2.6")
            "ftp12.circleftp.net" in data -> data.replace("ftp12.circleftp.net", "15.1.2.1")
            "ftp13.circleftp.net" in data -> data.replace("ftp13.circleftp.net", "15.1.1.18")
            "ftp15.circleftp.net" in data -> data.replace("ftp15.circleftp.net", "15.1.4.12")
            "ftp17.circleftp.net" in data -> data.replace("ftp17.circleftp.net", "15.1.3.8")
            else -> data
        }
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

    data class PageData(
        val posts: List<Post>
    )

    data class Post(
        val id: Int,
        val type: String,
        val imageSm: String,
        val title: String,
        val name: String?,
    )

    data class Data(
        val type: String,
        val imageSm: String,
        val title: String,
        val image: String,
        val metaData: String?,
        val name: String,
        val quality: String?,
        val year: String?,
        val watchTime: String?
    )

    data class TvSeries(
        val content: List<Content>,
    )

    data class Content(
        val episodes: List<EpisodeData>,
        val seasonName: String
    )

    data class EpisodeData(
        val link: String,
        val title: String
    )

    data class Movies(
        val content: String?
    )

}