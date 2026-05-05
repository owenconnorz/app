package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aioweb.app.ui.viewmodel.MoviesViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.aioweb.app.data.plugins.PluginRuntime
import com.aioweb.app.player.MoviePlayerSession
import com.aioweb.app.player.PlayerSource
import com.aioweb.app.player.WatchProgressKey

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit,
    onPlayStream: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.loadDiscover()
    }

    LazyColumn(Modifier.fillMaxSize()) {

        // TMDB
        if (!state.isPluginActive && !state.isStremioActive && !state.isNuvioActive) {
            item { SectionTitle("Trending") }

            items(state.trending) { movie ->
                MovieCard(
                    title = movie.title ?: "Unknown",
                    poster = movie.posterPath,
                    onClick = { onMovieClick(movie.id) }
                )
            }
        }

        // Plugins
        if (state.isPluginActive) {
            state.pluginSections.forEach { section ->

                item { SectionTitle(section.title ?: "Plugins") }

                items(section.items) { item ->

                    MovieCard(
                        title = item.name ?: "Unknown",
                        poster = item.posterUrl,
                        onClick = {

                            val plugin = state.installedPlugins
                                .firstOrNull { it.internalName == state.selectedSourceId }
                                ?: return@MovieCard

                            scope.launch {

                                val sources = mutableListOf<PlayerSource>()

                                PluginRuntime.loadLinks(
                                    context = context,
                                    filePath = plugin.filePath,
                                    url = item.url
                                ) { link ->

                                    if (link.url.isNullOrEmpty()) return@loadLinks

                                    sources.add(
                                        PlayerSource(
                                            id = "${link.name}_${link.quality}",
                                            url = link.url,
                                            label = link.name ?: "Stream",
                                            addonName = link.name ?: "Unknown",
                                            qualityTag = link.quality?.toString() ?: "Auto",
                                            isMagnet = link.url.startsWith("magnet")
                                        )
                                    )
                                }

                                if (sources.isNotEmpty()) {

                                    MoviePlayerSession.set(
                                        newSources = sources,
                                        progressKey = WatchProgressKey(
                                            title = item.name ?: "plugin"
                                        )
                                    )

                                    val first = sources.first()

                                    onPlayStream(
                                        first.url,
                                        item.name ?: "Stream"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        // Stremio
        if (state.isStremioActive) {
            state.stremioSections.forEach { section ->
                item { SectionTitle(section.title ?: "Stremio") }

                items(section.items) { item ->
                    MovieCard(
                        title = item.name ?: "Unknown",
                        poster = item.poster,
                        onClick = {}
                    )
                }
            }
        }

        // Nuvio
        if (state.isNuvioActive) {
            state.nuvioSections.forEach { section ->
                item { SectionTitle(section.title ?: "Nuvio") }

                items(section.items) { item ->
                    MovieCard(
                        title = item.name ?: "Unknown",
                        poster = item.poster,
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
fun MovieCard(
    title: String,
    poster: String?,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        AsyncImage(
            model = poster,
            contentDescription = null,
            modifier = Modifier
                .width(100.dp)
                .height(150.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column {
            Text(title)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(8.dp)
    )
}