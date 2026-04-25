@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")
package com.lagradost.cloudstream3

/**
 * Minimal cloudstream3 API stubs needed for `.cs3` plugins to load and execute.
 *
 * This is NOT the full cloudstream3 surface — only the parts that simple plugins
 * (movies/series scrapers) reference. Plugins that depend on advanced features
 * (extractors registry, sflix-style m3u8 schedulers, account sync, etc.) will
 * still throw `NoSuchMethodError` at runtime, and that is expected for now.
 *
 * Source of truth for shapes: github.com/recloudstream/cloudstream
 *  - app/src/main/java/com/lagradost/cloudstream3/MainAPI.kt
 *  - app/src/main/java/com/lagradost/cloudstream3/MvvmExtensions.kt
 */

// ────────────────────────── enums + dataclasses ──────────────────────────

enum class TvType {
    Movie, AnimeMovie, TvSeries, Anime, OVA, Cartoon, Documentary, AsianDrama,
    Live, Torrent, NSFW, Music, AudioBook, JAV, Hentai, CartoonHentai,
    Others
}

enum class DubStatus { Subbed, Dubbed, None }
enum class ShowStatus { Completed, Ongoing, OnHiatus, Cancelled }
enum class SearchQuality { HD, FHD, BluRay, UHD, FourK, SD, CamRip, Cam, HDR, DVD, WebRip, HDCam }

// ────────────────────────── search responses ──────────────────────────

open class SearchResponse(
    open var name: String = "",
    open var url: String = "",
    open var apiName: String = "",
    open var type: TvType? = null,
    open var posterUrl: String? = null,
    open var posterHeaders: Map<String, String>? = null,
    open var id: Int? = null,
    open var quality: SearchQuality? = null,
)

open class MovieSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Movie,
    override var posterUrl: String? = null,
    var year: Int? = null,
    override var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var quality: SearchQuality? = null,
) : SearchResponse(name, url, apiName, type, posterUrl, posterHeaders, id, quality)

open class TvSeriesSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.TvSeries,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var episodes: Int? = null,
    override var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var quality: SearchQuality? = null,
) : SearchResponse(name, url, apiName, type, posterUrl, posterHeaders, id, quality)

open class AnimeSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Anime,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var dubStatus: EnumSet? = null,
    var dubEpisodes: Int? = null,
    var subEpisodes: Int? = null,
    var otherName: String? = null,
    override var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var quality: SearchQuality? = null,
) : SearchResponse(name, url, apiName, type, posterUrl, posterHeaders, id, quality)

class EnumSet : HashSet<DubStatus>()

open class LiveSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Live,
    override var posterUrl: String? = null,
    var lang: String? = null,
    override var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var quality: SearchQuality? = null,
) : SearchResponse(name, url, apiName, type, posterUrl, posterHeaders, id, quality)

open class TorrentSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Torrent,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var quality: SearchQuality? = null,
) : SearchResponse(name, url, apiName, type, posterUrl, posterHeaders, id, quality)

// ────────────────────────── load responses ──────────────────────────

open class LoadResponse(
    open var name: String,
    open var url: String,
    open var apiName: String,
    open var type: TvType,
    open var posterUrl: String? = null,
    open var year: Int? = null,
    open var plot: String? = null,
    open var rating: Int? = null,
    open var tags: List<String>? = null,
    open var duration: Int? = null,
    open var trailers: MutableList<TrailerData> = mutableListOf(),
    open var recommendations: List<SearchResponse>? = null,
    open var actors: List<ActorData>? = null,
    open var comingSoon: Boolean = false,
    open var posterHeaders: Map<String, String>? = null,
    open var backgroundPosterUrl: String? = null,
    open var contentRating: String? = null,
)

class TrailerData(
    val extractorUrl: String,
    val referer: String? = null,
    val raw: Boolean = false,
)

class ActorData(
    val actor: Actor,
    val role: ActorRole? = null,
    val roleString: String? = null,
    val voiceActor: Actor? = null,
)
class Actor(val name: String, val image: String? = null)
enum class ActorRole { Main, Supporting, Background }

