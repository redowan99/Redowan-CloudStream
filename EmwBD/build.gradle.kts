// use an integer for version numbers
version = 12

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
    status = 1 // will be 3 if unspecified

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "AnimeMovie",
        "AsianDrama",
        "NSFW"
    )
    language = "bn"

    iconUrl = "https://www.emwbd.xyz/wp-content/uploads/2024/07/cropped-20240718_170236-192x192.png"
}
