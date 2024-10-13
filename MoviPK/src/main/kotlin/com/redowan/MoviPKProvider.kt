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
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(MoviPKProvider())
////    providerTester.testLoadLinks("https://checklinko.top/60382/")
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
//    providerTester.testLoad("https://www.movi.pk/dukaan-2024-hindi-v1/")
////    providerTester.testLoadLinks("51783")
//}


class MoviPKProvider : MainAPI() {
    override var mainUrl = "https://www.movi.pk"
    override var name = "MoviPK"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )
    override val mainPage = mainPageOf(
        "/genre/featured/" to "Featured",
        "/movies/" to "Latest",
        "/genre/indian-movies/" to "Indian",
        "/genre/hollywood-hindi-dubbed-movies/" to "Hollywood Hindi Dubbed",
        "/genre/south-indian-hindi-dubbed-movies/" to "South Indian Hindi",
        "/genre/tv-series/" to "Web Series [Indian]",
        "/genre/web-series-hindi-dubbed/" to "Web Series [Dub]",
        "/genre/hollywood-movies/" to "Hollywood",
        "/genre/anime/" to "Anime"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            "$mainUrl${request.data}page/$page",
            cacheTime = 60,
            allowRedirects = true,
            timeout = 30
        ).document
        val home = doc.select(".ml-mask.jt").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select("a:nth-child(1)").attr("oldtitle")
        val url = post.select("a:nth-child(1)").attr("href")
        val check = title.lowercase()
        val quality = post.select("a:nth-child(1) > span:nth-child(1)").text()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst(".lazy.thumb.mli-thumb")
                ?.attr("data-original")
            this.quality = getSearchQuality(quality)
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
        val doc = app.get("$mainUrl/?s=$query", cacheTime = 60, timeout = 30).document
        return doc.select(".ml-mask.jt").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(
            url,
            cacheTime = 60,
            timeout = 30,
            headers = mapOf(
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
            )).document
        val title = doc.select(".mvic-desc > h3:nth-child(1)").text()
        val image = doc.selectFirst(".hidden.lazyload")
            ?.attr("data-src")
        val plot = doc.select("p.f-desc:nth-child(1)").text().replace(title, "")
        var dataUrl = ""
        doc.select("div.btn-group:nth-child(2) a").forEach {
            val link = it.attr("href")
            if (link.contains("https://listeamed.net")) dataUrl = link
            else if (link.contains("https://bembed.net")) dataUrl =
                link.replace("https://bembed.net", "https://listeamed.net")
        }
        dataUrl = dataUrl.replace("/v/", "/e/")
        return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
            this.posterUrl = image
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data, subtitleCallback, callback)
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
}