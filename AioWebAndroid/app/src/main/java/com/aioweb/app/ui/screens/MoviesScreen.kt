package com.aioweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.aioweb.app.data.model.UiMovie
import com.aioweb.app.data.model.toUiMovie
import com.aioweb.app.ui.components.PosterGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(onMovieClick: (Long) -> Unit) {

    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()

    var query by remember { mutableStateOf("") }

    val mergedMovies = remember(state) {
        buildList<UiMovie> {

            // ✅ TMDB
            state.collections.forEach { row ->
                addAll(row.items.map { it.toUiMovie() })
            }

            // ✅ Stremio
            state.stremioSections.forEach { section ->
                section.items.forEach { item ->
                    add(
                        UiMovie(
                            id = item.id.hashCode().toLong(),
                            title = item.name,
                            posterUrl = item.poster ?: "",
                            backdropUrl = item.poster ?: "",
                            releaseDate = item.releaseInfo ?: ""
                        )
                    )
                }
            }

            // ✅ Nuvio
            state.nuvioSections.forEach { section ->
                section.items.forEach { item ->
                    add(
                        UiMovie(
                            id = item.id.hashCode().toLong(),
                            title = item.name,
                            posterUrl = item.poster ?: "",
                            backdropUrl = item.poster ?: "",
                            releaseDate = item.releaseInfo ?: ""
                        )
                    )
                }
            }

            // ✅ Plugins
            state.pluginSections.forEach { section ->
                section.items.forEach { item ->
                    add(
                        UiMovie(
                            id = item.url.hashCode().toLong(),
                            title = item.name,
                            posterUrl = item.posterUrl ?: "",
                            backdropUrl = item.posterUrl ?: "",
                            releaseDate = ""
                        )
                    )
                }
            }
        }.distinctBy { it.id }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            item {
                MoviesHeader()
            }

            item {
                MoviesSearchField(
                    query = query,
                    loading = state.loading,
                    onQueryChange = {
                        query = it
                        vm.search(it)
                    }
                )
            }

            item {
                SectionTitle("🔥 Trending")
            }

            item {
                PosterGrid(
                    movies = if (query.isBlank())
                        mergedMovies
                    else
                        state.searchResults.map { it.toUiMovie() },

                    onClick = onMovieClick
                )
            }
        }
    }
}