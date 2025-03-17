// use an integer for version numbers
version = 6

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "Bollywood, South, Punjabi, Hollywood Movies Download: Filmyhunk Punjabi Movies, Bollywood Movies, Hollywood Movies Dubbed Hindi Download"
    authors = listOf("salman731")

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
    )
    language = "hi"

    iconUrl = "https://filmyhunk.click/wp-content/uploads/2023/12/filmyhunk.png"
}
