// use an integer for version numbers
version = 3

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "720p Movies, 480p Movies, 300MB Movies"
    authors = listOf("salman731")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 0 // will be 3 if unspecified

    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    language = "hi"

    iconUrl = "https://hindmoviez.club/wp-content/themes/generate-pro/images/favicon.ico"
}
