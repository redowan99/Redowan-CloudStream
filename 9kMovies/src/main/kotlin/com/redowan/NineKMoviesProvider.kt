package com.redowan

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
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(NineKMoviesProvider())
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
//}

open class NineKMoviesProvider : MainAPI() {
    override var mainUrl = "https://9kmovies.ren/m"
    override var name = "9kMovies"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.NSFW)
    override val mainPage = mainPageOf(
        "" to "Latest Movies",
        "/category/18-movies/" to "18+ Movies",
        "/category/bengali-movies/" to "Bengali",
        "/category/dual-audio/" to "Dual Audio",
        "/category/hindi-dubbed/" to "Hindi Dubbed",
        "/category/hollywood-movies/" to "Hollywood",
        "/category/tv-shows/" to "WWE",
        "/category/original-web-series/" to "Web Series"
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}page/$page").document
        val home = doc.select(".thumb.col-md-2.col-sm-4.col-xs-6").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select("figure> figcaption> a").attr("href")
        val title = post.select("figure> figcaption> a").text()
        val imageUrl = post.select("figure img").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
            this.posterHeaders = mapOf("Referer" to " https://9kmovies.ren/")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search/$query").document
        val searchResponse = doc.select(".thumb.col-md-2.col-sm-4.col-xs-6")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select(".page-body h2").text()
        val imageUrl = doc.select(".page-body > h2 > img").attr("src")
        val story = doc.selectFirst(".page-body > p:nth-child(4)")?.html()
        val episodesData = mutableListOf<Episode>()
        doc.select("a.buttn.direct").forEach {
            episodesData.add(
                newEpisode(it.attr("href")){
                    this.name = it.text().replace(" Link 1", "")
                }
            )
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
            this.posterUrl = imageUrl
            this.plot = story?.trim()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.post(data).document
        doc.select(".col-sm-8.col-sm-offset-2.well.view-well a").forEach {
            val link = it.attr("href")//.replace("/v/","/e/")
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }
}