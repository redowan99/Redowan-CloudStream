package com.redowan

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class DflixMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://movies.discoveryftp.net"
    override var name = "(BDIX) Dflix Movies"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.AnimeMovie
    )
    override val mainPage = mainPageOf(
        "category/Bangla" to "Bangla",
        "category/English" to "English",
        "category/Hindi" to "Hindi",
        "category/Tamil" to "Tamil",
        "category/Animation" to "Animation",
        "category/Others" to "Others"
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
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/m/${request.data}/$page", referer = "$mainUrl/m", timeout = 30L).document
        val homeResponse = doc.select("div.card")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val rawHref = post.select("div.card > a:nth-child(1), a.cfocus, a").attr("href")
        val url = if (rawHref.startsWith("http")) rawHref else mainUrl + rawHref
        val title = post.select("div.card > div:nth-child(2) > h3:nth-child(1), h3").text() + ' ' +
                post.select("div.feedback > span:nth-child(1)").text()
        val posterSrc = post.selectFirst("div.poster > img:nth-child(1), img")?.attr("src")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = fixImageUrl(posterSrc)
            val check = post.select("div.card > a:nth-child(1) > span:nth-child(1)").text()
            this.quality = getSearchQuality(check)
            addDubStatus(
                dubExist = "DUAL" in check,
                subExist = false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/m/find/$query", referer = "$mainUrl/m", timeout = 30L).document
        val searchResponse = doc.select("div.card:not(:has(div.poster.disable))")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = "$mainUrl/m", timeout = 30L).document
        val title = doc.select(".movie-detail-content > h3:nth-child(1)").text()
        val dataUrl = doc.select("div.col-md-12:nth-child(3) > div:nth-child(1) > a:nth-child(1)")
            .attr("href")
        val size = doc.select(".badge.badge-fill").text()
        val img = fixImageUrl(doc.select(".movie-detail-banner > img:nth-child(1)").attr("src"))
        return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
            this.posterUrl = img
            this.plot = "<b>$size</b><br><br>" + doc.select(".storyline").text()
            this.tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
            this.actors = doc.select("div.col-lg-2").map { actor(it) }
            this.recommendations = doc.select("div.badge-outline > a").map { qualityRecommendations(it, title, img ?: "") }
        }
    }

    private fun qualityRecommendations(post: Element, title: String, imageLink: String): SearchResponse {
        val movieName = title + " " + post.text()
        val rawHref = post.attr("href")
        val movieUrl = if (rawHref.startsWith("http")) rawHref else mainUrl + rawHref
        return newMovieSearchResponse(movieName, movieUrl, TvType.Movie) {
            this.posterUrl = imageLink
        }
    }

    private fun actor(post: Element): ActorData {
        val imgTag = post.selectFirst("div.col-lg-2 > a:nth-child(1) > img:nth-child(1), img")
        val img = fixImageUrl(imgTag?.attr("src"))
        val name = imgTag?.attr("alt")?.ifBlank { post.select("p").firstOrNull()?.text() } ?: ""
        val role = post.select("p.text-center.text-white").text()
        return ActorData(
            actor = Actor(
                name,
                img
            ),
            roleString = role
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

    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("4k") -> SearchQuality.FourK
                lowercaseCheck.contains("web-r") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("br") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains(
                    "hdtc"
                ) -> SearchQuality.HdCam

                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("hd") || lowercaseCheck.contains("1080p") -> SearchQuality.HD
                else -> null
            }
        }
        return null
    }
}