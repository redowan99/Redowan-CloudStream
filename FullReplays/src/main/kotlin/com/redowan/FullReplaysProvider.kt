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
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(FullReplaysProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "england",verbose = true)
//    providerTester.testLoad("https://www.fullreplays.com/usa/major-league-soccer/inter-miami-vs-new-england-revolution-19-oct-2024/")
//}

class FullReplaysProvider : MainAPI() {
    override var mainUrl = "https://www.fullreplays.com"
    override var name = "FullReplays"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val mainPage = mainPageOf(
        "/" to "Latest",
        "/england/" to "England",
        "/spain/" to "Spain",
        "/uefa/" to "UEFA",
        "/italy/" to "Italy",
        "/germany/" to "Germany",
        "/france/" to "France",
        "/shows/" to "Shows",
        "/friendly/" to "Friendly"
    )
    override val supportedTypes = setOf(
        TvType.Others
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}page/$page/", cacheTime = 60).document
        val home = doc.select("article.vlog-lay-g").mapNotNull {toResult(it)}
        return newHomePageResponse(HomePageList(request.name,home,isHorizontalImages = true), true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst(".entry-title > a:nth-child(1)")?.text() ?: ""
        val url = post.selectFirst(".entry-title > a:nth-child(1)")?.attr("href") ?: ""
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst(".entry-image > a:nth-child(1) > img:nth-child(1)")
                ?.attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", cacheTime = 60).document
        return doc.select("article.vlog-lay-g").mapNotNull {toResult(it)}
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60).document
        val title = doc.selectFirst("h1.entry-title")?.text() ?: ""
        val image = doc.selectFirst(".vlog-cover > img:nth-child(1)")?.attr("src")
        val plot = doc.selectFirst(".frc_first_para_match_dt")?.html() ?: ""
        val episodesData = mutableListOf<Episode>()
        val episodeLinksMap = mutableMapOf<String, StringBuilder>()
        doc.select("ul.frc-vid-sources-list").map { element ->
            element.select(".frc-vid-sources-list > li > span:nth-child(1)").forEach { link ->
                val episodeName = link.text()
                if (episodeName.isNotEmpty()) {
                    episodeLinksMap.getOrPut(episodeName) { StringBuilder() }.apply {
                        append(link.attr("data-sc")).append(" ; ")
                    }.toString()
                }
            }
        }
        episodeLinksMap.map { (episodeName, episodeLinks) ->
            episodesData.add(
                newEpisode(episodeLinks.toString()){
                    this.name = episodeName
                }
            )
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
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
        data.split(" ; ").forEach {loadExtractor(it,subtitleCallback,callback)}
        return true
    }
}