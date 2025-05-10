package com.redowan

class BdixIptvidnProvider : BdixBdipTVProvider() {
    override var mainUrl = "http://iptvidn.com/"
    override var name = "(BDIX) IpTvIDN"
    override val liveServer = "http://103.89.248.30:8082/"
}