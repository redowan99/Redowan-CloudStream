package com.redowan

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TheMoviesFlixPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TheMoviesFlixProvider())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(GDFlix1())
        registerExtractorAPI(GDFlix2())
    }
}