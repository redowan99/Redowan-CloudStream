package com.redowan

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class EmwBDPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(EmwBDProvider())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(GDFlix3())
        registerExtractorAPI(GDFlix2())
        registerExtractorAPI(GDMirrorBot())
    }
}