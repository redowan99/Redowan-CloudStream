package com.redowan

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class EmwBDPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(EmwBDProvider())
        registerExtractorAPI(Do7go())
        registerExtractorAPI(Movearnpre())
    }
}