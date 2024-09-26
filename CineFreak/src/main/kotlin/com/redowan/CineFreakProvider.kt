package com.redowan

//import com.lagradost.api.Log
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class CineFreakProvider : MainAPI() {
    override var mainUrl = "https://cinefreak.net"
    override var name = "CineFreak"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "/" to "Latest Releases",
        "/category/animation/" to "Animation",
        "/category/bangla-dubbed/" to "Bangla Dubbed",
        "/category/chinese/" to "Chinese",
        "/category/bangla-movies/" to "Bangla Movies",
        "/category/chorki/" to "Chorki",
        "/category/dc-movies/" to "DC Movies",
        "/category/dual-audio/" to "Dual Audio",
        "/category/english-movies/" to "English Movies",
        "/category/german/" to "German",
        "/category/hindi-movies/" to "Hindi Movies",
        "/category/horror/" to "Horror",
        "/category/hoichoi/" to "Hoichoi",
        "/category/indonesian/" to "Indonesian",
        "/category/japanese/" to "Japanese",
        "/category/kannada/" to "Kannada",
        "/category/korean/" to "Korean",
        "/category/malayalam/" to "Malayalam",
        "/category/mcu/" to "MCU",
        "/category/tamil/" to "Tamil",
        "/category/telugu/" to "Telugu",
        "/category/turkish/" to "Turkish",
        "/category/south-hindi-movies/" to "South Hindi Movies",
        "/category/spanish/" to "Spanish",
        "/category/web-series/" to "WEB-Series",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        // This is necessary for load more posts on homepage
        val doc = app.get("$mainUrl${request.data}page/$page").document
        val home = doc.select(".post").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select(".post-thumbnail").attr("href")
        val title = post.select(".entry-title a").text()
        val imageUrl = post.select(".post-thumbnail img").attr("src")
        val quality = post.select(".video-label").text()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
            if(!quality.isNullOrEmpty())
            {
                this.quality = getSearchQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val searchResponse = doc.select(".post")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("title").text()
        val imageUrl = doc.select(".post-thumbnail img").attr("src")
        var plot = ""
        val eList = doc.selectFirst(".single-service-content")
        eList.children().forEach { item->
            if(item.tagName() == "h3" && item.text().contains("Storyline"))
            {
                plot = item.nextElementSibling().text()
            }
        }

        val dwnLinks = doc.selectFirst(".download-links-div")
        val episodeData = mutableListOf<Episode>()
        var episodeNo = 1
        val linkList = mutableListOf<String>()
        dwnLinks.children().forEach { item->
            if(item.tagName() == "h4" && item.text().lowercase().contains("season") && item.nextElementSibling().text().lowercase().contains("episode"))
            {
                val season = "Season(\\s\\d*)".toRegex().find(item.text())?.groups?.get(1)?.value?.trim()
                val episode = "Episode\\s(\\d*-\\d*)|".toRegex().find(item.nextElementSibling().text())?.groups?.get(1)?.value?.trim()
                val dlLinks = item.nextElementSibling().nextElementSibling().select("a")
                val linkList = mutableListOf<String>()
                dlLinks.forEach { item->
                    linkList.add(item.attr("href"))
                }
                if(!season.isNullOrEmpty() && !episode.isNullOrEmpty())
                {
                    episodeData.add(Episode(linkList.joinToString("+"),episode,season.toInt(),episodeNo))
                }
                episodeNo++
            }
            else if(item.tagName() == "h4" &&  !item.nextElementSibling().text().lowercase().contains("episode"))
            {
                var dlLinks: Elements
                if(item.nextElementSibling().className() == "downloads-btns-div")
                {
                    dlLinks = item.nextElementSibling().select("a")
                }
                else
                {
                    dlLinks = item.nextElementSibling().nextElementSibling().select("a")
                }
                dlLinks.forEach { item->
                    linkList.add(item.attr("href"))
                }
            }

        }


        if(!episodeData.isNullOrEmpty()) {
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeData) {
                this.posterUrl = imageUrl
                if (plot.isNotEmpty()) {
                    this.plot = plot
                }
            }
        }
        else
        {
            return newMovieLoadResponse(title, url, TvType.Movie, linkList.joinToString("+")) {
                this.posterUrl = imageUrl
                if(plot.isNotEmpty())
                {
                    this.plot = plot
                }
            }
        }


    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val links = data.split("+")
        links.forEach { link->
            val doc = app.get(link, timeout = 30).document
            val name = doc.select(".file-title").text().replace("CINEFREAK.NET - ","")
            val quality = getVideoQuality(name)
            val buttons = doc.select(".card-body button")
            buttons.forEach { item->
                if(item.text().contains("ZenCloud") || item.text().contains("Instant Download"))
                {
                    val finalLink = "window.open\\(\\'(.*)\\'\\)".toRegex().find(item.attr("onclick"))?.groups?.get(1)?.value.toString()
                    if (!finalLink.isNullOrEmpty()) {
                        val doc = app.get(finalLink, timeout = 30).document
                        val link = doc.select("#vd").attr("onclick")
                        val dlLink = "location.href=\\'(.*)\\'".toRegex().find(link)?.groups?.get(1)?.value.toString()
                        callback.invoke(
                            ExtractorLink("ZenCloud","ZenCloud ($name)",dlLink,"",quality)
                        )
                    }
                }

                if (item.text().contains("Cloud [Resumable]"))
                {
                    val dlLink = link.replace("/f/","/d/")
                    val doc = app.get(dlLink, timeout = 30).document
                    val buttons = doc.select(".card-body button")
                    buttons.forEach { item->
                        if(item.text().contains("Download Now"))
                        {
                            val finaldlLink = "location.href=\\'(.*)\\'".toRegex().find(item.attr("onclick"))?.groups?.get(1)?.value.toString()
                            callback.invoke(
                                ExtractorLink("ZenCloud","NeoDrive ($name)",finaldlLink,"",quality)
                            )
                        }
                    }
                }
            }
        }
        val doc = app.get(data).document
        val dwnLinks = doc.selectFirst(".download-links-div")
        dwnLinks.children().forEach { item->
            if(item.tagName() == "h4")
            {

            }
        }

        return true
    }

    private fun getVideoQuality(string: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(string ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

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
}