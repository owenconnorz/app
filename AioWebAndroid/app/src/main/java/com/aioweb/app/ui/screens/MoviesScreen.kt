package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.ui.viewmodel.MoviesViewModel

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit
) {
    val viewModel: MoviesViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    when {
        state.loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }

        state.error != null -> {
            Text(
                text = state.error ?: "Unknown error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // 🔥 Trending
                item {
                    SectionTitle("Trending")
                }
                items(state.trending) { movie ->
                    MovieItem(movie, onMovieClick)
                }

                // ⭐ Popular
                item {
                    SectionTitle("Popular")
                }
                items(state.popular) { movie ->
                    MovieItem(movie, onMovieClick)
                }

                // 🏆 Top Rated
                item {
                    SectionTitle("Top Rated")
                }
                items(state.topRated) { movie ->
                    MovieItem(movie, onMovieClick)
                }

                // 🎬 Now Playing
                item {
                    SectionTitle("Now Playing")
                }
                items(state.nowPlaying) { movie ->
                    MovieItem(movie, onMovieClick)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun MovieItem(
    movie: TmdbMovie,
    onMovieClick: (Long) -> Unit
) {
    Text(
        text = movie.displayTitle,
        modifier = Modifier
            .fillMaxSize()
            .clickable { onMovieClick(movie.id) }
            .padding(12.dp)
    )
}