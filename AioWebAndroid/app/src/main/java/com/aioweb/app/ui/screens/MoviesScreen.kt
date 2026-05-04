package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel

import com.aioweb.app.ui.viewmodel.MoviesViewModel
import com.aioweb.app.ui.viewmodel.MoviesState
import com.lagradost.cloudstream3.SearchResponse
import com.aioweb.app.data.stremio.StremioMetaPreview
import com.aioweb.app.data.api.TmdbMovie

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadDiscover()
    }

    Column(Modifier.fillMaxSize()) {

        // 🔹 SOURCE SELECTOR
        LazyRow(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SourceChip("TMDB", state.selectedSourceId == "builtin") {
                    vm.selectSource("builtin")
                }
            }

            state.installedPlugins.forEach {
                item {
                    SourceChip(it.name, state.selectedSourceId == it.internalName) {
                        vm.selectSource(it.internalName)
                    }
                }
            }

            state.installedStremioAddons.forEach {
                item {
                    SourceChip(it.name, state.selectedSourceId == "stremio_${it.manifestUrl}") {
                        vm.selectSource("stremio_${it.manifestUrl}")
                    }
                }
            }

            state.installedNuvioProviders.forEach {
                item {
                    SourceChip(it.name, state.selectedSourceId == "nuvio_${it.id}") {
                        vm.selectSource("nuvio_${it.id}")
                    }
                }
            }
        }

        // 🔴 MAIN CONTENT SWITCH
        when {
            state.isPluginActive -> PluginContent(state)
            state.isStremioActive -> StremioContent(state)
            state.isNuvioActive -> NuvioContent(state)
            else -> TmdbContent(state, onMovieClick)
        }
    }
}

//
// ===================== TMDB =====================
//

@Composable
private fun TmdbContent(
    state: MoviesState,
    onMovieClick: (Long) -> Unit
) {
    LazyColumn {

        item {
            Section("Trending", state.trending) {
                MovieCard(it.id, it.displayTitle, it.posterUrl, onMovieClick)
            }
        }

        item {
            Section("Popular", state.popular) {
                MovieCard(it.id, it.displayTitle, it.posterUrl, onMovieClick)
            }
        }
    }
}

//
// ===================== PLUGIN =====================
//

@Composable
private fun PluginContent(state: MoviesState) {

    if (state.pluginLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.pluginError != null) {
        Text("Error: ${state.pluginError}", modifier = Modifier.padding(16.dp))
        return
    }

    LazyColumn {
        state.pluginSections.forEach { section ->

            item {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }

            item {
                LazyRow {
                    items(section.items) { item ->
                        PluginCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginCard(item: SearchResponse) {

    val poster = item.posterUrl

    Column(
        modifier = Modifier
            .width(120.dp)
            .padding(8.dp)
            .clickable { /* TODO open links */ }
    ) {
        AsyncImage(
            model = poster,
            contentDescription = item.name,
            modifier = Modifier
                .height(180.dp)
                .fillMaxWidth()
        )

        Text(
            text = item.name ?: "Unknown",
            maxLines = 2
        )
    }
}

//
// ===================== STREMIO =====================
//

@Composable
private fun StremioContent(state: MoviesState) {
    LazyColumn {
        state.stremioSections.forEach { section ->

            item {
                Text(section.title, modifier = Modifier.padding(8.dp))
            }

            item {
                LazyRow {
                    items(section.items) {
                        StremioCard(it)
                    }
                }
            }
        }
    }
}

//
// ===================== NUVIO =====================
//

@Composable
private fun NuvioContent(state: MoviesState) {
    LazyColumn {
        state.nuvioSections.forEach { section ->

            item {
                Text(section.title, modifier = Modifier.padding(8.dp))
            }

            item {
                LazyRow {
                    items(section.items) {
                        StremioCard(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun StremioCard(item: StremioMetaPreview) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.name,
            modifier = Modifier
                .height(180.dp)
                .fillMaxWidth()
        )

        Text(item.name ?: "")
    }
}

//
// ===================== COMMON =====================
//

@Composable
private fun MovieCard(
    id: Long,
    title: String,
    poster: String?,
    onClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .padding(8.dp)
            .clickable { onClick(id) }
    ) {
        AsyncImage(
            model = poster,
            contentDescription = title,
            modifier = Modifier
                .height(180.dp)
                .fillMaxWidth()
        )

        Text(title, maxLines = 2)
    }
}

@Composable
private fun Section(
    title: String,
    items: List<TmdbMovie>,
    content: @Composable (TmdbMovie) -> Unit
) {
    Column {
        Text(title, modifier = Modifier.padding(8.dp))

        LazyRow {
            items(items) {
                content(it)
            }
        }
    }
}

@Composable
private fun SourceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(onClick = onClick) {
        Text(text)
    }
}