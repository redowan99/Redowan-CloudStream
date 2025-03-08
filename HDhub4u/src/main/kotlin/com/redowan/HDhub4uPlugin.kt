package com.redowan

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import android.content.Context

@CloudstreamPlugin
class HDhub4uPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(HDhub4uProvider())
        registerExtractorAPI(HdStream4u())
        //registerExtractorAPI(VidStack()))
    }
}