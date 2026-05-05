package com.aioweb.app.player

object MoviePlayerSession {

    var sources: List<PlayerSource> = emptyList()
        private set

    var progressKey: WatchProgressKey? = null
        private set

    fun set(newSources: List<PlayerSource>, progressKey: WatchProgressKey? = null) {
        sources = newSources
        this.progressKey = progressKey
    }

    fun clear() {
        sources = emptyList()
        progressKey = null
    }
}

data class WatchProgressKey(
    val tmdbId: Long? = null,
    val title: String,
    val posterUrl: String? = null,
    val mediaType: String = "movie"
)