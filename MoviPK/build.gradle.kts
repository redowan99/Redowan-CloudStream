// use an integer for version numbers
version = 2

android {
    namespace = "com.redowan"
}

cloudstream {
    //description = ""
    authors = listOf("MoviPK")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    language = "en"

    iconUrl = "https://www.movi.pk/wp-content/uploads/2023/06/apple-touch-icon.png"
}
