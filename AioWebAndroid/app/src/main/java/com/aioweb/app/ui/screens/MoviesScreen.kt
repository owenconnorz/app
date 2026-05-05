package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aioweb.app.ui.viewmodel.MoviesViewModel

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()

    LazyColumn {

        // 🔍 SEARCH
        item {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                label = { Text("Search...") }
            )
        }

        // 🎛 CLOUDSTREAM SELECTOR
        item {
            LazyRow(
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.installedPlugins) { plugin ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.loadPluginHome(plugin.internalName) },
                        label = { Text(plugin.name) }
                    )
                }
            }
        }

        // 🎬 HERO
        item {
            state.heroBanner.firstOrNull()?.let { movie ->
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w780${movie.posterPath}",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clickable { onMovieClick(movie.id) },
                    contentScale = ContentScale.Crop
                )
            }
        }

        // ▶ CONTINUE WATCHING
        if (state.continueWatching.isNotEmpty()) {
            item { SectionTitle("Continue Watching") }

            item {
                HorizontalRow(state.continueWatching.map {
                    PosterItem(it.title, it.posterUrl)
                })
            }
        }

        // 🔥 TRENDING
        item { SectionTitle("Trending") }
        item {
            HorizontalRow(state.trending.map {
                PosterItem(
                    it.title ?: "",
                    "https://image.tmdb.org/t/p/w500${it.posterPath}"
                )
            })
        }

        // ⭐ POPULAR
        item { SectionTitle("Popular") }
        item {
            HorizontalRow(state.popular.map {
                PosterItem(
                    it.title ?: "",
                    "https://image.tmdb.org/t/p/w500${it.posterPath}"
                )
            })
        }

        // 📺 STREMIO
        state.stremioSections.forEach {
            item { SectionTitle(it.title) }
            item {
                HorizontalRow(it.items.map {
                    PosterItem(it.name ?: "", it.poster)
                })
            }
        }

        // 🔌 PLUGINS
        state.pluginSections.forEach {
            item { SectionTitle(it.title) }
            item {
                HorizontalRow(it.items.map {
                    PosterItem(it.name ?: "", it.posterUrl)
                })
            }
        }
    }
}

data class PosterItem(val title: String, val poster: String?)

@Composable
fun HorizontalRow(items: List<PosterItem>) {
    LazyRow(
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) {
            Column(modifier = Modifier.width(140.dp)) {

                AsyncImage(
                    model = it.poster ?: "",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .