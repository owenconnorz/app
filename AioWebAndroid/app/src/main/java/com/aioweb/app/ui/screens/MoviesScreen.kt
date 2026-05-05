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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

import com.aioweb.app.ui.viewmodel.MoviesViewModel
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

    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.loadDiscover()
    }

    Column {

        // 🔍 SEARCH
        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
                vm.search(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("Search...") }
        )

        // 🔁 SOURCE SWITCH
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            FilterChip(
                selected = !state.isPluginActive && !state.isStremioActive && !state.isNuvioActive,
                onClick = { vm.setSource("tmdb") },
                label = { Text("TMDB") }
            )

            FilterChip(
                selected = state.isPluginActive,
                onClick = { vm.setSource("plugin") },
                label = { Text("Plugins") }
            )

            FilterChip(
                selected = state.isStremioActive,
                onClick = { vm.setSource("stremio") },
                label = { Text("Stremio") }
            )
        }

        LazyColumn {

            // =========================
            // 🎬 TMDB (RESTORED GRID STYLE)
            // =========================
            if (!state.isPluginActive && !state.isStremioActive && !state.isNuvioActive) {

                item { SectionTitle("🔥 Trending This Week") }

                item {
                    LazyRow {
                        items(state.trending) { movie ->
                            PosterCard(
                                title = movie.title ?: "Unknown",
                                poster = movie.posterPath,
                                onClick = { onMovieClick(movie.id) }
                            )
                        }
                    }
                }

                item { SectionTitle("⭐ Popular") }

                item {
                    LazyRow {
                        items(state.popular) { movie ->
                            PosterCard(
                                title = movie.title ?: "Unknown",
                                poster = movie.posterPath,
                                onClick = { onMovieClick(movie.id) }
                            )
                        }
                    }
                }
            }

            // =========================
            // 🔌 PLUGINS
            // =========================
            if (state.isPluginActive) {
                state.pluginSections.forEach { section ->

                    item { SectionTitle(section.title ?: "Plugins") }

                    item {
                        LazyRow {
                            items(section.items) { item ->

                                PosterCard(
                                    title = item.name ?: "Unknown",
                                    poster = item.posterUrl,
                                    onClick = {

                                        val plugin = state.installedPlugins
                                            .firstOrNull { it.internalName == state.selectedSourceId }
                                            ?: return@PosterCard

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

                                                onPlayStream(first.url, item.name ?: "Stream")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // =========================
            // 📺 STREMIO (AUTO HOME)
            // =========================
            if (state.isStremioActive) {

                state.stremioSections.forEach { section ->

                    item { SectionTitle(section.title ?: "Stremio") }

                    item {
                        LazyRow {
                            items(section.items) { item ->
                                PosterCard(
                                    title = item.name ?: "Unknown",
                                    poster = item.poster,
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    poster: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .padding(8.dp)
            .clickable { onClick() }
    ) {

        AsyncImage(
            model = poster ?: "",
            contentDescription = null,
            modifier = Modifier
                .height(180.dp)
                .fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = title,
            maxLines = 2,
            style = MaterialTheme.typography.bodySmall
        )
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