package com.redowan

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
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(NineKMoviesProvider())
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
//}

open class NineKMoviesProvider : MainAPI() {
    override var mainUrl = "https://9kmovies.claims/m"
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

        // This is necessary for load more posts on homepage
        val doc = if (request.data == "" && page == 1) {
            app.get(mainUrl).document
        } else if (request.data == "" && page > 1) {
            app.get("$mainUrl/page/$page").document
        } else {
            app.get("$mainUrl${request.data}page/$page").document
        }
        //Log.d("salman731 element size",doc.select(".thumb.col-md-2.col-sm-4.col-xs-6").size.toString())
        val home = doc.select(".thumb.col-md-2.col-sm-4.col-xs-6").mapNotNull { toResult(it) }
        //Log.d("salman731 total size",home.toString().length.toString())
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select("figure figcaption a").attr("href")
        val title = post.select("figure figcaption a p").text()
        val imageUrl = post.select("figure img").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
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
        val info = doc.select(".page-body p:nth-of-type(1)").text()
        val link = doc.select("a.buttn.red").joinToString(";") { it.attr("href") }
        val story =
            ("(?<=Storyline,).*|(?<=Story : ).*|(?<=Storyline : ).*|(?<=Description : ).*|(?<=Description,).*(?<=Story,).*").toRegex()
                .find(info)?.value
        return newMovieLoadResponse(title, url, TvType.Movie, link) {
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
        data.split(";").forEach { url ->
            val doc = app.post(url).document
            val links = doc.select(".col-sm-8.col-sm-offset-2.well.view-well a")
            links.forEach {
                val link = it.attr("href").replace("/v/","/e/")
                loadExtractor(link, subtitleCallback, callback)
            }
        }
        return true
    }
}