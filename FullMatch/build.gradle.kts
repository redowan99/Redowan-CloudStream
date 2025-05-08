// use an integer for version numbers
version = 5

android {
    namespace = "com.redowan"
}

cloudstream {
    description = "Watch FootBall Matches"
    authors = listOf("salman731,Redowan")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 0 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Others"
    )
    language = "en"

    iconUrl = "https://images.nightcafe.studio/jobs/CoDdYzJ7jvJ8L2NXtPBh/CoDdYzJ7jvJ8L2NXtPBh--2--gflwm.jpg"
}
