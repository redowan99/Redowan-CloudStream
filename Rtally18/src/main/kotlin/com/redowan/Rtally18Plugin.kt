package com.redowan

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Rtally18Plugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Rtally18Provider())
    }
}