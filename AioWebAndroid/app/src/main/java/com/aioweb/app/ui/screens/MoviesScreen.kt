@Composable
fun MoviesScreen(onMovieClick: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()

    var query by remember { mutableStateOf("") }

    val mergedMovies = remember(state) {
        buildList {

            state.collections.forEach { row ->
                addAll(row.items)
            }

            state.stremioSections.forEach { section ->
                section.items.forEach {
                    add(
                        TmdbMovie(
                            id = "stremio_${it.id}".hashCode().toLong(),
                            title = it.name,
                            posterUrl = it.poster ?: "",
                            backdropUrl = it.poster ?: "",
                            voteAverage = 0.0,
                            releaseDate = it.releaseInfo ?: ""
                        )
                    )
                }
            }

            state.nuvioSections.forEach { section ->
                section.items.forEach {
                    add(
                        TmdbMovie(
                            id = "nuvio_${it.id}".hashCode().toLong(),
                            title = it.name,
                            posterUrl = it.poster ?: "",
                            backdropUrl = it.poster ?: "",
                            voteAverage = 0.0,
                            releaseDate = it.releaseInfo ?: ""
                        )
                    )
                }
            }

            state.pluginSections.forEach { section ->
                section.items.forEach {
                    add(
                        TmdbMovie(
                            id = "plugin_${it.url}".hashCode().toLong(),
                            title = it.name,
                            posterUrl = it.posterUrl ?: "",
                            backdropUrl = it.posterUrl ?: "",
                            voteAverage = 0.0,
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

            item { MoviesHeader() }

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

            item { SectionTitle("🔥 Trending") }

            item {
                when {
                    state.loading -> {
                        Box(Modifier.fillMaxWidth().padding(24.dp)) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }

                    mergedMovies.isEmpty() -> {
                        Text(
                            "No content loaded",
                            modifier = Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        PosterGrid(
                            movies = if (query.isBlank()) mergedMovies else state.searchResults,
                            onClick = onMovieClick
                        )
                    }
                }
            }
        }
    }
}