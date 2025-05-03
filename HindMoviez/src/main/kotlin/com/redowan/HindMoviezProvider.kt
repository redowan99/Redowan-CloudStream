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
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(Film1KProvider())
////    providerTester.testLoadLinks("https://checklinko.top/60382/")
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
////    providerTester.testLoadLinks("51783")
//}


class HindMoviezProvider : MainAPI() {
    override var mainUrl = "https://hindmoviez.foo"
    override var name = "HindMoviez"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )
    override val mainPage = mainPageOf(
        "" to "Latest",
        "/movies/hollywood-movies" to "Hollywood ",
        "/dual-audio" to "Dual Audio",
        "/movies/hindi-movies" to "Hindi",
        "/movies/english-movies" to "English",
        "/movies/adult-movies" to "Adult",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            "$mainUrl${request.data}/page/$page",
            cacheTime = 60,
            allowRedirects = true,
            timeout = 30
        ).document
        val home = doc.select(".post").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select(".entry-title-link").text()
        val url = post.select(".entry-title-link").attr("href")
        val posterUrl = post.select(".featured-image img").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", cacheTime = 60, timeout = 30).document
        return doc.select(".post").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60, timeout = 30).document
        val title = doc.selectFirst(".entry-title")?.text() ?: ""
        val image = doc.select(".featured-image img").attr("src")
        var plot = ""
        val year = "<li><strong>Release Year: </strong>(.*)</li>".toRegex()
            .find(doc.html())?.groups?.get(1)?.value
        val qualityRegex2 = "(\\d{3,4})[pP]".toRegex()
        if (title.lowercase().contains("season")) {
            val elements = doc.selectFirst(".entry-content")
            val qualityRegex = ">(\\d{3,4}p).*<".toRegex()
            val seasonRegex = "(\\d)-(\\d)"
            val isMultiSeason = seasonRegex.toRegex().containsMatchIn(title)

            var startSeason: Int? = 1
            var endSeason: Int? = 1

            if (isMultiSeason) {
                startSeason = seasonRegex.toRegex().find(title)?.groups?.get(1)?.value?.toInt()
                endSeason = seasonRegex.toRegex().find(title)?.groups?.get(2)?.value?.toInt()
            }

            if (startSeason != null && endSeason != null) {

                val seasonList = mutableListOf<SeasonDetail>()
                for (i in startSeason..endSeason) {
                    if (elements != null) {
                        for (j in 0..<elements.children().size) {

                            val item = elements.children()[j]
                            if (plot.isEmpty()) {
                                if (item.text().contains("Storyline")) {
                                    plot = item.nextElementSibling()?.text() ?: ""
                                }
                            }
                            val currentSeason = "Season $i"
                            if (item.tagName() == "h3" && (qualityRegex.containsMatchIn(item.html()) || qualityRegex2.containsMatchIn(
                                    item.html()
                                ))
                            ) {

                                if (item.text().lowercase().contains(currentSeason.lowercase())) {

                                    val quality =
                                        item.select("span[style=\"color: #ff00ff;\"]").text()
                                    val episodeLinksMap =
                                        mutableMapOf<String, MutableList<String>>()
                                    item.nextElementSibling()?.select("a")?.forEach { item ->
                                        val episodeUrl = item.attr("href")
                                        if (episodeUrl.isNotEmpty()) {
                                            val doc = app.get(
                                                episodeUrl,
                                                allowRedirects = true,
                                                timeout = 30
                                            ).document
                                            val episodelinks = doc.select(".entry-content h3")
                                            episodelinks.forEach { item ->
                                                val url = item.select("a").attr("href")
                                                val episodeName = item.select("a").text()
                                                if (!episodeName.lowercase()
                                                        .contains("batch")
                                                ) {
                                                    if (!episodeLinksMap[episodeName].isNullOrEmpty()) {
                                                        episodeLinksMap[episodeName]?.add(url)
                                                    } else {
                                                        val links = mutableListOf<String>()
                                                        links.add(url)
                                                        episodeLinksMap[episodeName] = links

                                                    }
                                                }
                                            }


                                        }
                                    }

                                    seasonList.add(
                                        SeasonDetail(
                                            quality,
                                            episodeLinksMap,
                                            currentSeason
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                val episodeData = mutableListOf<Episode>()
                for (i in startSeason..endSeason) {
                    val seasonList = seasonList.filter { season ->
                        season.season == "Season $i"
                    }
                    val episodeMap = mutableMapOf<String, MutableList<String>>()
                    seasonList.forEach { item ->
                        val episodeList = item.episodeLinkMap
                        if (episodeList != null) {
                            for ((k, v) in episodeList) {
                                if (!episodeMap[k].isNullOrEmpty()) {
                                    episodeMap[k]?.addAll(v)
                                } else {
                                    episodeMap[k] = v
                                }
                            }

                        }
                    }
                    for ((k, v) in episodeMap) {
                        episodeData.add(
                            newEpisode(v.joinToString("+")){
                                this.name = k
                                this.season = i
                                this.episode = episodeMap.keys.indexOf(k) + 1
                            }
                        )
                    }

                }

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeData) {
                    this.posterUrl = image
                    this.plot = plot
                    if (year != null) {
                        this.year = year.toInt()
                    }
                }
            }
        } else {
            val elements = doc.selectFirst(".entry-content")
            val qualityRegex = ">(\\d{3,4}p).*<".toRegex()
            val movieLinksList = mutableListOf<String>()
            if (elements != null) {
                for (j in 0..<elements.children().size) {

                    val item = elements.children()[j]
                    if (plot.isEmpty()) {
                        if (item.text().contains("Storyline")) {
                            plot = item.nextElementSibling()?.text() ?: ""
                        }
                    }
                    if (item.tagName() == "h3" && (qualityRegex.containsMatchIn(item.html()) || qualityRegex2.containsMatchIn(
                            item.html()
                        ))
                    ) {

                        item.nextElementSibling()?.select("a")?.forEach { item ->
                            val episodeUrl = if (item.attr("href").contains("href.li")) {
                                item.attr("href").substringAfter("/?")
                            } else {
                                item.attr("href")
                            }
                            if (episodeUrl.isNotEmpty()) {
                                val doc = app.get(
                                    episodeUrl,
                                    allowRedirects = true,
                                    timeout = 30
                                ).document
                                val episodelinks = doc.select(".entry-content h3")
                                episodelinks.forEach { item ->
                                    val url = item.select("a").attr("href")
                                    movieLinksList.add(url)
                                }
                            }
                        }
                    }
                }
            }
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                movieLinksList.joinToString("+")
            ) {
                this.posterUrl = image
                this.plot = plot
                if (year != null) {
                    this.year = year.toInt()
                }

            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, "") {
            this.posterUrl = image
            this.plot = plot
            if (year != null) {
                this.year = year.toInt()
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
        links.forEach { item ->
            val res = app.get(item, timeout = 30, allowRedirects = true)
            val doc = res.document
            if (res.url.contains("hpage.site")) {
                val links = doc.select(".container a")
                links.forEach { item ->
                    callback.invoke(
                        newExtractorLink(
                            "H-Cloud (VLC)",
                            "H-Cloud (VLC)",
                            url = item.attr("href")
                        )
                    )
                }
            } else if (res.url.contains("hindshare.site")) {
                val quality =
                    getVideoQuality(doc.select(".container p:nth-of-type(1) strong").text())
                val links = doc.select(".btn-group a")
                links.forEach { item ->
                    if (item.text().contains("HCloud")) {
                        callback.invoke(
                            newExtractorLink(
                                "H-Cloud (VLC)",
                                "H-Cloud (VLC)",
                                url = item.attr("href")
                            )
                        )
                    } else if (item.attr("href").contains("hindcdn.site")) {
                        val doc =
                            app.get(item.attr("href"), timeout = 30, allowRedirects = true).document
                        val links = doc.select(".container a")
                        links.forEach { item ->
                            val host = if (item.text().lowercase().contains("google")) {
                                item.text() + " (VLC)"
                            } else {
                                "HindCdn H-Cloud (VLC)"
                            }
                            callback.invoke(
                                newExtractorLink(
                                    host,
                                    host,
                                    url = item.attr("href")
                                )
                            )
                        }
                    } else if (item.attr("href").contains("gdirect.cloud")) {
                        val doc =
                            app.get(item.attr("href"), timeout = 30, allowRedirects = true).document
                        val link = doc.select("a")
                        callback.invoke(
                            newExtractorLink(
                                "GDirect (VLC)",
                                "GDirect (VLC)",
                                url = link.attr("href")
                            )
                        )
                    }
                }
            }


        }

        return true
    }

    private fun getVideoQuality(string: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(string ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    data class SeasonDetail
        (
        val quality: String?,
        val episodeLinkMap: MutableMap<String, MutableList<String>>?,
        val season: String?,
    )

}