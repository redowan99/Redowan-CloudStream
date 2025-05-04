package com.redowan

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
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
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(BdixRoarZoneTVProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
//    providerTester.testLoad("http://tvassets.roarzone.info/images/4.png ; Channel 9 ; bk/4")
//}

class BdixRoarZoneTVProvider : MainAPI() {
    override var mainUrl = "http://tv.roarzone.info/"
    override var name = "(BDIX) RoarZone TV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)
    private val category = mapOf(
        "bangla" to "Bangla",
        "sports" to "Sports",
        "hindi" to "Hindi",
        "inbangla" to "Indian Bangla",
        "english" to "English",
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
                toResult(it)
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

    private val hrefRegex = Regex("player\\.php\\?stream=([^']+)")
    private fun toResult(post: Element): LiveSearchResponse {
        val imageAttr = post.select("img")
        val imageLink = imageAttr.attr("src")
        val name = imageAttr.attr("alt")
        val link = hrefRegex.find(post.select("a").attr("onclick"))?.groupValues?.get(1) ?: ""
        val joinedLink = "$imageLink ; $name ; $link"
        return newLiveSearchResponse(name, joinedLink) {
            this.posterUrl = imageLink
        }
    }

    private val tokenRegex = Regex("token=([^&]+)")
    override suspend fun load(url: String): LoadResponse {
        val splitLink = url.split(" ; ")
        val url1 = "http://tv.roarzone.info/player.php?stream=${splitLink[2]}"
        val redirectUrl = app.head(url1).url
        val token = tokenRegex.find(redirectUrl)?.value.toString()
        val m3uLink = "http://peer19.roarzone.info:8080/roarzone/${splitLink[2]}/index.fmp4.m3u8?$token"
        return newLiveStreamLoadResponse(name = splitLink[1], url = url1, dataUrl = m3uLink) {
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
                url = data,
                type = ExtractorLinkType.M3U8
            )
        )
        return true
    }
}