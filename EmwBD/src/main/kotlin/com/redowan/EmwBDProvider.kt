package com.redowan

import com.lagradost.api.Log
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
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(EmwBDProvider())
//    providerTester.testLoadLinks("https://new1.filepress.shop/file/66f107ab98fd7b89339181f8 + https://m.gdfile.org/file/HRSEE6xpdThp + https://gdmirrorbot.nl/file/l3b5eb9 + https://new4.gdflix.cfd/file/4Isc0LmYGI")
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("https://www.emwbd.xyz/rajkumar-2024-bengali-full-movie-480p-720p-hd-download/")
////    providerTester.testLoad("https://www.emwbd.xyz/meghna-konnya-2024-bengali-dp-web-dl-download/")
//}

class EmwBDProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.emwbd.com"
    override var name = "EmwBD"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val mainPage = mainPageOf(
        "/" to "Latest Movies",
        "/category/treanding/" to "Trending",
        "/category/bangla-movie/" to "Bangla Movies",
        "/category/bangla-dubbed/" to "Bangla Dub Movies",
        "/category/bangla-series/" to "Bangla Series",
        "/category/hollywood/" to "Hollywood Movies",
        "/category/web-series/" to "Web Series",
        "/category/bollywood/" to "Bollywood Movies",
        "/category/hindi-dubbed/" to "Hindi Dubbed",
        "/category/south-movie/" to "South Indian Movies",
        "/category/tv-shows/" to "TV Shows",
        "/category/animation/" to "Animation",
        "/category/18-adult/" to "18+ Adult"
    )
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.AnimeMovie,
        TvType.NSFW
    )
    private val headers =
        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl${request.data}page/$page/"
        val doc = app.get(url, headers = headers).document
        val home = doc.select("div.image-container").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst("a.title")?.attr("title") ?: ""
        val check = title.lowercase()
        val quality = post.selectFirst("span.quality")?.text()?.lowercase()
        val url = post.selectFirst("a.title")?.attr("href") ?: ""
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select("a.title > img").attr("data-src")
            this.quality = getSearchQuality(quality)
            if (check != "") {
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
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("div.image-container").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h2.mb-2")?.text() ?: ""
        val year = getYearFromString(title)
        val image = doc.select(".wp-post-image").attr("src")
        val episodesData = mutableListOf<Episode>()
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
            this.rating = doc.selectFirst("span.rating")?.text()?.toRatingInt()
        }
    }

    private val hrefRegex = Regex("window\\.location\\.href='([^']+)")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("center > button:gt(6)").forEach{
            val url = hrefRegex.find(it.attr("onclick"))?.groupValues?.get(1) ?: ""
            Log.d("Extractor Url",url)
            loadExtractor(url, subtitleCallback, callback)
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
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") ||
                        lowercaseCheck.contains("hdtc") || lowercaseCheck.contains("pre-hd") -> SearchQuality.HdCam
                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hd") ||
                        lowercaseCheck.contains("hdtv") -> SearchQuality.HD
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