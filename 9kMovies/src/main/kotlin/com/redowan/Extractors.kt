package com.redowan

import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.StreamTape

class StreamTapeTo : StreamTape() {
    override var mainUrl = "https://streamtape.net"
}

class StreamTapeSite : StreamTape() {
    override var mainUrl = "https://streamtape.site"
}

class DoodLi : DoodLaExtractor() {
    override var mainUrl = "https://dood.li"
}

