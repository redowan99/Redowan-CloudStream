package com.redowan

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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
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
    override val supportedTypes = setOf(
        TvType.Others
    )
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

    private val LinkRegex = Regex("href=\"([^\"]*)\"")
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60).document
        val title = doc.selectFirst("h1.entry-title")?.text() ?: ""
        val image = doc.selectFirst(".vlog-cover > img:nth-child(1)")?.attr("src")
        val linkHtml = doc.selectFirst(".frc-cdt-para")?.html() ?: ""
        val link = LinkRegex.find(linkHtml)?.groups?.get(1)?.value ?: ""
        val plot = doc.selectFirst(".frc_first_para_match_dt")?.html() ?: ""
        return newMovieLoadResponse(title, url, TvType.Movie, link) {
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
        loadExtractor(data,subtitleCallback,callback)
        return true
    }
}