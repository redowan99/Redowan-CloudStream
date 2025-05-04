package com.redowan

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
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
////    providerTester.testMainPage(verbose = true)
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
        val response = channels.map { channel ->
            newLiveSearchResponse(
                channel["name"].toString(), channel["link"].toString()
            ) {
                this.posterUrl = channel["logo"].toString()
            }
        }
        home.add(
            HomePageList(
                "Live Tv", response, isHorizontalImages = true
            )
        )
        return newHomePageResponse(home, hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse {
        val channel = channels.first { it["link"] == url }
        return newLiveStreamLoadResponse(name = channel["name"].toString(), url = mainUrl, dataUrl = channel["link"].toString()) {
            this.posterUrl = channel["logo"].toString()
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
                mainUrl, this.name, url = data, type = ExtractorLinkType.M3U8
            ){
                this.referer = "$mainUrl/live-tv"
            }
        )
        return true
    }
}