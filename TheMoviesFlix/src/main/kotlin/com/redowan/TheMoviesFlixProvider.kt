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
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(TheMoviesFlixProvider())
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "rome", verbose = true)
//}

class TheMoviesFlixProvider : MainAPI() {
    override var mainUrl = "https://themoviesflix.email"
    override var name = "TheMoviesFlix"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.NSFW)
    override val mainPage = mainPageOf(
        "" to "Latest Updates",
        "/category/hindi-dubbed-movies" to "Hindi Dubbed Movies",
        "/category/latest-released" to "Latest Released Movies",
        "/category/hollywood-movies" to "Hollywood Movies",
        "/category/english-movies" to "English Movies",
        "/category/adult-movies" to "Adult Movies",
        "/category/dual-audio" to "Dual Audio Movies",
        "/category/multi-audio" to "Multi Audio Movies",
        "/category/hindi-dubbed-series" to "Hindi Dubbed Series",
        "/category/english" to "English Series",
        "/category/netflix" to "Netflix",
        "/category/amazon-prime-video" to "Amazon Prime Video",
        "/category/hulu" to "Hulu",
        "/category/the-cw" to "The CW",
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            "$mainUrl${request.data}/page/$page", allowRedirects = true, timeout = 30
        ).document
        val home = doc.select(".latestPost.excerpt").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select(".title.front-view-title a").attr("href")
        val title = post.select(".title.front-view-title a").text()
        val imageUrl = post.select(".featured-thumbnail img").attr("src")
        val imageWithUrl = "$url + $imageUrl"
        return newMovieSearchResponse(title, imageWithUrl, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", allowRedirects = true, timeout = 30).document
        val searchResponse = doc.select(".latestPost.excerpt")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val urls = url.split(" + ")
        val doc = app.get(urls[0], allowRedirects = true, timeout = 30).document
        val title =
            doc.select(".title.single-title.entry-title").text().replace("Download", "").trim()
        val imageUrl = urls[1]
        var plot = ""
        val sRegex2 = "[Ss](\\d{1,2})"
        doc.selectFirst(".thecontent.clearfix")?.children()?.forEach { child ->
            if (child.text().lowercase().contains("storyline")) {
                plot = child.nextElementSibling()?.text() ?: ""
            }
        }

        if (!title.lowercase().contains("season") && !title.lowercase()
                .contains("series") && !sRegex2.toRegex().containsMatchIn(title)
        ) {
            val linkList = mutableListOf<String>()
            val links = doc.select(".maxbutton-6.maxbutton.maxbutton-post-button-1")
            links.forEach { item ->
                val link = item.attr("href")
                val documentContent = app.get(link, timeout = 30, allowRedirects = true).document
                val buttons = documentContent.select(".maxbutton")
                buttons.forEach { button ->
                    linkList.add(button.attr("href"))
                }
            }
            return newMovieLoadResponse(title, url, TvType.Movie, linkList.joinToString("+")) {
                this.posterUrl = imageUrl
                this.plot = plot.trim()
            }
        } else {
            val elements = doc.selectFirst(".thecontent.clearfix")
            val seasonRegex = "(\\d) – (\\d)"
            val seasonRegex2 = "(\\d)-(\\d)"
            val sRegex = "Season\\s+(\\d{1,2})".toRegex()
            val isMultiSeason =
                seasonRegex.toRegex().containsMatchIn(title) || seasonRegex2.toRegex()
                    .containsMatchIn(title)
            val isMultiWordSeason = sRegex.findAll(title).count() > 1
            var startSeason: Int? = 1
            var endSeason: Int? = 1
            val seasonEpisodeMap = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
            if (isMultiSeason) {
                if (seasonRegex.toRegex().containsMatchIn(title)) {
                    startSeason = seasonRegex.toRegex().find(title)?.groups?.get(1)?.value?.toInt()
                    endSeason = seasonRegex.toRegex().find(title)?.groups?.get(2)?.value?.toInt()
                } else if (seasonRegex2.toRegex().containsMatchIn(title)) {
                    startSeason = seasonRegex2.toRegex().find(title)?.groups?.get(1)?.value?.toInt()
                    endSeason = seasonRegex2.toRegex().find(title)?.groups?.get(2)?.value?.toInt()
                }
            } else if (isMultiWordSeason) {
                endSeason = sRegex.findAll(title).count()
            }
            if (startSeason != null && endSeason != null) {
                for (i in startSeason..endSeason) {
                    var currentSeason = "Season $i"
                    if (startSeason == 1 && endSeason == 1) {
                        if (sRegex.containsMatchIn(title)) {
                            currentSeason = "Season " + sRegex
                                .find(title)?.groups?.get(1)?.value.toString()
                        } else if (sRegex2.toRegex().containsMatchIn(title)) {
                            currentSeason = "S" + sRegex2.toRegex()
                                .find(title)?.groups?.get(1)?.value.toString()
                        }
                    }
                    if (elements != null) {
                        for (j in 0..<elements.children().size) {
                            val item = elements.children()[j]
                            var seasonTxt = ""
                            if (sRegex
                                    .containsMatchIn(item.text()) && (item.nextElementSibling()
                                    ?.tagName() ?: "") == "p"
                            ) {
                                seasonTxt = "Season " + sRegex
                                    .find(item.text())?.groups?.get(1)?.value.toString()

                            } else if (sRegex2.toRegex()
                                    .containsMatchIn(item.text()) && (item.nextElementSibling()
                                    ?.tagName() ?: "") == "p"
                            ) {
                                seasonTxt = "Season " + Integer.parseInt(
                                    sRegex2.toRegex()
                                        .find(item.text())?.groups?.get(1)?.value.toString()
                                )
                            }
                            if ((item.tagName() == "h3" || item.tagName() == "h4" || (item.tagName() == "p" && item.className()
                                    .contains("has-medium-font-size"))) && (seasonTxt.contains(
                                    currentSeason
                                )) && (item.nextElementSibling()?.tagName() ?: "") == "p"
                            ) {
                                var episodeMap = mutableMapOf<String, MutableList<String>>()
                                if (!seasonEpisodeMap[currentSeason].isNullOrEmpty()) {
                                    episodeMap = seasonEpisodeMap[currentSeason]!!
                                }

                                var nextSibling = item.nextElementSibling()
                                while (nextSibling?.tagName() == "p" && nextSibling.children()
                                        .isEmpty()
                                ) {
                                    nextSibling = nextSibling.nextElementSibling()
                                }

                                nextSibling?.select("a")?.forEach { element ->
                                    val document =
                                        app.get(element.attr("href"), timeout = 30).document
                                    val links =
                                        document.select(".wp-block-heading.has-text-align-center.has-text-color")
                                    links.forEach { item ->
                                        val link = item.select("a").attr("href")
                                        val episodeName = item.text().trim()
                                        if (!episodeMap[episodeName].isNullOrEmpty()) {
                                            episodeMap[episodeName]?.add(link)
                                        } else {
                                            val list = mutableListOf<String>()
                                            list.add(link)
                                            episodeMap[episodeName] = list
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
        links.forEach { link ->
            if (link.lowercase().contains("veryfastdownload")) {
                val downloadLink = link.replace("watch2", "download2")
                val html = app.get(downloadLink, timeout = 30).document.html()
                val gLink = "openInNewTab\\(\\\\'(.*)\\\\'\\);\"".toRegex().find(html)?.groups?.get(
                    1
                )?.value.toString()
                callback.invoke(
                    newExtractorLink(
                        "G-Direct",
                        "G-Direct",
                        url = gLink
                    )
                )
            } else {
                loadExtractor(link, subtitleCallback, callback)
            }
        }
        return true
    }
}