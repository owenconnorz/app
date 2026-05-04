package com.aioweb.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.aioweb.app.ui.viewmodel.MoviesViewModel
import com.aioweb.app.data.api.TmdbMovie

@Composable
fun MoviesScreen() {
    val context = LocalContext.current

    val viewModel: MoviesViewModel = viewModel(
        factory = MoviesViewModel.factory(context)
    )

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDiscover()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {

        item {
            Text(
                text = "Movies",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Trending
        if (state.trending.isNotEmpty()) {
            item { Text("Trending") }

            items(state.trending) { movie: TmdbMovie ->
                MovieItem(movie)
            }
        }

        // Popular
        if (state.popular.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Popular")
            }

            items(state.popular) { movie: TmdbMovie ->
                MovieItem(movie)
            }
        }

        // Top Rated
        if (state.topRated.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Top Rated")
            }

            items(state.topRated) { movie: TmdbMovie ->
                MovieItem(movie)
            }
        }

        // Now Playing
        if (state.nowPlaying.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Now Playing")
            }

            items(state.nowPlaying) { movie: TmdbMovie ->
                MovieItem(movie)
            }
        }
    }
}

@Composable
fun MovieItem(movie: TmdbMovie) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = movie.displayTitle)
        Text(text = movie.releaseDate ?: "")
    }
}