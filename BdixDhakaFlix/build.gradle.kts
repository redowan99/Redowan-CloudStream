// use an integer for version numbers
version = 4

android {
    namespace = "com.redowan"
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Dhaka Flix BDIX Provider"
    authors = listOf("Redowan")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
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

    iconUrl = "https://scontent.fdac138-1.fna.fbcdn.net/v/t39.30808-6/444482169_369047609513005_633848578398240822_n.jpg?_nc_cat=100&ccb=1-7&_nc_sid=6ee11a&_nc_ohc=bQMNK-SnESQQ7kNvwGpbhjS&_nc_oc=AdlKZkxCZ-ST1OlSyb-MSHyBVqd1KWn0XuAyeUX4xnGabklW8bqN7dJrqzhBf5a0Ay0&_nc_zt=23&_nc_ht=scontent.fdac138-1.fna&_nc_gid=B8mGdEcxIDp9BcgCel8wFA&oh=00_AfKY4Ftd0DqFnf5xJayXZtq9Eq1NoHOG0XhBkNSzMk3QiQ&oe=68201A97"
}