package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aioweb.app.ui.viewmodel.MoviesViewModel
import com.aioweb.app.ui.viewmodel.MoviesState
import com.lagradost.cloudstream3.SearchResponse

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit,
    onPlayStream: (String, String) -> Unit,
    vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(LocalContext.current))
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadDiscover()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {

        // HERO BANNER
        item {
            HeroBanner(state.heroBanner, onMovieClick)
        }

        // TRENDING
        item {
            SectionTitle("Trending")
        }

        item {
            HorizontalMovieRow(state.trending, onMovieClick)
        }

        // STREMIO
        state.stremioSections.forEach { section ->
            item { SectionTitle(section.title) }

            item {
                LazyRow {
                    items(section.items) { item ->
                        MovieCard(
                            title = item.name ?: "Unknown",
                            onClick = {
                                // fallback → just open details
                                item.id?.toLongOrNull()?.let(onMovieClick)
                            }
                        )
                    }
                }
            }
        }

        // PLUGINS
        state.pluginSections.forEach { section ->
            item { SectionTitle(section.title) }

            item {
                LazyRow {
                    items(section.items) { item: SearchResponse ->
                        MovieCard(
                            title = item.name ?: "Unknown",
                            onClick = {
                                val url = item.url ?: return@MovieCard
                                val title = item.name ?: "Stream"
                                onPlayStream(url, title)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(
    items: List<com.aioweb.app.data.api.TmdbMovie>,
    onClick: (Long) -> Unit
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Featured", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(8.dp))

        items.take(5).forEach {
            Text(
                text = it.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(it.id) }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun HorizontalMovieRow(
    movies: List<com.aioweb.app.data.api.TmdbMovie>,
    onClick: (Long) -> Unit
) {
    LazyRow {
        items(movies) {
            MovieCard(
                title = it.title,
                onClick = { onClick(it.id) }
            )
        }
    }
}

@Composable
private fun MovieCard(
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(title)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp)
    )
}