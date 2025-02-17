// use an integer for version numbers
version = 18

android {
    namespace = "com.redowan"
}

cloudstream {
    // All of these properties are optional, you can safely remove them
    description = "Indian movie site"
    authors = listOf("Redowan")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 0 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Cartoon",
        "NSFW"
    )
    language = "en"

    iconUrl = "https://www.oomoye.co/favicon.png"
}
