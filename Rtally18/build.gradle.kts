// use an integer for version numbers
version = 6

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "18+ website in Bangladesh"
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
        "NSFW"
    )
    language = "bn"

    iconUrl = "https://rtally18.vercel.app/rtally18.png"
}
