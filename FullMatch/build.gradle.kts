// use an integer for version numbers
version = 11
dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("com.google.code.gson:gson:2.8.9")
}

cloudstream {
    // All of these properties are optional, you can safely remove them
    description = "Watch Full Matches"
    authors = listOf("salman731")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Others"
    )
    language = "en"

    iconUrl = "https://www.oomoye.co/favicon.png"
}
