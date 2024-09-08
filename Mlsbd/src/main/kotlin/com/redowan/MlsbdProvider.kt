package com.redowan


import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element



class MlsbdProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://mlsbd.shop"
    override var name = "Mlsbd"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.NSFW,
        TvType.AsianDrama,
        TvType.AnimeMovie,
    )

    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "" to "Latest Movies",
        "/category/bangla-dubbed/page/" to "Bangla Dubbed",
        "/category/dual-audio-movies/page/" to "Multi Audio Movies",
        "/category/tv-series/page/" to "TV Series",
        "/category/foreign-language-film/page/" to "Foreign Language Film",
        "/category/bollywood-movies/page/" to "Bollywood Movies",
        "/category/bangla-movies/page/" to "Bengali Movies",
        "/category/hollywood-movies/page/" to "Hollywood Movies",
        "/category/natok-teleflim/page/" to "Natok & Teleflim",
        "/category/unrated/page/" to "UnRated"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if(request.data == "") mainUrl
        else "$mainUrl${request.data}$page/"
        //val doc = app.get(url, allowRedirects = true).document
        val doc = app.get(url, cacheTime = 60, allowRedirects = true, timeout = 10, headers =  mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")).document
        val homeResponse = doc.select("div.single-post")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(HomePageList(request.name,home,isHorizontalImages = false), true)
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.select(".post-title").text()
        val url = post.select(".thumb > a").attr("href")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.select(".thumb>a>picture>img:nth-child(3)")
                .attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val searchResponse = doc.select("div.single-post")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select(".name").text()
        val year = "(?<=\\()\\d{4}(?=\\))".toRegex().find(title)?.value?.toIntOrNull()
        val image = doc.select("img.aligncenter").attr("src")
        doc.select("br").append("\\n")
        val plot = doc.select(".single-post-title").text() + "\n" +
                doc.select(".misc").text() + "\n" +
                doc.select(".details").text().replace("\\n ", "\n") + "\n" +
                doc.select(".storyline").text() + "\n" +
                doc.select(".production").text().replace("\\n ", "\n") + "\n" +
                doc.select(".media").text().replace("\\n ", "\n")

        val episodeDivs = doc.select("div.post-section-title.download").reversed()
        var link = ""
        when (episodeDivs.size) {
            1 -> {
                episodeDivs[0].nextElementSibling()?.nextElementSibling()
                    ?.select("a.Dbtn.hd, a.Dbtn.sd, a.Dbtn.hevc")
                    ?.forEach {
                        link += it.attr("href") + " ; "
                    }
                return newMovieLoadResponse(title, url, TvType.Movie, link) {
                    this.posterUrl = image
                    this.year = year
                    this.plot = plot
                }
            }

            0 -> return newMovieLoadResponse(title, url, TvType.Movie, "") {
                this.posterUrl = image
                this.year = year
                this.plot = plot
            }

            else -> {
                val episodesData = mutableListOf<Episode>()
                for (episodeDiv in episodeDivs) {
                    var episodeUrl = ""
                    var episodeNum = 0


                    var downloadLink = episodeDiv.nextElementSibling()?.nextElementSibling()

                    //480p
                    episodeUrl += downloadLink?.selectFirst("a")?.attr("href") + " ; "

                    //720p
                    downloadLink = downloadLink?.nextElementSibling()
                    episodeUrl += downloadLink?.selectFirst("a")?.attr("href") + " ; "

                    //1080p
                    downloadLink = downloadLink?.nextElementSibling()
                    episodeUrl += downloadLink?.selectFirst("a")?.attr("href")

                    episodeNum++
                    episodesData.add(
                        Episode(
                            episodeUrl,
                            "Episode $episodeNum",
                            null,
                            episodeNum
                        )
                    )
                }
                return newTvSeriesLoadResponse(title, url, TvType.Movie, episodesData) {
                    this.posterUrl = image
                    this.year = year
                    this.plot = plot
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.split(" ; ").forEach{ link ->
            if (link.contains("savelinks")) {
                val doc = app.get(link).document
                doc.select("div.col-sm-8:nth-child(4) > a").forEach{
                    if (it.attr("href").contains("gdflix")){
                        gdflix(it.attr("href"),callback)
                    }
                }
            }
        }
        return true
    }

    private suspend fun gdflix(
        data: String,
        callback: (ExtractorLink) -> Unit){
        val doc = app.get(data).document
        val qualities = getVideoQuality(doc.select("li.list-group-item:nth-child(1)").text())
        //Instant DL
        val instantDlLink = doc.select("a.btn.btn-danger").attr("href")
        val instantDL = app.get(instantDlLink).url.replace("https://instantdl.pages.dev/?url=","")
        callback.invoke(
            ExtractorLink(
                "Instant DL",
                "Instant DL",
                url = instantDL,
                data,
                quality = qualities,
                isM3u8 = false,
                isDash = false
            )
        )
    }

    private fun getVideoQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}