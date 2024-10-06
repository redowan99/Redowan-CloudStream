package com.redowan

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

//class GDFlix39 : GDMirrorBot() {
//    override val mainUrl: String = "https://new3.gdflix.cfd"
//}
//
//class GDFlix28 : GDMirrorBot() {
//    override val mainUrl: String = "https://new2.gdflix.cfd"
//}

open class GDMirrorBot : ExtractorApi() {
    override val name: String = "GDMirrorBot"
    override val mainUrl: String = "https://gdmirrorbot.nl"
    override val requiresReferer = false
    private val headers =
        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, cacheTime = 60, allowRedirects = true, headers = headers).document
        for (tableRow in doc.select(".table > tbody > tr")) {
            if (tableRow.select("th").isNotEmpty()) {
                break
            }
            val link = storyyExtractor(tableRow.select("td:nth-child(2) > a:nth-child(1)").attr("href"))
            loadExtractor(link, subtitleCallback, callback)
        }
    }
    private suspend fun storyyExtractor(url:String): String {
        val doc = app.get(url, cacheTime = 60, allowRedirects = true, headers = headers).document
        return doc.select(".col-lg-12 > iframe:nth-child(4)").attr("src")
    }
}