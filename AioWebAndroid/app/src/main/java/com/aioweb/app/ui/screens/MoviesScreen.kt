package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

import com.aioweb.app.ui.viewmodel.MoviesViewModel
import com.aioweb.app.player.*

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit,
    onPlayStream: (String, String) -> Unit
) {
    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn {

        // =========================
        // STREMIO SECTION (FIXED)
        // =========================
        state.stremioSections.forEach { section ->

            item {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(8.dp)
                )
            }

            items(section.items) { item ->

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {

                            val addon = state.installedStremioAddons
                                .firstOrNull { it.name == section.addonName }
                                ?: return@clickable

                            scope.launch {

                                val sources = mutableListOf<PlayerSource>()

                                val streams = vm.getStremioStreams(addon, item)

                                streams.forEach { stream ->
                                    val url = stream.url ?: return@forEach

                                    sources.add(
                                        PlayerSource(
                                            id = url,
                                            url = url,
                                            label = stream.title ?: "Stream",
                                            addonName = addon.name,
                                            qualityTag = stream.title ?: "Auto",
                                            isMagnet = url.startsWith("magnet")
                                        )
                                    )
                                }

                                if (sources.isNotEmpty()) {
                                    MoviePlayerSession.set(
                                        newSources = sources,
                                        progressKey = WatchProgressKey(
                                            title = item.name ?: "stremio",
                                            posterUrl = item.poster
                                        )
                                    )

                                    val first = sources.first()

                                    onPlayStream(first.url, item.name ?: "Stream")
                                }
                            }
                        }
                        .padding(8.dp)
                ) {

                    AsyncImage(
                        model = item.poster,
                        contentDescription = null,
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(item.name ?: "Unknown")
                }
            }
        }
    }
}