// use an integer for version numbers
version = 2

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "watch hindi dubbed movie free download, free download latest bollywood movie, south hindi dubbed free movie , english adult movie, free watch 18+ movie"
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
		"NSFW"
    )
    language = "hi"

    iconUrl = "https://livemovie.org/wp-content/uploads/2023/11/sso.png.webp"
}
