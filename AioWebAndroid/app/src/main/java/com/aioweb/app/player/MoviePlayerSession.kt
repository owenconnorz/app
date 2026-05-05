package com.aioweb.app.player

/**
 * Hand-off between MovieDetailScreen and the player route.
 *
 * Holds:
 * - Full list of PlayerSource (for source picker)
 * - Optional WatchProgressKey (for resume system)
 *
 * Supports BOTH:
 * - Full metadata (TMDB / Stremio)
 * - Lightweight plugin playback
 */
object MoviePlayerSession {

    /** All playable sources (HLS, MP4, magnet, etc.) */
    var sources: List<PlayerSource> = emptyList()
        private set

    /** Optional resume/playback metadata */
    var progressKey: WatchProgressKey? = null
        private set

    /**
     * Set active playback session
     */
    fun set(
        newSources: List<PlayerSource>,
        progressKey: WatchProgressKey? = null
    ) {
        sources = newSources
        this.progressKey = progressKey
    }

    /**
     * Clear session (called when player closes)
     */
    fun clear() {
        sources = emptyList()
        progressKey = null
    }
}

/**
 * Flexible progress key:
 *
 * Used for:
 * - TMDB resume tracking (full fields)
 * - Plugin playback (title only)
 */
data class WatchProgressKey(

    /** TMDB ID (nullable for plugins) */
    val tmdbId: Long? = null,

    /** Title is ALWAYS required */
    val title: String,

    /** Poster (optional) */
    val posterUrl: String? = null,

    /** "movie" or "tv" (optional for plugins) */
    val mediaType: String? = null
)