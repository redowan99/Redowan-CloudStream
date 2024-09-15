package com.redowan

import com.lagradost.api.Log
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
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class k9kMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://9kmovies.com"
    override var name = "9kMovies"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie,TvType.TvSeries,TvType.NSFW,)
    override val mainPage = mainPageOf(
       "" to "Latest Movies",
        "/category/18-movies/" to "18+ Movies",
        "/category/4k-ultra-hd/" to "4K Ultra HD",
        "/category/bengali/" to "Bengali",
        "/category/dual-audio/" to "Dual Audio",
        "/category/gujarati/" to "Gujarati",
        "/category/hindi-dubbed/" to "Hindi Dubbed",
        "/category/hollywood/" to "Hollywood",
        "/category/kannada/" to "Kannada",
        "/category/malayalam/" to "Malayalam",
        "/category/marathi/" to "Marathi",
        "/category/punjabi/" to "Punjabi",
       "/category/tamil/" to "Tamil",
        "/category/telugu/" to "Telugu",
        "/category/tv-shows/" to "Tv Shows",
        "/category/uncategorized/" to "Uncategorized",
        "/category/web-series/" to "Web Series",

    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

            // This is necessary for load more posts on homepage
            val doc = if(request.data == "" && page == 1) {
                app.get("$mainUrl").document
            }
            else if (request.data == "" && page > 1)
            {
                app.get("$mainUrl/page/$page").document
            }
            else
            {
                app.get("$mainUrl${request.data}page/$page").document
            }
            //Log.d("salman731 element size",doc.select(".thumb.col-md-2.col-sm-4.col-xs-6").size.toString())
            val home = doc.select(".thumb.col-md-2.col-sm-4.col-xs-6").mapNotNull { toResult(it) }
            //Log.d("salman731 total size",home.toString().length.toString())
            return newHomePageResponse(HomePageList(request.name, home,isHorizontalImages = false),hasNext = true)

    }

    private fun toResult(post: Element): SearchResponse {
        val url = post.select("figure figcaption a").attr("href")
       // Log.d("salman731 url",url)
        val title = post.select("figure figcaption a p").text()
        //Log.d("salman731 title",title)
        val imageUrl = post.select("figure img").attr("src")
        //Log.d("salman731 imageUrl",imageUrl)
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = imageUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search/$query").document
        val searchResponse = doc.select(".thumb.col-md-2.col-sm-4.col-xs-6")
        return searchResponse.mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select(".page-body h2").text()
        val imageUrl = doc.select(".page-body img").attr("src")
        val info = doc.select(".page-body p:nth-of-type(1)").text()
        val story = ("(?<=Storyline,).*|(?<=Story : ).*|(?<=Storyline : ).*|(?<=Description : ).*|(?<=Description,).*(?<=Story,).*").toRegex().find(info)?.value
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = imageUrl
            if(story != null) {
                this.plot = story.trim()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document
        val buttonElements = doc.select(".buttn.red")
        Log.d("salman731 buttonElements",data + buttonElements.size.toString())
        buttonElements.forEach { item->
            val shortLinkUrl = item.attr("href")
            val sDoc = app.post(shortLinkUrl).document
            val links = sDoc.select(".col-sm-8.col-sm-offset-2.well.view-well a")
            links.forEach { item->
               val link = item.attr("href")
                Log.d("salman731 link",link)
                loadExtractor(link,subtitleCallback,callback)
            }
            Log.d("salman731 links",links.size.toString())


        }
        return true
    }
}