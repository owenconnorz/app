package com.aioweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext

import com.aioweb.app.data.models.TmdbMovie
import com.aioweb.app.ui.viewmodel.MoviesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(onMovieClick: (Long) -> Unit) {

    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()

    var query by remember { mutableStateOf("") }

    val mergedMovies = remember(state) {
        buildList<TmdbMovie> {

            state.collections.forEach { row ->
                addAll(row.items)
            }

            state.stremioSections.forEach { section ->
                section.items.forEach { item ->
                    add(
                        TmdbMovie(
                            id = item.id.hashCode().toLong(),
                            title = item.name,
                            posterUrl = item.poster ?: "",
                            backdropUrl = item.poster ?: "",
                            voteAverage = 0.0,
                            releaseDate = item.releaseInfo ?: ""
                        )
                    )
                }
            }

            state.nuvioSections.forEach { section ->
                section.items.forEach { item ->
                    add(
                        TmdbMovie(
                            id = item.id.hashCode().toLong(),
                            title = item.name,
                            posterUrl = item.poster ?: "",
                            backdropUrl = item.poster ?: "",
                            voteAverage = 0.0,
                            releaseDate = item.releaseInfo ?: ""
                        )
                    )
                }
            }

            state.pluginSections.forEach { section ->
                section.items.forEach { item ->
                    add(
                        TmdbMovie(
                            id = item.url.hashCode().toLong(),
                            title = item.name,
                            posterUrl = item.posterUrl ?: "",
                            backdropUrl = item.posterUrl ?: "",
                            voteAverage = 0.0,
                            releaseDate = ""
                        )
                    )
                }
            }
        }.distinctBy { it.id }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            item {
                Text(
                    "🔥 Trending",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                if (state.loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                } else {
                    PosterGrid(
                        movies = if (query.isBlank()) mergedMovies else state.searchResults,
                        onClick = onMovieClick
                    )
                }
            }
        }
    }
}