package com.redowan


import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
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
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.Requests
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element



class FzMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://fzmovies.net"
    override var name = "FzMovies"
    override val supportedTypes = setOf(
        TvType.Movie
    )


    // enable this when your provider has a main page
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true


    override val mainPage = mainPageOf(
        "imdb250.php?tag=imdb250&pg=" to "Top IMDB 250 movies",
        "oscars.php?category=Oscars Best Picture&pg=" to "Oscars Best Picture",
        "movieslist.php?catID=2&by=latest&pg=" to "Latest Hollywood Movies",
        "movieslist.php?catID=3&by=latest&pg=" to "Latest Hollywood Dubbed Movies",
        "movieslist.php?catID=1&by=latest&pg=" to "Latest Bollywood Movies",
        "movieslist.php?catID=1&by=rating&level=8&pg=" to "Bollywood Movies by Rating"

    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val requests = Requests()
        val doc = requests.get("$mainUrl/${request.data}$page").document
        val homeResponse = doc.select("div.mainbox")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = "$mainUrl/"+ post.selectXpath("table/tbody/tr/td[1]/a").attr("href")
        val title = post.selectXpath("table/tbody/tr/td[2]/span/a/small/b").text() + " " +
                post.selectXpath("table/tbody/tr/td[2]/span/small[1]").text()
        val check = post.selectXpath("table/tbody/tr/td[2]/span/small[2]").text().lowercase()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = mainUrl + post.selectXpath("table/tbody/tr/td[1]/a/img")
                .attr("src")
            this.quality = when {
                "webrip" in check -> SearchQuality.WebRip
                "web-dl" in check -> SearchQuality.WebRip
                "bluray" in check -> SearchQuality.BlueRay
                "hdts" in check -> SearchQuality.HdCam
                "dvd" in check -> SearchQuality.DVD
                "cam" in check -> SearchQuality.Cam
                "camrip" in check -> SearchQuality.CamRip
                "hdcam" in check -> SearchQuality.HdCam
                "hdtc" in check -> SearchQuality.HdCam
                "hdrip" in check -> SearchQuality.HD
                "hd" in check -> SearchQuality.HD
                "hdtv" in check -> SearchQuality.HD
                "rip" in check -> SearchQuality.CamRip
                else -> null
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)


    override suspend fun search(query: String): List<SearchResponse> {
        val client = OkHttpClient()
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val body = "searchname=$query&Search=Search&searchby=Name&category=All".toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://fzmovies.net/csearch.php")
            .post(body)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
        val response = client.newCall(request).execute()
        val doc = Jsoup.parse(response.body.string(), "UTF-8")
        val searchResponse = doc.select("div.mainbox")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val requests = Requests()
        val doc = requests.get(url).document
        val year = doc.select(".moviedesc > textcolor1:nth-child(9) > a:nth-child(1)").text().toIntOrNull()
        val title = doc.select(".moviename > span:nth-child(1)").text() +" "+ year
        return newMovieLoadResponse(title, url, TvType.Movie,url) {
            this.posterUrl = mainUrl + doc.select(".moviedesc > span:nth-child(1) > img:nth-child(1)")
                .attr("src")
            this.year = year
            this.plot = doc.select(".moviedesc > span:nth-child(5) > textcolor1:nth-child(1)").text()
            this.duration = doc.select(".moviedesc > textcolor2:nth-child(7)")
                .text().substringBefore(" ").toIntOrNull()?.times(60)
            this.rating = doc.select(".moviedesc > textcolor11:nth-child(27)")
                .text().toFloatOrNull()?.times(1000.0)?.toInt()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val requests = Requests()
        val html = requests.get(data)
        val doc = html.document
        val cookie = html.cookies
        doc.select(".moviesfiles").forEach{item ->
            val quality = getIndexQuality(item.select("#downloadoptionslink2").text())
            val qualityUrl = "$mainUrl/" + item.select("#downloadoptionslink2").attr("href")
            var newDoc = requests.get(qualityUrl, cookies = cookie).document
            val sudoDlUrl = "$mainUrl/" + newDoc.select("#downloadlink").attr("href")
            newDoc = requests.get(sudoDlUrl, cookies = cookie).document
            var server = 0
            newDoc.select("ul.downloadlinks:nth-child(5) > li").forEach{newItem ->
                val downloadUrl = newItem.select("p:nth-child(3) > input:nth-child(1)").attr("value")
                server++
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        "Server$server",
                        downloadUrl,
                        this.mainUrl,
                        quality,
                    )
                )

            }


        }

        return true
    }
    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}