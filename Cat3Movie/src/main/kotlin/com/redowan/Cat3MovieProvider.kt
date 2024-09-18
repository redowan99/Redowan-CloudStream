package com.redowan

import com.lagradost.api.Log
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
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(Film1KProvider())
////    providerTester.testLoadLinks("https://checklinko.top/60382/")
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
//    providerTester.testLoadLinks("51783")
//}


class Cat3MovieProvider : MainAPI() {
    override var mainUrl = "https://cat3movie.org"
    override var name = "Cat3Movie"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.NSFW
    )
    override val mainPage = mainPageOf(
        "/action" to "Action",
        "/horror" to "Horror",
        "/comedy" to "Comedy",
        "/animation" to "Animation",
        "/war" to "War",
        "/sci-fi" to "Sci-Fi",
        "/romance" to "Romance",
        "/asian" to "Asian",
        "/drama" to "Drama",
        "/classic-porn" to "Classic Porn",
        "/asian-erotica" to "Asian Erotica",
        "/newage-erotica" to "Newage Erotica",
        "/classic-erotica" to "Classic Erotica",
        "/sex-education" to "Sex Education",
        "/incest" to "Incest",
        "/thriller" to "Thriller",
        "/crime" to "Crime",
        "/adventure" to "Adventure",
        "/western" to "Western",
        "/fantasy" to "Fantasy",
        "/newage-porn" to "Newage Porn",
    )
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}/page/$page", timeout = 60).document
        //Log.d("salman731 html",doc.html())
        val home = doc.select(".col-md-2\\.5").mapNotNull { toResult(it) }
        return newHomePageResponse(HomePageList(request.name,home,isHorizontalImages = false), true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select(".card-body h3").text()
        val url = post.select("a:nth-child(1)").attr("href")
        val imageUrl = "$mainUrl${post.select("a:nth-child(1) img").attr("src")}"
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search/$query", timeout = 60).document
        return doc.select(".col-md-2\\.5").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 60).document
        val title = doc.selectFirst("div.text h1")?.text()?.split(" ")?.distinct()?.joinToString(" ").toString() // To remove duplicate words from title
        val image = mainUrl + doc.select(".clean-block.clean-hero").attr("style").substringAfter("background: url(").substringBefore(") ")
        val id = "\"post_id\":(\\d*)".toRegex().find(doc.html())?.groups?.get(1)?.value
        val plot = doc.select("meta[name=\"description\"]").attr("content")
        val released = "Released: - (\\d*)".toRegex().find(doc.html())?.groups?.get(1)?.value
        return newMovieLoadResponse(title, url, TvType.Movie, id) {
            this.posterUrl = image
            this.plot = plot
            if (released != null) {
                this.year = released.toInt()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

            val serverAjaxURL = "https://cat3movie.org/wp-content/themes/hnzphim/app/load.php?episode_slug=full&server_id=1&post_id=$data"
            val doc = app.get(serverAjaxURL, timeout = 60, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).document
            var url = doc.select("iframe").attr("src").replace("\\", "")
            url = url.replace("\"","")
            val hlsDoc = app.get(url).document
            val m3u8Link = getSecondOccurrenceBetween(hlsDoc.html(),"file: \"","\",") // Regex was not working here
            callback.invoke(
                ExtractorLink(
                    "HlsVip",
                    "HlsVip",
                    m3u8Link.toString(),
                    "",
                    Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
            return true
    }

    private fun getSecondOccurrenceBetween(input: String, after: String, before: String): String? {
        // Get the part of the string after the first occurrence of `after`
        val firstOccurrence = input.substringAfter(after)

        // Get the part of the string after the second occurrence of `after`
        val secondOccurrence = firstOccurrence.substringAfter(after, "")

        // If there is no second occurrence, return null
        if (secondOccurrence.isEmpty()) return null

        // Get the substring before the `before` string in the second occurrence
        return secondOccurrence.substringBefore(before)
    }

}