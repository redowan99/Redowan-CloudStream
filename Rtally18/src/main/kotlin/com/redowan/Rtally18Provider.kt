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
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(Rtally18Provider())
//    providerTester.testAll()
//}

class Rtally18Provider : MainAPI() {
    override var mainUrl = "https://rtally18.vercel.app"
    override var name = "Rtally18"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.NSFW
    )

    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val mainPage = mainPageOf(
        "Adult+18%2B" to "Adult 18+",
        "Philippines" to "Philippines",
        "Japan" to "Japan",
        "Indian" to "Indian"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val pageId = page * 12
        val url = "https://xotpxf3q.api.sanity.io/v2023-03-01/data/query/rtally?query=%0A*%5B_type+%3D%3D+%22movie%22+%26%26+%24keyword+in+categories%5B%5D-%3Etitle%5D+%7C++order%28_createdAt++desc%29+%7C+order%28released++desc%29%5B${pageId - 12}...%24end%5D%7B%0A++++...%2C%0A++++type%5B%5D-%3E%2C%0A++++categories%5B%5D-%3E%2C%0A++++genres%5B%5D-%3E%2C%0A++++language%5B%5D-%3E%2C%0A++++quality%5B%5D-%3E%2C%0A++++year%5B%5D-%3E%2C%0A++++%22count%22%3A+count%28*%5B_type+%3D%3D+%22movie%22+%26%26+%24keyword+in+categories%5B%5D-%3Etitle%5D%29%0A%7D&%24keyword=%22${request.data}%22&%24end=$pageId"
        val doc = app.get(
            url,
            cacheTime = 60,
            headers = mapOf(
                "Authorization" to "Bearer skBUVA1slvpATNipV4aNZnOfX6P8caCWUw8TGpqX340ror14OvULZvk6eJRx83KLM7GH1cFAWajBO7dlBQaBPjpKK3ZpTqUOJYp8Gmdmg1pLoUbrIaudAHGeQqDFe5E74zN2bEtD2xUqujp2YVp2wxgFk3i6CVhIE0p7aauoRK7LmDeVjq8o",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
            )
        )
        val home = AppUtils.parseJson<Post>(doc.text).result.map { toHomeResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toHomeResult(post: Result): SearchResponse {
        val url = if (post.slug?.current?.contains("https") == true) post.slug.current
            else "$mainUrl/post/${post.slug?.current}"
        val imageId = post.mainImage?.asset?._ref?.replaceFirst("image-", "")
            ?.replace("-jpg", ".jpg")?.replace("-webp", ".webp")
        val image = "https://rtally18.vercel.app/_next/image?url=https%3A%2F%2Fcdn.sanity.io%2Fimages%2Fxotpxf3q%2Frtally%2F${imageId}&w=1080&q=75"
        return newAnimeSearchResponse(post.title ?: "", url, TvType.Movie) {
            this.posterUrl = image
            addDubStatus(
                dubExist = when {
                    "dual" in (post.language?.first()?.title ?: "") -> true
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
        val image = mainUrl + doc.select(".p-\\[5px\\]").attr("src")
            .replace("&w=\\d+&q=75".toRegex(), "&w=1080&q=75")
        val plot = doc.selectFirst("p.text-sm:nth-child(3)")?.text()
        val download = doc.select("section.max-w-\\[90rem\\]:nth-child(2) > div:nth-child(1) > div > a:nth-child(1)")
        val duration = selectUntilNonInt(doc.select("div.space-x-4:nth-child(2) > span:nth-child(3)").text())
        val year = selectUntilNonInt(doc.select("div.grid:nth-child(3) > p:nth-child(2) > span:nth-child(1)").text())
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
            doc.select("div.space-x-2.px-2").forEach {
                val link = it.select(".grid > a:nth-child(1)").attr("href")
                val name = it.select(".line-clamp-1").text().replace("$title ", "")
                episodesData.add(
                    newEpisode(link){
                        this.name = name
                    }
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
            else if (it.contains("vidguard") || it.contains("listeamed")) loadExtractor(
                it.replace("/d/", "/e/"),
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
        val language: List<Language>?,
        val mainImage: MainImage?,
        val slug: Slug?,
        val title: String?
    )

    data class Language(
        val title: String?
    )

    data class MainImage(
        val asset: Asset?
    )

    data class Slug(
        val current: String?
    )

    data class Asset(
        val _ref: String?
    )

    private fun selectUntilNonInt(string: String): Int? {
        return Regex("^.*?(?=\\D|\$)").find(string)?.value?.toIntOrNull()
    }
}