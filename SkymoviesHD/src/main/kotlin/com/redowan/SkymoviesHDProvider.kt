package com.redowan


import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
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

    private suspend fun toResult(post: Element): SearchResponse {
        val url = mainUrl + post.select("a").attr("href")
        var title = post.text()
        val size = "\\[\\d(.*?)B]".toRegex().find(title)?.value
        if (size!=null){
            val newTitle = title.replace(size, "")
            title = "$size $newTitle"
        }
        val requests = Requests()
        val doc = requests.get(url).document
        return newTvSeriesSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = doc.select(".movielist > img:nth-child(1)").attr("src")
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
        val doc = requests.get(data).document
        val url = doc.getElementsContainingText("https://hubcloud.").last()?.select("a")?.attr("href")
        if (url != null) {
            hubCloud(url,callback)
        }
        return true
    }
    private suspend fun hubCloud(
        data: String,
        callback: (ExtractorLink) -> Unit){
        val app = Requests()
        val doc = app.get(data.replace(".lol",".club")).document
        val gamerLink: String

        if (data.contains("drive")) {
            val scriptTag = doc.selectFirst("script:containsData(url)")?.toString()
            gamerLink =
                scriptTag?.let { Regex("var url = '([^']*)'").find(it)?.groupValues?.get(1) }
                    ?: ""
        } else {
            gamerLink = doc.selectFirst("div.vd > center > a")?.attr("href") ?: ""
        }

        val document = app.get(gamerLink).document

        val size = document.selectFirst("i#size")?.text()
        val div = document.selectFirst("div.card-body")
        val header = document.selectFirst("div.card-header")?.text()
        div?.select("a")?.apmap {
            val link = it.attr("href")
            val text = it.text()
            if (link.contains("pixeldra")) {
                val pixeldrainLink = link.replace("/u/", "/api/file/")
                callback.invoke(
                    ExtractorLink(
                        "Pixeldrain",
                        "Pixeldrain $size",
                        pixeldrainLink,
                        link,
                        getIndexQuality(header),
                    )
                )
            } else if (text.contains("Download [Server : 10Gbps]")) {
                val response = app.get(link, allowRedirects = false)
                val downloadLink =
                    response.headers["location"].toString().split("link=").getOrNull(1) ?: link
                callback.invoke(
                    ExtractorLink(
                        "Google[Download]",
                        "Google[Download] $size",
                        downloadLink,
                        "",
                        getIndexQuality(header),
                    )
                )
            } else if (link.contains(".dev")) {
                callback.invoke(
                    ExtractorLink(
                        "Cloudflare",
                        "Cloudflare $size",
                        link,
                        "",
                        getIndexQuality(header),
                    )
                )
            }
        }
    }
    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}