package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(EmwBDProvider())
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
//}

class EmwBDProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.emwbd1.fun/"
    override var name = "EmwBD"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val mainPage = mainPageOf(
        "/" to "Latest Movies",
        "/category/bangla-movie/" to "Bangla Movies",
        "/category/bangla-dubbed/" to "Bangla Dubbed",
        "/category/bangla-series/" to "Bangla Series",
        "/category/kolkata-movie/" to "Kolkata Movies",
        "/category/hollywood/" to "Hollywood Movies",
        "/category/animation/" to "Animation",
        "/category/bollywood/" to "Bollywood Movies",
        "/category/hindi-dubbed/" to "Hindi Dubbed",
        "/category/south-movie/" to "South Indian Movies",
        "/category/china-korean-japan/" to "China.Korean.Japan",
        "/category/web-series/" to "Web Series",
        "/category/tv-shows/" to "TV Shows"
    )
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.AnimeMovie,
    )
    private val headers =
        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl${request.data}page/$page/"
        val doc = app.get(url, headers = headers).document
        val home = doc.select("div.movie-card").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select("a.title").attr("title")
        val check = title.lowercase()
        val quality = post.select(".quality").text().lowercase()
        val url = post.select("a.title").attr("href")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select("a.title > img").attr("data-src")
            this.quality = getSearchQuality(quality)
            addDubStatus(
                dubExist = when {
                    "dubbed" in check -> true
                    "dual audio" in check -> true
                    "multi audio" in check -> true
                    else -> false
                },
                subExist = when {
                    "esub" in check -> true
                    else -> false
                }
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", headers = headers).document
        return doc.select("div.movie-card").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document
        val title = doc.select("h2.mb-2").text()
        val year = getYearFromString(title)
        val image = doc.select(".wp-post-image").attr("src")
        val episodesData = mutableListOf<Episode>()
        val genre = doc.selectFirst("div.movie-card-view:nth-child(2) > p:nth-child(4) > b:nth-child(1)")?.text()?.split(",")
        doc.select("a.btn").forEach {
            val name = it.text()
            val link = it.attr("href")
            episodesData.add(
                newEpisode(link){
                    this.name = name
                }
            )
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
            this.posterUrl = image
            this.year = year
            this.tags = genre
        }
    }

    private val redirectRegex = "location.href='(.*)'".toRegex()
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = headers).document
        doc.select("button.download-button").forEach {button ->
            if(button.text().contains("Stream")) {
                val redirectUrl =
                    redirectRegex.find(button.attr("onclick"))?.groupValues?.getOrNull(1)
                loadExtractor(redirectUrl.toString(), subtitleCallback, callback)
            }
        }
        return true
    }

    /**
     * Determines the search quality based on the presence of specific keywords in the input string.
     *
     * @param check The string to check for keywords.
     * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
     */
    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("webrip") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("bluray") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains(
                    "hdtc"
                ) -> SearchQuality.HdCam

                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hd") || lowercaseCheck.contains(
                    "hdtv"
                ) -> SearchQuality.HD

                lowercaseCheck.contains("telesync") -> SearchQuality.Telesync
                lowercaseCheck.contains("telecine") -> SearchQuality.Telecine
                else -> null
            }
        }
        return null
    }

    /**
     * Extracts a four-digit year from a string, prioritizing years in parentheses and ensuring no word characters follow.
     *
     * Example:
     *
     * "This is (2023) movie" -> 2023
     *
     * "This is 1920x1080p" -> null
     *
     * "This is 2023 movie" -> 2023
     *
     * "This is 1999-2010 TvSeries" -> 1999
     *
     * @param check The input string.
     * @return The year as an integer, or `null` if no match is found.
     */
    private fun getYearFromString(check: String?): Int? {
        return check?.let {
            parenthesesYear.find(it)?.value?.toIntOrNull()
                ?: withoutParenthesesYear.find(it)?.value?.toIntOrNull()
        }
    }
    private val parenthesesYear = "(?<=\\()\\d{4}(?=\\))".toRegex()
    private val withoutParenthesesYear = "(19|20)\\d{2}(?!\\w)".toRegex()
}