package com.redowan

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NineKMoviesPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(NineKMoviesProvider())
        registerMainAPI(TenHitMoviesProvider())
        registerExtractorAPI(StreamTapeSite())
        registerExtractorAPI(StreamTapeTo())
        registerExtractorAPI(DoodLi())
    }
}