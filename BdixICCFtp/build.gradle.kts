// use an integer for version numbers
version = 2

android {
    namespace = "com.redowan"
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "ICC BDIX Provider"
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
        "Anime",
        "AnimeMovie",
        "OVA",
        "Cartoon",
        "AsianDrama",
        "Others",
        "Documentary",
    )
    language = "bn"

    iconUrl = "http://10.16.100.244/images/logo-purple.svg"
}