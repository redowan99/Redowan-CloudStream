package com.redowan


import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink


//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(FzMoviesProvider())
////    providerTester.testAll()
//    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
//
//}


class FzMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://fzmovies.net"
    override var name = "FzMovies"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.AnimeMovie
    )


    // enable this when your provider has a main page
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "imdb250.php?tag=imdb250&pg=" to "Top IMDB 250 movies",
        "oscars.php?category=Oscars Best Picture&pg=" to "Oscars Best Picture",
        "movieslist.php?catID=2&by=latest&pg=" to "Latest Hollywood Movies",
        "movieslist.php?catID=3&by=latest&pg=" to "Latest Hollywood Dubbed Movies",
        "movieslist.php?catID=1&by=latest&pg=" to "Latest Bollywood Movies",
        "movieslist.php?catID=1&by=rating&level=8&pg=" to "Bollywood Movies by Rating",
        "movieslist.php?catID=1&by=downloads&pg=" to "Most Downloaded Bollywood Movies"

    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/${request.data}$page").document
        val homeResponse = doc.select("div.mainbox > table")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = "$mainUrl/"+ post.selectXpath("tbody/tr/td[1]/a").attr("href")
        val title = post.selectXpath("tbody/tr/td[2]/span/a/small/b").text() + " " +
                post.selectXpath("tbody/tr/td[2]/span/small[1]").text()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = mainUrl + post.selectXpath("tbody/tr/td[1]/a/img")
                .attr("src")
            this.quality = getSearchQuality(post.selectXpath("tbody/tr/td[2]/span/small[2]").text())
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val client = OkHttpClient()
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val body = "searchname=$query&Search=Search&searchby=Name&category=All".toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$mainUrl/csearch.php")
            .post(body)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
        val response = client.newCall(request).execute()
        val doc = Jsoup.parse(response.body.string(), "UTF-8")
        val searchResponse = doc.select("div.mainbox > table")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val year = doc.select(".moviedesc > textcolor1:nth-child(9) > a:nth-child(1)").text().toIntOrNull()
        val title = doc.select(".moviename > span:nth-child(1)").text() +" "+ year
        var rating: Int? = null
        doc.select("textcolor11").forEach{
            if(it.text().matches("\\b\\d.\\d\\b".toRegex())) rating = it.text().toRatingInt()
        }
        return newMovieLoadResponse(title, url, TvType.Movie,url) {
            this.posterUrl = mainUrl + doc.select(".moviedesc > span:nth-child(1) > img:nth-child(1)")
                .attr("src")
            this.year = year
            this.plot = doc.select(".moviedesc > span:nth-child(5) > textcolor1:nth-child(1)").text()
            this.duration = doc.select(".moviedesc > textcolor2:nth-child(7)")
                .text().substringBefore(" ").toIntOrNull()
            this.rating = rating
            this.tags = doc.select("[itemprop=genre]").map { it.text() }
            addTrailer(doc.select(".fieldset-auto-width > iframe:nth-child(2)").attr("src"))
            addImdbUrl(doc.select(".moviedesc > textcolor2:nth-child(162) > span:nth-child(1)").text())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data)
        val doc = html.document
        val cookie = html.cookies
        doc.select(".moviesfiles").forEach{item ->
            val quality = getVideoQuality(item.select("#downloadoptionslink2").text())
            val qualityUrl = "$mainUrl/" + item.select("#downloadoptionslink2").attr("href")
            var newDoc = app.get(qualityUrl, cookies = cookie).document
            val sudoDlUrl = "$mainUrl/" + newDoc.select("#downloadlink").attr("href")
            newDoc = app.get(sudoDlUrl, cookies = cookie).document
            var server = 0
            newDoc.select("ul.downloadlinks:nth-child(5) > li").forEach{newItem ->
                val downloadUrl = newItem.select("p:nth-child(3) > input:nth-child(1)").attr("value")
                server++
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        "Server$server",
                        downloadUrl
                    )
                )
            }
        }
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
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains("hdtc") -> SearchQuality.HdCam
                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hd") || lowercaseCheck.contains("hdtv") -> SearchQuality.HD
                lowercaseCheck.contains("telesync") -> SearchQuality.Telesync
                lowercaseCheck.contains("telecine") -> SearchQuality.Telecine
                else -> null
            }
        }
        return null
    }

    private fun getVideoQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}