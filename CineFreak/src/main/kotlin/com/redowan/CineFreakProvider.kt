package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
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
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(CineFreakProvider())
////    providerTester.testAll()
//    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
////    providerTester.testLoadLinks("https://neodrive.xyz/f/3c861172+https://neodrive.xyz/f/8f007c1f+https://neodrive.xyz/f/600f7e15")
//}

class CineFreakProvider : MainAPI() {
    override var mainUrl = "https://cinefreak.net"
    override var name = "CineFreak"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AnimeMovie)
    override val mainPage = mainPageOf(
        "/" to "Latest Releases",
        "/category/animation/" to "Animation",
        "/category/bangla-dubbed/" to "Bangla Dubbed",
        "/category/chinese/" to "Chinese",
        "/category/bangla-movies/" to "Bangla Movies",
        "/category/dual-audio/" to "Dual Audio",
        "/category/english-movies/" to "English Movies",
        "/category/hindi-movies/" to "Hindi Movies",
        "/category/indonesian/" to "Indonesian",
        "/category/japanese/" to "Japanese",
        "/category/korean/" to "Korean",
        "/category/turkish/" to "Turkish",
        "/category/south-hindi-movies/" to "South Hindi Movies",
        "/category/web-series/" to "WEB-Series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}page/$page").document
        val home = doc.select(".post").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select(".post-thumbnail").attr("href")
        val title = post.select(".entry-title a").text()
        var imageUrl = post.select(".post-thumbnail img").attr("src")
        if (imageUrl.isEmpty()) {
            imageUrl = post.select(".post-thumbnail img").attr("data-src")
        }
        val quality = post.select(".video-label").text()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
            if (quality.isNotEmpty()) {
                this.quality = getSearchQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val searchResponse = doc.select(".post")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("title").text()
        var imageUrl = doc.select(".post-thumbnail img").attr("src")
        if (imageUrl.isEmpty()) {
            imageUrl = doc.select(".post-thumbnail img").attr("data-src")
        }
        var plot = ""
        val eList = doc.selectFirst(".single-service-content")
        eList?.children()?.forEach { item ->
            if (item.tagName() == "h3" && item.text().contains("Storyline")) {
                plot = item.nextElementSibling()?.text() ?: ""
            }
        }

        val dwnLinks = doc.selectFirst(".download-links-div")
        val episodeData = mutableListOf<Episode>()
        var episodeNo = 1
        val linkList = mutableListOf<String>()
        dwnLinks?.children()?.forEach { item ->
            if (item.tagName() == "h4" && item.text().lowercase()
                    .contains("season") && item.nextElementSibling()?.text()?.lowercase()
                    ?.contains("episode") == true
            ) {
                val season =
                    "Season(\\s\\d*)".toRegex().find(item.text())?.groups?.get(1)?.value?.trim()
                val episode = "Episode\\s(\\d*-\\d*)|".toRegex()
                    .find(item.nextElementSibling()!!.text())?.groups?.get(1)?.value?.trim()
                val dlLinks = item.nextElementSibling()?.nextElementSibling()?.select("a")
                val list = mutableListOf<String>()
                dlLinks?.forEach {
                    list.add(it.attr("href"))
                }
                if (!season.isNullOrEmpty() && !episode.isNullOrEmpty()) {
                    episodeData.add(
                        newEpisode(list.joinToString("+")){
                            this.name = episode
                            this.season = season.toInt()
                            this.episode = episodeNo
                        }
                    )
                }
                episodeNo++
            } else if (item.tagName() == "h4" && !item.nextElementSibling()?.text()?.lowercase()
                    ?.contains("episode")!!
            ) {
                val dlLinks: Elements? =
                    if (item.nextElementSibling()!!.className() == "downloads-btns-div") {
                        item.nextElementSibling()!!.select("a")
                    } else {
                        item.nextElementSibling()!!.nextElementSibling()?.select("a")
                    }
                dlLinks?.forEach {
                    linkList.add(it.attr("href"))
                }
            }

        }

        return if (episodeData.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeData) {
                this.posterUrl = imageUrl
                if (plot.isNotEmpty()) {
                    this.plot = plot
                }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, linkList.joinToString("+")) {
                this.posterUrl = imageUrl
                if (plot.isNotEmpty()) {
                    this.plot = plot
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
        links.forEach { link ->
            val document = app.get(link, timeout = 30).document
            val buttons = document.select(".card-body button")

            buttons.firstOrNull {
                it.text().contains("ZenCloud") || it.text().contains("Instant Download")
            }
                ?.let { extractZenCloudLink(it, callback) }

//            buttons.firstOrNull { it.text().contains("Cloud [Resumable]") }
//                ?.let { extractNeoDriveLink(link, quality, callback) }
        }
        return true
    }

    private val providerZenCloud = "ZenCloud"
    //private val providerNeoDrive = "NeoDrive"
    private val redirectRegex = "location.href='(.*)'".toRegex()
    private suspend fun extractZenCloudLink(
        button: Element,
        callback: (ExtractorLink) -> Unit
    ) {
        val redirectUrl = redirectRegex
            .find(button.attr("onclick"))?.groupValues?.getOrNull(1)
        if (!redirectUrl.isNullOrEmpty()) {
            val doc = app.get(redirectUrl, timeout = 30).document
            val onClickLink = doc.select("#vd").attr("onclick")
            val downloadUrl = redirectRegex
                .find(onClickLink)?.groupValues?.getOrNull(1)
            callback.invoke(
                newExtractorLink(
                    providerZenCloud,
                    providerZenCloud,
                    downloadUrl.orEmpty()
                )
            )
        }
    }

//    private suspend fun extractNeoDriveLink(
//        link: String,
//        quality: Int,
//        callback: (ExtractorLink) -> Unit
//    ) {
//        val dlLink = link.replace("/f/", "/d/")
//        val doc = app.get(dlLink, timeout = 30).document
//        val downloadButton = doc.select(".card-body button:contains(Download Now)").firstOrNull()
//        downloadButton?.let {
//            val redirectUrl = redirectRegex
//                .find(it.attr("onclick"))?.groupValues?.getOrNull(1)
//            callback.invoke(
//                ExtractorLink(
//                    providerZenCloud,
//                    providerNeoDrive,
//                    redirectUrl.orEmpty(),
//                    "",
//                    quality
//                )
//            )
//        }
//    }

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

                lowercaseCheck.contains("telesync") -> SearchQuality.Telesync
                lowercaseCheck.contains("telecine") -> SearchQuality.Telecine
                else -> null
            }
        }
        return null
    }
}