package com.redowan


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
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element



class EmwBDProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.emwbd.xyz"
    override var name = "EmwBD"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.NSFW,
        TvType.AsianDrama,
        TvType.AnimeMovie,
    )

    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "/" to "Latest Movies",
        /*"/category/bangla-dubbed/page/" to "Bangla Dubbed",
        "/category/dual-audio-movies/page/" to "Multi Audio Movies",
        "/category/tv-series/page/" to "TV Series",
        "/category/foreign-language-film/page/" to "Foreign Language Film",
        "/category/bollywood-movies/page/" to "Bollywood Movies",
        "/category/bangla-movies/page/" to "Bengali Movies",
        "/category/hollywood-movies/page/" to "Hollywood Movies",
        "/category/natok-teleflim/page/" to "Natok & Teleflim",
        "/category/unrated/page/" to "UnRated"*/
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if(page == 1) mainUrl + request.data
        else "$mainUrl${request.data}page/$page/"
        val doc = app.get(url, cacheTime = 60, allowRedirects = true, timeout = 5, headers =  mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")).document
        val home = doc.select(".thumb.col-md-2.col-sm-4.col-xs-6").mapNotNull {toResult(it)}
        return newHomePageResponse(request.name,home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select(".titl").text()
        val url = post.select(".thumb > div > a").attr("href")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select(".thumb > figure > img")
                .attr("src")
            this.quality = when {
                "webrip" in title -> SearchQuality.WebRip
                "web-dl" in title -> SearchQuality.WebRip
                "bluray" in title -> SearchQuality.BlueRay
                "hdts" in title -> SearchQuality.HdCam
                "dvd" in title -> SearchQuality.DVD
                "cam" in title -> SearchQuality.Cam
                "camrip" in title -> SearchQuality.CamRip
                "hdcam" in title -> SearchQuality.HdCam
                "hdtc" in title -> SearchQuality.HdCam
                "hdrip" in title -> SearchQuality.HD
                "hd" in title -> SearchQuality.HD
                "hdtv" in title -> SearchQuality.HD
                "rip" in title -> SearchQuality.CamRip
                else -> null
            }
            addDubStatus(
                dubExist = when {
                    "dubbed" in title -> true
                    "dual audio" in title -> true
                    "multi audio" in title -> true
                    else -> false
                },
                subExist = when {
                    "Esub" in title -> true
                    "Esubs" in title -> true
                    else -> false
                }
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", cacheTime = 60, allowRedirects = true, timeout = 5, headers =  mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")).document
        return doc.select(".thumb.col-md-2.col-sm-4.col-xs-6").mapNotNull {toResult(it)}
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60, allowRedirects = true, timeout = 5, headers =  mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")).document
        val title = doc.select("h1.page-title > span").text()
        val year = "(?<=\\()\\d{4}(?=\\))".toRegex().find(title)?.value?.toIntOrNull()
        val image = doc.select("div.block-head:nth-child(1) > p > img").attr("src")
        val link = doc.select("div.block-head:nth-child(3) > h5 > a").attr("href")
        return newMovieLoadResponse(title, url, TvType.Movie, link) {
            this.posterUrl = image
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = if (data.contains("playerwish"))data.replace("playerwish.com","mwish.pro")
                    else ""
        loadExtractor(link, subtitleCallback, callback)
        return true
    }
}