class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var dataUrl: String,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var contentRating: String? = null,
) : LoadResponse(name, url, apiName, type, posterUrl, year, plot, rating, tags, duration,
    trailers, recommendations, actors, comingSoon, posterHeaders, backgroundPosterUrl, contentRating)

data class Episode(
    val data: String,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val posterUrl: String? = null,
    val rating: Int? = null,
    val description: String? = null,
    val date: Long? = null,
)

class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var episodes: List<Episode>,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    var showStatus: ShowStatus? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var contentRating: String? = null,
) : LoadResponse(name, url, apiName, type, posterUrl, year, plot, rating, tags, duration,
    trailers, recommendations, actors, comingSoon, posterHeaders, backgroundPosterUrl, contentRating)

// ────────────────────────── home page ──────────────────────────

class HomePageList(
    val name: String,
    val list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false,
)

class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false,
)

class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
)

fun mainPage(data: String, name: String, horizontalImages: Boolean = false) =
    MainPageRequest(name = name, data = data, horizontalImages = horizontalImages)

fun mainPageOf(vararg pairs: Pair<String, String>) =
    pairs.map { (data, name) -> MainPageRequest(name, data, false) }.toTypedArray()

fun newHomePageResponse(name: String, list: List<SearchResponse>): HomePageResponse =
    HomePageResponse(listOf(HomePageList(name, list)))

fun newHomePageResponse(items: List<HomePageList>): HomePageResponse = HomePageResponse(items)

fun newHomePageResponse(request: MainPageRequest, list: List<SearchResponse>) =
    HomePageResponse(listOf(HomePageList(request.name, list, request.horizontalImages)))

// ────────────────────────── extractor links ──────────────────────────

enum class Qualities(val value: Int) {
    Unknown(0), P144(144), P240(240), P360(360), P480(480),
    P720(720), P1080(1080), P1440(1440), P2160(2160);
    companion object { fun getStringByInt(q: Int?): String = (q ?: 0).toString() + "p" }
}

open class ExtractorLink(
    var source: String,
    var name: String,
    var url: String,
    var referer: String,
    var quality: Int,
    var isM3u8: Boolean = false,
    var headers: Map<String, String> = emptyMap(),
    var extractorData: String? = null,
)

class SubtitleFile(val lang: String, val url: String)

// ────────────────────────── MainAPI base class ──────────────────────────

abstract class MainAPI {
    open var name: String = "Unnamed Provider"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open var hasMainPage: Boolean = false
    open var hasQuickSearch: Boolean = false
    open var hasChromecastSupport: Boolean = true
    open var hasDownloadSupport: Boolean = true
    open var supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open var mainPage: List<MainPageRequest> = emptyList()

    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null

    open suspend fun search(query: String): List<SearchResponse>? = null

    open suspend fun quickSearch(query: String): List<SearchResponse>? = null

    open suspend fun load(url: String): LoadResponse? = null

    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean = false
}

// ────────────────────────── result helpers used everywhere ──────────────────────────

fun fixUrl(url: String, mainUrl: String): String = when {
    url.isBlank() -> ""
    url.startsWith("http") -> url
    url.startsWith("//") -> "https:$url"
    url.startsWith("/") -> mainUrl.trimEnd('/') + url
    else -> "$mainUrl/$url"
}
fun fixUrlNull(url: String?, mainUrl: String): String? = url?.let { fixUrl(it, mainUrl) }
fun String.fixUrl(mainUrl: String): String = fixUrl(this, mainUrl)

// `safeApiCall { ... }` wrapper — common in plugins.
suspend fun <T> safeApiCall(apiCall: suspend () -> T): T? = try { apiCall() } catch (_: Exception) { null }

// Quality builders.
fun getQualityFromName(name: String?): Int = when (name?.lowercase()) {
    null -> 0
    "144p" -> 144; "240p" -> 240; "360p" -> 360; "480p" -> 480
    "720p" -> 720; "1080p" -> 1080; "1440p" -> 1440
    "2160p", "4k" -> 2160
    "hd" -> 720; "fhd" -> 1080; "uhd" -> 2160; "sd" -> 480
    else -> name.filter(Char::isDigit).toIntOrNull() ?: 0
}
