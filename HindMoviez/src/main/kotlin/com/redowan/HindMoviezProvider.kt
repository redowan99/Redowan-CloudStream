package com.redowan

import android.util.Log
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
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.util.Locale

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(Film1KProvider())
////    providerTester.testLoadLinks("https://checklinko.top/60382/")
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
////    providerTester.testLoadLinks("51783")
//}


class HindMoviezProvider : MainAPI() {
    override var mainUrl = "https://hindmoviez.club/"
    override var name = "HindMoviez"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )
    override val mainPage = mainPageOf(
        "" to "Latest",
        "/movies/hollywood-movies" to "Hollywood ",
        "/dual-audio" to "Dual Audio",
        "/movies/hindi-movies" to "Hindi",
        "/movies/english-movies" to "English",
        "/movies/adult-movies" to "Adult",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            "$mainUrl${request.data}/page/$page",
            cacheTime = 60,
            allowRedirects = true,
            timeout = 30
        ).document
        val home = doc.select(".post").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select(".entry-title-link").text()
        val url = post.select(".entry-title-link").attr("href")
        val posterUrl = post.select(".featured-image img").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", cacheTime = 60, timeout = 30).document
        return doc.select(".post").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60, timeout = 30).document
        val title = doc.selectFirst(".entry-title")?.text() ?: ""
        val image = doc.select(".featured-image img").attr("src")
        val plot = doc.select(".entry-content > p:nth-of-type(3)").text()
        val year = "<li><strong>Release Year: <\\/strong>(.*)<\\/li>".toRegex()
            .find(doc.html())?.groups?.get(1)?.value
        if (title.lowercase().contains("season")) {
            val elements = doc.selectFirst(".entry-content")
            val qualityRegex = ">(\\d{3,4}p).*<".toRegex()
            val seasonRegex = "(\\d)-(\\d)"
            val isMultiSeason = seasonRegex.toRegex().containsMatchIn(title)

            if (isMultiSeason) {

                val startSeason = seasonRegex.toRegex().find(title)?.groups?.get(1)?.value?.toInt()
                val endSeason = seasonRegex.toRegex().find(title)?.groups?.get(2)?.value?.toInt()

                if (startSeason != null && endSeason != null) {

                    for (i in startSeason..endSeason) {

                        val episodeData = mutableListOf<Episode>()
                        for (i in 0..(elements.children().size - 1)) {

                            val item = elements.children()[i]

                            val currentSeason = "Season $startSeason"
                            if (item.tagName() == "h3" && qualityRegex.containsMatchIn(item.html())) {

                                if (item.text().lowercase().contains(currentSeason.lowercase())) {

                                    Log.d("salman731 text", item.text())
                                    val quality = item.select("span[style=\"color: #ff00ff;\"]").text()
                                    Log.d("salman731 quality", quality)
                                    val link = elements.children()[i + 1].select(".maxbutton").attr("href")
                                    Log.d("salman731 link", link)
                                    val doc = app.get(link, allowRedirects = true, timeout = 30).document
                                    Log.d("salman731 baseUri", doc.baseUri())
                                }
                            }
                        }
                    }
                }
            }
            //Log.d("salman731 html + ${i}",item.html())
            /*if (item.tagName() == "h3" && qualityRegex.containsMatchIn(item.html())) {
                Log.d("salman731 text",item.text())
                val quality = item.select("span[style=\"color: #ff00ff;\"]").text()
                Log.d("salman731 quality",quality)
                val link = elements.children()[i+1].select(".maxbutton").attr("href")
                Log.d("salman731 link",link)
                val doc = app.get(link, allowRedirects = true, timeout = 30).document
                Log.d("salman731 baseUri",doc.baseUri())

            }*/

        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = image
            this.plot = plot
            if (year != null) {
                this.year = year.toInt()
            }

        }
    }

    private suspend fun getHindShare (url:String)
    {
        val list = mutableListOf<String>()
        val doc = app.get(url, timeout = 30, allowRedirects = true).document
        val btn = doc.select("btn-group a")
        btn.forEach { item ->
            item.attr("href")
            if(item.text().contains("HCloud"))
            {
                list.add(item.attr("href"))
            }
            else if(item.text().contains("HindFile"))
            {
                val doc = app.get(item.attr("href"), timeout = 30, allowRedirects = true).document
                val btn = doc.select("#download-btn3")
                list.add(btn.attr("href"))
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val linkMap = mutableMapOf<String, String>()
        val doc = app.get(data, timeout = 30).document
        val elements = doc.selectFirst(".entry-content")
        val qualityRegex = ">(\\d{3,4}p).*<".toRegex()
        for (i in 0..(elements.children().size - 1)) {

            val item = elements.children()[i]
            //Log.d("salman731 html + ${i}",item.html())
            if (item.tagName() == "h3" && qualityRegex.containsMatchIn(item.html())) {
                Log.d("salman731 text", item.text())
                val quality = item.select("span[style=\"color: #ff00ff;\"]").text()
                Log.d("salman731 quality", quality)
                val link = elements.children()[i + 1].select(".maxbutton").attr("href")
                Log.d("salman731 link", link)
                val doc = app.get(link, allowRedirects = true, timeout = 30).document
                Log.d("salman731 baseUri", doc.baseUri())

            }
        }
        /*repeat(5) { i ->
            val mediaType = "application/x-www-form-urlencoded".toMediaType()
            val body = "action=action_change_player_eroz&ide=$data&key=$i".toRequestBody(mediaType)
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            val doc = app.post(ajaxUrl, requestBody = body, cacheTime = 60, timeout = 30).document
            var url = doc.select("iframe").attr("src").replace("\\", "").replace("\"","") // It is necessary because it returns link with double qoutes like this ("https://voe.sx/e/edpgpjsilexe")
            Log.d("salman 731 iframe",url)
            if (url.contains("https://film1k.xyz")) {
                val matchResult = film1kRegex.find(url)
                if (matchResult != null) {
                    val code = matchResult.groupValues[1]
                    url = "https://filemoon.sx/e/$code"
                }
            }
            url = url.replace("https://films5k.com","https://mwish.pro")
            Log.d("salman 731 url",url)
            loadExtractor(url, subtitleCallback, callback)
        }*/


        return true
    }

    class SeasonDetail
    {
        val quality:String = ""
        val link:String = ""
        val season:String = ""
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
    /*private fun getYearFromString(check: String?): Int? {
        return check?.let {
            parenthesesYear.find(it)?.value?.toIntOrNull()
                ?: withoutParenthesesYear.find(it)?.value?.toIntOrNull()
        }
    }
    private val parenthesesYear = "(?<=\\()\\d{4}(?=\\))".toRegex()
    private val withoutParenthesesYear = "(19|20)\\d{2}(?!\\w)".toRegex()*/
}