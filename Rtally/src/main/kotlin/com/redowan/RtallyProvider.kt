package com.redowan

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass

class RtallyProvider : MainAPI() {
    override var mainUrl = "https://rtally.vercel.app"
    override var name = "Rtally"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.AnimeMovie,
        TvType.Anime
    )

    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "Trending" to "Trending",
        "Featured" to "Featured",
        "Tv-Shows" to "Tv Shows",
        "Anime" to "Anime",
        "Bengali" to "Bangladeshi",
        "Bollywood" to "Indian",
        "Hollywood" to "Hollywood"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "https://12wlmtcp.api.sanity.io/v2023-03-01/data/query/production?query=%0A*%5B_type+%3D%3D+%22movie%22+%26%26+%24keyword+in+categories%5B%5D-%3Etitle%5D+%7C++order%28_createdAt++desc%29+%7C+order%28released++desc%29%5B0...%24end%5D%7B%0A++++...%2C%0A++++type%5B%5D-%3E%2C%0A++++categories%5B%5D-%3E%2C%0A++++%2F%2F+genres%5B%5D-%3E%2C%0A++++language%5B%5D-%3E%2C%0A++++quality%5B%5D-%3E%2C%0A++++year%5B%5D-%3E%2C%0A++++dramasLink%5B%5D-%3E%2C%0A++++%22count%22%3A+count%28*%5B_type+%3D%3D+%22movie%22+%26%26+%24keyword+in+categories%5B%5D-%3Etitle%5D%29%0A%7D&%24keyword=%22${request.data}%22&%24end=14"
        val doc = app.get(
            url,
            cacheTime = 60,
            responseParser = parser,
            headers = mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
        ).parsed<Post>()
        val home = doc.result.map { toHomeResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toHomeResult(post: Result): SearchResponse {
        val url = if (post.slug.current.contains("https")) post.slug.current
            else "$mainUrl/post/${post.slug.current}"
        return newAnimeSearchResponse(post.title, url, TvType.Movie) {
            this.posterUrl = post.otherImg
            addDubStatus(
                dubExist = when {
                    "dual" in post.language.first().title -> true
                    else -> false
                },
                subExist = false
            )
        }
    }

    private fun toSearchResult(post: Element): SearchResponse {
        val title = post.selectFirst(".line-clamp-1")?.text() ?: ""
        val check = post.select("span.absolute:nth-child(5)").text().lowercase()
        val link = post.select("a.relative").attr("href")
        val url = if (link.contains("https")) link
            else mainUrl + link
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select(".object-cover")
                .attr("src")
            addDubStatus(
                dubExist = when {
                    "dual" in check -> true
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
            headers = mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
        ).document
        return doc.select("a.relative.space-y-2").mapNotNull { toSearchResult(it) }
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
        val duration = selectUntilNonInt(doc.select("div.space-x-4:nth-child(2) > span:nth-child(3)").text())
        val year = selectUntilNonInt(doc.select("div.grid:nth-child(3) > p:nth-child(2) > a:nth-child(1) > span:nth-child(1)").text())
        if (download.isNotEmpty()) {
            var links = ""
            download.forEach { links += it.attr("href") + " ; " }
            return newMovieLoadResponse(title, url, TvType.Movie, links) {
                this.posterUrl = image
                this.plot = plot
                this.duration = duration
                this.year = year
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
                this.duration = duration
                this.year = year
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

    data class Post(
        val result: List<Result>
    )

    data class Result(
        val language: List<Language>,
        val otherImg: String,
        val slug: Slug,
        val title: String
    )

    data class Language(
        val title: String
    )

    data class Slug(
        val current: String
    )

    private val parser = object : ResponseParser {
        val mapper: ObjectMapper = jacksonObjectMapper().configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )

        override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
            return mapper.readValue(text, kClass.java)
        }

        override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
            return try {
                mapper.readValue(text, kClass.java)
            } catch (e: Exception) {
                null
            }
        }

        override fun writeValueAsString(obj: Any): String {
            return mapper.writeValueAsString(obj)
        }
    }

    private fun selectUntilNonInt(string: String): Int?{
        return Regex("^.*?(?=\\D|\$)").find(string)?.value?.toInt()
    }
}