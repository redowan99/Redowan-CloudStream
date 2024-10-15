// use an integer for version numbers
version = 13

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
    status = 1 // will be 3 if unspecified

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
