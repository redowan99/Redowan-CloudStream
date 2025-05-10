package com.redowan

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(BDIXCloudTVProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
//    providerTester.testLoad("http://172.19.178.180/play.php?id=1743012009583 ; T Sports ; http://172.19.178.178/tv/img/tsports.png")
//}

class BDIXCloudTVProvider : MainAPI() {
    override var mainUrl = "http://172.19.178.180/"
    override var name = "(BDIX) CloudTV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val home = mutableListOf<HomePageList>()
        channelDataMap.forEach{
            val response = it.value.map { item ->
                getResult(item)
            }
            home.add(
                HomePageList(
                    it.key, response, isHorizontalImages = true
                )
            )
        }
        return newHomePageResponse(home, hasNext = false)
    }

    private fun getResult(item: Map<String, String>): LiveSearchResponse {
        val link = "${mainUrl}play.php?id=${item["channelId"]}"
        val joinedLink = "$link ; ${item["channelName"]} ; ${item["logo"]}"
        return newLiveSearchResponse(
            item["channelName"].toString(), joinedLink
        ) {
            this.posterUrl = item["logo"].toString()
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val searchResult: MutableList<LiveSearchResponse> = mutableListOf()
        channelDataMap.forEach{
            it.value.map { item ->
                getSearchResult(item,query,searchResult)
            }
        }
        return searchResult
    }

    private fun getSearchResult(
        item: Map<String, String>,
        query: String,
        searchResult: MutableList<LiveSearchResponse>
    ) {
        val name = item["channelName"].toString()
        val distance = partialRatioLevenshtein(name.lowercase(), query.lowercase())
        if (distance >= 70) {
            val link = "${mainUrl}play.php?id=${item["channelId"]}"
            val joinedLink = "$link ; ${item["channelName"]} ; ${item["logo"]}"
            searchResult.add(
                newLiveSearchResponse(name, joinedLink) {
                    this.posterUrl = item["logo"].toString()
                }
            )
        }
    }

    private var token = ""
    private var tokenLink = ""
    private val tokenRegex = Regex("token=([^&]+)")
    override suspend fun load(url: String): LoadResponse {
        val splitLink = url.split(" ; ")
        val doc = app.get(splitLink[0]).document
        tokenLink = doc.selectFirst("iframe")?.attr("src") ?: ""
        if(token.isBlank()) token = tokenRegex.find(tokenLink)?.value.toString()
        val link = tokenLink.replaceAfter(token,"").replace("embed.html?","index.fmp4.m3u8?")
        return newLiveStreamLoadResponse(
            name = splitLink[1],
            url = splitLink[0],
            dataUrl = link,
        ) {
            posterUrl = splitLink[2]
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data,
                this.name,
                url = data,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = tokenLink
            }
        )
        return true
    }
    private fun levenshteinDistance(s1: String, s2: String): Int {
        // ... (same Levenshtein distance function as before)
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) {
            dp[i][0] = i
        }
        for (j in 0..n) {
            dp[0][j] = j
        }

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost)
            }
        }
        return dp[m][n]
    }

    private fun partialRatioLevenshtein(s1: String, s2: String): Int {
        val shorter: String
        val longer: String

        if (s1.length <= s2.length) {
            shorter = s1
            longer = s2
        } else {
            shorter = s2
            longer = s1
        }

        val n = shorter.length
        var minDistance = longer.length // Initialize with maximum possible distance

        for (i in 0..longer.length - n) {
            val sub = longer.substring(i, i + n)
            val distance = levenshteinDistance(shorter, sub)
            minDistance = minOf(minDistance, distance)
        }

        // Normalize the distance to a 0-100 scale
        // A distance of 0 is a perfect match (score 100)
        // The maximum possible distance for a partial match is the length of the shorter string
        val maxLength = shorter.length
        val similarity = ((maxLength - minDistance).toDouble() / maxLength) * 100

        return similarity.toInt()
    }

    private val channelDataMap = mapOf(
        "Bangladeshi" to listOf(
            mapOf(

                "channelId" to "1743017681682",
                "channelName" to "GTV",
                "logo" to "http://172.19.178.178/tv/img/gtv.png",

                ),
            mapOf(

                "channelId" to "1743017958008",
                "channelName" to "Maasranga",
                "logo" to "http://172.19.178.178/tv/img/maasranga.png",

                ),
            mapOf(

                "channelId" to "1743018335900",
                "channelName" to "Jamuna TV",
                "logo" to "http://172.19.178.178/tv/img/jamunatv.png",

                ),
            mapOf(

                "channelId" to "1743018412949",
                "channelName" to "Somoy TV",
                "logo" to "http://172.19.178.178/tv/img/somoytv.png",

                ),
            mapOf(

                "channelId" to "1743018479488",
                "channelName" to "Channel I",
                "logo" to "http://172.19.178.178/tv/img/channeli.png",

                ),
            mapOf(

                "channelId" to "1743542150784",
                "channelName" to "Bangla TV",
                "logo" to "http://172.19.178.178/tv/img/banglatv.png",

                ),
            mapOf(

                "channelId" to "1743542373826",
                "channelName" to "Bangla Vision",
                "logo" to "http://172.19.178.178/tv/img/banglavision.png",

                ),
            mapOf(

                "channelId" to "1743542663768",
                "channelName" to "Bijoy TV",
                "logo" to "http://172.19.178.178/tv/img/bijoytv.png",

                ),
            mapOf(

                "channelId" to "1743545282943",
                "channelName" to "Channel 9",
                "logo" to "http://172.19.178.178/tv/img/channel9.png",

                ),
            mapOf(

                "channelId" to "1743545848935",
                "channelName" to "Deepto",
                "logo" to "http://172.19.178.178/tv/img/deepto.png",

                ),
            mapOf(

                "channelId" to "1743546111567",
                "channelName" to "Desh TV",
                "logo" to "http://172.19.178.178/tv/img/deshtv.png",

                ),
            mapOf(

                "channelId" to "1743546371751",
                "channelName" to "Duronto",
                "logo" to "http://172.19.178.178/tv/img/duronto.png",

                ),
            mapOf(

                "channelId" to "1743546888991",
                "channelName" to "Ekushey TV",
                "logo" to "http://172.19.178.178/tv/img/ekusheytv.png",

                ),
            mapOf(

                "channelId" to "1743548677072",
                "channelName" to "Independent",
                "logo" to "http://172.19.178.178/tv/img/independent.png",

                ),
            mapOf(

                "channelId" to "1743550196812",
                "channelName" to "News 24",
                "logo" to "http://172.19.178.178/tv/img/news24.png",

                ),
            mapOf(

                "channelId" to "1743550329181",
                "channelName" to "Nexus Television",
                "logo" to "http://172.19.178.178/tv/img/nexustelevision.png",

                ),
            mapOf(

                "channelId" to "1743551064875",
                "channelName" to "RTV",
                "logo" to "http://172.19.178.178/tv/img/rtv.png",

                ),
            mapOf(

                "channelId" to "1743552070126",
                "channelName" to "NTV",
                "logo" to "http://172.19.178.178/tv/img/ntv.png",

                ),
            mapOf(

                "channelId" to "1743553215693",
                "channelName" to "ATN Bangla",
                "logo" to "http://172.19.178.178/tv/img/atnbangla.png",

                ),
            mapOf(

                "channelId" to "1743553255361",
                "channelName" to "Boishakhi TV",
                "logo" to "http://172.19.178.178/tv/img/boishakhitv.png",

                ),
            mapOf(

                "channelId" to "1743553499259",
                "channelName" to "Channel 24",
                "logo" to "http://172.19.178.178/tv/img/channel24.png",

                ),
            mapOf(

                "channelId" to "1743553628830",
                "channelName" to "Asian",
                "logo" to "http://172.19.178.178/tv/img/asian.png",

                ),
            mapOf(

                "channelId" to "1743553876644",
                "channelName" to "SA TV",
                "logo" to "http://172.19.178.178/tv/img/satv.png",

                ),
            mapOf(

                "channelId" to "1743554022213",
                "channelName" to "DBC",
                "logo" to "http://172.19.178.178/tv/img/dbc.png",

                ),
            mapOf(

                "channelId" to "1743554160090",
                "channelName" to "Ekhon",
                "logo" to "http://172.19.178.178/tv/img/ekhon.png",

                ),
            mapOf(

                "channelId" to "1743554442703",
                "channelName" to "71",
                "logo" to "http://172.19.178.178/tv/img/71.png",

                ),
            mapOf(

                "channelId" to "1743554633424",
                "channelName" to "Mohona TV",
                "logo" to "http://172.19.178.178/tv/img/mohonatv.png",

                ),
            mapOf(

                "channelId" to "1743554809292",
                "channelName" to "Global Television",
                "logo" to "http://172.19.178.178/tv/img/globaltelevision.png",

                ),
            mapOf(

                "channelId" to "1743554926099",
                "channelName" to "My TV",
                "logo" to "http://172.19.178.178/tv/img/mytv.png",

                ),
            mapOf(

                "channelId" to "1743555141100",
                "channelName" to "Ananda TV",
                "logo" to "http://172.19.178.178/tv/img/anandatv.png",

                ),
            mapOf(

                "channelId" to "1743663429051",
                "channelName" to "ATN News",
                "logo" to "http://172.19.178.178/tv/img/atnnews.png",

                ),
            mapOf(

                "channelId" to "1743663495299",
                "channelName" to "Movie Bangla",
                "logo" to "http://172.19.178.178/tv/img/moviebangla.png",

                ),
        ),
        "Sports" to listOf(
            mapOf(

                "channelId" to "1743086176641",
                "channelName" to "T Sports",
                "logo" to "http://172.19.178.178/tv/img/tsports.png",

                ),
            mapOf(

                "channelId" to "1743098626999",
                "channelName" to "Star Sports 1",
                "logo" to "http://172.19.178.178/tv/img/starsports1.png",

                ),
            mapOf(

                "channelId" to "1743099075078",
                "channelName" to "Star Sports 2",
                "logo" to "http://172.19.178.178/tv/img/starsports2.png",

                ),
            mapOf(

                "channelId" to "1743100377651",
                "channelName" to "Star Sports 3",
                "logo" to "http://172.19.178.178/tv/img/starsports3.png",

                ),
            mapOf(

                "channelId" to "1743100898825",
                "channelName" to "Star Sports Select 1",
                "logo" to "http://172.19.178.178/tv/img/starsportsselect1.png",

                ),
            mapOf(

                "channelId" to "1743100955769",
                "channelName" to "Star Sports Select 2 ",
                "logo" to "http://172.19.178.178/tv/img/starsportsselect2.png",

                ),
            mapOf(

                "channelId" to "1743281092131",
                "channelName" to "Sony Sports Ten 1",
                "logo" to "http://172.19.178.178/tv/img/sonysportsten1.png",

                ),
            mapOf(

                "channelId" to "1743281340957",
                "channelName" to "Sony Sports Ten 2",
                "logo" to "http://172.19.178.178/tv/img/sonysportsten2.png",

                ),
            mapOf(

                "channelId" to "1743281381389",
                "channelName" to "Sony Sports Ten 3",
                "logo" to "http://172.19.178.178/tv/img/sonysportsten3.png",

                ),
            mapOf(

                "channelId" to "1743281501175",
                "channelName" to "Sony Sports Ten 4",
                "logo" to "http://172.19.178.178/tv/img/sonysportsten4.png",

                ),
            mapOf(

                "channelId" to "1743281540455",
                "channelName" to "Sony Sports Ten 5",
                "logo" to "http://172.19.178.178/tv/img/sonysportsten5.png",

                ),
            mapOf(

                "channelId" to "1743555343987",
                "channelName" to "A Sports",
                "logo" to "http://172.19.178.178/tv/img/asports.png",

                ),
            mapOf(

                "channelId" to "1743555756615",
                "channelName" to "PTV Sports",
                "logo" to "http://172.19.178.178/tv/img/ptvsports.png",

                ),
            mapOf(

                "channelId" to "1743555922161",
                "channelName" to "Fox Sports",
                "logo" to "http://172.19.178.178/tv/img/foxsports.png",

                ),
            mapOf(

                "channelId" to "1743556231511",
                "channelName" to "TNT Sports",
                "logo" to "http://172.19.178.178/tv/img/tntsports.png",

                ),
            mapOf(

                "channelId" to "1743556724229",
                "channelName" to "ESPN",
                "logo" to "http://172.19.178.178/tv/img/espn.png",

                ),
            mapOf(

                "channelId" to "1743556749975",
                "channelName" to "TSN",
                "logo" to "http://172.19.178.178/tv/img/tsn.png",

                ),
            mapOf(

                "channelId" to "1744013031560",
                "channelName" to "Eurosport",
                "logo" to "http://172.19.178.178/tv/img/eurosport.png",

                ),
            mapOf(

                "channelId" to "1744775202401",
                "channelName" to "Ten Cricket",
                "logo" to "http://172.19.178.178/tv/img/tencricket.png",

                ),
        ),
        "Infotainment" to listOf(
            mapOf(

                "channelId" to "1743541864551",
                "channelName" to "BBC Lifestyle",
                "logo" to "http://172.19.178.178/tv/img/bbclifestyle.png",

                ),
            mapOf(

                "channelId" to "1744771842367",
                "channelName" to "Discovery",
                "logo" to "http://172.19.178.178/tv/img/discovery.png",

                ),
            mapOf(

                "channelId" to "1744772014540",
                "channelName" to "Nat Geo Wild",
                "logo" to "http://172.19.178.178/tv/img/natgeowild.png",

                ),
            mapOf(

                "channelId" to "1745144466579",
                "channelName" to "TLC",
                "logo" to "http://172.19.178.178/tv/img/tlc.png",

                ),
            mapOf(

                "channelId" to "1745144496905",
                "channelName" to "Animal Planet",
                "logo" to "http://172.19.178.178/tv/img/animalplanet.png",

                ),
            mapOf(

                "channelId" to "1745144517000",
                "channelName" to "Sony BBC Earth",
                "logo" to "http://172.19.178.178/tv/img/sonybbcearth.png",

                ),
            mapOf(

                "channelId" to "1745144550567",
                "channelName" to "Discovery Science",
                "logo" to "http://172.19.178.178/tv/img/discoveryscience.png",

                ),
            mapOf(

                "channelId" to "1745144571388",
                "channelName" to "Discovery Turbo",
                "logo" to "http://172.19.178.178/tv/img/discoveryturbo.png",

                ),
            mapOf(

                "channelId" to "1745144594406",
                "channelName" to "Investigation Discovery",
                "logo" to "http://172.19.178.178/tv/img/investigationdiscovery.png",

                ),
        ),
        "Hindi" to listOf(
            mapOf(

                "channelId" to "1744663941983",
                "channelName" to "Star Plus",
                "logo" to "http://172.19.178.178/tv/img/starplus.png",

                ),
            mapOf(

                "channelId" to "1743551774539",
                "channelName" to "Zee  TV",
                "logo" to "http://172.19.178.178/tv/img/zeetv.png",

                ),
            mapOf(

                "channelId" to "1744664018367",
                "channelName" to "Sony Entertainment Television",
                "logo" to "http://172.19.178.178/tv/img/sonyentertainmenttelevision.png",

                ),
            mapOf(

                "channelId" to "1744664050488",
                "channelName" to "Colors",
                "logo" to "http://172.19.178.178/tv/img/colors.png",

                ),
            mapOf(

                "channelId" to "1744664069401",
                "channelName" to "& TV",
                "logo" to "http://172.19.178.178/tv/img/andtv.png",

                ),
            mapOf(

                "channelId" to "1744664115724",
                "channelName" to "Star Gold",
                "logo" to "http://172.19.178.178/tv/img/stargold.png",

                ),
            mapOf(

                "channelId" to "1743551812597",
                "channelName" to "Zee Cinema",
                "logo" to "http://172.19.178.178/tv/img/zeecinema.png",

                ),
            mapOf(

                "channelId" to "1744664182801",
                "channelName" to "Sony Max",
                "logo" to "http://172.19.178.178/tv/img/sonymax.png",

                ),
            mapOf(

                "channelId" to "1744664205283",
                "channelName" to "& Pictures",
                "logo" to "http://172.19.178.178/tv/img/andpictures.png",

                ),
            mapOf(

                "channelId" to "1745119246399",
                "channelName" to "Sony SAB",
                "logo" to "http://172.19.178.178/tv/img/sonysab.png",

                ),
            mapOf(

                "channelId" to "1745119941130",
                "channelName" to "B4U Movies",
                "logo" to "http://172.19.178.178/tv/img/b4umovies.png",

                ),
            mapOf(

                "channelId" to "1745120121718",
                "channelName" to "Sony Max 2",
                "logo" to "http://172.19.178.178/tv/img/sonymax2.png",

                ),
        ),
        "Indian Bangla" to listOf(
            mapOf(

                "channelId" to "1743289513200",
                "channelName" to "Star Jalsha",
                "logo" to "http://172.19.178.178/tv/img/starjalsha.png",

                ),
            mapOf(

                "channelId" to "1743289547975",
                "channelName" to "Zee Bangla",
                "logo" to "http://172.19.178.178/tv/img/zeebangla.png",

                ),
            mapOf(

                "channelId" to "1744662256048",
                "channelName" to "Sony Aath",
                "logo" to "http://172.19.178.178/tv/img/sonyaath.png",

                ),
            mapOf(

                "channelId" to "1744662418727",
                "channelName" to "Colors Bangla",
                "logo" to "http://172.19.178.178/tv/img/colorsbangla.png",

                ),
            mapOf(

                "channelId" to "1744662448484",
                "channelName" to "Jalsha Movies",
                "logo" to "http://172.19.178.178/tv/img/jalshamovies.png",

                ),
            mapOf(

                "channelId" to "1744662526474",
                "channelName" to "Zee Bangla Cinema",
                "logo" to "http://172.19.178.178/tv/img/zeebanglacinema.png",

                ),
        ),
        "Kids" to listOf(
            mapOf(

                "channelId" to "1743544469094",
                "channelName" to "CBeebies BBC",
                "logo" to "http://172.19.178.178/tv/img/cbeebiesbbc.png",

                ),
            mapOf(

                "channelId" to "1744771588117",
                "channelName" to "Hungama",
                "logo" to "http://172.19.178.178/tv/img/hungama.png",

                ),
            mapOf(

                "channelId" to "1744771612218",
                "channelName" to "Nick Jr.",
                "logo" to "http://172.19.178.178/tv/img/nickjr.png",

                ),
            mapOf(

                "channelId" to "1744775615859",
                "channelName" to "Cartoon Network",
                "logo" to "http://172.19.178.178/tv/img/cartoonnetwork.png",

                ),
            mapOf(

                "channelId" to "1744775476597",
                "channelName" to "Cartoon Network HD+",
                "logo" to "http://172.19.178.178/tv/img/cartoonnetworkhdplus.png",

                ),
            mapOf(

                "channelId" to "1745117412375",
                "channelName" to "Pogo",
                "logo" to "http://172.19.178.178/tv/img/pogo.png",

                ),
            mapOf(

                "channelId" to "1745117673331",
                "channelName" to "Discovery Kids",
                "logo" to "http://172.19.178.178/tv/img/discoverykids.png",

                ),
            mapOf(

                "channelId" to "1745117714452",
                "channelName" to "Sony YAY",
                "logo" to "http://172.19.178.178/tv/img/sonyyay.png",

                ),
        ),
        "Music" to listOf(
            mapOf(

                "channelId" to "1744773856821",
                "channelName" to "B4U Music",
                "logo" to "http://172.19.178.178/tv/img/b4umusic.png",

                ),
            mapOf(

                "channelId" to "1744773886327",
                "channelName" to "9x Jalwa",
                "logo" to "http://172.19.178.178/tv/img/9xjalwa.png",

                ),
        ),
        "Foreign" to listOf(
            mapOf(

                "channelId" to "1743540455154",
                "channelName" to "Arirang",
                "logo" to "http://172.19.178.178/tv/img/arirang.png",

                ),
            mapOf(

                "channelId" to "1743549175928",
                "channelName" to "Lotus Macau",
                "logo" to "http://172.19.178.178/tv/img/lotusmacau.png",

                ),
            mapOf(

                "channelId" to "1745118544636",
                "channelName" to "Hum TV",
                "logo" to "http://172.19.178.178/tv/img/humtv.png",

                ),
            mapOf(

                "channelId" to "1745118586484",
                "channelName" to "Hum Masala",
                "logo" to "http://172.19.178.178/tv/img/hummasala.png",

                ),
            mapOf(

                "channelId" to "1745118620733",
                "channelName" to "Hum Sitaray",
                "logo" to "http://172.19.178.178/tv/img/humsitaray.png",

                ),
        ),
        "News" to listOf(
            mapOf(

                "channelId" to "1743544977514",
                "channelName" to "CGTN",
                "logo" to "http://172.19.178.178/tv/img/cgtn.png",

                ),
            mapOf(

                "channelId" to "1743545138036",
                "channelName" to "CNN",
                "logo" to "http://172.19.178.178/tv/img/cnn.png",

                ),
            mapOf(

                "channelId" to "1743549742386",
                "channelName" to "NHK World Japan",
                "logo" to "http://172.19.178.178/tv/img/nhkworldjapan.png",

                ),
            mapOf(

                "channelId" to "1744773377210",
                "channelName" to "Deutsche Welle",
                "logo" to "http://172.19.178.178/tv/img/deutschewelle.png",

                ),
        ),
        "English" to listOf(
            mapOf(

                "channelId" to "1743548479753",
                "channelName" to "FOX Action Movies",
                "logo" to "http://172.19.178.178/tv/img/foxactionmovies.png",

                ),
            mapOf(

                "channelId" to "1744772967849",
                "channelName" to "Star Movies",
                "logo" to "http://172.19.178.178/tv/img/starmovies.png",

                ),
            mapOf(

                "channelId" to "1744773002365",
                "channelName" to "Sony Pix",
                "logo" to "http://172.19.178.178/tv/img/sonypix.png",

                ),
        ),
        "CloudTV" to listOf(
            mapOf(

                "channelId" to "1743010891723",
                "channelName" to "Sony Sports Ten 5",
                "logo" to "http://172.19.178.178/tv/img/sonysportsten5.png",

                ),
            mapOf(

                "channelId" to "1743012009583",
                "channelName" to "T Sports",
                "logo" to "http://172.19.178.178/tv/img/tsports.png",

                ),
        ),
        "Religious" to listOf(
            mapOf(

                "channelId" to "1743550752114",
                "channelName" to "Saudi Quraan TV",
                "logo" to "http://172.19.178.178/tv/img/saudiqurantv.png",

                ),
        ),
    )



}

