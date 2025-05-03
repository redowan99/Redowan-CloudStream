package com.redowan

import com.lagradost.cloudstream3.Episode
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
import org.json.JSONObject
import org.jsoup.nodes.Element

class LiveMovieProvider : MainAPI() {
    override var mainUrl = "https://livemovie.org"
    override var name = "LiveMovie"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.NSFW)

    private val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"

    override val mainPage = mainPageOf(
        "/movies/" to "Recently Added",
        "/genre/bollywood-movies/" to "Bollywood",
        "/genre/hindi-dubbed-movies/" to "Hindi Dubbed Movies",
        "/genre/south-hindi-dubbed/" to "South Hindi Dubbed",
        "/genre/unofficial-dubbed/" to "Unofficial Dubbed",
        "/genre/bollywood-18/" to "Bollywood 18+",
        "/genre/hollywood-18/" to "Hollywood 18+",
        "/genre/korean-18/" to "Korean 18+",

        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = if (request.data == "" && page == 1) {
            app.get(mainUrl, allowRedirects = true, timeout = 30).document
        } else {
            app.get(
                "$mainUrl${request.data}page/$page/",
                allowRedirects = true,
                timeout = 30
            ).document
        }
        val itemElement = if (request.name == "Recently Added") {
            ".items.normal .item.movies"
        } else {
            ".item.movies"
        }
        val home = doc.select(itemElement).mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, hasNext = true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select("a").attr("href")
        val title = post.select("img").attr("alt")
        val imageUrl = post.select("img").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    private fun toSearchResult(post: Element): SearchResponse {
        val url = post.select(".image a").attr("href")
        val title = post.select(".image img").attr("alt")
        val imageUrl = post.select(".image img").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", allowRedirects = true, timeout = 30).document
        val searchResponse = doc.select(".result-item")
        return searchResponse.mapNotNull { toSearchResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, allowRedirects = true, timeout = 30).document
        val title =
            doc.select("meta[property=\"og:title\"]").attr("content").replace(" - LiveMovie", "")
        val poster = doc.selectFirst(".poster img")?.attr("src")
        val description = doc.selectFirst("meta[property=\"og:description\"]")?.attr("content")
        val tags = doc.select(".sgeneros a")
        val playerText = doc.select(".dooplay_player_option .title").text()
        if (playerText.lowercase().contains("player")) {
            val playerList = doc.select(".dooplay_player_option")
            val linkList = mutableListOf<String>()
            playerList.forEach { item ->
                val type = item.attr("data-type")
                val post = item.attr("data-post")
                val nume = item.attr("data-nume")
                val requestBody = getRequestBody(post, nume, type)
                val ajaxResponse = app.post(ajaxUrl, requestBody = requestBody).text
                val jsonObj = JSONObject(ajaxResponse)
                val link = jsonObj.get("embed_url")
                linkList.add(link.toString())
            }

            return newMovieLoadResponse(title, url, TvType.Movie, linkList.joinToString("+")) {
                this.posterUrl = poster
                if (!description.isNullOrEmpty()) {
                    this.plot = description
                }

                if (!tags.isEmpty()) {
                    this.tags = tags.map { it.text() }
                }
            }
        } else {
            val episodeData = mutableListOf<Episode>()
            val list = doc.selectFirst(".wp-content")
            list?.children()?.forEach { item ->
                if (item.tagName() == "h1" || item.tagName() == "h2") {
                    if (item.children().size == 1 && (item.children().first()?.tagName()
                            ?: "") == "strong"
                    ) {
                        val name = item.select("strong")
                        val link = item.nextElementSibling()?.select("a")?.attr("href") ?: ""
                        episodeData.add(newEpisode(link) { this.name = name.text() })
                    } else if (item.children().size == 2) {
                        val name = item.select("strong")
                        val link = item.select("a").attr("href")
                        episodeData.add(newEpisode(link) { this.name = name.text() })
                    }
                }
            }
            // Disabled due to abyss not implemented yet
            /*val playerList = doc.select(".dooplay_player_option")
            playerList.forEach { item->
                val type = item.attr("data-type")
                val post = item.attr("data-post")
                val nume = item.attr("data-nume")
                val requestBody = getRequestBody(post,nume,type)
                val doc = app.post(ajaxUrl,requestBody = requestBody).text

                val jsonObj = JSONObject(doc)
                var link = jsonObj.get("embed_url").toString()
                val title = item.select(".title")
                if(link.contains("short.ink"))
                {
                    val doc = app.get(link)
                    link = doc.url
                }
                episodeData.add(Episode(link,title.text(),null,null))
            }*/
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeData) {
                this.posterUrl = poster
                if (!description.isNullOrEmpty()) {
                    this.plot = description
                }

                if (!tags.isEmpty()) {
                    this.tags = tags.map { it.text() }
                }
            }
        }

    }

    private fun getRequestBody(post: String, nume: String, type: String): FormBody {
        return FormBody.Builder()
            .addEncoded("action", "doo_player_ajax")
            .addEncoded("post", post)
            .addEncoded("nume", nume)
            .addEncoded("type", type)
            .build()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val list = data.split("+")
        list.forEach { item ->
            loadExtractor(
                item.replace("listeamed.net/v/", "listeamed.net/e/"),
                subtitleCallback,
                callback
            )
        }

        return true
    }
}