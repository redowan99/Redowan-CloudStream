package com.redowan

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(BdixBdipTVProvider())
////    providerTester.testMainPage(verbose = true)
////    providerTester.testLoad("http://tv.bdiptv.net/assets/images/sonyyay.jpg ; SONY YAY ; SONY-YAY")
//    providerTester.testSearch("sports")
//}

open class BdixBdipTVProvider : MainAPI() {
    override var mainUrl = "http://tv.bdiptv.net/"
    override var name = "(BDIX) BDIP TV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)
    open val liveServer = "http://103.89.248.14:8082/"
    private val category = mapOf(
        "lsports" to "Live Sports",
        "sports" to "Sports",
        "news" to "News",
        "bangla" to "Bangla",
        "hindi" to "Hindi",
        "movies" to "Movies",
        "documentary" to "Documentary",
        "kids" to "Kids",
        "music" to "Music"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(mainUrl).document
        val home = mutableListOf<HomePageList>()
        category.forEach { name ->
            val response = doc.select("div.item.${name.key}").mapNotNull {
                getResult(it)
            }
            home.add(
                HomePageList(
                    name.value,
                    response,
                    isHorizontalImages = false
                )
            )
        }
        return newHomePageResponse(home, hasNext = false)
    }

    private val hrefRegex = Regex("play\\.php\\?stream=([^']+)")
    private fun getResult(post: Element): LiveSearchResponse {
        val imageLink = mainUrl + post.select("img").attr("src")
        val link = hrefRegex.find(post.select("a").attr("onclick"))?.groupValues?.get(1) ?: ""
        val name = link.replace("-"," ")
        val joinedLink = "$imageLink ; $name ; $link"
        return newLiveSearchResponse(name, joinedLink) {
            this.posterUrl = imageLink
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val doc = app.get(mainUrl).document
        val searchResult: MutableList<LiveSearchResponse> = mutableListOf()
        doc.select("div.item_content > a").mapNotNull { post ->
            getSearchResult(post, query, searchResult)
        }
        return searchResult
    }

    private fun getSearchResult(
        post: Element,
        query: String,
        searchResult: MutableList<LiveSearchResponse>
    ) {
        val link = hrefRegex.find(post.select("a").attr("onclick"))?.groupValues?.get(1) ?: ""
        val name = link.replace("-", " ")
        val distance = partialRatioLevenshtein(name.lowercase(), query.lowercase())
        if (distance >= 70) {
            val imageLink = mainUrl + post.select("img").attr("src")
            val joinedLink = "$imageLink ; $name ; $link"
            searchResult.add(
                newLiveSearchResponse(name, joinedLink, TvType.Live, true) {
                    this.posterUrl = imageLink
                }
            )
        }
    }

    private val tokenRegex = Regex("token=([^&]+)")
    override suspend fun load(url: String): LoadResponse {
        val splitLink = url.split(" ; ")
        val url1 = "$mainUrl/play.php?stream=${splitLink[2]}"
        val redirectUrl = app.get(url1, referer = mainUrl).text
        val token = tokenRegex.find(redirectUrl)?.value.toString()
        val m3uLink = "${splitLink[2]}/index.fmp4.m3u8?$token"
        return newLiveStreamLoadResponse(name = splitLink[1], url = url, dataUrl = m3uLink) {
            this.posterUrl = splitLink[0]
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data,
                this.name,
                url = "$liveServer$data",
                type = ExtractorLinkType.M3U8
            )
        )
        return true
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        // ... (same Levenshtein distance function as before)
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) {
            dp[i][0] = i
        }
        for (j in 0..n) {
            dp[0][j] = j
        }

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost)
            }
        }
        return dp[m][n]
    }

    private fun partialRatioLevenshtein(s1: String, s2: String): Int {
        val shorter: String
        val longer: String

        if (s1.length <= s2.length) {
            shorter = s1
            longer = s2
        } else {
            shorter = s2
            longer = s1
        }

        val n = shorter.length
        var minDistance = longer.length // Initialize with maximum possible distance

        for (i in 0..longer.length - n) {
            val sub = longer.substring(i, i + n)
            val distance = levenshteinDistance(shorter, sub)
            minDistance = minOf(minDistance, distance)
        }

        // Normalize the distance to a 0-100 scale
        // A distance of 0 is a perfect match (score 100)
        // The maximum possible distance for a partial match is the length of the shorter string
        val maxLength = shorter.length
        val similarity = ((maxLength - minDistance).toDouble() / maxLength) * 100

        return similarity.toInt()
    }
}