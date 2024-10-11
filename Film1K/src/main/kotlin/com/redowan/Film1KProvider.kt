package com.redowan

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(Film1KProvider())
////    providerTester.testLoadLinks("https://checklinko.top/60382/")
//    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("")
////    providerTester.testLoadLinks("51783")
//}


class Film1KProvider : MainAPI() {
    override var mainUrl = "https://www.film1k.com"
    override var name = "Film1K"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.NSFW
    )
    override val mainPage = mainPageOf(
        "" to "Latest",
        "/tag/hd" to "HD",
        "/tag/india" to "India",
        "/tag/japan" to "Japan",
        "/category/action" to "Action",
        "/category/horror" to "Horror",
        "/category/comedy" to "Comedy",
        "/category/romance" to "Romance",
        "/videos/category/12" to "Korean",
    )
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}/page/$page", cacheTime = 60, allowRedirects = true, timeout = 30).document
        val home = doc.select("header.entry-header").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name,home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select(".entry-title").text()
        val url = post.select("a:nth-child(1)").attr("href")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst(".lazys")
                ?.attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", cacheTime = 60, timeout = 30).document
        return doc.select("header.entry-header").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60, timeout = 30).document
        val title = doc.selectFirst("h1.title")?.text() ?: ""
        val image =
            doc.select("img.alignleft")
                .attr("src")
        val id = doc.select("a.Button.B.on").attr("data-ide")
        val plot = doc.select(".MoreInfo > div:nth-child(1)").html()
        return newMovieLoadResponse(title, url, TvType.Movie, id) {
            this.posterUrl = image
            this.plot = plot
            this.year = getYearFromString(title)
        }
    }

    private val film1kRegex = Regex("https://film1k\\.xyz/e/([^/]+)/.*")

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        repeat(5) { i ->
            val mediaType = "application/x-www-form-urlencoded".toMediaType()
            val body = "action=action_change_player_eroz&ide=$data&key=$i".toRequestBody(mediaType)
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            val doc = app.post(ajaxUrl, requestBody = body, cacheTime = 60, timeout = 30).document
            var url = doc.select("iframe").attr("src").replace("\\", "").replace("\"","") // It is necessary because it returns link with double qoutes like this ("https://voe.sx/e/edpgpjsilexe")
            if (url.contains("https://film1k.xyz")) {
                val matchResult = film1kRegex.find(url)
                if (matchResult != null) {
                    val code = matchResult.groupValues[1]
                    url = "https://filemoon.sx/e/$code"
                }
            }
            url = url.replace("https://films5k.com","https://mwish.pro")
            loadExtractor(url, subtitleCallback, callback)
        }
        return true
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
}