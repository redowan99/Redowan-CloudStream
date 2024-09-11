package com.redowan


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
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
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element


class FullMatchProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://fullmatch.info/"
    override var name = "FullMatch"
    override var lang = "en"
    override val hasMainPage = true

    override val hasDownloadSupport = true
    private val ajaxUrl = "https://fullmatch.info/wp-admin/admin-ajax.php";

    override val mainPage = mainPageOf(
        "/" to "Latest Full Match",
        "/4k-full-match" to "4K Full Match",
        "/africa-cup-of-nations-2025" to "Africa Cup of Nations 2025",
        "/belgium-jupiler-league" to "Belgium Jupiler League",
        "/dfb-pokal" to "DFB-Pokal",
        "/england-full-match-full-replays" to "England",
        "/epl-24-25" to "EPL 24/25",
        "/eredivisie-24-25" to "Eredivisie 24/25",
        "/fifa-world-cup-2026" to "FIFA World Cup 2026",
        "/france" to "France",
        "/german-2-bundesliga" to "German 2. Bundesliga",
        "/germany" to "Germany",
        "/international-friendly" to "International Friendly",
        "/italy" to "Italy",
        "/la-liga" to "La Liga",
        "/la-liga-2" to "La Liga 2",
        "/leagues-cup-2024" to "Leagues Cup 2024",
        "/mini-match" to "Mini Match",
        "/olympic-2024" to "Olympic 2024",
        "/portugal" to "Portugal",
        "/saudi-pro-league" to "Saudi Pro League",
        "/scottish-championship" to "Scottish Championship",
        "/scottish-premiership" to "Scottish Premiership",
        "/spain" to "Spain",
        "/turkish-super-lig" to "Turkish Super Lig",
        "/uefa" to "UEFA",
        "/uefa-champions-league" to "UEFA Champions League",
        "/uefa-conference-league" to "UEFA Conference League",
        "/uefa-euro-2024" to "UEFA Euro 2024",
        "/uefa-europa-league-24-25" to "UEFA Europa League 24/25",
        "/uefa-nations-league" to "UEFA Nations League",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        if(request.data == "/"){
            val body = FormBody.Builder()
                .addEncoded("action", "tie_blocks_load_more")
                .addEncoded("block[order]", "latest")
                .addEncoded("block[asc_or_desc]", "DESC")
                .addEncoded("block[number]", "18")
                .addEncoded("block[pagi]", "load-more")
                .addEncoded("block[dark]", "true")
                .addEncoded("block[post_meta]", "true")
                .addEncoded("block[media_overlay]", "true")
                .addEncoded("block[read_more]", "true")
                .addEncoded("block[read_more_text]", "Go+To+Match+Page")
                .addEncoded("block[posts_category]", "true")
                .addEncoded("block[breaking_effect]", "reveal")
                .addEncoded("block[sub_style]", "big")
                .addEncoded("block[is_full]", "true")
                .addEncoded("block[style]", "default")
                .addEncoded("block[title_length]", "default")
                .addEncoded("block[excerpt_length]", "default")
                .addEncoded("page", page.toString())
                .addEncoded("width", "full")
                .build()
            val resposne = app.post(ajaxUrl, requestBody = body)

            val str = resposne.text.replace("\"{","{").replace("}\"","}")
            val un = removeEscapeCharacters(str);
            val de = decodeUnicodeEscapes(un)
            val jsonParsed = parseJson<PostResponse>(un)
            val document = Jsoup.parse(jsonParsed.code);
            val latestPosts = document.select(".post-item").mapNotNull { toResult(it) }
            return newHomePageResponse(HomePageList(request.name,latestPosts,isHorizontalImages = true),true)
        }
        val doc = app.get("$mainUrl${request.data}/page/$page").document
        val isList = doc.select(".featured-area")
        if(isList.isNotEmpty())
        {

            val listElement = doc.select(".featured-area").mapNotNull { toListResult(it) }
            return  newHomePageResponse(HomePageList(request.name,listElement,isHorizontalImages = true),true)
        }
        else
        {
            val listElement = doc.select(".post-item").mapNotNull { toResult(it) }
            return  newHomePageResponse(HomePageList(request.name,listElement,isHorizontalImages = true),true)
        }
    }

    fun removeEscapeCharacters(jsonString: String): String {
        // Replace escaped quotes and newlines with their non-escaped equivalents
        val cleanedString = jsonString
            .replace("\\\"", "\"")   // Replace escaped tabs with actual tabs
            .replace("\\\\\\\"", "\\\"")  // Replace escaped double quotes with unescaped double quotes
            .replace("\\\\n", "\\n")   // Replace escaped newlines with actual newlines
            .replace("\\\\r", "\\r")   // Replace escaped carriage returns with actual carriage returns
            .replace("\\\\t", "\\t")   // Replace escaped tabs with actual tabs
            .replace("\\\\\\/", "/")   // Replace escaped tabs with actual tabs
            .replace("\\\\u", "\\u")   // Replace escaped tabs with actual tabs
            .replace("\\\\\"", "\\\"")   // Replace escaped tabs with actual tabs

        return cleanedString
    }

   private fun decodeUnicodeEscapes(input: String): String {
        // Regular expression to match \u followed by 4 hexadecimal digits
        val unicodePattern = "\\\\u([0-9A-Fa-f]{4})".toRegex()

        // Replace each \uXXXX with the corresponding Unicode character
        return unicodePattern.replace(input) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            val charCode = hexCode.toInt(16)
            charCode.toChar().toString()
        }
    }


    private fun toResult (post: Element) : SearchResponse
    {
        val thumbElement = post.select(".post-thumb")
        if(thumbElement.isNotEmpty())
        {
            val url = post.select(".post-thumb").attr("href")
            val imageUrl = post.select(".wp-post-image").attr("src")
            val title = post.select(".post-thumb").attr("aria-label")
            return newMovieSearchResponse(title,url,TvType.Movie){
                this.posterUrl = imageUrl
            }
        }
        else
        {
            val url = post.select(".post-title a").attr("href")
            Log.d("salman731 no thumb url",url)
            val title = post.select(".post-title a").text()
            Log.d("salman731 no thumb title",title)
            return newMovieSearchResponse(title,url,TvType.Movie){
            }
        }

    }

    private fun toListResult(post: Element) : SearchResponse
    {
        val url = post.select("a").attr("href")
        val imageUrl = post.select("a img").attr("src")
        val title = post.select("a").attr("aria-label")
        return newMovieSearchResponse(title,url,TvType.Movie)
        {
            this.posterUrl = imageUrl;
        }
    }

    /*private fun toResult(post: Element): SearchResponse {
        val url = post.select("a").attr("href")
        val imageId = "\\d+".toRegex().find(url)?.value
        val title = post.text().replaceAfter(")", "")
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = "$mainUrl/cover/$imageId.png"
            this.quality = getSearchQuality(post.select("font[color=green]").text())
        }
    }
*/
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select(".entry-header h1").text()
        val imageUrl = doc.select(".single-featured-image img").attr("src")
        val iframesElement = doc.select(".tabcontent iframe")
        if(iframesElement.size > 1)
        {
            val episodesData = mutableListOf<Episode>()
            var episodeNo = 1
            iframesElement.forEach{item ->
                val videoUrl = if (item.attr("src").contains("ok.ru")) {
                    val link = item.attr("src")
                    "https:"+link.replace("ok.ru/videoembed","ok.ru/video")

                } else {
                    ""
                }
                episodesData.add(Episode(videoUrl,"Episode$episodeNo",1,episodeNo))
                episodeNo++
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.posterUrl = imageUrl
            }

        }
        else
        {

            val videoUrl = if (iframesElement.attr("src").contains("ok.ru")) {
                val link = iframesElement.attr("src")
                "https:"+link.replace("ok.ru/videoembed","ok.ru/video")

            } else {
                ""
            }
            return newMovieLoadResponse(title, url, TvType.Movie, videoUrl) {
                this.posterUrl = imageUrl
            }
        }



    }


    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        val searchResponse = doc.select(".post-item")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data,subtitleCallback,callback)
        return true
    }

    data class PostResponse (
        @JsonProperty("hide_next" ) var hinde_next : String?,
        @JsonProperty("hide_prev"    ) var hide_prev    : String?,
        @JsonProperty("code"  ) var code  : String?)
}