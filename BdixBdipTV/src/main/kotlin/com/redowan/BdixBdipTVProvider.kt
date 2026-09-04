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
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Element

data class LiveChannelData(
    val poster: String,
    val name: String,
    val stream: String
)

open class BdixBdipTVProvider : MainAPI() {
    override var mainUrl = "http://tv.bdiptv.net"
    override var name = "(BDIX) BD IP TV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)
    open val liveServer = "http://103.89.248.14:8082/"

    override val mainPage = mainPageOf(
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

    private suspend fun isChannelWorking(stream: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val playUrl = "$mainUrl/play.php?stream=$stream"
                val doc = app.get(playUrl, referer = mainUrl, timeout = 1L, cacheTime = 60).document
                val iframeSrc = doc.selectFirst("iframe")?.attr("src") ?: return@runCatching false
                val m3uLink = iframeSrc.replace("embed.html", "index.fmp4.m3u8")
                val res = app.get(m3uLink, referer = mainUrl, timeout = 1L, cacheTime = 60)
                res.isSuccessful && res.text.contains("#EXTM3U")
            }.getOrDefault(false)
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse = coroutineScope {
        val doc = app.get(mainUrl, cacheTime = 30).document
        val posts = doc.select("div.item.${request.data}")
        val workingItems = posts.map { post ->
            async(Dispatchers.IO) {
                val result = getResult(post)
                val stream = try {
                    AppUtils.parseJson<LiveChannelData>(result.url).stream
                } catch (_: Exception) {
                    result.url.split(" ; ").getOrNull(2) ?: ""
                }
                if (stream.isNotBlank() && isChannelWorking(stream)) {
                    result
                } else null
            }
        }.awaitAll().filterNotNull()

        newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = workingItems,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private val hrefRegex = Regex("""play\.php\?stream=([^']+)""")
    private fun getResult(post: Element): LiveSearchResponse {
        val imgRelative = post.select("img").attr("src")
        val imageLink = fixUrl(imgRelative)
        val link = hrefRegex.find(post.select("a").attr("onclick"))?.groupValues?.get(1) ?: ""
        val name = link.replace("-", " ")
        val payload = LiveChannelData(imageLink, name, link).toJson()
        return newLiveSearchResponse(name, payload) {
            this.posterUrl = imageLink
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(mainUrl, cacheTime = 60).document
        return doc.select("div.item_content > a").mapNotNull { post ->
            val link = hrefRegex.find(post.attr("onclick"))?.groupValues?.get(1) ?: ""
            if (link.isBlank()) return@mapNotNull null
            val name = link.replace("-", " ")
            if (name.contains(query, ignoreCase = true)) {
                val imgRelative = post.select("img").attr("src")
                val imageLink = fixUrl(imgRelative)
                val payload = LiveChannelData(imageLink, name, link).toJson()
                newLiveSearchResponse(name, payload, TvType.Live, true) {
                    this.posterUrl = imageLink
                }
            } else null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val channelData = try {
            AppUtils.parseJson<LiveChannelData>(url)
        } catch (_: Exception) {
            val parts = url.split(" ; ")
            LiveChannelData(
                poster = parts.getOrNull(0) ?: "",
                name = parts.getOrElse(1) { name },
                stream = parts.getOrNull(2) ?: ""
            )
        }

        val playUrl = "$mainUrl/play.php?stream=${channelData.stream}"
        val doc = try {
            app.get(playUrl, referer = mainUrl, timeout = 15L).document
        } catch (_: Exception) {
            null
        }

        val iframeSrc = doc?.selectFirst("iframe")?.attr("src") ?: ""
        val m3uLink = if (iframeSrc.isNotBlank()) {
            iframeSrc.replace("embed.html", "index.fmp4.m3u8")
        } else {
            "$liveServer${channelData.stream}/index.fmp4.m3u8"
        }

        return newLiveStreamLoadResponse(
            name = channelData.name.ifBlank { name },
            url = url,
            dataUrl = m3uLink
        ) {
            this.posterUrl = channelData.poster
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
}