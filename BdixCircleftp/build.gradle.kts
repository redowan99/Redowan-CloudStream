// use an integer for version numbers
version = 24

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "Only works in Bangladesh. Works even in internet Shutdown"
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
        "Anime",
        "AnimeMovie",
        "OVA",
        "Cartoon",
        "AsianDrama",
        "Others",
        "Documentary",
    )
    language = "bn"

    iconUrl = "http://new.circleftp.net/static/media/logo.fce2c9029060a10687b8.png"
}
