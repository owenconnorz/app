package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.ui.viewmodel.*

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit
) {
    val context = LocalContext.current

    val vm: MoviesViewModel = viewModel(
        factory = MoviesViewModel.factory(context)
    )

    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadDiscover()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // 🔥 SOURCE SWITCHER
        SourceSwitcher(state, vm)

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {

            // 🎬 TMDB CONTENT (POSTERS)
            if (state.selectedSourceId == SOURCE_BUILTIN) {
                state.collections.forEach { row ->

                    item {
                        SectionTitle(row.title)
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(row.items) { movie ->
                                MoviePosterCard(movie, onMovieClick)
                            }
                        }
                    }
                }
            }

            // 🔌 PLUGINS
            if (state.isPluginActive) {
                state.pluginSections.forEach { section ->
                    item { SectionTitle(section.title) }

                    items(section.items) { item ->
                        Text(
                            text = item.name ?: "Unknown",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // 📺 STREMIO
            if (state.isStremioActive) {
                state.stremioSections.forEach { section ->
                    item { SectionTitle(section.title) }

                    items(section.items) { item ->
                        Text(
                            text = item.name ?: "Unknown",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // 🌐 NUVIO
            if (state.isNuvioActive) {
                state.nuvioSections.forEach { section ->
                    item { SectionTitle(section.title) }

                    items(section.items) { item ->
                        Text(
                            text = item.name ?: "Unknown",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // ⏳ Loading / Error
            item {
                if (state.loading) {
                    Text("Loading...", modifier = Modifier.padding(16.dp))
                }

                state.error?.let {
                    Text("Error: $it", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceSwitcher(
    state: MoviesState,
    vm: MoviesViewModel
) {
    Column(modifier = Modifier.padding(8.dp)) {

        Text("Source: ${state.selectedSourceName}")

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            SourceButton("TMDB") {
                vm.selectSource(SOURCE_BUILTIN)
            }

            state.installedPlugins.forEach { plugin ->
                SourceButton(plugin.name) {
                    vm.selectSource(plugin.internalName)
                }
            }

            state.installedStremioAddons.forEach { addon ->
                SourceButton(addon.name) {
                    vm.selectSource(SOURCE_STREMIO_PREFIX + addon.manifestUrl)
                }
            }

            state.installedNuvioProviders.forEach { provider ->
                SourceButton(provider.name) {
                    vm.selectSource(SOURCE_NUVIO_PREFIX + provider.id)
                }
            }
        }
    }
}

@Composable
private fun SourceButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(40.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(12.dp)
    )
}

@Composable
fun MoviePosterCard(
    movie: TmdbMovie,
    onClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick(movie.id) }
    ) {

        AsyncImage(
            model = movie.posterUrl ?: "https://via.placeholder.com/500x750",
            contentDescription = movie.displayTitle,
            modifier = Modifier
                .height(180.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = movie.displayTitle,
            maxLines = 2,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}