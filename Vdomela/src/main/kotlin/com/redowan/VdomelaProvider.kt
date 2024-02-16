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
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

suspend fun main() {
    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(VdomelaProvider())
    providerTester.testAll()
    //val responses =
    //VdomelaProvider().test()
    //println(responses)
}

class VdomelaProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "http://vdomela.com/"
    override var name = "Vdomela"
    override val supportedTypes = setOf(
        TvType.Movie
    )
    override var lang = "bn"


    // enable this when your provider has a main page
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true


    override val mainPage = mainPageOf(
        "mainpage" to "Movies",
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val doc = app.get(mainUrl).document
        val home = doc.select(".browse-content").mapNotNull { movie ->
            newMovieSearchResponse(
                movie.select(".browseTitleLink a").text(),
                "$mainUrl${movie.select(".browse-img a").attr("href")}", TvType.Movie
            ) {
                this.posterUrl = "$mainUrl${movie.select(".browse-img img").attr("src")}"
            }
        }
        return newHomePageResponse(request.name, home)
    }


    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://vdomela.com/search.php")
            .post("field=$query".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
        val doc = Jsoup.parse(client.newCall(request).execute().body.string())
        return doc.select("a[href^='view.php']").mapNotNull { movie ->
            newMovieSearchResponse(
                movie.select("span.name").text(),
                "$mainUrl${movie.attr("href")}", TvType.Movie
            ) {
                this.posterUrl = "$mainUrl${movie.select("img").attr("src")}"
            }
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val movie = doc.selectFirst(".movie-content")

        return newMovieLoadResponse(
            movie.selectFirst(".movie_name").text(),
            url, TvType.Movie,
            "$mainUrl${movie.selectFirst("a[href^='download_counter.php']")?.attr("href")}")
        {
            this.posterUrl = "$mainUrl${movie.selectFirst(".boximg.posthov")?.attr("src")}"
            this.plot = movie.select("[class=movie-info] p:contains(Synopsis)").text().replace(
                "Synopsis : ","")
            this.rating = movie.select("[class=movie-info] p:contains(Rating)").text().replace(
                "Rating : ","").toRatingInt()
        }
    }    

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        callback.invoke(
            ExtractorLink(
            mainUrl,
            this.name,
            url = data,
            mainUrl,
            quality = 1080,
            isM3u8 = false,
            isDash = false
            )
        )
        return true
    }
}