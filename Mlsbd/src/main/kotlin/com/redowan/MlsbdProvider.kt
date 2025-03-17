package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
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
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element


//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(MlsbdProvider())
////    providerTester.testLoadLinks("https://savelinks.me/view/FdfZ1kEO ; ")
//    providerTester.testLoadLinks("https://savelinks.me/view/EwKQsnWG ; ")
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("https://mlsbd.shop/sentimentaaal-2024-bengali-hdtv-rip-480p-720p-1080p-x264-500mb-1-1gb-2-8gb-download-watch-online/")
//}

class MlsbdProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://mlsbd.shop"
    override var name = "Mlsbd"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val mainPage = mainPageOf(
        "" to "Latest Movies",
        "/category/bangla-dubbed/page/" to "Bangla Dubbed",
        "/category/dual-audio-movies/page/" to "Multi Audio Movies",
        "/category/tv-series/page/" to "TV Series",
        "/category/foreign-language-film/page/" to "Foreign Language Film",
        "/category/bollywood-movies/page/" to "Bollywood Movies",
        "/category/bangla-movies/page/" to "Bengali Movies",
        "/category/hollywood-movies/page/" to "Hollywood Movies",
        "/category/natok-teleflim/page/" to "Natok & Teleflim",
        "/category/unrated/page/" to "UnRated"
    )
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.NSFW,
        TvType.AsianDrama,
        TvType.AnimeMovie,
    )
    private val headers =
        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (request.data == "") mainUrl
        else "$mainUrl${request.data}$page/"
        val doc = app.get(
            url,
            cacheTime = 60,
            allowRedirects = true,
            timeout = 60,
            headers = headers
        ).document
        val homeResponse = doc.select("div.single-post")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = true),
            true
        )
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select(".post-title").text()
        val url = post.select(".thumb > a").attr("href")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select(".thumb>a>picture>img:nth-child(3)")
                .attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc =
            app.get("$mainUrl/?s=$query", cacheTime = 60, timeout = 30, headers = headers).document
        val searchResponse = doc.select("div.single-post")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60, timeout = 30, headers = headers).document
        val title = doc.select(".name").text()
        val year = getYearFromString(title)
        val image = doc.select("img.aligncenter").attr("src")
        val plot = doc.select(".single-post-title").text() + "<br>" +
                doc.select(".misc").text() + "<br>" +
                doc.select(".details").text() + "<br>" +
                doc.select(".storyline").text() + "<br>" +
                doc.select(".production").text() + "<br>" +
                doc.select(".media").text()

        val episodeDivs = doc.select("div.post-section-title.download").reversed()
        var link = ""
        when (episodeDivs.size) {
            1 -> {
                episodeDivs[0].nextElementSibling()?.nextElementSibling()
                    ?.select("a.Dbtn.hd, a.Dbtn.sd, a.Dbtn.hevc")
                    ?.forEach {
                        link += it.attr("href") + " ; "
                    }
                return newMovieLoadResponse(title, url, TvType.Movie, link) {
                    this.posterUrl = image
                    this.year = year
                    this.plot = plot
                }
            }

            0 -> return newMovieLoadResponse(title, url, TvType.Movie, "") {
                this.posterUrl = image
                this.year = year
                this.plot = plot
            }

            else -> {
                val episodesData = mutableListOf<Episode>()
                for (episodeDiv in episodeDivs) {
                    var episodeUrl = ""
                    var episodeNum = 0


                    var downloadLink = episodeDiv.nextElementSibling()?.nextElementSibling()

                    //480p
                    episodeUrl += downloadLink?.selectFirst("a")?.attr("href") + " ; "

                    //720p
                    downloadLink = downloadLink?.nextElementSibling()
                    episodeUrl += downloadLink?.selectFirst("a")?.attr("href") + " ; "

                    //1080p
                    downloadLink = downloadLink?.nextElementSibling()
                    episodeUrl += downloadLink?.selectFirst("a")?.attr("href")

                    episodeNum++
                    episodesData.add(
                        newEpisode(episodeUrl) {
                            this.name = "Episode $episodeNum"
                            this.episode = episodeNum
                        }
                    )
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                    this.posterUrl = image
                    this.year = year
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
        data.split(" ; ").forEach { link ->
            if (link.contains("savelinks")) {
                val html = app.get(
                    link,
                    cacheTime = 60,
                    timeout = 30,
                    headers = headers
                )
                val doc = html.document
                val input = doc.select("form.flex > input:nth-child(2)")
                val mediaType = "text/plain".toMediaType()
                val body = "${input.attr("name")}=${input.attr("value")}".toRequestBody(mediaType)
                val cookieHeaderValue = html.cookies.map { (name, value) -> "$name=$value" }
                    .joinToString(separator = "; ")
                val unlockDoc = app.post(
                    "${link.replace("/view", "")}unlock",
                    requestBody = body,
                    allowRedirects = true,
                    cacheTime = 60,
                    timeout = 30,
                    headers = mapOf("Cookie" to cookieHeaderValue, "Host" to "savelinks.me")
                ).document
                unlockDoc.select("li.py-2 > a").forEach {
                    val url = it.attr("href")
                    if (url.contains("filepress")) filePress(url, subtitleCallback, callback)
                }
            }
        }
        return true
    }

    private suspend fun filePress(
        url: String, subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val json = app.get(
            url.replace("/file/", "/api/file/"),
            cacheTime = 60,
            timeout = 30,
            headers = mapOf("Referer" to url)
        ).text
        AppUtils.parseJson<EmbedUrl>(json).data.alternativeSource.forEach {
            if (it.url.contains("embed")) {
                val streamWishUrl = app.get(
                    it.url,
                    cacheTime = 60,
                    timeout = 30
                ).document.select("iframe").attr("src")
                println(streamWishUrl)
                loadExtractor(streamWishUrl, subtitleCallback, callback)
            }

        }
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

    data class EmbedUrl(
        val `data`: Data = Data()
    )

    data class Data(
        val alternativeSource: List<AlternativeSource> = listOf()
    )

    data class AlternativeSource(
        val url: String = "" // https://v1.sdsp.xyz/embed/movie/811941
    )

}