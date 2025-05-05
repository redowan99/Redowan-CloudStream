package com.redowan

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class BdixIptvidnProvider : BdixBdipTVProvider() {
    override var mainUrl = "http://iptvidn.com/"
    override var name = "(BDIX) IpTvIDN"
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
                url = "http://103.89.248.30:8082/${data}",
                type = ExtractorLinkType.M3U8
            )
        )
        return true
    }
}