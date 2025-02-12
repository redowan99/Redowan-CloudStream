// use an integer for version numbers
version = 13

android {
    namespace = "com.redowan"
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "480p, 720p, 1080p Full Movies In SkymoviesHD"
    authors = listOf("Redowan")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "NSFW"
    )
    language = "en"

    iconUrl = "https://skymovieshd.diy/images/logo2.png"
}
