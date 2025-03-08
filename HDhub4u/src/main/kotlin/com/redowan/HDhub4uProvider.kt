package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(HDhub4uProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
//    providerTester.testLoad("https://hdhub4u.supply/fateh-2025-hindi-proper-webrip-full-movie/")
////    providerTester.testLoadLinks()
//}

class HDhub4uProvider : MainAPI() {
    override var mainUrl = "https://hdhub4u.supply"
    override var name = "HDhub4u"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.NSFW
    )
    override val mainPage = mainPageOf(
        "/" to "Latest",
        "/category/bollywood-movies/" to "Bollywood",
        "/category/hollywood-movies/" to "Hollywood",
        "/category/hindi-dubbed/" to "hindi Dubbed",
        "/category/south-hindi-movies/" to "South Hindi Dubbed",
        "/category/category/web-series/" to "Web Series",
        "/category/adult/" to "Adult",
    )
    private val headers =
        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    private suspend fun getMainUrl() {
        newMainUrl.ifEmpty {
            val response =
                app.get("https://hdhublist.com/?re=hdhub", allowRedirects = true, cacheTime = 60)
            if (response.isSuccessful) {
                // Method 1: Extract from <meta http-equiv="refresh">
                val doc = response.document
                val metaRefresh = doc.selectFirst("meta[http-equiv=refresh]")
                if (metaRefresh != null) {
                    val content = metaRefresh.attr("content")
                    val urlMatch = Regex("""url=(.+)""").find(content)
                    if (urlMatch != null) {
                        newMainUrl = urlMatch.groupValues[1]
                    }
                }

                // Method 2: Extract from location.replace in <BODY>
                val bodyOnLoad = doc.selectFirst("body[onload]")
                if (bodyOnLoad != null) {
                    val onLoad = bodyOnLoad.attr("onload")
                    val urlMatch = Regex("""location\.replace\(['"](.+)['"]\)""").find(onLoad)
                    if (urlMatch != null) {
                        newMainUrl = urlMatch.groupValues[1].replace("+document.location.hash", "")
                    }
                }
            } else newMainUrl = mainUrl
        }
    }

    private var newMainUrl = ""

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        getMainUrl()
        val doc = app.get(
            "$newMainUrl/${request.data}page/$page/",
            cacheTime = 60,
            headers = headers,
            allowRedirects = true
        ).document
        val home = doc.select(".recent-movies > li.thumb").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select("figcaption:nth-child(2) > a:nth-child(1) > p:nth-child(1)").text()
        val url = post.select("figure:nth-child(1) > a:nth-child(2)").attr("href")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select("figure:nth-child(1) > img:nth-child(1)").attr("src")
            this.quality = getSearchQuality(title)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        getMainUrl()
        val doc = app.get(
            "$newMainUrl/?s=$query",
            cacheTime = 60,
            headers = headers
        ).document
        return doc.select(".recent-movies > li.thumb").mapNotNull { toResult(it) }
    }

    private fun extractLinksATags(aTags: Elements): List<String> {
        val links = mutableListOf<String>()
        val baseUrl: List<String> = listOf("https://hdstream4u.com", "https://hubstream.art")
        baseUrl.forEachIndexed { index, link ->
            var count = 0
            for (aTag in aTags) {
                val href = aTag.attr("href")
                if (href.contains(baseUrl[index])) {
                    try {
                        links[count] = links[count] + " , " + href
                    } catch (_: Exception) {
                        links.add(href)
                        count++
                    }
                }
            }
        }
        return links
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(
            url, cacheTime = 60, headers = headers
        ).document
        val title = doc.select(".page-title > span:nth-child(2)").text()
        val image = doc.select(".aligncenter").attr("src")
        val plot = doc.selectFirst(".kno-rdesc .kno-rdesc")?.text()
        val year = getYearFromString(title)
        val tags = doc.select(".page-meta em").eachText()
        val trailer =
            doc.selectFirst(".responsive-embed-container > iframe:nth-child(1)")?.attr("src")
                ?.replace("/embed/", "/watch?v=")
        val links =
            extractLinksATags(doc.select(".page-body > div a"))

        if (links.size <= 1) {
            return newMovieLoadResponse(title, url, TvType.Movie, links.first()) {
                this.posterUrl = image
                this.year = year
                this.plot = plot
                this.tags = tags
                addTrailer(trailer)

            }
        } else {
            val episodesData = mutableListOf<Episode>()
            links.forEachIndexed { index, item ->
                episodesData.add(
                    newEpisode(
                        Episode(
                            item,
                            "Episode ${index + 1}",
                        )
                    )
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.posterUrl = image
                this.year = year
                this.plot = plot
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.split(" , ").forEach {
            loadExtractor(it, subtitleCallback, callback)
        }
        return true
    }

    /**
     * Determines the search quality based on the presence of specific keywords in the input string.
     *
     * @param check The string to check for keywords.
     * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
     */
    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("webrip") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("bluray") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains(
                    "hdtc"
                ) -> SearchQuality.HdCam

                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hd") || lowercaseCheck.contains(
                    "hdtv"
                ) -> SearchQuality.HD

                else -> null
            }
        }
        return null
    }

    /**
     * Extracts a four-digit year from a string, prioritizing years in parentheses and ensuring no word characters follow.
     *
     * Example:
     *
     * "This is (2023) movie" -> 2023
     *
     * "This is 1920x1080p" -> null
     *
     * "This is 2023 movie" -> 2023
     *
     * "This is 1999-2010 TvSeries" -> 1999
     *
     * @param check The input string.
     * @return The year as an integer, or `null` if no match is found.
     */
    private fun getYearFromString(check: String?): Int? {
        return check?.let {
            parenthesesYear.find(it)?.value?.toIntOrNull()
                ?: withoutParenthesesYear.find(it)?.value?.toIntOrNull()
        }
    }

    private val parenthesesYear = "(?<=\\()\\d{4}(?=\\))".toRegex()
    private val withoutParenthesesYear = "(19|20)\\d{2}(?!\\w)".toRegex()
}