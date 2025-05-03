package com.redowan

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(OomoyeProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
//    providerTester.testLoad("https://oomoye.guru/movie/26127/Chhaava-2025-hindi-movie.html")
////    providerTester.testLoadLinks("https://www.oomoye.yachts/files/40136/Vedaa-2024-480p-mkv/1.html ; https://www.oomoye.yachts/files/40134/Vedaa-2024-720p-mkv/1.html ; https://www.oomoye.yachts/files/40132/Vedaa-2024-1080p-mkv/1.html")
//}

class OomoyeProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://oomoye.guru"
    override var name = "Oomoye"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val mainPage = mainPageOf(
        "2025-new-bollywood-movies" to "Bollywood Movies",
        "2025-new-south-indian-hindi-dubbed-movies" to "South Indian Dubbed Movies",
        "2025-latest-hollywood-hindi-dubbed-movies" to "Hollywood Dubbed Movies",
        "2024-latest-hollywood-english-movies" to "Hollywood Movies",
        "Latest-bengali-movies-2024" to "Bengali Movies",
        "2024-latest-hollywood-cartoon-movies" to "Hollywood Cartoon Movies",
        "Chinese-dubbed-movies-2020-to-1980" to "Chinese Dubbed Movies",
        "English-web-series" to "English Web Series",
        "Hollywood-dubbed-web-series" to "Hollywood Dubbed Web Series",
        "Hindi-web-series" to "Hindi Web Series",
        "Indian-Hot-Web-Series" to "Indian Hot Web Series"
    )
    private val headers =
        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(
            "$mainUrl/category/${request.data}.html?page=$page",
            cacheTime = 60,
            allowRedirects = true,
            headers = headers
        ).document
        val home = doc.select(".catRow").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select("a").attr("href")
        val imageId = "\\d+".toRegex().find(url)?.value
        val title = post.text().replaceAfter(")", "")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = "$mainUrl/cover/$imageId.png"
            this.quality = getSearchQuality(post.select("font[color=green]").text())
        }
    }

//    override suspend fun search(query: String): List<SearchResponse> {
//        val doc = app.get(
//            "$mainUrl/search.php?q=$query", cacheTime = 60, headers = headers
//        ).document
//        return doc.select(".catRow").mapNotNull { toResult(it) }
//    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(
            url, cacheTime = 60, headers = headers
        ).document
        val title = doc.selectFirst("div.moviename")?.text() ?: ""
        val links = doc.select(".catRow a[href*=/servers/]").joinToString(separator = " ; ") {
                it.attr("href").replace("/servers/", "/server/")
            }
        return newMovieLoadResponse(title, url, TvType.Movie, links) {
            this.posterUrl = doc.getElementsByClass("posterss").attr("src")
            this.plot = doc.getElementsByClass("description").text()
        }
    }

    private val nameExtractorRegex = "(?<=\\))\\s*(.*?)(?=\\.)"

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.split(" ; ").forEach {
            val doc = app.get(
                it, cacheTime = 60, headers = headers
            ).document
            doc.select(".fastdl:contains(Start Download from Server) > a").forEach { links ->
                val linkTitle = links.attr("title")
                val extractedName = Regex(nameExtractorRegex).find(linkTitle)
                    ?.groups?.get(1)?.value ?: ""
                val categoryName = doc.select(".category").text()
                val link = links.attr("href")
                val providerName = when {
                    link.contains("main") -> "Cloudflare"
                    else -> "Pixeldrain"
                }
                val finalName = "$extractedName - $categoryName $providerName"
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        finalName,
                        url = link
                    )
                )
            }
        }
        return true
    }

    /**
     * Determines the search quality based on the presence of specific keywords in the input string.
     *
     * @param check The string to check for keywords.
     * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
     */
    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("webrip") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("bluray") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains(
                    "hdtc"
                ) -> SearchQuality.HdCam

                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hd") || lowercaseCheck.contains(
                    "hdtv"
                ) -> SearchQuality.HD

                lowercaseCheck.contains("telesync") -> SearchQuality.Telesync
                lowercaseCheck.contains("telecine") -> SearchQuality.Telecine
                else -> null
            }
        }
        return null
    }

    private fun getVideoQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
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