package com.redowan

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FilmyHunkProvider : MainAPI() {
    override var mainUrl = "https://filmyhunk.com.ro"
    override var name = "FilmyHunk"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries,)
    /*private val proxyServer = "https://y.demo.wvusd.homes/" // To bypass cloudflare verification
    private var isCloudFlareChecked = false
    private var finalUrl = ""*/
    override val mainPage = mainPageOf(
        "" to "Latest Updates",
        "/category/bollywood-hindi-movies" to "Bollywood Hindi Movies",
        "/category/web-series" to "Web Series",
        "/category/south-hindi-dubbed-movies-download" to "South Hindi Dubbed Movies",
        "/category/dual-audio" to "Dual Audio",
        "/category/punjabi-movies" to "Punjabi Movies",
        "/category/marathi-movies" to "Marathi Movies",
        "/category/pakistani-movies" to "Pakistani Movies",
        "/category/bengali-movies" to "Bengali Movies",
        "/category/gujarati-movies" to "Gujarati Movies",
        "/category/english-movies" to "English Movies",
        "/category/alt-balaji-web-series" to "Alt Balaji Web Series",
        "/category/disneyplus-series" to "Disney Plus Series",
        "/category/k-drama-series" to "K-Drama Series",
        "/category/mx-player-web-series" to "Mx Player Web Series",
        "/category/netflix-series" to "Netfilx Series",
        "/category/sonyliv-web-series" to "Sonyliv Web Series",
        "/category/voot-web-series" to "Voot Web Series",
        "/category/zee5-web-series" to "Zee5 Web Series",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        /*if(!isCloudFlareChecked)
        {
            val code = app.get(mainUrl,allowRedirects = true, timeout = 30).code
            isCloudFlareChecked = true
            if(code == 403 || code == 401)
            {
                finalUrl = "$proxyServer$mainUrl"
            }
            else
            {
                finalUrl = mainUrl
            }
        }*/
        val doc = app.get("$mainUrl${request.data}/page/$page",allowRedirects = true, timeout = 30).document
        val home = doc.select(".col-md-2.col-sm-3.col-xs-6").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select(".bw_thumb a").attr("href")
        val title = post.select(".h1title").text().replace("Download","").trim()
        val imageUrl =post.select(".tm_hide").attr("data-lazy-src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query",allowRedirects = true, timeout = 30).document
        val searchResponse = doc.select(".col-md-2.col-sm-3.col-xs-6")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url,allowRedirects = true, timeout = 30).document
        val title = doc.select(".bw_h1title_single").text().replace("Download","").trim()
        val imageUrl = doc.select("img[fetchpriority=\"high\"]").attr("src")
        var plot = ""
        var releasedDate = ""
        var duration : Int? = 0
        val sRegex2 = "[Ss](\\d{1,2})"
        doc.select(".bw_desc ul li").forEach { item->
            item.childNodes().forEach{ child->
                if(child.nextSibling() !=null)
               {

                   if(child.outerHtml().lowercase().contains("released"))
                   {
                       releasedDate = "(\\d{4})".toRegex().find(child.nextSibling().outerHtml())?.groups?.get(1)?.value.toString()
                   }
                   if (child.outerHtml().lowercase().contains("duration"))
                   {

                       duration = getDurationFromString(child.nextSibling().outerHtml().replace(":","").trim())
                   }
               }
            }
        }
        doc.selectFirst(".bw_desc").children().forEach { child->
            if(child.text().lowercase().contains("storyline") || child.text().lowercase().contains("movie-synopsis/plot") || child.text().lowercase().contains("series synopsis/plot"))
            {
                plot = child.nextElementSibling().text()
            }
        }

        if(!title.lowercase().contains("season") && !title.lowercase().contains("series") && !sRegex2.toRegex().containsMatchIn(title))
        {
            val linkList = mutableListOf<String>()
            val links = if(doc.select(".myButton1").isNullOrEmpty()) {doc.select(".myButton2")} else {doc.select(".myButton1")}
            links.forEach { item->
                val link = item.attr("href")
                val doc = app.get(link, timeout = 30, allowRedirects = true).document
                val buttons = if(doc.select(".myButton1").isNullOrEmpty()) {doc.select(".myButton2")} else {doc.select(".myButton1")}
                buttons.forEach { button->
                    linkList.add(button.attr("href"))
                }
            }
            return newMovieLoadResponse(title, url, TvType.Movie, linkList.joinToString("+")) {
                this.posterUrl = imageUrl
                if (plot != null) {
                    this.plot = plot.trim()
                }
                if (!releasedDate.isNullOrEmpty()) {
                    this.year = releasedDate?.toInt()
                }
                if(duration != null && duration != 0)
                {
                    this.duration = duration
                }
            }
        }
        else
        {
            var elements = doc.selectFirst(".bw_desc")
            val seasonRegex = "(\\d) – (\\d)"
            val seasonRegex2 = "(\\d)-(\\d)"
            val sRegex = "Season\\s+(\\d{1,2})"
            val qualityRegex2 = "(\\d{3,4})[pP]".toRegex()
            val episodeRegex = "(Episode\\s*(\\d{1,3}))"
            val isMultiSeason = seasonRegex.toRegex().containsMatchIn(title) || seasonRegex2.toRegex().containsMatchIn(title)
            val isMultiWordSeason = sRegex.toRegex().findAll(title).count() > 1
            var startSeason:Int? = 1
            var endSeason:Int? = 1
            var seasonEpisodeMap = mutableMapOf<String,MutableMap<String,MutableList<String>>>()
            if(isMultiSeason) {
                if (seasonRegex.toRegex().containsMatchIn(title)) {
                    startSeason = seasonRegex.toRegex().find(title)?.groups?.get(1)?.value?.toInt()
                    endSeason = seasonRegex.toRegex().find(title)?.groups?.get(2)?.value?.toInt()
                }
                else if(seasonRegex2.toRegex().containsMatchIn(title))
                {
                    startSeason = seasonRegex2.toRegex().find(title)?.groups?.get(1)?.value?.toInt()
                    endSeason = seasonRegex2.toRegex().find(title)?.groups?.get(2)?.value?.toInt()
                }
            }
            else if (isMultiWordSeason)
            {
                endSeason = sRegex.toRegex().findAll(title).count()
            }
            if(startSeason != null && endSeason != null)
            {
                for (i in startSeason..endSeason)
                {
                    var currentSeason = "Season $i"
                    if(startSeason == 1 && endSeason == 1)
                    {
                        if(sRegex.toRegex().containsMatchIn(title))
                        {
                            currentSeason = "Season " + sRegex.toRegex().find(title)?.groups?.get(1)?.value.toString()
                        }
                        else if (sRegex2.toRegex().containsMatchIn(title))
                        {
                            currentSeason = "S" + sRegex2.toRegex().find(title)?.groups?.get(1)?.value.toString()
                        }
                    }
                    for(j in 0..(elements.children().size - 1))
                    {
                        val item = elements.children()[j]
                        var seasonTxt = ""
                        if(sRegex.toRegex().containsMatchIn(item.text()))
                        {
                            seasonTxt = "Season " + sRegex.toRegex().find(item.text())?.groups?.get(1)?.value.toString()

                        }
                        else if (sRegex2.toRegex().containsMatchIn(item.text()))
                        {
                            seasonTxt = "S" + sRegex2.toRegex().find(item.text())?.groups?.get(1)?.value.toString()

                        }
                        if(item.tagName() == "h4" && (seasonTxt.contains(currentSeason)))
                        {
                            var episodeMap = mutableMapOf<String,MutableList<String>>()
                            if(!seasonEpisodeMap[currentSeason].isNullOrEmpty())
                            {
                                episodeMap = seasonEpisodeMap[currentSeason]!!
                            }
                            var siblingList = mutableListOf<Element>()
                            var nextSibling = item.nextElementSibling()
                            while (nextSibling.tagName() == "p")
                            {
                                if (nextSibling.text().isNotEmpty() && qualityRegex2.containsMatchIn(nextSibling.text())) {
                                    siblingList.add(nextSibling)
                                }
                                nextSibling = nextSibling.nextElementSibling()
                            }

                            siblingList.forEach { element->
                                val link = if(element.select(".myButton1").isNullOrEmpty()) {element.select(".myButton2")} else {element.select(".myButton1")}
                                if(!link.text().lowercase().contains("zip") && !link.attr("href").isNullOrEmpty())
                                {
                                    val doc = app.get(link.attr("href"), timeout = 30).document
                                    val links = if(doc.select(".myButton1").isNullOrEmpty()) {doc.select(".myButton2")} else {doc.select(".myButton1")}
                                    links.forEach { item->
                                        val link = item.attr("href")
                                        var episodeName = item.text()
                                        if(episodeName.lowercase().contains("mb"))
                                        {
                                            episodeName = episodeRegex.toRegex().find(episodeName)?.groups?.get(1)?.value.toString()
                                        }
                                        if(!episodeMap[episodeName].isNullOrEmpty()) {
                                            episodeMap[episodeName]?.add(link)
                                        }else
                                        {
                                            var list = mutableListOf<String>()
                                            list.add(link)
                                            episodeMap[episodeName] = list
                                        }
                                    }
                                }

                            }
                            seasonEpisodeMap[currentSeason] = episodeMap
                        }
                    }
                }
            }
            val episodeData = mutableListOf<Episode>()

            for((k,v) in seasonEpisodeMap)
            {
                val episodeMap = v
                for((index, entry) in episodeMap.entries.withIndex())
                {
                    var episodeName = entry.key
                    if(!episodeName.lowercase().contains("episode"))
                    {
                        episodeName = "Full Season/Episode (Server ${index + 1})"
                    }
                    episodeData.add(Episode(
                        data = entry.value.joinToString("+"),
                        name = episodeName,
                        season = sRegex.toRegex().find(k)?.groups?.get(1)?.value?.toInt()
                    ))
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeData) {
                this.posterUrl = imageUrl
                if (plot != null) {
                    this.plot = plot.trim()
                }
                if (!releasedDate.isNullOrEmpty()) {
                    this.year = releasedDate?.toInt()
                }
                if(duration != null && duration != 0)
                {
                    this.duration = duration
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
        val urls = mutableListOf<String>()
        links.forEach { link->
                val decodedUrl = base64Decode(link.substringAfter("go/"))
                if(decodedUrl.lowercase().contains("gdmirrorbot"))
                {
                    val doc = app.get(decodedUrl, timeout = 30).document
                    val streamLinks = doc.select(".align-middle a")
                    streamLinks.forEach { stLink->
                        val doc = app.get(stLink.attr("href")).document
                        val iframe = doc.select("iframe").attr("src")
                        urls.add(iframe)
                        //loadExtractor(iframe,subtitleCallback,callback)
                    }
                }
                if(decodedUrl.lowercase().contains("gdflix"))
                {
                    urls.add(decodedUrl)
                }
                if(decodedUrl.lowercase().contains("reshare"))
                {
                    urls.add(decodedUrl)

                }

        }

        val (containsreshare, others) = urls.partition { it.contains("reshare") }


        val reorderedList = others + containsreshare
        // Implement seperate becasue of missing links
        for (link in reorderedList)
        {
            if (!link.lowercase().contains("reshare")) {
                loadExtractor(link,subtitleCallback,callback)
            } else {
                val id = "reshare\\.pm\\/d\\/(.*)\\/(.*)".toRegex().find(link) ?. groupValues ?. get(1) ?: ""
                val downloadLink = "https://reshare.pm/v1/download?id=$id"
                val response = app.get(downloadLink, timeout = 15, headers = mapOf("X-Referer" to "https://mainlinks.xyz/")).text
                val json = JSONObject(response)
                val url = json.getString("direct_link")
                callback.invoke(ExtractorLink(
                    "P-Direct",
                    "P-Direct",
                    url = url,
                    "",
                    quality = getVideoQuality(json.getString("file_name")),
                ))
            }
        }
        return true
    }

    private fun getVideoQuality(string: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(string ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}