// use an integer for version numbers
version = 5

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "Free Download All Hollywood, Bollywood, Bangla, TV Series, Turkish/ Chinese Movies | Korean Drama Series In Hindi Dubbed, English (Dual Audio), | HEVC 10bit | X264 300mb | K-Drama | Anime In Hindi | Watch Online"
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
        "TvSeries"
    )
    language = "hi"

    iconUrl = "https://cinefreak.net/wp-content/uploads/2024/08/cropped-cgk-192x192.png"
}
