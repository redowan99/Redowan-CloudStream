// use an integer for version numbers
version = 1

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "720p Movies, 1080p movies, Dual Audio Movies, Hindi Dubbed Series, Hollywood Movies"
    authors = listOf("salman731")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "NSFW",
    )
    language = "hi"

    iconUrl = "https://themoviesflix.ae.org/wp-content/uploads/2022/08/cropped-cropped-favicon-32x32-1-180x180.png"
}
