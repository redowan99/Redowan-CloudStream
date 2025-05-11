package com.redowan

import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro

class Do7go : DoodLaExtractor() {
    override var mainUrl: String = "https://do7go.com"
}

class Movearnpre : VidHidePro() {
    override val mainUrl: String = "https://movearnpre.com"
}