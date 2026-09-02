package com.redowan

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.*

data class TmdbSearchResponse(val results: List<TmdbItem>?)
data class TmdbItem(
    val id: Int?,
    @JsonProperty("poster_path") val posterPath: String?,
    @JsonProperty("backdrop_path") val backdropPath: String?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("release_date") val releaseDate: String?,
    @JsonProperty("first_air_date") val firstAirDate: String?,
    @JsonProperty("vote_average") val voteAverage: Double?,
    val videos: TmdbVideoResponse? = null,
    val credits: TmdbCreditsResponse? = null
)

data class TmdbVideoResponse(val results: List<TmdbVideo>?)
data class TmdbVideo(val key: String?, val site: String?, val type: String?)
data class TmdbCreditsResponse(val cast: List<TmdbCast>?)
data class TmdbCast(
    val name: String?,
    @JsonProperty("profile_path") val profilePath: String?,
    val character: String?
)

open class BdixDhakaFlix14Provider : MainAPI() {
    override var mainUrl = "http://172.16.50.14"
    override var name = "(BDIX) DhakaFlix 14"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.AnimeMovie, TvType.TvSeries
    )
    open val year = 2025
    open val tvSeriesKeyword: List<String>? = listOf("KOREAN%20TV%20%26%20WEB%20Series", "TV-WEB-Series")
    open val serverName: String = "DHAKA-FLIX-14"

    override val mainPage = mainPageOf(
        "Animation Movies (1080p)/" to "Animation Movies",
        "English Movies (1080p)/($year) 1080p/" to "English Movies",
        "Hindi Movies/($year)/" to "Hindi Movies",
        "IMDb Top-250 Movies/" to "IMDb Top-250 Movies",
        "SOUTH INDIAN MOVIES/Hindi Dubbed/($year)/" to "Hindi Dubbed",
        "SOUTH INDIAN MOVIES/South Movies/$year/" to "South Movies",
        "/KOREAN TV %26 WEB Series/" to "Korean TV & WEB Series"
    )

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""^\d{1,3}\.\s*"""), "")
            .replace(Regex("""\s*\([^)]*?\d{4}.*?\).*$"""), "")
            .replace("  ", " ")
            .trim()
    }

    private suspend fun getExternalMetadata(rawName: String, isTv: Boolean, fullDetails: Boolean = false): TmdbItem? {
        val clean = cleanTitle(rawName)
        val type = if (isTv) "tv" else "movie"
        return try {
            val search = app.get(
                "https://api.themoviedb.org/3/search/$type",
                params = mapOf(
                    "api_key" to "e6333b32409e02a4a6eba6fb7ff866bb",
                    "query" to clean
                ),
                timeout = 10,
                cacheTime = 60
            ).parsed<TmdbSearchResponse>().results?.firstOrNull()

            if (fullDetails && search?.id != null) {
                app.get(
                    "https://api.themoviedb.org/3/$type/${search.id}",
                    params = mapOf(
                        "api_key" to "e6333b32409e02a4a6eba6fb7ff866bb",
                        "append_to_response" to "videos,credits"
                    ),
                    cacheTime = 60
                ).parsed<TmdbItem>()
            } else search
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getLocalPoster(url: String): String? {
        return try {
            val doc = app.get(url, cacheTime = 60).document
            val allImages = doc.select("td.fb-n > a[href~=(?i)\\.(png|jpe?g)]").map { it.attr("href") }
            val posterPath = allImages.find { img ->
                val lower = img.lowercase()
                lower.contains("a_al_") || lower.contains("a11") || lower.contains("poster") || lower.contains("folder")
            } ?: allImages.firstOrNull()
            if (posterPath != null) mainUrl + posterPath else null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse = coroutineScope {
        val doc = app.get("$mainUrl/$serverName/${request.data}").document
        val homeResponse = doc.select("tbody > tr:gt(1):lt(12)")
        val home = homeResponse.map { post ->
            async { getPostResult(post) }
        }.awaitAll().filterNotNull()
        newHomePageResponse(request.name, home, false)
    }

    private suspend fun getPostResult(post: Element): SearchResponse? = coroutineScope {
        val folderHtml = post.selectFirst("td.fb-n > a") ?: return@coroutineScope null
        val name = folderHtml.text()
        if (name.isBlank()) return@coroutineScope null
        val url = mainUrl + folderHtml.attr("href")
        val isTv = containsAnyLoop(url, tvSeriesKeyword)

        val metaDeferred = async { getExternalMetadata(name, isTv) }
        val localPosterDeferred = async { getLocalPoster(url) }

        val meta = metaDeferred.await()
        val tmdbPoster = meta?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val localPoster = localPosterDeferred.await()
        val finalPoster = tmdbPoster ?: localPoster

        newAnimeSearchResponse(name, url, if (isTv) TvType.TvSeries else TvType.Movie) {
            this.posterUrl = finalPoster
            this.score = Score.from10(meta?.voteAverage)
            addDubStatus(
                dubExist = "Dual" in name,
                subExist = "ESub" in name
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> = coroutineScope {
        val body =
            "{\"action\":\"get\",\"search\":{\"href\":\"/$serverName/\",\"pattern\":\"$query\",\"ignorecase\":true}}".toRequestBody(
                "application/json".toMediaType()
            )
        val doc = app.post("$mainUrl/$serverName/", requestBody = body).text
        val searchJson = AppUtils.parseJson<SearchResult>(doc)
        
        searchJson.search.take(40).map { post ->
            async {
                if (post.size == null) {
                    val href = post.href
                    val name = nameFromUrl(href)
                    val url = if (href.startsWith("http")) href else mainUrl + href
                    val isTv = containsAnyLoop(url, tvSeriesKeyword)
                    
                    val metaDeferred = async { getExternalMetadata(name, isTv) }
                    val localPosterDeferred = async { getLocalPoster(url) }

                    val meta = metaDeferred.await()
                    val tmdbPoster = meta?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                    val localPoster = localPosterDeferred.await()
                    val finalPoster = tmdbPoster ?: localPoster

                    newAnimeSearchResponse(name, url, if (isTv) TvType.TvSeries else TvType.Movie) {
                        this.posterUrl = finalPoster
                        this.score = Score.from10(meta?.voteAverage)
                        addDubStatus(
                            dubExist = "Dual" in name,
                            subExist = "ESub" in name
                        )
                    }
                } else null
            }
        }.awaitAll().filterNotNull()
    }

    private val nameRegex = Regex(""".*/([^/]+)(?:/[^/]*)*$""")
    private fun nameFromUrl(href: String): String {
        val hrefDecoded = URLDecoder.decode(href, StandardCharsets.UTF_8.toString())
        val name = nameRegex.find(hrefDecoded)?.groups?.get(1)?.value
        return name.toString()
    }

    override suspend fun load(url: String): LoadResponse = coroutineScope {
        val rawName = nameFromUrl(url)
        val isTv = containsAnyLoop(url, tvSeriesKeyword)

        val docDeferred = async { app.get(url).document }
        val metaDeferred = async { getExternalMetadata(rawName, isTv, fullDetails = true) }

        val doc = docDeferred.await()
        val meta = metaDeferred.await()

        val tmdbPoster = meta?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val tmdbBackdrop = meta?.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
        
        val allImages = doc.select("td.fb-n > a[href~=(?i)\\.(png|jpe?g)]").map { it.attr("href") }
        val posterPath = allImages.find { img ->
            val lower = img.lowercase()
            lower.contains("a_al_") || lower.contains("a11") || lower.contains("poster") || lower.contains("folder")
        } ?: allImages.firstOrNull()
        
        val localPoster = if (posterPath != null) mainUrl + posterPath else null
        val finalPoster = tmdbPoster ?: localPoster

        val tableHtml = doc.select("tbody > tr:gt(1)")

        if (isTv) {
            val episodesData = mutableListOf<Episode>()
            val seasonFolders = mutableListOf<Pair<String, Int>>()
            var seasonNum = 0

            tableHtml.forEach {
                val aHtml = it.selectFirst("td.fb-n > a")
                val link = mainUrl + aHtml?.attr("href")
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    seasonNum++
                    if (link.isNotBlank()) {
                        seasonFolders.add(Pair(link, seasonNum))
                    }
                } else if (aHtml?.attr("href")?.contains(Regex("(?i)\\.(mkv|mp4)")) == true) {
                    val tittle = aHtml.text()
                    episodesData.add(
                        newEpisode(link) {
                            this.name = tittle
                            this.season = 1
                        }
                    )
                }
            }

            if (seasonFolders.isNotEmpty()) {
                val extractedSeasons = seasonFolders.map { (seasonLink, sNum) ->
                    async {
                        val epList = mutableListOf<Episode>()
                        seasonExtractor(seasonLink, epList, sNum)
                        epList
                    }
                }.awaitAll()

                extractedSeasons.forEach { episodesData.addAll(it) }
            }

            newTvSeriesLoadResponse(rawName, url, TvType.TvSeries, episodesData) {
                this.posterUrl = finalPoster
                this.backgroundPosterUrl = tmdbBackdrop
                this.plot = meta?.overview
                this.year = (meta?.firstAirDate ?: meta?.releaseDate)?.split("-")?.firstOrNull()?.toIntOrNull()
                this.score = Score.from10(meta?.voteAverage)
                meta?.id?.let { addTMDbId(it.toString()) }
                addActors(meta?.credits?.cast?.map { Actor(it.name ?: "", it.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }) })
            }
        } else {
            val folderHtml = tableHtml.select("td.fb-n > a[href~=(?i)\\.(mkv|mp4)]")
            val link = mainUrl + folderHtml.attr("href")
            newMovieLoadResponse(rawName, url, TvType.Movie, link) {
                this.posterUrl = finalPoster
                this.backgroundPosterUrl = tmdbBackdrop
                this.plot = meta?.overview
                this.year = meta?.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
                this.score = Score.from10(meta?.voteAverage)
                meta?.id?.let { addTMDbId(it.toString()) }
                addActors(meta?.credits?.cast?.map { Actor(it.name ?: "", it.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }) })
            }
        }
    }

    private suspend fun seasonExtractor(
        url: String, episodesData: MutableList<Episode>, seasonNum: Int
    ) {
        val doc = app.get(url).document
        var episodeNum = 0
        doc.select("tbody > tr:gt(1) > td.fb-n > a[href~=(?i)\\.(mkv|mp4)]").forEach {
            episodeNum++
            val folderHtml = it.select("a")
            val name = folderHtml.text()
            val link = mainUrl + folderHtml.attr("href")
            episodesData.add(
                newEpisode(link) {
                    this.name = name
                    this.season = seasonNum
                    this.episode = episodeNum
                }
            )
        }
    }

    private fun containsAnyLoop(text: String, keyword: List<String>?): Boolean {
        if (!keyword.isNullOrEmpty()) {
            for (keyword in keyword) {
                if (text.contains(keyword, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data, this.name, url = data, type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }

    data class SearchResult(
        val search: List<Search>
    )

    data class Search(
        val fetched: Boolean,
        val href: String,
        val managed: Boolean,
        val size: Long?,
        val time: Long
    )
}