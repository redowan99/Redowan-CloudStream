package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(RtallyProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("https://rtally.vercel.app/post/from-season-1")
////    providerTester.testLoad("https://rtally.vercel.app/post/the-substance")
////    providerTester.testLoad("https://rtally.vercel.app/post/all-of-us-are-dead-season-1")
//    providerTester.testLoad("https://rtally.vercel.app/post/bigg-boss-season-18")
//}

class RtallyProvider : MainAPI() {
    override var mainUrl = "https://rtally.vercel.app"
    override var name = "Rtally"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.AnimeMovie,
        TvType.Anime
    )
    override val mainPage = mainPageOf(
        "/categories/trending" to "Trending",
        "/categories/featured" to "Featured",
        "/categories/hollywood" to "Hollywood",
        "/categories/bengali" to "Bangla",
        "/categories/bollywood" to "Bollywood",
        "/categories/tv-shows" to "Tv Shows",
        "/categories/korean" to "Korean",
        "/categories/anime" to "Anime"
    )
    private val headers =
        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            "$mainUrl${request.data}?page=$page",
            cacheTime = 60,
            headers = headers
        ).document
        val home = doc.select("div.grid:nth-child(1) > a[href]:not([target])").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select("h2").text()
        val check = post.select("div.absolute:nth-child(4)").text()
        val url = mainUrl + (post.selectFirst("a")?.attr("href") ?: "")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select(".relative.border.border-gray-500.rounded-lg.group img")
                .attr("src")
            addDubStatus(
                dubExist = when {
                    "Dual" in check -> true
                    else -> false
                },
                subExist = false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(
            "$mainUrl/search/$query",
            cacheTime = 60,
            headers = headers
        ).document
        return doc.select("div.grid:nth-child(1) > a[href]:not([target])").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(
            url,
            cacheTime = 60,
            headers = headers
        ).document
        val title = doc.select(".font-serif").text()
        val image = doc.selectFirst(".w-\\[200px\\] > img:nth-child(1)")?.attr("src")
        val plot = doc.selectFirst("p.text-sm:nth-child(2)")?.text()
        val year = doc.select("div.infoDiv:nth-child(7) > span:nth-child(2)").text().toIntOrNull()
        val recommendations = doc.select(".gap-8").mapNotNull {
            val link = it.select("a")
            newMovieSearchResponse(link.text(), link.attr("href"), TvType.Movie)
            {
                this.posterUrl = it.select("img").attr("src")
            }
        }
        val episode = doc.select("ul.flex > li")
        if (episode.isNotEmpty()) {
            val episodesData = mutableListOf<Episode>()
            val scriptHtml = doc.select("script").joinToString { it.html() }.replace("\\", "")
            val linkList: MutableList<String> = mutableListOf()
            doc.select("div.justify-center:nth-child(2) > a").forEach {
                val link = it.attr("href")
                when {
                    //Filemoon
                    link.contains("filemoon") -> extractFileMoonUrls(scriptHtml)?.split(",")
                        ?.forEachIndexed { index, id ->
                            if (index in linkList.indices) {
                                linkList[index] += "https://filemoon.sx/e/$id ; "
                            } else {
                                linkList.add("https://filemoon.sx/e/$id ; ")
                            }
                        }
                    //Vidhideplus
                    link.contains("vidhideplus") -> extractVidhideplus(scriptHtml)?.split(",")
                        ?.forEachIndexed { index, id ->
                            if (index in linkList.indices) {
                                linkList[index] += "https://vidhideplus.com/v/$id ; "
                            } else {
                                linkList.add("https://vidhideplus.com/v/$id ; ")
                            }
                        }
                    //StreamWish
                    link.contains("wish") -> extractStreamwishUrls(scriptHtml)?.split(",")
                        ?.forEachIndexed { index, id ->
                            if (index in linkList.indices) {
                                linkList[index] += "https://playerwish.com/e/$id ; "
                            } else {
                                linkList.add("https://playerwish.com/e/$id ; ")
                            }
                        }
                }
            }
            linkList.forEachIndexed {index, it ->
                episodesData.add(
                    newEpisode(it)
                )
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.posterUrl = image
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }

        } else {
            var links = ""
            doc.select("div.justify-center:nth-child(2) > a").forEach {
                links += downloadToEmbedUrl(it)
            }
            return newMovieLoadResponse(title, url, TvType.Movie, links) {
                this.posterUrl = image
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        }
    }

    private fun downloadToEmbedUrl(urlElement: Element): String {
        val url = urlElement.attr("href")
        return when {
            //Filemoon
            url.contains("filemoon") -> url.replace("/download/", "/e/") + " ; "
            //Vidhideplus
            url.contains("vidhideplus") -> url.replace("/download/", "/v/") + " ; "
            //Vidhidepre
            url.contains("vidhidepre") -> url.replace("/d/", "/v/") + " ; "
            //StreamWish
            url.contains("playerwish") -> url.replace("/d/", "/e/") + " ; "
            else -> url
        }
    }

    private val fileMoonRegex = Regex("\"multiLinksDl\":\\s*\"([^\"]+)\"")
    private fun extractFileMoonUrls(text: String): String? {
        val fileMoonMatch = fileMoonRegex.find(text)
        return fileMoonMatch?.groupValues?.getOrNull(1)
    }

    private val streamwishMultiUrlRegex = Regex("\"streamwishMultiUrl\":\\s*\"([^\"]+)\"")
    private fun extractStreamwishUrls(text: String): String? {
        val streamwishMultiUrlMatch = streamwishMultiUrlRegex.find(text)
        return streamwishMultiUrlMatch?.groupValues?.getOrNull(1)
    }

    private val vidhideplusRegex = Regex("\"multiLinksSl\":\\s*\"([^\"]+)\"")
    private fun extractVidhideplus(text: String): String? {
        val vidhideplusMatch = vidhideplusRegex.find(text)
        return vidhideplusMatch?.groupValues?.getOrNull(1)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.split(" ; ").forEach {
            loadExtractor(
                it,
                subtitleCallback,
                callback
            )
        }
        return true
    }
}