package com.redowan


import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
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
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element


//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(DflixMoviesProvider())
////    providerTester.testAll()
//    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("https://dflix.discoveryftp.net/m/view/34449")
//}


class DflixMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://dflix.discoveryftp.net"
    override var name = "(BDIX) Dflix Movies"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.AnimeMovie
    )
    override val mainPage = mainPageOf(
        "category/Bangla" to "Bangla",
        "category/English" to "English",
        "category/Hindi" to "Hindi",
        "category/Tamil" to "Tamil",
        "category/Animation" to "Animation",
        "category/Others" to "Others"
    )

    private var loginCookie: Map<String, String>? = null
    private suspend fun login() {
        if (loginCookie?.size != 2) {
            val client =
                app.get("https://dflix.discoveryftp.net/login/demo", allowRedirects = false)
            loginCookie = client.cookies
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        login()
        val doc = app.get("$mainUrl/m/${request.data}/$page", cookies = loginCookie!!).document
        val homeResponse = doc.select("div.card")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = mainUrl + post.select("div.card > a:nth-child(1)").attr("href")
        val title = post.select("div.card > div:nth-child(2) > h3:nth-child(1)").text() + ' ' +
                post.select("div.feedback > span:nth-child(1)").text()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst("div.poster > img:nth-child(1)")?.attr("src")
            val check = post.select("div.card > a:nth-child(1) > span:nth-child(1)").text()
            this.quality = getSearchQuality(check)
            addDubStatus(
                dubExist = when {
                    "DUAL" in check -> true
                    else -> false
                },
                subExist = false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        val doc = app.get("$mainUrl/m/find/$query", cookies = loginCookie!!).document
        val searchResponse = doc.select("div.card:not(:has(div.poster.disable))")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        login()
        val doc = app.get(url, cookies = loginCookie!!).document
        val title = doc.select(".movie-detail-content > h3:nth-child(1)").text()
        val dataUrl = doc.select("div.col-md-12:nth-child(3) > div:nth-child(1) > a:nth-child(1)")
            .attr("href")
        val size = doc.select(".badge.badge-fill").text()
        val img = doc.select(".movie-detail-banner > img:nth-child(1)").attr("src")
        return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
            this.posterUrl = img
            this.plot = "<b>$size</b><br><br>" + doc.select(".storyline").text()
            this.tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
            this.actors = doc.select("div.col-lg-2").map { actor(it) }
            this.recommendations = doc.select("div.badge-outline > a").map { qualityRecommendations(it,title,img) }
        }
    }
    private fun qualityRecommendations(post: Element, title:String, imageLink:String): SearchResponse{
        val movieName = title +" "+ post.text()
        val movieUrl = mainUrl + post.attr("href")
        return newMovieSearchResponse(movieName,movieUrl,TvType.Movie) {
            this.posterUrl = imageLink
        }
    }

    private fun actor(post: Element): ActorData {
        val html = post.select("div.col-lg-2 > a:nth-child(1) > img:nth-child(1)")
        val img = html.attr("src")
        val name = html.attr("alt")
        return ActorData(
            actor = Actor(
                name,
                img
            ), roleString = post.select("div.col-lg-2 > p.text-center.text-white").text()
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data,
                this.name,
                url = data,
                type = ExtractorLinkType.VIDEO
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
                lowercaseCheck.contains("4k") -> SearchQuality.FourK
                lowercaseCheck.contains("web-r") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("br") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains(
                    "hdtc"
                ) -> SearchQuality.HdCam

                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("hd") || lowercaseCheck.contains("1080p") -> SearchQuality.HD
                else -> null
            }
        }
        return null
    }
}