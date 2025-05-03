package com.redowan


import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class FzTvSeriesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://fztvseries.live"
    override var name = "FzTvSeries"
    override val supportedTypes = setOf(
        TvType.TvSeries, TvType.Anime, TvType.Cartoon
    )

    // enable this when your provider has a main page
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false


    override val mainPage = mainPageOf(
        "popular.php?&pg=" to "Popular Shows",
        "imdbtop250.php?&pg=" to "IMDB Top 250",
        "freshseries.php?&pg=" to "Latest Series",
        "miniseries.php?&pg=" to "Top Rated Miniseries",
        "cartoon.php?&pg=" to "Cartoon/Anime"
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/${request.data}$page").document
        val homeResponse =
            doc.select("div.mainbox3 > table:nth-child(1) > tbody:nth-child(1) > tr:nth-child(1)")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = "$mainUrl/" + post.selectXpath("td[1]/a").attr("href")
        val title = post.selectXpath("td[2]/span/a/small/b").text()
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = "$mainUrl/" + post.selectXpath("td[1]/div/a/img").attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc =
            app.get("$mainUrl/search.php?search=${query}&beginsearch=Search&vsearch=&by=series").document
        val searchResponse =
            doc.select("div.mainbox3 > table:nth-child(1) > tbody:nth-child(1) > tr:nth-child(1)")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title =
            doc.selectXpath("/html/body/div[9]/div[1]/table/tbody/tr/td[2]/span/a/small/b").text()
        var season = 1
        val episodesData = mutableListOf<Episode>()
        doc.select("div.mainbox2").forEach { item ->
            if ("Season $season" == item.text()) {
                var episodeNum = 0
                val newUrl = "$mainUrl/" + item.select("a").attr("href")
                val newDoc = app.get(newUrl).document
                newDoc.select(
                    "div.mainbox > table:nth-child(1) > tbody:nth-child(1) > tr:nth-child(1) >" + " td:nth-child(2) > span:nth-child(1)"
                ).forEach { episode ->
                    episodeNum++
                    episodesData.add(
                        newEpisode(
                            "$mainUrl/" + episode.select("span:nth-child(1) > a:nth-child(2)")
                                .attr("href")
                        ) {
                            this.name =
                                episode.select("span:nth-child(1) > small:nth-child(1) > b:nth-child(1)")
                                    .text().replace("(.*) - ".toRegex(), "")
                            this.season = season
                            this.episode = episodeNum
                            this.description =
                                episode.select("span:nth-child(1) > small:nth-child(6)").text()
                                    .replace(" Stars:(.*)".toRegex(), "")
                        })
                }
                season++
            }
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
            this.posterUrl =
                "$mainUrl/" + doc.selectXpath("/html/body/div[9]/div[1]/table/tbody/tr/td[1]/div/a/img")
                    .attr("src")
            this.plot =
                doc.selectXpath("/html/body/div[9]/div[1]/table/tbody/tr/td[2]/span/small[2]")
                    .text()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data)
        var doc = html.document
        val cookie = html.cookies
        val sudoDlUrl = "$mainUrl/" + doc.select("#dlink2").attr("href")
        doc = app.get(sudoDlUrl, cookies = cookie).document
        var server = 0
        doc.select(".downloadlinks2").forEach { newItem ->
            server++
            callback.invoke(
                newExtractorLink(
                    this.name,
                    "Server$server",
                    newItem.select("p:nth-child(2) > input:nth-child(1)").attr("value")
                )
            )
        }
        return true
    }
}