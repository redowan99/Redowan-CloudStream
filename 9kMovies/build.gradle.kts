// use an integer for version numbers
version = 7

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "Included:10HitMovies"
    authors = listOf("salman731","Dilip")

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

    iconUrl = "https://9kmovies.claims/m/wp-content/uploads/2019/08/9kmovies.ico"
}
