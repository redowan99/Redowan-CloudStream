package com.redowan

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
import org.jsoup.nodes.Element

class FibWatchProvider : MainAPI() {
    override var mainUrl = "https://fibwatch.online"
    override var name = "FibWatch"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.AnimeMovie,
        TvType.NSFW
    )

    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true

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
        return newHomePageResponse(request.name,home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst("a > img")?.attr("alt") ?: ""
        val url = post.selectFirst("a:nth-child(1)")?.attr("href") ?: ""
        val check = title.lowercase()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst("a > img")
                ?.attr("src")
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
        val year = "(?<=\\()\\d{4}(?=\\))".toRegex().find(title)?.value?.toIntOrNull()
        val image = doc.selectFirst("#my-video")?.attr("poster")
        val link = doc.selectFirst(".download-placement > a")?.attr("href")
        return newMovieLoadResponse(title, url, TvType.Movie, link) {
            this.posterUrl = image
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return true
    }

    private fun getSearchQuality(check: String): SearchQuality? {
        return when(check.lowercase()){
            in "webrip" -> SearchQuality.WebRip
            in "web-dl" -> SearchQuality.WebRip
            in "bluray" -> SearchQuality.BlueRay
            in "hdts" -> SearchQuality.HdCam
            in "dvd" -> SearchQuality.DVD
            in "cam" -> SearchQuality.Cam
            in "camrip" -> SearchQuality.CamRip
            in "hdcam" -> SearchQuality.HdCam
            in "hdtc" -> SearchQuality.HdCam
            in "hdrip" -> SearchQuality.HD
            in "hd" -> SearchQuality.HD
            in "hdtv" -> SearchQuality.HD
            in "rip" -> SearchQuality.CamRip
            in "telecine" -> SearchQuality.Telecine
            in "telesync" -> SearchQuality.Telesync
            else -> null
        }
    }
}