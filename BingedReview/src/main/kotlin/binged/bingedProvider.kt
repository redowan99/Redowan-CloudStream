package binged

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class BingedProvider : MainAPI() {
    override var mainUrl = "https://www.binged.com"
    override var name = "Binged(No Streaming)"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = true

    private suspend fun getData(
        titled: String, i: Int, fltr: String = ""
    ): List<MovieSearchResponse> {
        val j = i - 10
        val response = app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            data = mapOf(
                "filters[search]" to "",
                "filters[recommend]" to "false",
                "filters[date-from]" to "",
                "filters[date-to]" to "",
                "filters[mode]" to "streaming-soon",
                "filters[page]" to "0",
                "action" to "mi_events_load_data",
                "mode" to titled,
                "start" to "$j",
                "length" to "$i",
                "customcatalog" to "0"
            ), headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
                "Referer" to mainUrl
            )
        ).text
        val json = tryParseJson<Map<String, Any>>(response)
        var dataList = json?.get("data") as? List<Map<String, Any>>
        if (fltr.isNotEmpty()) {
            dataList = dataList?.filter { entry ->
                val platforms = entry["platform"] as? List<String>
                platforms?.firstOrNull()?.contains(fltr) == true
            }
        }
        val movies = dataList?.map { entry ->
            newMovieSearchResponse(
                name = entry["title"].toString(),
                url = entry["link"].toString(),
                type = TvType.Movie
            ) {
                this.posterUrl = entry["big-image"].toString()
            }
        } ?: emptyList()
        return movies
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val stsoon = getData("streaming-soon", page * 10)
        val stnow = getData("streaming-now", page * 10)
        val netflix = getData("streaming-now", page * 10, "netflix.webp")
        val amazon = getData("streaming-now", page * 10, "amazon")/*val liv = getData("streaming-now",page*10,"sony")
        val hotstar = getData("streaming-now",page*10,"hotstar")
        val  zee = getData("streaming-now",page*10,"zee")
        val jio = getData("streaming-now",page*10,"jio")*/
        return newHomePageResponse(
            listOf(
                HomePageList("Streaming Soon", stsoon, false),
                HomePageList("Streaming Now", stnow, false),
                HomePageList("Netflix", netflix, false),
                HomePageList("Prime", amazon, false),
                /*HomePageList("Sony liv", liv, false),
                HomePageList("Hotstar", hotstar, false),
                HomePageList("Zee5", zee, false),
                HomePageList("JioCinema", jio, false)*/
            ), true

        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/wp-admin/admin-ajax.php", data = mapOf(
                "action" to "mi_events_load_data",
                "test-search" to "1",
                "start" to "0",
                "length" to "20",
                "search[value]" to query,
                "customcatalog" to "0",
                "mode" to "all",
                "filters[search]" to query
            ), headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
                "Referer" to mainUrl
            )
        ).text
        val json = tryParseJson<Map<String, Any>>(response)
        val dataList = json?.get("data") as? List<Map<String, Any>>
        val movies = dataList?.map { entry ->
            newMovieSearchResponse(
                name = entry["title"].toString(),
                url = entry["link"].toString(),
                type = TvType.Movie
            ) {
                this.posterUrl = entry["big-image"].toString()
            }
        } ?: emptyList()
        return movies
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, cacheTime = 60).document
        val title = doc.select("h1").first()!!.text()
        val dt = doc.select("div.single-mevents-meta").text()
        val dtsplit = dt.split("|")
        val imageUrl = doc.select("meta")[15].attr("content").toString()
        val trailer = doc.select("div.bng-section__content")[1].select("a").attr("href").toString()
        val tags = listOf(
            doc.select("span.single-mevents-platforms-row-date").text().toString(),
            doc.select("span.rating-span").first().text().toString(),
            doc.select("img.single-mevents-platforms-row-image").attr("alt").toString(),
            doc.select("span.audiostring").text().toString(),
            if (dtsplit.size > 1) dtsplit[1] else "",
            if (dtsplit.size > 2) dtsplit[2] else "",
            if (dtsplit.size > 3) dtsplit[3] else ""
        )
        val plot = doc.select("p").first()!!.text()
        val year = dtsplit[0].toIntOrNull()
        return newMovieLoadResponse(title, url, TvType.Movie, null) {
            this.posterUrl = imageUrl
            this.year = year
            this.plot = plot
            this.tags = tags
            addTrailer(trailer)
        }
    }
}
