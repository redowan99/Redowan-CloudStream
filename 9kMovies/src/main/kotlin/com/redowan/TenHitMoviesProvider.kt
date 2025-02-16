package com.redowan

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class TenHitMoviesProvider : NineKMoviesProvider() {
    override var mainUrl = "https://10hitmovies.com/"
    override var name = "10HitMovies"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.NSFW
    )
    override val mainPage = mainPageOf(
        "" to "Latest Movies",
        "/category/18-movies/" to "18+ Movies",
        "/category/dual-audio/" to "Dual Audio",
        "/category/hindi-dubbed/" to "Hindi Dubbed"
        )

 
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("span.material-text").text()
        val imageUrl = doc.select(".page-body img").attr("src")
        val info = doc.select(".page-body p:nth-of-type(1)").text()
        val story = ("(?<=Storyline,).*|(?<=Story : ).*|(?<=Storyline : ).*|(?<=Description : ).*|(?<=Description,).*(?<=Story,).*")
            .toRegex().find(info)?.value
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = imageUrl
            if (story != null) {
                this.plot = story.trim()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val buttonElements = doc.select("a[href^=https://mysavelinks]")
        buttonElements.forEach { item ->
            val shortLinkUrl = item.attr("href")
            val sDoc = app.post(shortLinkUrl).document
            val links = sDoc.select(".col-sm-8.col-sm-offset-2.well.view-well a")
            links.forEach {
                val link = it.attr("href")
                loadExtractor(link, subtitleCallback, callback)
            }
        }
        return true
    }
}