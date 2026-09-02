package com.redowan

import com.fasterxml.jackson.annotation.JsonProperty
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
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class BDIXCloudTVProvider : MainAPI() {
    override var mainUrl = "http://172.19.178.180"
    override var name = "(BDIX) CloudTV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private val encryptedDataRegex = Regex("""const\s+encryptedData\s*=\s*"([^"]+)"""")
    private val encodedUrlRegex = Regex("""const\s+encodedUrl\s*=\s*"([^"]+)"""")

    private suspend fun fetchChannelsMap(): Map<String, List<ChannelItem>> {
        return runCatching {
            val html = app.get("$mainUrl/", cacheTime = 15).text
            val b64Data = encryptedDataRegex.find(html)?.groupValues?.get(1) ?: return emptyMap()
            val jsonText = base64Decode(b64Data)
            AppUtils.parseJson<Map<String, List<ChannelItem>>>(jsonText)
        }.getOrDefault(emptyMap())
    }

    private suspend fun isChannelWorking(channelId: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val playUrl = "$mainUrl/play.php?id=$channelId"
                val html = app.get(playUrl, referer = mainUrl, timeout = 2L).text
                val b64Encoded = encodedUrlRegex.find(html)?.groupValues?.get(1) ?: return@runCatching false
                val decodedEmbedUrl = base64Decode(b64Encoded)
                val m3uLink = decodedEmbedUrl.replace("embed.html", "index.fmp4.m3u8")
                val res = app.get(m3uLink, referer = mainUrl, timeout = 1L)
                res.isSuccessful && res.text.contains("#EXTM3U")
            }.getOrDefault(false)
        }
    }

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse = coroutineScope {
        val channelsMap = fetchChannelsMap()
        val home = mutableListOf<HomePageList>()

        channelsMap.forEach { (catName, channelList) ->
            val activeChannels = channelList.filter { it.active != false }
            val workingItems = activeChannels.map { ch ->
                async(Dispatchers.IO) {
                    if (ch.channelId != null && isChannelWorking(ch.channelId)) {
                        toResult(ch)
                    } else null
                }
            }.awaitAll().filterNotNull()

            if (workingItems.isNotEmpty()) {
                val prettyCatName = catName.replace("_", " ")
                home.add(
                    HomePageList(
                        prettyCatName,
                        workingItems,
                        isHorizontalImages = true
                    )
                )
            }
        }

        newHomePageResponse(home, hasNext = false)
    }

    private fun toResult(ch: ChannelItem): LiveSearchResponse {
        val playUrl = "$mainUrl/play.php?id=${ch.channelId}"
        val joinedLink = "$playUrl ; ${ch.channelName} ; ${ch.logo}"
        return newLiveSearchResponse(
            ch.channelName ?: "Channel",
            joinedLink
        ) {
            this.posterUrl = ch.logo
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val channelsMap = fetchChannelsMap()
        val searchResult = mutableListOf<LiveSearchResponse>()
        channelsMap.values.flatten().forEach { ch ->
            val name = ch.channelName ?: ""
            if (name.contains(query, ignoreCase = true) || partialRatioLevenshtein(name.lowercase(), query.lowercase()) >= 70) {
                searchResult.add(toResult(ch))
            }
        }
        return searchResult
    }

    override suspend fun load(url: String): LoadResponse {
        val splitLink = url.split(" ; ")
        val playUrl = splitLink[0]
        val html = app.get(playUrl, referer = mainUrl).text
        val b64Encoded = encodedUrlRegex.find(html)?.groupValues?.get(1) ?: ""
        val decodedEmbedUrl = if (b64Encoded.isNotBlank()) base64Decode(b64Encoded) else ""
        val m3uLink = decodedEmbedUrl.replace("embed.html", "index.fmp4.m3u8")

        val channelName = splitLink.getOrElse(1) { name }
        val poster = splitLink.getOrNull(2)

        return newLiveStreamLoadResponse(
            name = channelName,
            url = playUrl,
            dataUrl = m3uLink,
        ) {
            this.posterUrl = poster
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
                source = this.name,
                name = this.name,
                url = data,
                type = ExtractorLinkType.M3U8
            )
        )
        return true
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
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
        var minDistance = longer.length

        for (i in 0..longer.length - n) {
            val sub = longer.substring(i, i + n)
            val distance = levenshteinDistance(shorter, sub)
            minDistance = minOf(minDistance, distance)
        }

        val maxLength = shorter.length
        val similarity = ((maxLength - minDistance).toDouble() / maxLength) * 100

        return similarity.toInt()
    }

    data class ChannelItem(
        @JsonProperty("channelId") val channelId: String? = null,
        @JsonProperty("channelName") val channelName: String? = null,
        @JsonProperty("groupName") val groupName: String? = null,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("active") val active: Boolean? = true,
        @JsonProperty("serverId") val serverId: String? = null,
        @JsonProperty("streamName") val streamName: String? = null
    )
}