// use an integer for version numbers
version = 15

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "Movie website in Bangladesh"
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
        "Anime",
        "AnimeMovie",
        "AsianDrama"
    )
    language = "bn"

    iconUrl = "https://rtally.vercel.app/rtally.png"
}
