package com.redowan

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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(Cat3MovieProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
//    providerTester.testLoad("https://cat3movie.org/info/deaden-2006")
////    providerTester.testLoadLinks("51783")
//}


class Cat3MovieProvider : MainAPI() {
    override var mainUrl = "https://cat3movie.org"
    private var apiMainUrl = "https://api.cat3movie.org"
    override var name = "Cat3Movie"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.NSFW
    )
    override val mainPage = mainPageOf(
        "action" to "Action",
        "horror" to "Horror",
        "comedy" to "Comedy",
        "animation" to "Animation",
        "war" to "War",
        "sci-fi" to "Sci-Fi",
        "romance" to "Romance",
        "asian" to "Asian",
        "drama" to "Drama",
        "classic-porn" to "Classic Porn",
        "asian-erotica" to "Asian Erotica",
        "newage-erotica" to "Newage Erotica",
        "classic-erotica" to "Classic Erotica",
        "sex-education" to "Sex Education",
        "incest" to "Incest",
        "thriller" to "Thriller",
        "crime" to "Crime",
        "adventure" to "Adventure",
        "western" to "Western",
        "fantasy" to "Fantasy",
        "newage-porn" to "Newage Porn",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val mediaType = "application/json".toMediaType()
        val body = "{\"filters\":{\"category\":\"${request.data}\"},\"page\":$page}".toRequestBody(
            mediaType
        )
        val json = app.post(
            "$apiMainUrl/movie/filter",
            requestBody = body,
            cacheTime = 60
        ).text
        val home = AppUtils.parseJson<MovieJson>(json).result.mapNotNull { toResult(it) }
        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = false),
            true
        )
    }

    private fun toResult(post: Result?): SearchResponse {
        val title = post?.title ?: ""
        val url = "$mainUrl/info/${post?.slug}"
        val imageUrl = "$apiMainUrl/${post?.thumbnail}"
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val mediaType = "application/json".toMediaType()
        val body = "{\"filters\":{\"keyword\":\"$query\"}}".toRequestBody(mediaType)
        val json = app.post(
            "$apiMainUrl/movie/filter",
            requestBody = body,
            cacheTime = 60
        ).text
        return AppUtils.parseJson<MovieJson>(json).result.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 60).document
        val title = doc.select("p.text-lg").text()
        val image = doc.select("img.object-contain").attr("src")
        val link = mainUrl + doc.select("a.btn-watch").attr("href")
        return newMovieLoadResponse(title, url, TvType.Movie, link) {
            this.posterUrl = image
            this.plot = doc.select("p.description > span.text-white").text()
            this.tags = doc.select("div.categories > a").map { it.text() }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val serverAjaxURL =
            "https://cat3movie.org/wp-content/themes/hnzphim/app/load.php?episode_slug=full&server_id=1&post_id=$data"
        val doc = app.get(
            serverAjaxURL,
            timeout = 60,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).document
        var url = doc.select("iframe").attr("src").replace("\\", "")
        url = url.replace("\"", "")
        val hlsDoc = app.get(url).document
        val m3u8Link = getSecondOccurrenceBetween(hlsDoc.html())
        callback.invoke(
            newExtractorLink(
                "HlsVip",
                "HlsVip",
                m3u8Link.toString(),
            )
        )
        return true
    }

    private fun getSecondOccurrenceBetween(input: String): String? {
        val after = "file: \""
        val before = "\","
        // Get the part of the string after the first occurrence of `after`
        val firstOccurrence = input.substringAfter(after)

        // Get the part of the string after the second occurrence of `after`
        val secondOccurrence = firstOccurrence.substringAfter(after, "")

        // If there is no second occurrence, return null
        if (secondOccurrence.isEmpty()) return null

        // Get the substring before the `before` string in the second occurrence
        return secondOccurrence.substringBefore(before)
    }

    data class MovieJson(
        val result: List<Result?> = listOf(),
    )

    data class Result(
        val slug: String = "", // girl-chef-2011
        val thumbnail: String = "", // assets/thumbnail/girl-chef-2011.webp
        val title: String = "", // Girl Chef (2011)
    )
}

