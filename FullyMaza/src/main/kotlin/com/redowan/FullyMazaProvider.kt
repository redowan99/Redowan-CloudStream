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
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(FullyMazaProvider())
////    providerTester.testLoadLinks("https://checklinko.top/60382/")
////    providerTester.testAll()
//    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("https://fullymaza.pw/2024/06/die-in-a-gunfight-2021-hdrip-hindi-dual-audio-480p-720p-1080p/")
//}


class FullyMazaProvider : MainAPI() {
    override var mainUrl = "https://fullymaza.qpon"
    override var name = "FullyMaza"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val mainPage = mainPageOf(
        "/" to "Latest",
        "/category/720p-brrip-movies/bollywood-720p-brrip-movies/" to "Bollywood",
        "/category/720p-brrip-movies/dual-audio-hindi-720p-brrip-movies/" to "Dual audio (Hindi)",
        "/category/720p-brrip-movies/hindi-dubbed-720p-brrip-movies/" to "Hindi Dubbed",
        "/category/720p-brrip-movies/south-indian-dubbed-movie-720p/" to "South indian Dubbed",
        "/category/720p-brrip-movies/hollywood-720p-brrip-movies/" to "Hollywood",
        "/category/tv-shows/web-series-hindi/" to "Hindi Web Series",
        "/category/tv-shows/hindi-dubbed-tv-shows/" to "Hindi Dubbed Web Series"
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}page/$page", timeout = 30).document
        val home = if (request.name == "Latest") {
            doc.select("article.movi-item > div").mapNotNull { toLatestResult(it) }
        } else {
            doc.select(".blog-starter-standard-post__entry-content").mapNotNull { toResult(it) }
        }
        return newHomePageResponse(request.name, home, true)
    }

    private val nonAscii = Regex("[^\\x00-\\x7F]")
    private fun toLatestResult(post: Element): SearchResponse {
        val title = post.select(".movi-title > a:nth-child(1)").text().replace(nonAscii, "")
        val url = post.select(".movi-title > a:nth-child(1)").attr("href")
        val check = title.lowercase()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst(".thumbnail-wrapper > img:nth-child(1)")?.attr("src")
            this.quality = getQualityFromString(check)
            addDubStatus(
                dubExist = when {
                    "dubbed" in check -> true
                    "dual audio" in check -> true
                    "multi audio" in check -> true
                    else -> false
                }, subExist = false
            )
        }
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select(".res-grid-title").text().replace(nonAscii, "")
        val url = post.select(".blog-starter-standard-post__post-title > a:nth-child(1)").attr("href")
        val check = title.lowercase()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst(".thumbnail-wrapper > img:nth-child(1)")?.attr("src")
            this.quality = getQualityFromString(check)
            addDubStatus(
                dubExist = when {
                    "dubbed" in check -> true
                    "dual audio" in check -> true
                    "multi audio" in check -> true
                    else -> false
                }, subExist = false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", cacheTime = 60, timeout = 30).document
        return doc.select(".blog-starter-standard-post__entry-content").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60, timeout = 30).document
        val title = doc.selectFirst(".blog-starter-standard-post__post-title")?.text() ?: ""
        val image =
            doc.select(".blog-starter-standard-post__full-summery > p:nth-child(3) > noscript img")
                .attr("src")
        val plot = doc.select(".blog-starter-standard-post__full-summery > p:nth-child(4)").html()
        val episodesData = mutableListOf<Episode>()
        doc.select("h4.tabdownload > a:nth-child(1)").forEach {
            val link = it.attr("href")
            val name = it.text().replace("Download Links", "")
            episodesData.add(
                newEpisode(link) {
                    this.name = name
                })
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
            this.posterUrl = image
            this.plot = plot
            this.year = getYearFromString(title)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, cacheTime = 60, timeout = 30).document
        doc.select(".well > a").forEach {
            loadExtractor(it.attr("href"), subtitleCallback, callback)
        }
        return true
    }

    private fun getQualityFromString(check: String?): SearchQuality? {
        val lowerCaseCheck = check?.lowercase()
        return when {
            lowerCaseCheck == null -> null
            lowerCaseCheck.contains("hdrip") -> SearchQuality.HD
            lowerCaseCheck.contains("hdts") || lowerCaseCheck.contains(" hdcam ") -> SearchQuality.HdCam
            lowerCaseCheck.contains("pre-dvd") -> SearchQuality.CamRip
            lowerCaseCheck.contains(" cam ") -> SearchQuality.Cam
            lowerCaseCheck.contains("bluray") -> SearchQuality.BlueRay
            else -> null
        }
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
    private val yearWithParentheses = "(?<=\\()\\d{4}(?=\\))".toRegex()
    private val yearWithOutParentheses = "(19|20)\\d{2}(?!\\w)".toRegex()
    private fun getYearFromString(check: String?): Int? {
        return check?.let {
            yearWithParentheses.find(it)?.value?.toIntOrNull()
                ?: yearWithOutParentheses.find(it)?.value?.toIntOrNull()
        }
    }
}