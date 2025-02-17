// use an integer for version numbers
version = 2

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "Download Mp4Moviez free full movies, high quality movies, latest movies from Mp4Moviez. Mp4moviez is number one entertainment hollywood bollywood website and provide free Mp4moviez full movie download facility"
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
		"NSFW"
    )
    language = "hi"

    iconUrl = "https://www.mp4moviez.glass/images/mp4moviez.png"
}
