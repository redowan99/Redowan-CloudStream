// use an integer for version numbers
version = 4

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "Watch Full Sexiest Movie For Free"
    authors = listOf("Film1K")

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
    language = "en"

    iconUrl = "https://www.film1k.com/wp-content/uploads/2023/05/cropped-film1k-1.png"
}
