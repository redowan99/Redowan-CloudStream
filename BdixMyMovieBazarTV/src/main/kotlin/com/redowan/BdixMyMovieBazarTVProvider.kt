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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(BdixMyMovieBazarTVProvider())
//    providerTester.testMainPage(verbose = true)
//    providerTester.testLoad("https://tv.mymoviebazar.net/stream/stream13.m3u8")
//}

class BdixMyMovieBazarTVProvider : MainAPI() {
    override var mainUrl = "https://tv.mymoviebazar.net"
    override var name = "(BDIX) MyMovieBazar TV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private val channels = listOf(
        mapOf(
            "name" to "T-Sports",
            "logo" to "$mainUrl/uploads/images/broadcasts/T_Sports_logo.svg.png",
            "link" to "https://tv.mymoviebazar.net/stream/stream56.m3u8"
        ),
        mapOf(
            "name" to "Fast Sports",
            "logo" to "$mainUrl/uploads/images/broadcasts/images.png",
            "link" to "https://tv.mymoviebazar.net/stream/stream11.m3u8"
        ),
        mapOf(
            "name" to "Star Sports 3",
            "logo" to "$mainUrl/uploads/images/broadcasts/Screenshot 2025-03-25 131522.png",
            "link" to "https://tv.mymoviebazar.net/stream/stream13.m3u8"
        )
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val home = mutableListOf<HomePageList>()
        home.add(
            HomePageList(
                "Live Tv", channelResponse(), isHorizontalImages = true
            )
        )
        return newHomePageResponse(home, hasNext = false)
    }
    private fun channelResponse(): List<LiveSearchResponse> {
        return channels.map { channel ->
            newLiveSearchResponse(
                channel["name"].toString(), channel["link"].toString()
            ) {
                this.posterUrl = channel["logo"].toString()
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val searchResult: MutableList<LiveSearchResponse> = mutableListOf()
        channels.forEach{ channel ->
            val name = channel["name"].toString()
            val distance = partialRatioLevenshtein(name.lowercase(), query.lowercase())
            if (distance >= 70) {
                searchResult.add(
                    newLiveSearchResponse(channel["name"].toString(), channel["link"].toString()) {
                        this.posterUrl = channel["logo"].toString()
                    }
                )
            }
        }
        return searchResult
    }

    override suspend fun load(url: String): LoadResponse {
        val channel = channels.first { it["link"] == url }
        return newLiveStreamLoadResponse(name = channel["name"].toString(), url = url, dataUrl = url) {
            this.posterUrl = channel["logo"].toString()
            this.recommendations = channelResponse()
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
                data, this.name, url = data, type = ExtractorLinkType.M3U8
            ){
                this.referer = "$mainUrl/live-tv"
            }
        )
        channels.map { channel ->
            callback.invoke(
                newExtractorLink(
                    channel["link"].toString(),
                    channel["name"].toString(),
                    url = channel["link"].toString(),
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/live-tv"
                }
            )
        }
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