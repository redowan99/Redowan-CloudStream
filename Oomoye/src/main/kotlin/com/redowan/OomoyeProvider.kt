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
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.Requests
import org.jsoup.nodes.Element

import com.lagradost.cloudstreamtest.ProviderTester
suspend fun main() {
    val providerTester = ProviderTester(OomoyeProvider())
    //providerTester.testLoad("https://www.oomoye.co/movie/44186/Auron-mein-kahan-dum-tha-2024.html")
    //providerTester.testMainPage()
    //providerTester.testAll()
    providerTester.testLoadLinks("https://www.oomoye.co/movie/44270/Ghudchadi-2024.html")
}

class OomoyeProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.oomoye.co"
    override var name = "Oomoye"
    //override var lang = "bn"


    // enable this when your provider has a main page
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false


    override val mainPage = mainPageOf(
        "Bollywood-movies" to "Bollywood Movies",
        "South-indian-hindi-movies" to "South Indian Hindi Movies",
        "Hollywood-hindi-dubbed-movies" to "Hollywood Hindi Movies",
        "Hollywood-english-movies" to "Hollywood English Movies",
        "Hollywood-cartoon-movies" to "Hollywood Cartoon Movies",
        "Punjabi-movies" to "Punjabi Movies",
        "Tamil-movies" to "Tamil Movies",
        "Telugu-movies" to "Telugu Movies",
        "Malayalam-movies" to "Malayalam Movies",
        "Marathi-movies" to "Marathi Movies",
        "Bengali-movies" to "Bengali Movies",
        "Kannada-movies" to "Kannada Movies",
        "Gujarati-movies" to "Gujarati Movies"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val requests = Requests()
        val doc = requests.get("$mainUrl/category/${request.data}.html?page=$page").document
        val homeResponse = doc.getElementsByClass("catRow")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }


    private fun toResult(post: Element): SearchResponse {
        val url = post.select("a").attr("href")
        val imageIdPattern = "\\d+".toRegex()
        val imageId = imageIdPattern.find(url)?.value
        val title = post.text().replaceAfter(")", "")
        return newTvSeriesSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = "$mainUrl/cover/$imageId.png"
            val check = post.select("font[color=green]").text().lowercase()
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
                "telecine" in check -> SearchQuality.Telecine
                "telesync" in check -> SearchQuality.Telesync
                else -> null
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val requests = Requests()
        val doc = requests.get(url).document
        val title = doc.selectXpath("/html/body/div[7]/div").text()
        val year = "(?<=\\()\\d{4}(?=\\))".toRegex().find(title)?.value?.toIntOrNull()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = doc.getElementsByClass("posterss").attr("src")
            this.year = year
            this.plot = doc.getElementsByClass("description").text()
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val requests = Requests()
        val doc = requests.get(data).document
        doc.select("a").forEach { item ->
            if ("https://www.oomoye.co/server/" in item.attr("href")) {
                var quality = "\\d{3,4}(?=p)".toRegex().find(item.text())?.value?.toIntOrNull()
                if (quality == null) quality = 720
                val links = requests.get(item.attr("href").replace("/server/", "/files/"))
                    .document.select("div.fastdl:nth-child(14) > a:nth-child(1)").attr("href")
                if (links.isNotEmpty())
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "pixeldrain - ${quality}P",
                            url = links,
                            data,
                            quality = quality,
                            isM3u8 = false,
                            isDash = false
                        )
                    )
            }
        }
        return true
    }
}