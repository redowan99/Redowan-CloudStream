package com.redowan

import com.lagradost.cloudstream3.HomePageList
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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(FibWatchProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
//    providerTester.testLoad("https://fibwatch.info/watch/agontuk-2024-bengali-iscreen-web-dl-720p_SPyeH84EDn3i7gK.html")
//}

class FibWatchProvider : MainAPI() {
    override var mainUrl = "https://fibwatch.info"
    override var name = "FibWatch"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.AnimeMovie,
    )

    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "/videos/latest" to "Latest",
        "/videos/trending" to "Trending",
        "/videos/category/1" to "Bangla & Kolkata",
        "/videos/category/852" to "Bangla Dubbed",
        "/videos/category/3" to "Web Series",
        "/videos/category/4" to "Hindi",
        "/videos/category/5" to "Hindi Dubbed",
        "/videos/category/8" to "English",
        "/videos/category/12" to "Korean",
        "/videos/category/7" to "Cartoon",
        "/videos/category/855" to "Natok",
        "/videos/category/other" to "Other",
        "/videos/category/853" to "Mix"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}?page_id=$page", cacheTime = 60).document
        val home = doc.select(".video-thumb > a").mapNotNull {toResult(it)}
        return newHomePageResponse(HomePageList(request.name,home,isHorizontalImages = true), true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst("a > img")?.attr("alt") ?: ""
        val url = post.selectFirst("a:nth-child(1)")?.attr("href") ?: ""
        val check = title.lowercase()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst("a > img")
                ?.attr("src")
            this.posterHeaders = mapOf("Referer" to mainUrl)
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

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search?keyword=$query", cacheTime = 60).document
        return doc.select(".video-thumb > a").mapNotNull {toResult(it)}
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60).document
        val title = doc.selectFirst(".hptag")?.text() ?: ""
        val image = doc.selectFirst("#my-video")?.attr("poster")
        val link = doc.selectFirst(".download-placement > a")?.attr("href")
        return newMovieLoadResponse(title, url, TvType.Movie, link) {
            this.posterUrl = image
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                mainUrl,
                this.name,
                url = data
            )
        )
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
}