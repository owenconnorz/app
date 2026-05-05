package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aioweb.app.ui.viewmodel.MoviesViewModel
import com.lagradost.cloudstream3.SearchResponse

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit,
    onPlayStream: (String, String) -> Unit
) {
    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadDiscover()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // HERO
        item {
            HeroBanner(state.heroBanner, onMovieClick)
        }

        // TRENDING
        item { SectionTitle("Trending") }
        item {
            LazyRow {
                items(state.trending) { movie ->
                    MovieCard(
                        title = movie.title ?: "Unknown",
                        onClick = { onMovieClick(movie.id) }
                    )
                }
            }
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
                                onPlayStream(url, item.name ?: "Stream")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBanner(
    items: List<com.aioweb.app.data.api.TmdbMovie>,
    onClick: (Long) -> Unit
) {
    if (items.isEmpty()) return

    Column(Modifier.padding(16.dp)) {
        Text("Featured", style = MaterialTheme.typography.titleLarge)

        items.take(5).forEach {
            Text(
                text = it.title ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(it.id) }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun MovieCard(
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Box(Modifier.padding(16.dp)) {
            Text(title)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp)
    )
}