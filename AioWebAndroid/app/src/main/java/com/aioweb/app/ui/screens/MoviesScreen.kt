package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aioweb.app.ui.viewmodel.MoviesViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.aioweb.app.data.plugins.PluginRuntime
import com.aioweb.app.player.MoviePlayerSession
import com.aioweb.app.player.MovieSource
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

LazyColumn(
    modifier = Modifier.fillMaxSize()
) {

    // =========================
    // BUILTIN (TMDB)
    // =========================
    if (!state.isPluginActive && !state.isStremioActive && !state.isNuvioActive) {
        item {
            SectionTitle("Trending")
        }

        items(state.trending) { movie ->
            MovieCard(
                title = movie.title,
                poster = movie.posterPath,
                onClick = { onMovieClick(movie.id) }
            )
        }
    }

    // =========================
    // CLOUDSTREAM PLUGINS
    // =========================
    if (state.isPluginActive) {

        state.pluginSections.forEach { section ->

            item { SectionTitle(section.title) }

            items(section.items) { item ->

                MovieCard(
                    title = item.name,
                    poster = item.posterUrl,
                    onClick = {

                        val plugin = state.installedPlugins
                            .firstOrNull { it.internalName == state.selectedSourceId }
                            ?: return@MovieCard

                        scope.launch {

                            val sources = mutableListOf<MovieSource>()

                            PluginRuntime.loadLinks(
                                context = context,
                                filePath = plugin.filePath,
                                url = item.url
                            ) { link ->

                                sources.add(
                                    MovieSource(
                                        id = "${link.name}_${link.quality}",
                                        url = link.url,
                                        addonName = link.name,
                                        qualityTag = link.quality?.toString() ?: "Auto"
                                    )
                                )
                            }

                            if (sources.isNotEmpty()) {

                                MoviePlayerSession.set(
                                    newSources = sources,
                                    progressKey = WatchProgressKey(
                                        item.name ?: "plugin"
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

    // =========================
    // STREMIO
    // =========================
    if (state.isStremioActive) {

        state.stremioSections.forEach { section ->

            item { SectionTitle(section.title) }

            items(section.items) { item ->

                MovieCard(
                    title = item.name,
                    poster = item.poster,
                    onClick = {
                        // handled elsewhere
                    }
                )
            }
        }
    }

    // =========================
    // NUVIO
    // =========================
    if (state.isNuvioActive) {

        state.nuvioSections.forEach { section ->

            item { SectionTitle(section.title) }

            items(section.items) { item ->

                MovieCard(
                    title = item.name,
                    poster = item.poster,
                    onClick = {
                        // handled elsewhere
                    }
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
modifier = Modifier
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

    Spacer(modifier = Modifier.width(8.dp))

    Column(
        verticalArrangement = Arrangement.Center
    ) {
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