package com.aioweb.app.data.collections

import com.aioweb.app.data.api.TmdbApi
import com.aioweb.app.data.api.TmdbMovie

/**
 * Nuvio-style "Home collections" — curated TMDB lists the user can toggle on/off
 * from Settings. Each collection knows how to fetch its own row from the TMDB API.
 *
 * The IDs used here are stable strings that we persist in DataStore.
 */
data class HomeCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val defaultEnabled: Boolean,
    val fetch: suspend (TmdbApi, String) -> List<TmdbMovie>,
)

object HomeCollections {

    /** The full registry. Order here is the order rows appear on the home screen. */
    val ALL: List<HomeCollection> = listOf(
        HomeCollection(
            id = "trending", title = "Trending This Week",
            subtitle = "What everyone's watching right now",
            emoji = "🔥", defaultEnabled = true,
        ) { api, key -> api.trending(apiKey = key).results },

        HomeCollection(
            id = "now_playing", title = "In Theatres",
            subtitle = "Now playing in cinemas",
            emoji = "🎬", defaultEnabled = true,
        ) { api, key -> api.nowPlaying(key).results },

        HomeCollection(
            id = "popular", title = "Popular Movies",
            subtitle = "All-time fan favourites",
            emoji = "⭐", defaultEnabled = true,
        ) { api, key -> api.popular(key).results },

        HomeCollection(
            id = "top_rated", title = "Top Rated",
            subtitle = "Highest-scored on TMDB",
            emoji = "🏆", defaultEnabled = true,
        ) { api, key -> api.topRated(key).results },

        // ── Nuvio's curated company / studio collections ──
        HomeCollection(
            id = "marvel", title = "Marvel Cinematic Universe",
            subtitle = "Avengers, X-Men, Spider-Man and more",
            emoji = "🕷️", defaultEnabled = false,
        ) { api, key -> api.discover(key, withCompanies = "420").results },

        HomeCollection(
            id = "dc", title = "DC Universe",
            subtitle = "Batman, Superman, Justice League",
            emoji = "🦇", defaultEnabled = false,
        ) { api, key -> api.discover(key, withCompanies = "9993").results },

        HomeCollection(
            id = "pixar", title = "Pixar Animation",
            subtitle = "Toy Story, Inside Out, Wall-E",
            emoji = "🎈", defaultEnabled = false,
        ) { api, key -> api.discover(key, withCompanies = "3").results },

        HomeCollection(
            id = "disney_anim", title = "Disney Animation",
            subtitle = "Moana, Frozen, Encanto",
            emoji = "🏰", defaultEnabled = false,
        ) { api, key -> api.discover(key, withCompanies = "6125").results },

        HomeCollection(
            id = "ghibli", title = "Studio Ghibli",
            subtitle = "Spirited Away, Totoro, Howl's Castle",
            emoji = "🌳", defaultEnabled = false,
        ) { api, key -> api.discover(key, withCompanies = "10342").results },

        HomeCollection(
            id = "a24", title = "A24",
            subtitle = "Indie & arthouse — Everything Everywhere All at Once",
            emoji = "🎭", defaultEnabled = false,
        ) { api, key -> api.discover(key, withCompanies = "41077").results },

        // ── Genre rows ──
        HomeCollection(
            id = "action", title = "Action",
            subtitle = "High-octane blockbusters",
            emoji = "💥", defaultEnabled = false,
        ) { api, key -> api.discover(key, withGenres = "28").results },

        HomeCollection(
            id = "horror", title = "Horror",
            subtitle = "Things that go bump in the night",
            emoji = "🎃", defaultEnabled = false,
        ) { api, key -> api.discover(key, withGenres = "27").results },

        HomeCollection(
            id = "scifi", title = "Sci-Fi",
            subtitle = "Spaceships, time travel, parallel universes",
            emoji = "🚀", defaultEnabled = false,
        ) { api, key -> api.discover(key, withGenres = "878").results },

        HomeCollection(
            id = "anime", title = "Anime Movies",
            subtitle = "Anime feature films",
            emoji = "🎌", defaultEnabled = false,
        ) { api, key -> api.discover(key, withKeywords = "210024").results },
    )

    fun byId(id: String): HomeCollection? = ALL.firstOrNull { it.id == id }
}
