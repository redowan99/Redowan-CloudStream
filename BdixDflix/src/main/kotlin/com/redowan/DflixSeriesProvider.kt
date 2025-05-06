package com.redowan

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.FormBody
import org.jsoup.nodes.Element


//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(DflixSeriesProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "game of",verbose = true)
//    providerTester.testLoad("https://dflix.discoveryftp.net/s/view/5967")
//}


class DflixSeriesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://dflix.discoveryftp.net"
    override var name = "(BDIX) Dflix Series"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime,
        TvType.Documentary,
        TvType.Cartoon
    )
    override val mainPage = mainPageOf(
        "category/Foreign" to "English",
        "category/Bangla" to "Bangla",
        "category/Hindi" to "Hindi",
        "category/South" to "South",
        "category/Animation" to "Animation",
        "category/Dubbed" to "Dubbed"
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
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        login()
        val doc = app.get("$mainUrl/s/${request.data}/$page", cookies = loginCookie!!).document
        val homeResponse = doc.select("div.col-xl-4")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = mainUrl + (post.selectFirst("div > a:nth-child(1)")?.attr("href") ?: "")
        val title = post.select("div.fcard > div:nth-child(2) > div:nth-child(1)").text()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst("img:nth-child(1)")?.attr("src")
        }
    }

    private fun toSearchResult(post: Element): SearchResponse {
        val url = mainUrl + (post.selectFirst("a")?.attr("href") ?: "")
        val title = post.select("div.searchtitle").text()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst("img:nth-child(1)")?.attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        val requestBody = FormBody.Builder()
            .add("term", query)
            .add("types","s")
            .build()
        val doc = app.post("$mainUrl/search", cookies = loginCookie!!, requestBody = requestBody).document
        val searchResponse = doc.select("div.moviesearchiteam > a")
        return searchResponse.mapNotNull { post ->
            toSearchResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        login()
        val doc = app.get(url, cookies = loginCookie!!).document
        val title = doc.select(".movie-detail-content-test > h3:nth-child(1)").text()
        val img = doc.select(".movie-detail-banner > img:nth-child(1)").attr("src")

        val episodesData = mutableListOf<Episode>()
        var seasonNum = 0
        doc.select("table.table:nth-child(1) > tbody:nth-child(1) > tr a").reversed()
            .forEach { season ->
                seasonNum++
                extractedSeason(seasonNum, season, episodesData)
            }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
            this.posterUrl = img
            this.plot = doc.select(".storyline").text()
            this.tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
            this.actors = doc.select("div.col-lg-2").map { actor(it) }
        }
    }

    private suspend fun extractedSeason(
        seasonNum: Int,
        season: Element?,
        episodesData: MutableList<Episode>
    ) {
        var episodeNum = 0

        val seasonUrl = mainUrl + season?.attr("href")
        val seasonDoc = app.get(seasonUrl, cookies = loginCookie!!).document
        seasonDoc.select("div.container:nth-child(6) > div").forEach { episode ->
            val episodeName = episode.selectFirst("h4")?.childNode(0).toString()
            val episodeImage =
                episode.selectFirst("div")?.attr("style")?.let { extractBGImageUrl(it) }
            val episodeDescription = episode.selectFirst("div.season_overview")?.text()
            val episodeLink = episode.select("div.mt-2 >h5>a").attr("href")
            episodeNum++
            episodesData.add(
                newEpisode(episodeLink) {
                    this.name = episodeName
                    this.posterUrl = episodeImage
                    this.season = seasonNum
                    this.episode = episodeNum
                    this.description = episodeDescription
                }
            )
        }
    }

    private val bgImageRegex = Regex("""url\(['"]?(.*?)['"]?\)""")
    private fun extractBGImageUrl(text: String): String? {
        val matchResult = bgImageRegex.find(text)
        return matchResult?.groupValues?.get(1)
    }

    private fun actor(post: Element): ActorData {
        val html = post.select("div.col-lg-2 > a:nth-child(1) > img:nth-child(1)")
        val img = html.attr("src")
        val name = html.attr("alt")
        return ActorData(
            actor = Actor(
                name, img
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
}