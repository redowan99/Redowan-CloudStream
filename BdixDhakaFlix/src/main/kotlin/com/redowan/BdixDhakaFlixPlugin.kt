package com.redowan

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class BdixDhakaFlixPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BdixDhakaFlix14Provider())
        registerMainAPI(BdixDhakaFlix7Provider())
        registerMainAPI(BdixDhakaFlix9Provider())
        registerMainAPI(BdixDhakaFlix12Provider())
    }
}