package com.redowan


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.Score
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

data class TmdbSearchResponse(val results: List<TmdbItem>?)
data class TmdbItem(
    val id: Int?,
    @param:JsonProperty("poster_path") val posterPath: String?,
    @param:JsonProperty("overview") val overview: String?,
    @param:JsonProperty("release_date") val releaseDate: String?,
    @param:JsonProperty("first_air_date") val firstAirDate: String?,
    @param:JsonProperty("vote_average") val voteAverage: Double?
){
    var imdbId: String? = null
}

data class ExternalIdsResponse(
    @param:JsonProperty("imdb_id") val imdbId: String?
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
            // 1. Remove Prefixes like "01-", "007 ", "009 "
            .replace(Regex("""^\d{2,3}[- ]"""), "")
            // 2. Remove Type & Year blocks like "(TV Series 2011–2017)" or "(2024– )" or "[2004]"
            .replace(Regex("""(?i)\((?:TV\s(?:Mini[- ]?)?Series|TV\sCartoon|TV\sMini-Series)\s?\d{4}.*?\)|[\(\[]\d{4}.*?[\)\]]"""), "")
            // 3. Remove Technical Tags: 1080p, 720p, [Dual Audio], (Dual Audio), NF, AMZN, HDRip, etc.
            .replace(Regex("""(?i)\d{3,4}p|\[.*?\]|\((?:Dual|Multi|Bangla|Tamil|Telugu|English|Hindi)\sAudio\)|NF|AMZN|HDRip|WEBRip|AMZN|Bluray|Web-Dl"""), "")
            .replace("  ", " ")
            .trim()
    }

    private suspend fun getExternalMetadata(rawName: String, isTv: Boolean): TmdbItem? {
        val clean = cleanTitle(rawName)
        val type = if (isTv) "tv" else "movie"
        return try {
            val result = app.get(
                "https://api.themoviedb.org/3/search/$type",
                params = mapOf(
                    "api_key" to "e6333b32409e02a4a6eba6fb7ff866bb",
                    "query" to clean
                ),
                timeout = 10
            ).parsed<TmdbSearchResponse>().results?.firstOrNull()

            if (result != null && result.id != null) {
                try {
                    val external = app.get(
                        "https://api.themoviedb.org/3/$type/${result.id}/external_ids",
                        params = mapOf("api_key" to "e6333b32409e02a4a6eba6fb7ff866bb"),
                        timeout = 10
                    ).parsed<ExternalIdsResponse>()
                    result.imdbId = external?.imdbId
                } catch (_: Exception) {
                    // ignore external ids failures
                }
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/$serverName/${request.data}").document
        val homeResponse = doc.select("tbody > tr:gt(1):lt(12)")
        val home = homeResponse.mapNotNull { post ->
            getPostResult(post)
        }
        return newHomePageResponse(request.name, home, false)
    }

    private fun getPostResult(post: Element): SearchResponse {
        val folderHtml = post.select("td.fb-n > a")
        val name = folderHtml.text()
        val url = mainUrl + folderHtml.attr("href")
        return newAnimeSearchResponse(name, url, TvType.Movie) {
            addDubStatus(
                dubExist = when {
                    "Dual" in name -> true
                    else -> false
                }, subExist = when {
                    "ESub" in name -> true
                    else -> false
                }
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val body =
            "{\"action\":\"get\",\"search\":{\"href\":\"/$serverName/\",\"pattern\":\"$query\",\"ignorecase\":true}}".toRequestBody(
                "application/json".toMediaType()
            )
        val doc = app.post("$mainUrl/$serverName/", requestBody = body).text
        val searchJson = AppUtils.parseJson<SearchResult>(doc)
        val searchResponse: MutableList<SearchResponse> = mutableListOf()
        searchJson.search.take(40).map { post ->
            if (post.size == null) {
                val href = post.href
                val name = nameFromUrl(href)
                searchResponse.add(
                    newAnimeSearchResponse(
                        name, href
                    ) {
                        addDubStatus(
                            dubExist = when {
                                "Dual" in name -> true
                                else -> false
                            }, subExist = when {
                                "ESub" in name -> true
                                else -> false
                            }
                        )
                    }
                )
            }
        }
        return searchResponse
    }

    private val nameRegex = Regex(""".*/([^/]+)(?:/[^/]*)*$""")
    private fun nameFromUrl(href: String): String {
        val hrefDecoded = URLDecoder.decode(href, StandardCharsets.UTF_8.toString())
        val name = nameRegex.find(hrefDecoded)?.groups?.get(1)?.value
        return name.toString()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val rawName = nameFromUrl(url)
        val isTv = containsAnyLoop(url, tvSeriesKeyword)

        // Metadata Enrichment
        val meta = getExternalMetadata(rawName, isTv)
        val tmdbPoster = meta?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

        // Local Poster Search
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
            var seasonNum = 0
            tableHtml.forEach {
                val aHtml = it.selectFirst("td.fb-n > a")
                val link = mainUrl + aHtml?.attr("href")
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    seasonNum++
                    seasonExtractor(link, episodesData, seasonNum)
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

            return newTvSeriesLoadResponse(rawName, url, TvType.TvSeries, episodesData) {
                this.posterUrl = finalPoster
                this.plot = meta?.overview
                this.year = (meta?.firstAirDate ?: meta?.releaseDate)?.split("-")?.firstOrNull()?.toIntOrNull()
                meta?.imdbId?.let { addImdbId(it) }
                meta?.voteAverage?.let { this.score = Score.from10(it) }
            }
        } else {
            val folderHtml = tableHtml.select("td.fb-n > a[href~=(?i)\\.(mkv|mp4)]")
            val name = folderHtml.text().toString()
            val link = mainUrl + folderHtml.attr("href")
            return newMovieLoadResponse(name, url, TvType.Movie, link) {
                this.posterUrl = finalPoster
                this.plot = meta?.overview
                this.year = meta?.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
                meta?.imdbId?.let { addImdbId(it) }
                meta?.voteAverage?.let { this.score = Score.from10(it) }
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
                    return true // Return immediately if a match is found
                }
            }
        }
        return false // Return false if no match is found after checking all keywords
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

