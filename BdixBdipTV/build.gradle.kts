// use an integer for version numbers
version = 6

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "BDIX Live Tv in Bangladesh"
    authors = listOf("Redowan")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    tvTypes = listOf(
        "Live"
    )
    language = "bn"

    iconUrl = "http://tv.bdiptv.net/assets/images/Live1.jpeg"
}
