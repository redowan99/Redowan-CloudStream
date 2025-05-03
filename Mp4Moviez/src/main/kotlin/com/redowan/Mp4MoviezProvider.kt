package com.redowan

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class Mp4MoviezProvider : MainAPI() {
    override var mainUrl = "https://www.mp4moviez.capital"
    override var name = "Mp4Moviez"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.NSFW)
    override val mainPage = mainPageOf(
        "" to "Latest Updates",
        "/88/hot-web-series.html" to "Latest Hindi Hot Web Series",
        "/22/bollywwood-old-movies.html" to "Old Bollywood Movies",
        "/49/hollywood-all-movies.html" to "Hollywood Movies",
        "/42/punjabi-movies-collection.html" to "Punjabi Movies",
        "/74/latest-kannada-movies.html" to "Kannada Movies",
        "/189/netflix-series-hindi-dubbed.html" to "New Hindi Dubbed Series",
        "/167/tagalog-movies.html" to "Tagalog Hot Movies Hot",
        "/267/filipino-movies.html" to "Filipino Hot Movies Hot",
        "/296/latest-hindi-short-films-(2024).html" to "New Hindi Short Films Hot",
        "/245/ullu-web-series.html" to "ULLU Web series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {


        val doc = if (request.data == "") {
            app.get(mainUrl, allowRedirects = true, timeout = 30).document
        } else {
            app.get("$mainUrl${request.data}&p=$page", allowRedirects = true, timeout = 30).document
        }
        val home = doc.select(".fl").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = mainUrl + post.select("a").attr("href")
        val title = post.select("img").attr("alt")
        val imageUrl = mainUrl + post.select("img").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc =
            app.get("$mainUrl/search/$query.html", allowRedirects = true, timeout = 30).document
        val searchResponse = doc.select(".fl")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, allowRedirects = true, timeout = 30).document
        val title = doc.select(".moviename").text()
        val imageUrl = mainUrl + doc.select(".posterss").attr("src")
        val plot = doc.select(".description").text()
        val releasedDate =
            "(\\d{4})".toRegex().find(doc.select(".releasedate").text())?.groups?.get(1)?.value
        val rating = doc.select(".duration").text()
        val link = mainUrl + doc.select("div[style=\"text-align:left;\"] a").attr("href")
        return newMovieLoadResponse(title, url, TvType.Movie, link) {
            this.posterUrl = imageUrl
            this.plot = plot.trim()
            if (!rating.contains("N/A")) this.rating = rating.toFloat().toInt()
            this.year = releasedDate?.toInt()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val links = doc.select("div[style=\"text-align:left;\"]")
        links.forEach { item ->
            val link = item.select("a").attr("href")
            if (!link.contains("links4mad.online")) {
                callback.invoke(
                    newExtractorLink(
                        "FastxMp4",
                        "FastxMp4",
                        url = link,
                    ) {
                        quality = getVideoQuality(link)
                    }
                )
            } else if (link.contains("links4mad.online")) {
                val shortLinkUrl = item.select("a").attr("href")
                val sDoc = app.post(shortLinkUrl).document
                val links1 = sDoc.select(".col-sm-8.col-sm-offset-2.well.view-well a")
                links1.forEach {
                    loadExtractor(it.attr("href"), subtitleCallback, callback)
                }
            }
        }

        return true
    }

    private fun getVideoQuality(string: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(string ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}