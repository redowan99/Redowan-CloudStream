package com.redowan


import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
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



class SkymoviesHDProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://skymovieshd.diy"
    override var name = "SkymoviesHD"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.NSFW
    )


    // enable this when your provider has a main page
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true


    override val mainPage = mainPageOf(
        "Bollywood-Movies" to "Bollywood Movies",
        "South-Indian-Hindi-Dubbed-Movies" to "South Indian Hindi Dubbed Movies",
        "Bengali-Movies" to "Bengali Movies",
        "Pakistani-Movies" to "Pakistani Movies",
        "Hollywood-English-Movies" to "Hollywood English Movies",
        "Hollywood-Hindi-Dubbed-Movies" to "Hollywood Hindi Dubbed Movies",
        "Tamil-Movies" to "Tamil Movies",
        "Telugu-Movies" to "Telugu Movies",
        "Punjabi-Movies" to "Punjabi Movies",
        "Bhojpuri-Movies" to "Bhojpuri Movies",
        "Bangladeshi-Movies" to "Bangladeshi Movies",
        "Marathi-Movies" to "Marathi Movies",
        "Kannada-Movies" to "Kannada Movies",
        "WWE-TV-Shows" to "WWE TV Shows",
        "TV-Serial-Episodes" to "TV Serial Episodes",
        "Gujrati-Movies-" to "Gujrati Movies",
        "Malayalam-Movies" to "Malayalam Movies",
        "Korean-and-China-Movies" to "Korean and China Movies",
        "Movies-Trailer" to "Movies Trailer",
        "Hot-Short-Film" to "Hot Short Film",
        "All-Web-Series" to "All Web Series",
        "Regional-Movies" to "Regional Movies"
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val requests = Requests()
        val doc = requests.get("$mainUrl/category/${request.data}/$page.html").document
        val homeResponse = doc.select("div.L")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, false)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = mainUrl + post.select("a").attr("href")
        //val imageIdPattern = "\\d+".toRegex()
        //val imageId = imageIdPattern.find(url)?.value

        var title = post.text()
        val size = "\\[\\d(.*?)B]".toRegex().find(title)?.value
        val newTitle = size?.let { post.text().replace(it, "") }
        title = "$size $newTitle"
        return newTvSeriesSearchResponse(title, url, TvType.Movie) {
            //this.posterUrl = "$mainUrl/cover/$imageId.png"
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)


    override suspend fun search(query: String): List<SearchResponse> {
        val requests = Requests()
        val doc = requests.get("$mainUrl/search.php?search=$query&cat=All").document
        val searchResponse = doc.select("div.L")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val requests = Requests()
        val doc = requests.get(url).document
        val title = doc.select("div.Robiul").first().text()
        val year = "(?<=\\()\\d{4}(?=\\))".toRegex().find(title)?.value?.toIntOrNull()
        return newMovieLoadResponse(title, url, TvType.Movie,doc.select(".Bolly > a:nth-child(3)").attr("href")) {
            this.posterUrl = doc.select(".movielist > img:nth-child(1)").attr("src")
            this.year = year
            this.plot = doc.select("div.Let:nth-child(8)").text()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val requests = Requests()
        var doc = requests.get(data).document
        val url = doc.getElementsContainingText("https://fastxyz.in/").last()?.select("a")?.attr("href")
        if (url != null) {
            doc = requests.get(url).document
            var server = 0
            doc.getElementsByClass("flb_download_buttons").mapNotNull {download ->
                server++
                callback.invoke(
                    ExtractorLink(
                        mainUrl,
                        "Server$server",
                        url = download.select("a").attr("href"),
                        data,
                        quality = 0,
                        isM3u8 = false,
                        isDash = false
                    )
                )
            }
        }
        return true
    }

}