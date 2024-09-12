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
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class FullMatchProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://fullmatch.info"
    override var name = "FullMatch"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val mainPage = mainPageOf(
        "/" to "Latest Full Match",
        "/club-friendly" to "Club Friendly",
        "/england-full-match-full-replays" to "England",
        "/fifa-world-cup-2026" to "FIFA World Cup 2026",
        "/germany" to "Germany",
        "/france" to "France",
        "/italy" to "Italy",
        "/spain" to "Spain",
        "/olympic-2024" to "Olympic 2024"

        //"/mini-match" to "Mini Match",
        //"/uefa-euro-2024" to "UEFA Euro 2024",
        //"/uefa-nations-league" to "UEFA Nations League"
        //"/4k-full-match" to "4K Full Match",
        //"/africa-cup-of-nations-2025" to "Africa Cup of Nations 2025",
        //"/belgium-jupiler-league" to "Belgium Jupiler League",
        //"/dfb-pokal" to "DFB-Pokal",
        //"/epl-24-25" to "EPL 24/25",
        //"/eredivisie-24-25" to "Eredivisie 24/25",
        //"/german-2-bundesliga" to "German 2. Bundesliga",
        //"/international-friendly" to "International Friendly",
        //"/la-liga" to "La Liga",
        //"/la-liga-2" to "La Liga 2",
        //"/leagues-cup-2024" to "Leagues Cup 2024",
        //"/portugal" to "Portugal",
        //"/saudi-pro-league" to "Saudi Pro League",
        //"/scottish-championship" to "Scottish Championship",
        //"/scottish-premiership" to "Scottish Premiership",
        //"/turkish-super-lig" to "Turkish Super Lig",
        //"/uefa" to "UEFA",
        //"/uefa-champions-league" to "UEFA Champions League",
        //"/uefa-conference-league" to "UEFA Conference League",
        //"/uefa-europa-league-24-25" to "UEFA Europa League 24/25",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc: Element
        if (request.data == "/") {
            val body = FormBody.Builder()
                .addEncoded("action", "tie_blocks_load_more")
                .addEncoded("block[order]", "latest")
                .addEncoded("block[asc_or_desc]", "DESC")
                .addEncoded("block[number]", "18")
                .addEncoded("block[pagi]", "load-more")
                .addEncoded("block[style]", "default")
                .addEncoded("page", page.toString())
                .build()
            val ajaxUrl = "https://fullmatch.info/wp-admin/admin-ajax.php"
            val response = app.post(ajaxUrl, requestBody = body)
            val text = response.text
                .replaceBefore("<li class", "")
                .replaceAfterLast("/li>", "")
                .replace("\\", "")
            doc = Jsoup.parse(text)
        } else doc = app.get("$mainUrl${request.data}/page/$page").document

        val home = doc.select(".post-item").mapNotNull { toResult(it) }
        return newHomePageResponse(HomePageList(request.name, home, isHorizontalImages = true))
    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select(".post-title a").attr("href")
        val title = post.select(".post-title a").text()
        //.Avif type image format isn't in Android. Might Try something in the future
        //val imageUrl = post.select(".wp-post-image").attr("src")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            //this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val searchResponse = doc.select(".post-item")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select(".entry-header h1").text()
        //.Avif type image format isn't in Android. Might Try something in the future
        //val imageUrl = doc.select(".single-featured-image img").attr("src")
        val videoUrls = doc.select(".tabcontent iframe")
        if (videoUrls.size > 1) {
            val episodesData = mutableListOf<Episode>()
            var episodeNo = 1
            videoUrls.forEach { item ->
                val videoUrl = item.attr("src")
                    .replace("//", "https://")
                episodesData.add(Episode(videoUrl, season = 1, episode = episodeNo))
                episodeNo++
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                //this.posterUrl = imageUrl
            }
        } else {
            val videoUrl = doc.select(".tabcontent iframe").attr("src")
                .replace("//", "https://")
            return newMovieLoadResponse(title, url, TvType.Movie, videoUrl) {
                //this.posterUrl = imageUrl
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data, subtitleCallback, callback)
        return true
    }
}