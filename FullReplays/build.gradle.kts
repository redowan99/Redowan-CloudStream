// use an integer for version numbers
version = 3

cloudstream {
    description = "Watch Football Full Match Replay and Shows"
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
        "Others",
    )
    language = "en"

    iconUrl = "https://www.fullreplays.com/wp-content/uploads/2023/05/logo-ball-150x150.png"
}
