package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

class CircleFtpProvider : MainAPI() {
    override var mainUrl = "http://new.circleftp.net"
    private var mainApiUrl = "http://new.circleftp.net:5000"
    private val apiUrl = "http://15.1.1.50:5000"
    override var name = "(BDIX) Circle FTP"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
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

    override val mainPage = mainPageOf(
        "80" to "Featured",
        "6" to "English Movies",
        "9" to "English & Foreign TV Series",
        "22" to "Dubbed TV Series",
        "2" to "Hindi Movies",
        "5" to "Hindi TV Series",
        "238" to "Indian TV Show",
        "7" to "English & Foreign Hindi Dubbed Movies",
        "8" to "Foreign Language Movies",
        "3" to "South Indian Dubbed Movies",
        "4" to "South Indian Movies",
        "1" to "Animation Movies",
        "21" to "Anime Series",
        "85" to "Documentary",
        "15" to "WWE"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val json = try {
            app.get(
                "$mainApiUrl/api/posts?categoryExact=${request.data}&page=$page&order=desc&limit=10",
                verify = false,
                cacheTime = 60
            )
        } catch (_: Exception) {
            app.get(
                "$apiUrl/api/posts?categoryExact=${request.data}&page=$page&order=desc&limit=10",
                verify = false,
                cacheTime = 60
            )
        }

        val home = AppUtils.parseJson<PageData>(json.text).posts.mapNotNull { post ->
            toSearchResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toSearchResult(post: Post): SearchResponse? {
        if (post.type == "singleVideo" || post.type == "series") {
            val type = if (post.type == "series") TvType.Anime else TvType.Movie
            return newAnimeSearchResponse(post.title, "$mainUrl/content/${post.id}", type) {
                this.posterUrl = "$mainApiUrl/uploads/${post.imageSm}"
                val check = post.title.lowercase()
                this.quality = getSearchQuality(check)
                addDubStatus(
                    dubExist = when {
                        "dubbed" in check -> true
                        "dual audio" in check -> true
                        "multi audio" in check -> true
                        else -> false
                    },
                    subExist = false
                )
            }
        }
        return null
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val json = try {
            app.get(
                "$mainApiUrl/api/posts?searchTerm=$query&order=desc",
                verify = false,
                cacheTime = 60
            )
        } catch (_: Exception) {
            app.get(
                "$apiUrl/api/posts?searchTerm=$query&order=desc",
                verify = false,
                cacheTime = 60
            )
        }

        return AppUtils.parseJson<PageData>(json.text).posts.mapNotNull { post ->
            toSearchResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val json = try {
            app.get(
                url.replace("$mainUrl/content/", "$mainApiUrl/api/posts/"),
                verify = false,
                cacheTime = 60
            )
        } catch (_: Exception) {
            app.get(
                url.replace("$mainUrl/content/", "$apiUrl/api/posts/"),
                verify = false,
                cacheTime = 60
            )
        }

        val urlCheck = json.url.contains(mainApiUrl)
        val loadData = AppUtils.parseJson<Data>(json.text)
        val title = loadData.title
        val poster = "$apiUrl/uploads/${loadData.image}"
        val description = loadData.metaData
        val year = selectUntilNonInt(loadData.year)

        if (loadData.type == "singleVideo") {
            val movieUrl = json.parsed<Movies>().content
            val link = if (urlCheck) movieUrl else linkToIp(movieUrl)
            val duration = getDurationFromString(loadData.watchTime)
            return newMovieLoadResponse(title, url, TvType.Movie, link) {
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
                    val episodeUrl = it.link
                    val link = if (urlCheck) episodeUrl else linkToIp(episodeUrl)
                    episodesData.add(
                        newEpisode(link) {
                            this.episode = episodeNum
                            this.season = seasonNum
                        }
                    )
                }
            }

            val (malId, anilistId, simklId) = extractStaticIds(title)

            return newAnimeLoadResponse(title, url, TvType.Anime, episodesData) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                addMalId(malId)
                addAniListId(anilistId)
                tryAddSimklId(simklId)
            }
        }
    }

    private fun tryAddSimklId(simklId: Int?) {
        if (simklId == null) return
        runCatching {
            val method = this::class.java.methods.firstOrNull { it.name == "addSimklId" && it.parameterTypes.size == 1 }
            method?.invoke(this, simklId)
        }
    }

    private fun extractStaticIds(title: String): Triple<Int?, Int?, Int?> {
        val t = title.lowercase()
        return when {
            "naruto" in t -> Triple(20, 20, 20)
            "one piece" in t -> Triple(21, 21, 21)
            "bleach" in t -> Triple(269, 269, 269)
            "attack on titan" in t || "shingeki" in t -> Triple(16498, 16498, 16498)
            "demon slayer" in t || "kimetsu" in t -> Triple(38000, 38000, 38000)
            "jujutsu kaisen" in t -> Triple(40748, 40748, 40748)
            "my hero academia" in t || "boku no hero academia" in t -> Triple(31964, 31964, 31964)
            else -> Triple(null, null, null)
        }
    }

    private fun linkToIp(data: String?): String {
        if (data != null) {
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
        } else return ""
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = data
            )
        )
        return true
    }

    private fun selectUntilNonInt(string: String?): Int? {
        return string?.let { Regex("^.*?(?=\\D|$)").find(it)?.value?.toIntOrNull() }
    }

    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("webrip") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("bluray") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains("hdtc") -> SearchQuality.HdCam
                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hd") || lowercaseCheck.contains("hdtv") -> SearchQuality.HD
                lowercaseCheck.contains("telesync") -> SearchQuality.Telesync
                lowercaseCheck.contains("telecine") -> SearchQuality.Telecine
                else -> null
            }
        }
        return null
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