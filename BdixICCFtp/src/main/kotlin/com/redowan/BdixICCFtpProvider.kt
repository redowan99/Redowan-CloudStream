package com.redowan


import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(BdixICCFtpProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////   providerTester.testSearch(query = "dragon", verbose = true)
//    providerTester.testLoad("http://10.16.100.244/player.php?play=40575")
//}

open class BdixICCFtpProvider : MainAPI() {
    override var mainUrl = "http://10.16.100.244/"
    override var name = "(BDIX) ICC Ftp"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.Cartoon,
        TvType.AsianDrama,
        TvType.Documentary,
        TvType.OVA,
        TvType.Others
    )

    override val mainPage = mainPageOf(
        "index.php?category=0" to "Latest",
        "index.php?category=59" to "Bangla Movies",
        "index.php?category=2" to "Hindi Movies",
        "index.php?category=19" to "English Movies",
        "index.php?category=43" to "Dual Audio",
        "index.php?category=32" to "South Movies",
        "index.php?category=33" to "Animated",
        "index.php?category=36" to "English Series",
        "index.php?category=37" to "Hindi Series",
        "index.php?category=41" to "Documentary"
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}").document
        val homeResponse = doc.select("div.post-wrapper > a")
        val home = homeResponse.mapNotNull { post ->
            getPostResult(post)
        }
        return newHomePageResponse(request.name, home, false)
    }

    private fun getPostResult(post: Element): SearchResponse {
        val name = post.select("img").attr("alt")
        val url = mainUrl + post.select("a").attr("href")
        val image = mainUrl + post.select("img").attr("src")
        return newMovieSearchResponse(name, url, TvType.Movie) {
            this.posterUrl = image
        }
    }

    private fun getSearchResult(post: SearchJsonItem): SearchResponse {
        val name = post.name.toString()
        val url = "${mainUrl}player.php?play=${post.id}"
        val image = "${mainUrl}files/${post.image}"
        return newMovieSearchResponse(name, url, TvType.Movie) {
            this.posterUrl = image
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val body = "cSearch=$query".toRequestBody(mediaType)
        val doc = app.post("$mainUrl/command.php", requestBody = body).text
        val json = AppUtils.parseJson<SearchJson>(doc)
        return json.mapNotNull {getSearchResult(it)}
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val table = doc.select(".table > tbody:nth-child(1)")
        val title = table.select("tr:nth-child(1)").text()
        val year = table.select("tr:nth-child(2) > td:nth-child(2)").text().toIntOrNull()
        val genre = table.select("tr:nth-child(5) > td:nth-child(2)").text().split(",")
        val plot = table.select("tr:nth-child(12) > td:nth-child(2)").text()
        val duration = table.select("tr:nth-child(4) > td:nth-child(2)").text()
        val trailer = doc.selectFirst(".pull-left")?.attr("data-thevideo")
        val trailerData = mutableListOf<TrailerData>()
        if(!trailer.isNullOrEmpty()) {
            trailerData.add(
                TrailerData(
                    extractorUrl = trailer.toString(),
                    raw = false,
                    referer = mainUrl
                )
            )
        }
        val image = mainUrl + doc.selectFirst(".col-md-4 > img")?.attr("src")
        val downloadEpisode = doc.select(".btn-group > ul > li")
        if(downloadEpisode.isEmpty()){
            val link = doc.selectFirst("a.btn")?.attr("href")
            return newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = image
                this.year = year
                this.plot = plot
                this.tags = genre
                this.duration = getDurationFromString(duration)
                this.trailers = trailerData
            }
        }
        else{
            val episodesData = mutableListOf<Episode>()
            downloadEpisode.forEach {
                val link = it.select("a").attr("href")
                val name = it.select("a").text()
                val span = it.select("span").text()
                episodesData.add(
                    newEpisode(link){
                        this.name = name.replace(span,"")
                    }
                )
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.posterUrl = image
                this.year = year
                this.plot = plot
                this.tags = genre
                this.duration = getDurationFromString(duration)
                this.trailers = trailerData
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data,
                this.name,
                url = data
            )
        )
        return true
    }

    class SearchJson : ArrayList<SearchJsonItem>()

    data class SearchJsonItem(
        val id: String?, // 367
        val image: String?, // 2_guns.jpg
        val name: String?, // 2 Guns
        val type: String? // 1
    )
}
