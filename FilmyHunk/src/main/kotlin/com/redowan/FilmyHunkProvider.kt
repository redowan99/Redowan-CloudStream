package com.redowan

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
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(FilmyHunkProvider())
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
////    providerTester.testLoadLinks("")
//}

class FilmyHunkProvider : MainAPI() {
    override var mainUrl = "https://filmyhunk.click"
    override var name = "FilmyHunk"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
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
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            "$mainUrl${request.data}/page/$page", allowRedirects = true, timeout = 30
        ).document
        val home = doc.select(".col-md-2.col-sm-3.col-xs-6").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select(".bw_thumb a").attr("href")
        val title = post.select(".h1title").text().replace("Download", "").trim()
        val imageUrl = post.select(".tm_hide").attr("data-lazy-src").ifEmpty {
            post.select(".tm_hide").attr("src")
        }.substringAfter("wp-content/uploads/http")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", allowRedirects = true, timeout = 30).document
        val searchResponse = doc.select(".col-md-2.col-sm-3.col-xs-6")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, allowRedirects = true, timeout = 30).document
        val title = doc.select(".bw_h1title_single").text().replace("Download", "").trim()
        val imageUrl = doc.select("img[fetchpriority=\"high\"]").attr("src")
        var plot = ""
        var releasedDate = ""
        var duration: Int? = 0
        val sRegex2 = "[Ss](\\d{1,2})".toRegex()
        doc.select(".bw_desc ul li").forEach { item ->
            item.childNodes().forEach { child ->
                if (child.nextSibling() != null) {

                    if (child.outerHtml().lowercase().contains("released")) {
                        releasedDate = "(\\d{4})".toRegex()
                            .find(child.nextSibling()!!.outerHtml())?.groups?.get(
                                1
                            )?.value.toString()
                    }
                    if (child.outerHtml().lowercase().contains("duration")) {

                        duration = getDurationFromString(
                            child.nextSibling()!!.outerHtml().replace(":", "").trim()
                        )
                    }
                }
            }
        }
        doc.selectFirst(".bw_desc")?.children()?.forEach { child ->
            if (child.text().lowercase().contains("storyline") || child.text().lowercase()
                    .contains("movie-synopsis/plot") || child.text().lowercase()
                    .contains("series synopsis/plot")
            ) {
                plot = child.nextElementSibling()?.text() ?: ""
            }
        }

        if (!title.lowercase().contains("season") && !title.lowercase()
                .contains("series") && !sRegex2.containsMatchIn(title)
        ) {
            val linkList = mutableListOf<String>()
            val links = if (doc.select(".myButton1").isEmpty()) {
                doc.select(".myButton2")
            } else {
                doc.select(".myButton1")
            }
            links.forEach { item ->
                val link = item.attr("href")
                val webpageContent = app.get(link, timeout = 30, allowRedirects = true).document
                val buttons = if (webpageContent.select(".myButton1").isEmpty()) {
                    webpageContent.select(".myButton2")
                } else {
                    webpageContent.select(".myButton1")
                }
                buttons.forEach { button ->
                    linkList.add(button.attr("href"))
                }
            }
            return newMovieLoadResponse(title, url, TvType.Movie, linkList.joinToString("+")) {
                this.posterUrl = imageUrl
                this.plot = plot.trim()
                if (releasedDate.isNotEmpty()) {
                    this.year = releasedDate.toInt()
                }
                if (duration != null && duration != 0) {
                    this.duration = duration
                }
            }
        } else {
            val elements = doc.selectFirst(".bw_desc")
            val seasonRegex = "(\\d) – (\\d)".toRegex()
            val seasonRegex2 = "(\\d)-(\\d)".toRegex()
            val sRegex = "Season\\s+(\\d{1,2})".toRegex()
            val qualityRegex2 = "(\\d{3,4})[pP]".toRegex()
            val episodeRegex = "([E|e]pisode\\s*(\\d{1,3}))".toRegex()
            val episodeRegex2 = "(^[E|e]\\s*(\\d{1,3}))".toRegex()
            val isMultiSeason =
                seasonRegex.containsMatchIn(title) || seasonRegex2.containsMatchIn(title)
            val isMultiWordSeason = sRegex.findAll(title).count() > 1
            var startSeason: Int? = 1
            var endSeason: Int? = 1
            val seasonEpisodeMap = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
            if (isMultiSeason) {
                if (seasonRegex.containsMatchIn(title)) {
                    startSeason = seasonRegex.find(title)?.groups?.get(1)?.value?.toInt()
                    endSeason = seasonRegex.find(title)?.groups?.get(2)?.value?.toInt()
                } else if (seasonRegex2.containsMatchIn(title)) {
                    startSeason = seasonRegex2.find(title)?.groups?.get(1)?.value?.toInt()
                    endSeason = seasonRegex2.find(title)?.groups?.get(2)?.value?.toInt()
                }
            } else if (isMultiWordSeason) {
                endSeason = sRegex.findAll(title).count()
            }
            if (startSeason != null && endSeason != null) {
                for (i in startSeason..endSeason) {
                    var currentSeason = "Season $i"
                    if (startSeason == 1 && endSeason == 1) {
                        if (sRegex.containsMatchIn(title)) {
                            currentSeason =
                                "Season " + sRegex.find(title)?.groups?.get(1)?.value.toString()
                        } else if (sRegex2.containsMatchIn(title)) {
                            currentSeason =
                                "S" + sRegex2.find(title)?.groups?.get(1)?.value.toString()
                        }
                    }
                    if (elements != null) {
                        for (j in 0..<elements.children().size) {
                            val item = elements.children()[j]
                            var seasonTxt = ""
                            if (sRegex.containsMatchIn(item.text())) {
                                seasonTxt = "Season " + sRegex.find(item.text())?.groups?.get(
                                    1
                                )?.value.toString()

                            } else if (sRegex2.containsMatchIn(item.text())) {
                                seasonTxt =
                                    "S" + sRegex2.find(item.text())?.groups?.get(1)?.value.toString()

                            }
                            if (item.tagName() == "h4" && (seasonTxt.contains(currentSeason))) {
                                var episodeMap = mutableMapOf<String, MutableList<String>>()
                                if (!seasonEpisodeMap[currentSeason].isNullOrEmpty()) {
                                    episodeMap = seasonEpisodeMap[currentSeason]!!
                                }
                                val siblingList = mutableListOf<Element>()
                                var nextSibling = item.nextElementSibling()
                                if (nextSibling != null) {
                                    while (nextSibling?.tagName() == "p") {
                                        if (nextSibling.text()
                                                .isNotEmpty() && qualityRegex2.containsMatchIn(
                                                nextSibling.text()
                                            )
                                        ) {
                                            siblingList.add(nextSibling)
                                        }
                                        nextSibling = nextSibling.nextElementSibling()
                                    }
                                }

                                siblingList.forEach { element ->
                                    val link = if (element.select(".myButton1").isEmpty()) {
                                        element.select(".myButton2")
                                    } else {
                                        element.select(".myButton1")
                                    }
                                    if (!link.text().lowercase()
                                            .contains("zip") && link.attr("href").isNotEmpty()
                                    ) {
                                        val htmlDocument =
                                            app.get(link.attr("href"), timeout = 30).document
                                        val links =
                                            if (htmlDocument.select(".myButton1").isEmpty()) {
                                                htmlDocument.select(".myButton2")
                                            } else {
                                                htmlDocument.select(".myButton1")
                                            }
                                        links.forEach { item ->
                                            val episodeUrl = item.attr("href")
                                            var episodeName = item.text().trim()
                                            if (episodeRegex.containsMatchIn(episodeName)) {
                                                episodeName =
                                                    "Episode " + episodeRegex.find(episodeName)?.groups?.get(
                                                            2
                                                        )?.value.toString().toInt()
                                            } else if (episodeRegex2.containsMatchIn(episodeName)) {
                                                episodeName =
                                                    "Episode " + episodeRegex2.find(episodeName)?.groups?.get(
                                                            2
                                                        )?.value.toString().toInt()
                                            }
                                            if (!episodeMap[episodeName].isNullOrEmpty()) {
                                                episodeMap[episodeName]?.add(episodeUrl)
                                            } else {
                                                val list = mutableListOf<String>()
                                                list.add(episodeUrl)
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
            }
            val episodeData = mutableListOf<Episode>()

            for ((k, v) in seasonEpisodeMap) {
                for ((index, entry) in v.entries.withIndex()) {
                    var episodeName = entry.key
                    if (!episodeName.lowercase().contains("episode")) {
                        episodeName = "Full Season/Episode (Server ${index + 1})"
                    }
                    episodeData.add(
                        newEpisode(entry.value.joinToString("+")) {
                            this.name = episodeName
                            this.season = sRegex.find(k)?.groups?.get(1)?.value?.toInt()
                        })
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeData) {
                this.posterUrl = imageUrl
                this.plot = plot.trim()
                if (releasedDate.isNotEmpty()) {
                    this.year = releasedDate.toInt()
                }
                if (duration != null && duration != 0) {
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
        links.forEach { link ->
            val decodedUrl = base64Decode(link.substringAfter("go/"))
            if (decodedUrl.lowercase().contains("gdmirrorbot")) {
                val doc = app.get(decodedUrl, timeout = 30).document
                val streamLinks = doc.select(".align-middle a")
                streamLinks.forEach { stLink ->
                    val streamDoc = app.get(stLink.attr("href")).document
                    val iframe = streamDoc.select("iframe").attr("src")
                    urls.add(iframe)
                    //loadExtractor(iframe,subtitleCallback,callback)
                }
            }
            if (decodedUrl.lowercase().contains("gdflix")) {
                urls.add(decodedUrl)
            }
            if (decodedUrl.lowercase().contains("reshare")) {
                urls.add(decodedUrl)

            }

        }

        val (containsreshare, others) = urls.partition { it.contains("reshare") }


        val reorderedList = others + containsreshare
        // Implement seperate becasue of missing links
        for (link in reorderedList) {
            if (!link.lowercase().contains("reshare")) {
                loadExtractor(link, subtitleCallback, callback)
            } else {
                val id = "reshare\\.pm/d/(.*)/(.*)".toRegex().find(link)?.groupValues?.get(1) ?: ""
                val downloadLink = "https://reshare.pm/v1/download?id=$id"
                val response = app.get(
                    downloadLink,
                    timeout = 15,
                    headers = mapOf("X-Referer" to "https://mainlinks.xyz/")
                ).text
                val json = JSONObject(response)
                val url = json.getString("direct_link")
                callback.invoke(
                    newExtractorLink(
                        "P-Direct",
                        "P-Direct",
                        url = url
                    )
                )
            }
        }
        return true
    }
}