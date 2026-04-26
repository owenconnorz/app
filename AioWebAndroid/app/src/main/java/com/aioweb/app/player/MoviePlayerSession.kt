package com.aioweb.app.player

/**
 * In-memory hand-off between MovieDetailScreen and the player route.
 *
 * Stremio resolves a list of streams. We pick the best as the initial
 * playback URL, but the player needs the *full* sorted list for the
 * "Sources" picker. Encoding a 30+ item list through nav-route arguments
 * is ugly, so we just stash it in a singleton until the player consumes it.
 *
 * Lives only for the lifetime of the process — fine, because the user
 * always re-enters the player by tapping "Play Movie" again on detail.
 */
object MoviePlayerSession {
    var sources: List<PlayerSource> = emptyList()
        private set

    fun set(newSources: List<PlayerSource>) {
        sources = newSources
    }

    fun clear() {
        sources = emptyList()
    }
}
