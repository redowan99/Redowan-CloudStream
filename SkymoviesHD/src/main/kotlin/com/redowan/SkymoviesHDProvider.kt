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
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.Requests
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.loadExtractor

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
    override val hasQuickSearch = false

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
        val doc = app.get("$mainUrl/category/${request.data}/$page.html", cacheTime = 60, allowRedirects = true, headers =  mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")).document
        val homeResponse = doc.select("div.L")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, false)
    }

    private suspend fun toResult(post: Element): SearchResponse {
        var title = post.text()
        val size = "\\[\\d(.*?)B]".toRegex().find(title)?.value
        if (size!=null){
            val newTitle = title.replace(size, "")
            title = "$size $newTitle"
        }
        val url = mainUrl + post.select("a").attr("href")
        val doc = app.get(url, cacheTime = 60, allowRedirects = true, headers =  mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")).document
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = doc.select(".movielist > img:nth-child(1)").attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search.php?search=$query&cat=All").document
        val searchResponse = doc.select("div.L")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60, allowRedirects = true, headers =  mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")).document
        val title = doc.select("div.Robiul").first()!!.text()
        val year = "(?<=\\()\\d{4}(?=\\))".toRegex().find(title)?.value?.toIntOrNull()
        return newMovieLoadResponse(title, url, TvType.Movie,url) {
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
        var doc = app.get(data).document
        val watchOnline = doc.select(".Bolly > a:nth-child(1)").attr("href")
        loadExtractor(watchOnline, subtitleCallback, callback)
        doc = Requests().get(doc.select(".Bolly > a:nth-child(3)").attr("href")).document
        doc.select(".cotent-box > a").forEach {
            val link = it.attr("href")
            if("fastxyz" in link) fastxyz(link,callback)
            else if("hubcloud" in link) hubCloud(link, callback)
        }
        return true
    }

    private suspend fun fastxyz(
        data: String,
        callback: (ExtractorLink) -> Unit) {
        val doc = app.get(data).document
        val server = listOf("Fastxyz-MediaFire", "Fastxyz-Cloudflare")
        var count = 0
        doc.select(".flb_download_buttons > a:nth-child(1)").forEach {
            callback.invoke(
                ExtractorLink(
                    "Fastxyz",
                    server[count],
                    it.attr("href"),
                    "",
                    Qualities.Unknown.value,
                )
            )
            count++
        }
    }

    private suspend fun hubCloud(
        data: String,
        callback: (ExtractorLink) -> Unit){
        val doc = app.get(data.replaceBefore("/drive/","https://hubcloud.club")).document
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

        val div = document.selectFirst("div.card-body")
        val header = document.selectFirst("div.card-header")?.text()
        div?.select("a")?.forEach {
            val link = it.attr("href")
            val text = it.text()
            if (link.contains("pixeldra")) {
                val pixeldrainLink = link.replace("/u/", "/api/file/")
                callback.invoke(
                    ExtractorLink(
                        "Pixeldrain",
                        "Pixeldrain",
                        pixeldrainLink,
                        link,
                        getVideoQuality(header),
                    )
                )
            } else if (text.contains("Download [Server : 10Gbps]")) {
                val response = app.get(link, allowRedirects = false)
                val downloadLink =
                    response.headers["location"].toString().split("link=").getOrNull(1) ?: link
                callback.invoke(
                    ExtractorLink(
                        "Google[Download]",
                        "Google[Download]",
                        downloadLink,
                        "",
                        getVideoQuality(header),
                    )
                )
            } else if (link.contains("fastdl")) {
                callback.invoke(
                    ExtractorLink(
                        "Fastdl",
                        "Fastdl",
                        link,
                        "",
                        getVideoQuality(header),
                    )
                )
            }
        }
    }

    private fun getVideoQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}