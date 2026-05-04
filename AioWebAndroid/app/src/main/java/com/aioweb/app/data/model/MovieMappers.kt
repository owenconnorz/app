package com.aioweb.app.data.model

import com.aioweb.app.data.api.TmdbMovie

fun TmdbMovie.toUiMovie(): UiMovie {
    return UiMovie(
        id = id,
        title = displayTitle,
        posterUrl = posterUrl ?: "",
        backdropUrl = backdropUrl ?: "",
        releaseDate = releaseDate ?: ""
    )
}