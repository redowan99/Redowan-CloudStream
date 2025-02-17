// use an integer for version numbers
version = 3

android {
    namespace = "com.redowan"
}

cloudstream {
    //description = "Movie website in Bangladesh"
    authors = listOf("Redowan")

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
        "TvSeries",
    )
    language = "en"

    iconUrl = "https://www.hdhub4u.com.mx/templates/favicon.ico"
}
