package com.aioweb.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    // Load once
    LaunchedEffect(Unit) {
        vm.loadDiscover()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // 🔥 SOURCE SWITCHER
        SourceSwitcher(state, vm)

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {

            // 🔥 TMDB CONTENT
            if (state.selectedSourceId == SOURCE_BUILTIN) {
                state.collections.forEach { row ->
                    item {
                        SectionTitle(row.title)
                    }

                    items(row.items) { movie ->
                        Text(
                            text = movie.displayTitle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMovieClick(movie.id) }
                                .padding(12.dp)
                        )
                    }
                }
            }

            // 🔥 PLUGIN CONTENT
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

            // 🔥 STREMIO CONTENT
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

            // 🔥 NUVIO CONTENT
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

            // 🔥 LOADING / ERROR
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

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

            // TMDB
            Button(onClick = { vm.selectSource(SOURCE_BUILTIN) }) {
                Text("TMDB")
            }

            // Plugins
            state.installedPlugins.forEach { plugin ->
                Button(
                    onClick = { vm.selectSource(plugin.internalName) }
                ) {
                    Text(plugin.name)
                }
            }

            // Stremio
            state.installedStremioAddons.forEach { addon ->
                Button(
                    onClick = {
                        vm.selectSource(SOURCE_STREMIO_PREFIX + addon.manifestUrl)
                    }
                ) {
                    Text(addon.name)
                }
            }

            // Nuvio
            state.installedNuvioProviders.forEach { provider ->
                Button(
                    onClick = {
                        vm.selectSource(SOURCE_NUVIO_PREFIX + provider.id)
                    }
                ) {
                    Text(provider.name)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(12.dp)
    )
}