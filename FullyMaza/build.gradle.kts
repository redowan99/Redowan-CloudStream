// use an integer for version numbers
version = 5

android {
    namespace = "com.redowan"
}

cloudstream {
    //description = "Watch m"
    authors = listOf("Redowan")

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
        "TvSeries",
        "AnimeMovie",
        "Cartoon"
    )
    language = "en"

    iconUrl = "https://fibwatch.online/themes/default/img/fibwatch-logo-new.png"
}
