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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class RtallyProvider : MainAPI() {
    override var mainUrl = "https://rtally.vercel.app"
    override var name = "Rtally"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.AnimeMovie,
        TvType.NSFW,
        TvType.Anime
    )

    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "5" to "Trending",
        "6" to "Featured",
        "7" to "TV-Drama",
        "8" to "Adult 18+",
        "9" to "Anime",
        "10" to "Bangladeshi",
        "11" to "Indian",
        "12" to "Hollywood"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            mainUrl,
            cacheTime = 60,
            headers = mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
        ).document
        val categories = doc.select("div.p-2.space-y-10:nth-child(${request.data})")
        val home = categories.select("a.relative.space-y-2").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst(".line-clamp-1")?.text() ?: ""
        val check = post.select("span.absolute:nth-child(5)").text().lowercase()
        val url = mainUrl + post.select("a.relative")
            .attr("href")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select(".object-cover")
                .attr("src")
            addDubStatus(
                dubExist = when {
                    "dual" in check -> true
                    else -> false
                },
                subExist = when {
                    "sub" in check -> true
                    else -> false
                }
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(
            "$mainUrl/search/$query",
            cacheTime = 60,
            headers = mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
        ).document
        return doc.select("a.relative.space-y-2").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(
            url,
            cacheTime = 60,
            headers = mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
        ).document
        val title = doc.select("h3.text-3xl.font-semibold").text()
        val image = doc.select(".p-\\[5px\\]").attr("src")
        val plot = doc.selectFirst("p.text-sm:nth-child(3)")?.text()
        val download = doc.select(".gap-4 > div > a:nth-child(1)")
        if (download.isNotEmpty()) {
            var links = ""
            download.forEach { links += it.attr("href") + " ; " }
            return newMovieLoadResponse(title, url, TvType.Movie, links) {
                this.posterUrl = image
                this.plot = plot
            }
        } else {
            val episodesData = mutableListOf<Episode>()
            var episodeNum = 0
            doc.select("div.space-x-2.px-2").forEach {
                episodeNum++
                val link = it.select(".grid > a:nth-child(1)").attr("href")
                val name = it.select(".line-clamp-1").text().replace("$title ", "")
                episodesData.add(
                    Episode(
                        link,
                        name,
                        null,
                        episodeNum
                    )
                )
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.posterUrl = image
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.split(" ; ").forEach {
            if (it.contains("filemoon")) loadExtractor(
                it.replace("/download/", "/e/"),
                subtitleCallback,
                callback
            )
            else if (it.contains("vidhidepre")) loadExtractor(
                it.replace("/d/", "/v/"),
                subtitleCallback,
                callback
            )
        }
        return true
    }
}