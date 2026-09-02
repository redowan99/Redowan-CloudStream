package com.redowan

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
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
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.FormBody
import org.jsoup.nodes.Element

class DflixSeriesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://movies.discoveryftp.net"
    override var name = "(BDIX) Dflix Series"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime,
        TvType.Documentary,
        TvType.Cartoon
    )
    override val mainPage = mainPageOf(
        "category/Foreign" to "English",
        "category/Bangla" to "Bangla",
        "category/Hindi" to "Hindi",
        "category/South" to "South",
        "category/Animation" to "Animation",
        "category/Dubbed" to "Dubbed"
    )

    private fun fixImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        var fixed = url.trim()
        if (fixed.startsWith("//")) {
            fixed = "https:$fixed"
        } else if (fixed.startsWith("http://")) {
            fixed = fixed.replace("http://", "https://")
        } else if (!fixed.startsWith("http")) {
            fixed = "$mainUrl/${fixed.removePrefix("/")}"
        }
        return fixed.replace("300//", "300/").replace("1080//", "1080/").replace("media//", "media/")
    }

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/s/${request.data}/$page", referer = "$mainUrl/s", timeout = 30L).document
        val homeResponse = doc.select("div.col-xl-4")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val rawHref = post.selectFirst("div > a:nth-child(1)")?.attr("href") ?: ""
        val url = if (rawHref.startsWith("http")) rawHref else mainUrl + rawHref
        val title = post.select("div.fcard > div:nth-child(2) > div:nth-child(1)").text()
        return newMovieSearchResponse(title, url, TvType.TvSeries) {
            this.posterUrl = fixImageUrl(post.selectFirst("img:nth-child(1)")?.attr("src"))
        }
    }

    private fun toSearchResult(post: Element): SearchResponse {
        val rawHref = post.selectFirst("a")?.attr("href") ?: ""
        val url = if (rawHref.startsWith("http")) rawHref else mainUrl + rawHref
        val title = post.select("div.searchtitle").text()
        return newMovieSearchResponse(title, url, TvType.TvSeries) {
            this.posterUrl = fixImageUrl(post.selectFirst("img:nth-child(1)")?.attr("src"))
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val requestBody = FormBody.Builder()
            .add("term", query)
            .add("types", "s")
            .build()
        val doc = app.post("$mainUrl/search", referer = "$mainUrl/s", requestBody = requestBody, timeout = 30L).document
        val searchResponse = doc.select("div.moviesearchiteam > a")
        return searchResponse.mapNotNull { post ->
            toSearchResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = "$mainUrl/s", timeout = 30L).document
        val title = doc.select(".movie-detail-content-test > h3, .movie-detail-content > h3").text()
        val img = fixImageUrl(doc.select(".movie-detail-banner > img:nth-child(1)").attr("src"))

        val episodesData = mutableListOf<Episode>()
        var seasonNum = 0
        doc.select("table.table:nth-child(1) > tbody:nth-child(1) > tr a").reversed()
            .forEach { season ->
                seasonNum++
                extractedSeason(seasonNum, season, episodesData)
            }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
            this.posterUrl = img
            this.plot = doc.select(".storyline").text()
            this.tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
            this.actors = doc.select("div.col-lg-2").map { actor(it) }
        }
    }

    private suspend fun extractedSeason(
        seasonNum: Int,
        season: Element?,
        episodesData: MutableList<Episode>
    ) {
        var episodeNum = 0
        val rawHref = season?.attr("href") ?: ""
        val seasonUrl = if (rawHref.startsWith("http")) rawHref else mainUrl + rawHref
        val seasonDoc = app.get(seasonUrl, referer = "$mainUrl/s", timeout = 30L).document
        seasonDoc.select("div.container:nth-child(6) > div, div[style*='background-image'], .card").forEach { episode ->
            val episodeName = episode.selectFirst("h5, h4")?.text()?.trim() ?: "Episode"
            val episodeStyle = episode.attr("style")
            val rawImage = extractBGImageUrl(episodeStyle)
            val episodeImage = fixImageUrl(rawImage)
            val episodeDescription = episode.selectFirst("div.season_overview, .card-body, p")?.text()?.trim()
            val episodeLink = episode.select("div.mt-2 > h5 > a, a.btn, h5 > a").attr("href")
            if (episodeLink.isNotBlank()) {
                episodeNum++
                val fullEpLink = if (episodeLink.startsWith("http")) episodeLink else mainUrl + episodeLink
                episodesData.add(
                    newEpisode(fullEpLink) {
                        this.name = episodeName
                        this.posterUrl = episodeImage
                        this.season = seasonNum
                        this.episode = episodeNum
                        this.description = episodeDescription
                    }
                )
            }
        }
    }

    private val bgImageRegex = Regex("""url\(['"]?(.*?)['"]?\)""")
    private fun extractBGImageUrl(text: String): String? {
        val matchResult = bgImageRegex.find(text)
        return matchResult?.groupValues?.get(1)
    }

    private fun actor(post: Element): ActorData {
        val imgTag = post.selectFirst("div.col-lg-2 > a:nth-child(1) > img:nth-child(1), img")
        val img = fixImageUrl(imgTag?.attr("src"))
        val name = imgTag?.attr("alt")?.ifBlank { post.select("p").firstOrNull()?.text() } ?: ""
        val role = post.select("p.text-center.text-white, p:nth-of-type(2)").text()
        return ActorData(
            actor = Actor(
                name, img
            ), roleString = role
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = data,
                type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }
}