//private val category = listOf(
//    "Bangladeshi",
//    "Sports",
//    "CloudTV",
//    "Foreign",
//    "Indian Bangla",
//    "Infotainment",
//    "Kids",
//    "News",
//    "English",
//    "Religious",
//    "Hindi",
//    "Music"
//)
//
//private fun mapMaker(): Boolean {
//    val parsedJson = AppUtils.parseJson<Channels>(json)
//    println("mapOf(")
//    category.forEach { it ->
//        println("   \"$it\" to listOf(")
//        val test1: List<String>
//        parsedJson.forEach { item ->
//            if (it == item.groupName) {
//                println("       mapOf(")
//                println(
//                    """
//            "channelId" to "${item.channelId}",
//            "channelName" to "${item.channelName}",
//            "logo" to "${item.logo}",
//                        """
//                )
//                println("       ),")
//            }
//        }
//        println("   ),")
//    }
//    println(")")
//    return true
//}
//private var json = """[
//  {
//    "channelId": "1743010891723",
//    "channelName": "Sony Sports Ten 5",
//    "groupName": "CloudTV",
//    "logo": "http://172.19.178.178/tv/img/sonysportsten5.png",
//
//    "active": true,
//    "serverId": "67e43c187e835",
//    "streamName": "CLOUD-TV-01"
//  },
//  {
//    "channelId": "1743012009583",
//    "channelName": "T Sports",
//    "groupName": "CloudTV",
//    "logo": "http://172.19.178.178/tv/img/tsports.png",
//
//    "serverId": "67e43c187e835",
//    "streamName": "CLOUD-TV-02",
//    "active": true
//  },
//  {
//    "channelId": "1743017681682",
//    "channelName": "GTV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/gtv.png",
//
//    "active": true,
//    "serverId": "67e456c5ae421",
//    "streamName": "gtv"
//  },
//  {
//    "channelId": "1743017958008",
//    "channelName": "Maasranga",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/maasranga.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "maasranga",
//    "active": true
//  },
//  {
//    "channelId": "1743018335900",
//    "channelName": "Jamuna TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/jamunatv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "jamunatv",
//    "active": true
//  },
//  {
//    "channelId": "1743018412949",
//    "channelName": "Somoy TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/somoytv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "somoytv",
//    "active": true
//  },
//  {
//    "channelId": "1743018479488",
//    "channelName": "Channel I",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/channeli.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "channeli",
//    "active": true
//  },
//  {
//    "channelId": "1743086176641",
//    "channelName": "T Sports",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/tsports.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "tsports",
//    "active": true
//  },
//  {
//    "channelId": "1743098626999",
//    "channelName": "Star Sports 1",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/starsports1.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "starsports1"
//  },
//  {
//    "channelId": "1743099075078",
//    "channelName": "Star Sports 2",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/starsports2.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "starsports2"
//  },
//  {
//    "channelId": "1743100377651",
//    "channelName": "Star Sports 3",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/starsports3.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "starsports3"
//  },
//  {
//    "channelId": "1743100898825",
//    "channelName": "Star Sports Select 1",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/starsportsselect1.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "starsportsselect1"
//  },
//  {
//    "channelId": "1743540455154",
//    "channelName": "Arirang",
//    "groupName": "Foreign",
//    "logo": "http://172.19.178.178/tv/img/arirang.png",
//
//    "serverId": "67ec50dd96550",
//    "streamName": "arirang",
//    "active": true
//  },
//  {
//    "channelId": "1743100955769",
//    "channelName": "Star Sports Select 2 ",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/starsportsselect2.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "starsportsselect2"
//  },
//  {
//    "channelId": "1743289513200",
//    "channelName": "Star Jalsha",
//    "groupName": "Indian Bangla",
//    "logo": "http://172.19.178.178/tv/img/starjalsha.png",
//
//    "active": true,
//    "serverId": "67e87b6509828",
//    "streamName": "starjalsha"
//  },
//  {
//    "channelId": "1743289547975",
//    "channelName": "Zee Bangla",
//    "groupName": "Indian Bangla",
//    "logo": "http://172.19.178.178/tv/img/zeebangla.png",
//
//    "active": true,
//    "serverId": "67e87b6509828",
//    "streamName": "zeebangla"
//  },
//  {
//    "channelId": "1743281092131",
//    "channelName": "Sony Sports Ten 1",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/sonysportsten1.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "sonysportsten1"
//  },
//  {
//    "channelId": "1743281340957",
//    "channelName": "Sony Sports Ten 2",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/sonysportsten2.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "sonysportsten2"
//  },
//  {
//    "channelId": "1743281381389",
//    "channelName": "Sony Sports Ten 3",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/sonysportsten3.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "sonysportsten3"
//  },
//  {
//    "channelId": "1743281501175",
//    "channelName": "Sony Sports Ten 4",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/sonysportsten4.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "sonysportsten4"
//  },
//  {
//    "channelId": "1743281540455",
//    "channelName": "Sony Sports Ten 5",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/sonysportsten5.png",
//
//    "active": true,
//    "serverId": "67e56025c2d4c",
//    "streamName": "sonysportsten5"
//  },
//  {
//    "channelId": "1743542150784",
//    "channelName": "Bangla TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/banglatv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "banglatv",
//    "active": true
//  },
//  {
//    "channelId": "1743541864551",
//    "channelName": "BBC Lifestyle",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/bbclifestyle.png",
//
//    "active": true,
//    "serverId": "67ec53c1b4472",
//    "streamName": "bbclifestyle"
//  },
//  {
//    "channelId": "1743542373826",
//    "channelName": "Bangla Vision",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/banglavision.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "banglavision",
//    "active": true
//  },
//  {
//    "channelId": "1743542663768",
//    "channelName": "Bijoy TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/bijoytv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "bijoytv",
//    "active": true
//  },
//  {
//    "channelId": "1743544469094",
//    "channelName": "CBeebies BBC",
//    "groupName": "Kids",
//    "logo": "http://172.19.178.178/tv/img/cbeebiesbbc.png",
//
//    "serverId": "67ec6052b2a40",
//    "streamName": "cbeebiesbbc",
//    "active": true
//  },
//  {
//    "channelId": "1743544977514",
//    "channelName": "CGTN",
//    "groupName": "News",
//    "logo": "http://172.19.178.178/tv/img/cgtn.png",
//
//    "serverId": "67ec626be9b4e",
//    "streamName": "cgtn",
//    "active": true
//  },
//  {
//    "channelId": "1743545138036",
//    "channelName": "CNN",
//    "groupName": "News",
//    "logo": "http://172.19.178.178/tv/img/cnn.png",
//
//    "serverId": "67ec626be9b4e",
//    "streamName": "cnn",
//    "active": true
//  },
//  {
//    "channelId": "1743545282943",
//    "channelName": "Channel 9",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/channel9.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "channel9",
//    "active": true
//  },
//  {
//    "channelId": "1743545848935",
//    "channelName": "Deepto",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/deepto.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "deepto",
//    "active": true
//  },
//  {
//    "channelId": "1743546111567",
//    "channelName": "Desh TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/deshtv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "deshtv",
//    "active": true
//  },
//  {
//    "channelId": "1743546371751",
//    "channelName": "Duronto",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/duronto.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "duronto",
//    "active": true
//  },
//  {
//    "channelId": "1743546888991",
//    "channelName": "Ekushey TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/ekusheytv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "ekusheytv",
//    "active": true
//  },
//  {
//    "channelId": "1743548479753",
//    "channelName": "FOX Action Movies",
//    "groupName": "English",
//    "logo": "http://172.19.178.178/tv/img/foxactionmovies.png",
//
//    "serverId": "67ec6c834ac4f",
//    "streamName": "foxactionmovies",
//    "active": true
//  },
//  {
//    "channelId": "1743548677072",
//    "channelName": "Independent",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/independent.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "independent",
//    "active": true
//  },
//  {
//    "channelId": "1743549175928",
//    "channelName": "Lotus Macau",
//    "groupName": "Foreign",
//    "logo": "http://172.19.178.178/tv/img/lotusmacau.png",
//
//    "serverId": "67ec50dd96550",
//    "streamName": "lotusmacau",
//    "active": true
//  },
//  {
//    "channelId": "1743549742386",
//    "channelName": "NHK World Japan",
//    "groupName": "News",
//    "logo": "http://172.19.178.178/tv/img/nhkworldjapan.png",
//
//    "serverId": "67ec626be9b4e",
//    "streamName": "nhkworldjapan",
//    "active": true
//  },
//  {
//    "channelId": "1743550196812",
//    "channelName": "News 24",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/news24.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "news24",
//    "active": true
//  },
//  {
//    "channelId": "1743550329181",
//    "channelName": "Nexus Television",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/nexustelevision.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "nexustelevision",
//    "active": true
//  },
//  {
//    "channelId": "1743550752114",
//    "channelName": "Saudi Quraan TV",
//    "groupName": "Religious",
//    "logo": "http://172.19.178.178/tv/img/saudiqurantv.png",
//
//    "active": true,
//    "serverId": "67ec78c95d2b8",
//    "streamName": "saudiqurantv"
//  },
//  {
//    "channelId": "1743551064875",
//    "channelName": "RTV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/rtv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "rtv",
//    "active": true
//  },
//  {
//    "channelId": "1743552070126",
//    "channelName": "NTV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/ntv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "ntv",
//    "active": true
//  },
//  {
//    "channelId": "1743553215693",
//    "channelName": "ATN Bangla",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/atnbangla.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "atnbangla",
//    "active": true
//  },
//  {
//    "channelId": "1743553255361",
//    "channelName": "Boishakhi TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/boishakhitv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "boishakhitv",
//    "active": true
//  },
//  {
//    "channelId": "1743553499259",
//    "channelName": "Channel 24",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/channel24.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "channel24",
//    "active": true
//  },
//  {
//    "channelId": "1743553628830",
//    "channelName": "Asian",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/asian.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "asian",
//    "active": true
//  },
//  {
//    "channelId": "1743553876644",
//    "channelName": "SA TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/satv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "satv",
//    "active": true
//  },
//  {
//    "channelId": "1743554022213",
//    "channelName": "DBC",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/dbc.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "dbc",
//    "active": true
//  },
//  {
//    "channelId": "1743554160090",
//    "channelName": "Ekhon",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/ekhon.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "ekhon",
//    "active": true
//  },
//  {
//    "channelId": "1743554442703",
//    "channelName": "71",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/71.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "71",
//    "active": true
//  },
//  {
//    "channelId": "1743554633424",
//    "channelName": "Mohona TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/mohonatv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "mohonatv",
//    "active": true
//  },
//  {
//    "channelId": "1743554809292",
//    "channelName": "Global Television",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/globaltelevision.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "globaltelevision",
//    "active": true
//  },
//  {
//    "channelId": "1743554926099",
//    "channelName": "My TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/mytv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "mytv",
//    "active": true
//  },
//  {
//    "channelId": "1743555141100",
//    "channelName": "Ananda TV",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/anandatv.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "anandatv",
//    "active": true
//  },
//  {
//    "channelId": "1743555343987",
//    "channelName": "A Sports",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/asports.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "asports",
//    "active": true
//  },
//  {
//    "channelId": "1743555756615",
//    "channelName": "PTV Sports",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/ptvsports.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "ptvsports",
//    "active": true
//  },
//  {
//    "channelId": "1743555922161",
//    "channelName": "Fox Sports",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/foxsports.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "foxsports",
//    "active": true
//  },
//  {
//    "channelId": "1743556231511",
//    "channelName": "TNT Sports",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/tntsports.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "tntsports",
//    "active": true
//  },
//  {
//    "channelId": "1743556724229",
//    "channelName": "ESPN",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/espn.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "espn",
//    "active": true
//  },
//  {
//    "channelId": "1743556749975",
//    "channelName": "TSN",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/tsn.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "tsn",
//    "active": true
//  },
//  {
//    "channelId": "1743663429051",
//    "channelName": "ATN News",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/atnnews.png",
//
//    "active": true,
//    "serverId": "67e456c5ae421",
//    "streamName": "atnnews"
//  },
//  {
//    "channelId": "1743663495299",
//    "channelName": "Movie Bangla",
//    "groupName": "Bangladeshi",
//    "logo": "http://172.19.178.178/tv/img/moviebangla.png",
//
//    "serverId": "67e456c5ae421",
//    "streamName": "moviebangla",
//    "active": true
//  },
//  {
//    "channelId": "1744013031560",
//    "channelName": "Eurosport",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/eurosport.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "eurosport",
//    "active": true
//  },
//  {
//    "channelId": "1744662256048",
//    "channelName": "Sony Aath",
//    "groupName": "Indian Bangla",
//    "logo": "http://172.19.178.178/tv/img/sonyaath.png",
//
//    "serverId": "67e87b6509828",
//    "streamName": "sonyaath",
//    "active": true
//  },
//  {
//    "channelId": "1744662418727",
//    "channelName": "Colors Bangla",
//    "groupName": "Indian Bangla",
//    "logo": "http://172.19.178.178/tv/img/colorsbangla.png",
//
//    "serverId": "67e87b6509828",
//    "streamName": "colorsbangla",
//    "active": true
//  },
//  {
//    "channelId": "1744662448484",
//    "channelName": "Jalsha Movies",
//    "groupName": "Indian Bangla",
//    "logo": "http://172.19.178.178/tv/img/jalshamovies.png",
//
//    "serverId": "67e87b6509828",
//    "streamName": "jalshamovies",
//    "active": true
//  },
//  {
//    "channelId": "1744662526474",
//    "channelName": "Zee Bangla Cinema",
//    "groupName": "Indian Bangla",
//    "logo": "http://172.19.178.178/tv/img/zeebanglacinema.png",
//
//    "serverId": "67e87b6509828",
//    "streamName": "zeebanglacinema",
//    "active": true
//  },
//  {
//    "channelId": "1744663941983",
//    "channelName": "Star Plus",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/starplus.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "starplus",
//    "active": true
//  },
//  {
//    "channelId": "1743551774539",
//    "channelName": "Zee  TV",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/zeetv.png",
//
//    "active": true,
//    "serverId": "67ec7c69036c5",
//    "streamName": "zeetv"
//  },
//  {
//    "channelId": "1744664018367",
//    "channelName": "Sony Entertainment Television",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/sonyentertainmenttelevision.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "sonyentertainmenttelevision",
//    "active": true
//  },
//  {
//    "channelId": "1744664050488",
//    "channelName": "Colors",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/colors.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "colors",
//    "active": true
//  },
//  {
//    "channelId": "1744664069401",
//    "channelName": "& TV",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/andtv.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "andtv",
//    "active": true
//  },
//  {
//    "channelId": "1744664115724",
//    "channelName": "Star Gold",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/stargold.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "stargold",
//    "active": true
//  },
//  {
//    "channelId": "1743551812597",
//    "channelName": "Zee Cinema",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/zeecinema.png",
//
//    "active": true,
//    "serverId": "67ec7c69036c5",
//    "streamName": "zeecinema"
//  },
//  {
//    "channelId": "1744664182801",
//    "channelName": "Sony Max",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/sonymax.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "sonymax",
//    "active": true
//  },
//  {
//    "channelId": "1744664205283",
//    "channelName": "& Pictures",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/andpictures.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "andpictures",
//    "active": true
//  },
//  {
//    "channelId": "1744771588117",
//    "channelName": "Hungama",
//    "groupName": "Kids",
//    "logo": "http://172.19.178.178/tv/img/hungama.png",
//
//    "serverId": "67ec6052b2a40",
//    "streamName": "hungama",
//    "active": true
//  },
//  {
//    "channelId": "1744771612218",
//    "channelName": "Nick Jr.",
//    "groupName": "Kids",
//    "logo": "http://172.19.178.178/tv/img/nickjr.png",
//
//    "serverId": "67ec6052b2a40",
//    "streamName": "nickjr",
//    "active": true
//  },
//  {
//    "channelId": "1744771842367",
//    "channelName": "Discovery",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/discovery.png",
//
//    "serverId": "67ec53c1b4472",
//    "streamName": "discovery",
//    "active": true
//  },
//  {
//    "channelId": "1744772014540",
//    "channelName": "Nat Geo Wild",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/natgeowild.png",
//
//    "serverId": "67ec53c1b4472",
//    "streamName": "natgeowild",
//    "active": true
//  },
//  {
//    "channelId": "1744772967849",
//    "channelName": "Star Movies",
//    "groupName": "English",
//    "logo": "http://172.19.178.178/tv/img/starmovies.png",
//
//    "serverId": "67ec6c834ac4f",
//    "streamName": "starmovies",
//    "active": true
//  },
//  {
//    "channelId": "1744773002365",
//    "channelName": "Sony Pix",
//    "groupName": "English",
//    "logo": "http://172.19.178.178/tv/img/sonypix.png",
//
//    "serverId": "67ec6c834ac4f",
//    "streamName": "sonypix",
//    "active": true
//  },
//  {
//    "channelId": "1744773377210",
//    "channelName": "Deutsche Welle",
//    "groupName": "News",
//    "logo": "http://172.19.178.178/tv/img/deutschewelle.png",
//
//    "serverId": "67ec626be9b4e",
//    "streamName": "deutschewelle",
//    "active": true
//  },
//  {
//    "channelId": "1744773856821",
//    "channelName": "B4U Music",
//    "groupName": "Music",
//    "logo": "http://172.19.178.178/tv/img/b4umusic.png",
//
//    "serverId": "67ec78c95d2b8",
//    "streamName": "b4umusic",
//    "active": true
//  },
//  {
//    "channelId": "1744775202401",
//    "channelName": "Ten Cricket",
//    "groupName": "Sports",
//    "logo": "http://172.19.178.178/tv/img/tencricket.png",
//
//    "serverId": "67e56025c2d4c",
//    "streamName": "tencricket",
//    "active": true
//  },
//  {
//    "channelId": "1744775615859",
//    "channelName": "Cartoon Network",
//    "groupName": "Kids",
//    "logo": "http://172.19.178.178/tv/img/cartoonnetwork.png",
//
//    "serverId": "67ec6052b2a40",
//    "streamName": "cartoonnetwork",
//    "active": true
//  },
//  {
//    "channelId": "1744775476597",
//    "channelName": "Cartoon Network HD+",
//    "groupName": "Kids",
//    "logo": "http://172.19.178.178/tv/img/cartoonnetworkhdplus.png",
//
//    "active": true,
//    "serverId": "67ec6052b2a40",
//    "streamName": "cartoonnetworkhdplus"
//  },
//  {
//    "channelId": "1744773886327",
//    "channelName": "9x Jalwa",
//    "groupName": "Music",
//    "logo": "http://172.19.178.178/tv/img/9xjalwa.png",
//
//    "active": true,
//    "serverId": "67ec78c95d2b8",
//    "streamName": "9xjalwa"
//  },
//  {
//    "channelId": "1745117412375",
//    "channelName": "Pogo",
//    "groupName": "Kids",
//    "logo": "http://172.19.178.178/tv/img/pogo.png",
//
//    "serverId": "67ec6052b2a40",
//    "streamName": "pogo",
//    "active": true
//  },
//  {
//    "channelId": "1745117673331",
//    "channelName": "Discovery Kids",
//    "groupName": "Kids",
//    "logo": "http://172.19.178.178/tv/img/discoverykids.png",
//
//    "serverId": "67ec6052b2a40",
//    "streamName": "discoverykids",
//    "active": true
//  },
//  {
//    "channelId": "1745117714452",
//    "channelName": "Sony YAY",
//    "groupName": "Kids",
//    "logo": "http://172.19.178.178/tv/img/sonyyay.png",
//
//    "serverId": "67ec6052b2a40",
//    "streamName": "sonyyay",
//    "active": true
//  },
//  {
//    "channelId": "1745118544636",
//    "channelName": "Hum TV",
//    "groupName": "Foreign",
//    "logo": "http://172.19.178.178/tv/img/humtv.png",
//
//    "serverId": "67ec50dd96550",
//    "streamName": "humtv",
//    "active": true
//  },
//  {
//    "channelId": "1745118586484",
//    "channelName": "Hum Masala",
//    "groupName": "Foreign",
//    "logo": "http://172.19.178.178/tv/img/hummasala.png",
//
//    "serverId": "67ec50dd96550",
//    "streamName": "hummasala",
//    "active": true
//  },
//  {
//    "channelId": "1745118620733",
//    "channelName": "Hum Sitaray",
//    "groupName": "Foreign",
//    "logo": "http://172.19.178.178/tv/img/humsitaray.png",
//
//    "serverId": "67ec50dd96550",
//    "streamName": "humsitaray",
//    "active": true
//  },
//  {
//    "channelId": "1745119246399",
//    "channelName": "Sony SAB",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/sonysab.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "sonysab",
//    "active": true
//  },
//  {
//    "channelId": "1745119941130",
//    "channelName": "B4U Movies",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/b4umovies.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "b4umovies",
//    "active": true
//  },
//  {
//    "channelId": "1745120121718",
//    "channelName": "Sony Max 2",
//    "groupName": "Hindi",
//    "logo": "http://172.19.178.178/tv/img/sonymax2.png",
//
//    "serverId": "67ec7c69036c5",
//    "streamName": "sonymax2",
//    "active": true
//  },
//  {
//    "channelId": "1745144466579",
//    "channelName": "TLC",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/tlc.png",
//
//    "serverId": "67ec53c1b4472",
//    "streamName": "tlc",
//    "active": true
//  },
//  {
//    "channelId": "1745144496905",
//    "channelName": "Animal Planet",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/animalplanet.png",
//
//    "serverId": "67ec53c1b4472",
//    "streamName": "animalplanet",
//    "active": true
//  },
//  {
//    "channelId": "1745144517000",
//    "channelName": "Sony BBC Earth",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/sonybbcearth.png",
//
//    "serverId": "67ec53c1b4472",
//    "streamName": "sonybbcearth",
//    "active": true
//  },
//  {
//    "channelId": "1745144550567",
//    "channelName": "Discovery Science",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/discoveryscience.png",
//
//    "serverId": "67ec53c1b4472",
//    "streamName": "discoveryscience",
//    "active": true
//  },
//  {
//    "channelId": "1745144571388",
//    "channelName": "Discovery Turbo",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/discoveryturbo.png",
//
//    "serverId": "67ec53c1b4472",
//    "streamName": "discoveryturbo",
//    "active": true
//  },
//  {
//    "channelId": "1745144594406",
//    "channelName": "Investigation Discovery",
//    "groupName": "Infotainment",
//    "logo": "http://172.19.178.178/tv/img/investigationdiscovery.png",
//
//    "active": true,
//    "serverId": "67ec53c1b4472",
//    "streamName": "investigationdiscovery"
//  }
//]"""
//class Channels : ArrayList<ChannelsItem>()
//
//data class ChannelsItem(
//    val active: Boolean,
//    val channelId: String,
//    val channelName: String,
//    val groupName: String,
//    val logo: String,
//    val serverId: String,
//    val streamName: String
//)