// use an integer for version numbers
version = 1

cloudstream {
    description = "Movies 18+ ,Rare Movies and selected from all genres online for free, updated daily."
    authors = listOf("salman731")

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
        "NSFW"
    )
    language = "en"

    iconUrl = "https://cat3movie.org/wp-content/themes/hnzphim/public/images/MainPoster.f0fe51.webp"
}
