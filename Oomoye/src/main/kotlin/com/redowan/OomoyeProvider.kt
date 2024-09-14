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
import org.jsoup.nodes.Element
import java.net.URL



class OomoyeProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.oomoye.store"
    override var name = "Oomoye"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true

    override val mainPage = mainPageOf(
        "Bollywood-movies" to "Bollywood Movies",
        "South-indian-hindi-movies" to "South Indian Hindi Movies",
        "Hollywood-hindi-dubbed-movies" to "Hollywood Hindi Movies",
        "Hollywood-english-movies" to "Hollywood English Movies",
        "Hollywood-cartoon-movies" to "Hollywood Cartoon Movies",
        "Punjabi-movies" to "Punjabi Movies",
        "Tamil-movies" to "Tamil Movies",
        "Telugu-movies" to "Telugu Movies",
        "Malayalam-movies" to "Malayalam Movies",
        "Marathi-movies" to "Marathi Movies",
        "Bengali-movies" to "Bengali Movies",
        "Kannada-movies" to "Kannada Movies",
        "Gujarati-movies" to "Gujarati Movies"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/category/${request.data}.html?page=$page").document
        val home = doc.select(".catRow").mapNotNull {toResult(it)}
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

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectXpath("/html/body/div[7]/div").text()
        val year = "(?<=\\()\\d{4}(?=\\))".toRegex().find(title)?.value?.toIntOrNull()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = doc.getElementsByClass("posterss").attr("src")
            this.year = year
            this.plot = doc.getElementsByClass("description").text()
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("a[href*=$mainUrl/server/]").forEach { item ->
            val links = app.get(item.attr("href").replace("/server/", "/files/")).document
            val link = links.select(".fastdl a").attr("href")
            val url = URL(link)
            val hostName = url.host.replace("www.","").substringBefore(".")
            if (link.isNotEmpty())
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        hostName,
                        url = link,
                        data,
                        quality = getVideoQuality(item.text()),
                        isM3u8 = false,
                        isDash = false
                    )
                )
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
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains("hdtc") -> SearchQuality.HdCam
                lowercaseCheck.contains("dvd") -> SearchQuality.DVD
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("camrip") || lowercaseCheck.contains("rip") -> SearchQuality.CamRip
                lowercaseCheck.contains("hdrip") || lowercaseCheck.contains("hd") || lowercaseCheck.contains("hdtv") -> SearchQuality.HD
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
}