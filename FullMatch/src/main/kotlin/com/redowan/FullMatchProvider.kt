package com.redowan

import android.util.Log
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
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class FullMatchProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://fullmatch.info"
    override var name = "FullMatch"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val mainPage = mainPageOf(
        "/" to "Latest Full Match",
        "/club-friendly" to "Club Friendly",
        "/england-full-match-full-replays" to "England",
        "/fifa-world-cup-2026" to "FIFA World Cup 2026",
        "/germany" to "Germany",
        "/france" to "France",
        "/italy" to "Italy",
        "/spain" to "Spain",
        "/olympic-2024" to "Olympic 2024"
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc: Element
        if (request.data == "/") {
            val body = FormBody.Builder().addEncoded("action", "tie_blocks_load_more")
                .addEncoded("block[order]", "latest").addEncoded("block[asc_or_desc]", "DESC")
                .addEncoded("block[number]", "18").addEncoded("block[pagi]", "load-more")
                .addEncoded("block[style]", "default").addEncoded("page", page.toString()).build()
            val ajaxUrl = "https://fullmatch.info/wp-admin/admin-ajax.php"
            val response = app.post(ajaxUrl, requestBody = body)
            val text = response.text.replaceBefore("<li class", "").replaceAfterLast("/li>", "")
                .replace("\\", "")
            doc = Jsoup.parse(text)
        } else doc = app.get("$mainUrl${request.data}/page/$page").document

        val home = doc.select(".post-item").mapNotNull { toResult(it) }
        return newHomePageResponse(HomePageList(request.name, home, isHorizontalImages = true))
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select(".post-title a").attr("href")
        val title = post.select(".post-title a").text()
        val imageUrl = post.select(".wp-post-image").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val searchResponse = doc.select(".post-item")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select(".entry-header h1").text()
        val imageUrl = doc.select(".single-featured-image img").attr("src")
        val tabs = doc.select(".tabcontent iframe")
        // When post page has tabs
        if (tabs.isNotEmpty()) {
            val tabcontent = doc.select(".tabcontent")
            if (tabcontent.size > 1) {
                val episodesData = mutableListOf<Episode>()
                var episodeNo = 1
                tabcontent.forEach { item ->
                    val hostUrls = getHostUrlsWithIframe(item)
                    val episodeTitle = doc.select(".tabtitle")[episodeNo - 1].text()
                    if (hostUrls.size > 1) {
                        episodesData.add(
                            newEpisode(
                                hostUrls.joinToString("+")
                            ) { this.name = episodeTitle }

                        )
                    } else if (hostUrls.size == 1) {
                        episodesData.add(
                            newEpisode(hostUrls[0]) {
                                this.name = episodeTitle
                            }
                        )
                    } else {
                        episodesData.add(
                            newEpisode(""){
                                this.name = episodeTitle
                            }
                        )
                    }
                    episodeNo++
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                    this.posterUrl = imageUrl
                }

            } else {
                val tab = doc.select(".tabcontent")
                val hostUrls = getHostUrlsWithIframe(tab[0])
                val links = if (hostUrls.size > 1) {
                    hostUrls.joinToString("+")
                } else if (hostUrls.size == 1) {
                    hostUrls[0]
                } else {
                    ""
                }
                return newMovieLoadResponse(title, url, TvType.Movie, links) {
                    this.posterUrl = imageUrl
                }
            }
        }
        // When post page has single iframe without tabs
        else if (doc.select(".entry-content.entry.clearfix iframe").isNotEmpty()) {
            val hostUrls = mutableListOf<String>()
            val videoUrl = doc.select(".entry-content.entry.clearfix iframe").attr("src")
                .replace("//", "https://")
            hostUrls.add(videoUrl)
            val urls = doc.select(".entry-content.entry.clearfix p")
            urls.forEach { item ->
                val link = item.select("a").text()
                if (link.contains(" ")) {
                    val list = item.select("a").text().split(" ")
                    hostUrls.addAll(list)
                } else {
                    hostUrls.add(link)
                }

            }
            return newMovieLoadResponse(title, url, TvType.Movie, hostUrls.joinToString("+")) {
                this.posterUrl = imageUrl
            }
        }
        // When post page has not iframe and other hoster links are available
        else {
            val servers = doc.select(".entry-content.entry.clearfix p")
            val episodeData = mutableListOf<Episode>()
            servers.forEach { item ->
                val hostTxt = item.text()
                val isHostLink =
                    "https://(.*)\\.(.*)/(.*)".toRegex().containsMatchIn(item.select("a").text())
                // When post page has colored links
                if (hostTxt.contains("StreamWish")) {
                    val links = item.select("a")
                    links.forEach { linkElement ->
                        val value = "(https://fullmatch.info/goto/)\\d+".toRegex()
                            .containsMatchIn(linkElement.attr("href"))
                        if (value) {
                            val doc2 = app.get(linkElement.attr("href")).document
                            val finalHostUrl =
                                doc2.select(".entry-content.entry.clearfix p a").attr("href")
                            episodeData.add(
                                newEpisode(finalHostUrl) {
                                    this.name = linkElement.text()
                                })
                        }
                    }
                }
                // When post has no iframe and colored links
                else if (isHostLink) {
                    val hostUrls = getHostUrls(item)
                    return newMovieLoadResponse(
                        title, url, TvType.Movie, hostUrls.joinToString("+")
                    ) {
                        this.posterUrl = imageUrl
                    }
                }

            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeData) {
                this.posterUrl = imageUrl
            }
        }
    }

    private suspend fun getHostUrlsWithIframe(element: Element): MutableList<String> {
        val hostUrls = mutableListOf<String>()
        val videoUrl = element.select("iframe").attr("src").replace("//", "https://")
        hostUrls.add(videoUrl)
        val pEle = element.select("p")
        pEle.forEach { item ->
            val isHostLink =
                "https://(.*)\\.(.*)/(.*)".toRegex().containsMatchIn(item.select("a").text())
            if (isHostLink) {
                hostUrls.addAll(getHostUrls(item))
            }
        }

        return hostUrls
    }

    private suspend fun getHostUrls(element: Element): MutableList<String> {
        val hostUrls = mutableListOf<String>()
        val list = element.select("a")
        list.forEach { item ->
            val link = item.attr("href")
            val value = "(https://fullmatch.info/goto/)\\d+".toRegex().containsMatchIn(link)
            if (value) {
                val doc3 = app.get(item.attr("href")).document
                val finalHostUrl = doc3.select(".entry-content.entry.clearfix p a").attr("href")
                hostUrls.add(finalHostUrl)
            }
        }
        return hostUrls
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.contains("+")) {
            val links = data.split("+")
            links.forEach { item ->
                Log.d("salman731 item links", item)
                loadExtractor(item, subtitleCallback, callback)
            }
        } else {
            loadExtractor(data, subtitleCallback, callback)
        }
        return true
    }
}