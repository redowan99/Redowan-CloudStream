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
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets


//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(BdixDhakaFlix14Provider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////   providerTester.testSearch(query = "dragon", verbose = true)
////    providerTester.testLoad("http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies%20%281080p%29/009%20Re-Cyborg%20%282012%29%201080p%20%5BDual%20Audio%5D/")
//    //Tv Series
//    providerTester.testLoad("http://172.16.50.14/DHAKA-FLIX-14/KOREAN%20TV%20%26%20WEB%20Series/Squid%20Game%20%28TV%20Series%202021%E2%80%932025%29%201080p%20%5BMulti%20Audio%5D/")
//}

open class BdixDhakaFlix14Provider : MainAPI() {
    override var mainUrl = "http://172.16.50.14"
    override var name = "(BDIX) DhakaFlix 14"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.AnimeMovie, TvType.TvSeries
    )
    open val year = 2025
    open val tvSeriesKeyword: List<String>? = listOf("KOREAN%20TV%20%26%20WEB%20Series")
    open val serverName: String = "DHAKA-FLIX-14"

    override val mainPage = mainPageOf(
        "Animation Movies (1080p)/" to "Animation Movies",
        "English Movies (1080p)/($year) 1080p/" to "English Movies",
        "Hindi Movies/($year)/" to "Hindi Movies",
        "IMDb Top-250 Movies/" to "IMDb Top-250 Movies",
        "SOUTH INDIAN MOVIES/Hindi Dubbed/($year)/" to "Hindi Dubbed",
        "SOUTH INDIAN MOVIES/South Movies/$year/" to "South Movies",
        "/KOREAN TV %26 WEB Series/" to "Korean TV & WEB Series"
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/$serverName/${request.data}").document
        val homeResponse = doc.select("tbody > tr:gt(1):lt(12)")
        val home = homeResponse.mapNotNull { post ->
            getPostResult(post)
        }
        return newHomePageResponse(request.name, home, false)
    }

    private fun getPostResult(post: Element): SearchResponse {
        val folderHtml = post.select("td.fb-n > a")
        val name = folderHtml.text()
        val url = mainUrl + folderHtml.attr("href")
        return newAnimeSearchResponse(name, url, TvType.Movie) {
            addDubStatus(
                dubExist = when {
                    "Dual" in name -> true
                    else -> false
                }, subExist = when {
                    "ESub" in name -> true
                    else -> false
                }
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val body =
            "{\"action\":\"get\",\"search\":{\"href\":\"/$serverName/\",\"pattern\":\"$query\",\"ignorecase\":true}}".toRequestBody(
                "application/json".toMediaType()
            )
        val doc = app.post("$mainUrl/$serverName/", requestBody = body).text
        val searchJson = AppUtils.parseJson<SearchResult>(doc)
        val searchResponse: MutableList<SearchResponse> = mutableListOf()
        searchJson.search.take(40).map { post ->
            if (post.size == null) {
                val href = post.href
                val name = nameFromUrl(href)
                searchResponse.add(
                    newAnimeSearchResponse(
                        name, href
                    ) {
                        addDubStatus(
                            dubExist = when {
                                "Dual" in name -> true
                                else -> false
                            }, subExist = when {
                                "ESub" in name -> true
                                else -> false
                            }
                        )
                    }
                )
            }
        }
        return searchResponse
    }

    private val nameRegex = Regex(""".*/([^/]+)(?:/[^/]*)*$""")
    private fun nameFromUrl(href: String): String {
        val hrefDecoded = URLDecoder.decode(href, StandardCharsets.UTF_8.toString())
        val name = nameRegex.find(hrefDecoded)?.groups?.get(1)?.value
        return name.toString()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val imageLink = mainUrl + doc.select("td.fb-n > a[href~=(?i)\\.(png|jpe?g)]").attr("href")
        val tableHtml = doc.select("tbody > tr:gt(1)")

        if (containsAnyLoop(url, tvSeriesKeyword)) {
            val episodesData = mutableListOf<Episode>()
            var seasonNum = 0
            val name = nameFromUrl(url)
            tableHtml.forEach {
                seasonNum++
                val aHtml = it.selectFirst("td.fb-n > a")
                val link = mainUrl + aHtml?.attr("href")
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    seasonExtractor(link, episodesData, seasonNum)
                } else if (aHtml?.selectFirst("a[href~=(?i)\\.(mkv|mp4)]") != null) {
                    val tittle = aHtml.text()
                    episodesData.add(
                        newEpisode(link) {
                            this.name = tittle
                            this.season = 1
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(name, url, TvType.TvSeries, episodesData) {
                this.posterUrl = imageLink
            }
        } else {
            val folderHtml = tableHtml.select("td.fb-n > a[href~=(?i)\\.(mkv|mp4)]")
            val name = folderHtml.text().toString()
            val link = mainUrl + folderHtml.attr("href")
            return newMovieLoadResponse(name, url, TvType.Movie, link) {
                this.posterUrl = imageLink
            }
        }
    }

    private suspend fun seasonExtractor(
        url: String, episodesData: MutableList<Episode>, seasonNum: Int
    ) {
        val doc = app.get(url).document
        var episodeNum = 0
        doc.select("tbody > tr:gt(1) > td.fb-n > a[href~=(?i)\\.(mkv|mp4)]").forEach {
            episodeNum++
            val folderHtml = it.select("a")
            val name = folderHtml.text()
            val link = mainUrl + folderHtml.attr("href")
            episodesData.add(
                newEpisode(link) {
                    this.name = name
                    this.season = seasonNum
                    this.episode = episodeNum
                }
            )
        }
    }

    private fun containsAnyLoop(text: String, keyword: List<String>?): Boolean {
        if (!keyword.isNullOrEmpty()) {
            for (keyword in keyword) {
                if (text.contains(keyword, ignoreCase = true)) {
                    return true // Return immediately if a match is found
                }
            }
        }
        return false // Return false if no match is found after checking all keywords
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data, this.name, url = data, type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }

    data class SearchResult(
        val search: List<Search>
    )

    data class Search(
        val fetched: Boolean,
        val href: String,
        val managed: Boolean,
        val size: Long?,
        val time: Long
    )
}