package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
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
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(FootReplaysProvider())
////    providerTester.testLoadLinks("https://checklinko.top/60382/")
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "rome", verbose = true)
////    providerTester.testLoad("https://www.footreplays.com/england/premier-league/chelsea-vs-liverpool-04-05-2025/")
//}

class FootReplaysProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.footreplays.com"
    override var name = "FootReplays"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Others)
    override val mainPage = mainPageOf(
        "/" to "Latest Match",
        "//" to "Most Viewed",
        "england" to "England",
        "germany" to "Germany",
        "france" to "France",
        "italy" to "Italy",
        "spain" to "Spain",
        "uefa" to "Uefa",
        "international" to "International"
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val home = when (request.data) {
            "/" -> {
                val doc = app.get(mainUrl).document
                doc.select(".p-wrap.p-overlay.p-overlay-flex a.p-flink")
                    .mapNotNull { toResult(it) }
            }

            "//" -> {
                val doc = app.get(mainUrl).document
                doc.select(".p-wrap.p-small.p-list-small-2 a.p-flink")
                    .mapNotNull { toResult(it) }
            }

            else -> {
                val doc = app.get("$mainUrl/${request.data}/page/$page/").document
                doc.select(".p-wrap.p-grid.p-grid-1 a.p-flink").mapNotNull { toResult(it) }
            }
        }
        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = true), hasNext = false
        )
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.selectFirst("a")?.attr("href") ?: ""
        val title = post.selectFirst("a")?.attr("title") ?: ""
        val imageUrl = post.selectFirst("img")?.attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val searchResponse = doc.select(".p-wrap.p-grid.p-grid-small-1 a.p-flink")
        return searchResponse.mapNotNull { toResult(it) }
    }

    private val regex = Regex("loadVideo\\('([^']+)")
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.s-title")?.text() ?: ""
        val imageUrl = doc.selectFirst("div.s-feat >img")?.attr("src")
        val plot = doc.selectFirst(".entry-content > p")?.text()
        val episodesData = mutableListOf<Episode>()
        var episodeNum = 0
        doc.select("table.video-table tbody > tr").forEach { it ->
            episodeNum++
            val episodeTitle = it.select("td:lt(3)").mapNotNull { it.text() }.joinToString(" ")
            val dataHtml = it.selectFirst("td:nth-child(4) > a:nth-child(1)")?.attr("onclick")
            val data = dataHtml?.let { it1 -> regex.find(it1)?.groupValues?.get(1) }
            episodesData.add(newEpisode(data?.let { it1 ->
                newEpisode(it1) {
                    this.name = episodeTitle
                    this.episode = episodeNum
                }
            }))

        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
            this.posterUrl = imageUrl
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
}