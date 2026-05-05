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
import kotlinx.coroutines.delay
import com.aioweb.app.ui.viewmodel.MoviesViewModel

@Composable
fun MoviesScreen(
    onMovieClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()

    LazyColumn {

        // SEARCH
        item {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                label = { Text("Search...") }
            )
        }

        // PLUGINS SELECTOR
        item {
            LazyRow(
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.installedPlugins) { plugin ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            vm.setSource(plugin.internalName)
                        },
                        label = { Text(plugin.name) }
                    )
                }
            }
        }

        // HERO BANNER (AUTO SLIDE)
        item {
            val movies = state.heroBanner
            var index by remember { mutableStateOf(0) }

            LaunchedEffect(movies) {
                while (true) {
                    delay(4000)
                    if (movies.isNotEmpty()) {
                        index = (index + 1) % movies.size
                    }
                }
            }

            val movie = movies.getOrNull(index)

            if (movie != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clickable { onMovieClick(movie.id) }
                ) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w780${movie.backdropPath ?: movie.posterPath}",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        Text(movie.title ?: "")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onMovieClick(movie.id) }) {
                            Text("View Details")
                        }
                    }
                }
            }
        }

        // TRENDING
        item { SectionTitle("Trending") }

        item {
            HorizontalRow(
                state.trending.map {
                    PosterItem(
                        it.title ?: "",
                        "https://image.tmdb.org/t/p/w500${it.posterPath}",
                        onClick = { onMovieClick(it.id) }
                    )
                }
            )
        }

        // STREMIO
        state.stremioSections.forEach { section ->
            item { SectionTitle(section.title) }

            item {
                HorizontalRow(
                    section.items.map {
                        PosterItem(it.name ?: "", it.poster)
                    }
                )
            }
        }

        // PLUGINS
        state.pluginSections.forEach { section ->
            item { SectionTitle(section.title) }

            item {
                HorizontalRow(
                    section.items.map {
                        PosterItem(it.name ?: "", it.posterUrl)
                    }
                )
            }
        }
    }
}

data class PosterItem(
    val title: String,
    val poster: String?,
    val onClick: (() -> Unit)? = null
)

@Composable
fun HorizontalRow(items: List<PosterItem>) {
    LazyRow(
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { item ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { item.onClick?.invoke() }
            ) {
                AsyncImage(
                    model = item.poster ?: "",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.height(6.dp))

                Text(item.title, maxLines = 2)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(8.dp)
    )
}