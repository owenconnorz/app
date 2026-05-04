@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(onMovieClick: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: MoviesViewModel = viewModel(factory = MoviesViewModel.factory(context))
    val state by vm.state.collectAsState()

    var query by remember { mutableStateOf("") }

    // 🔥 MERGE EVERYTHING INTO ONE LIST
    val mergedMovies = remember(state) {
        buildList {

            // Built-in (TMDB)
            state.collections.forEach { row ->
                addAll(row.items)
            }

            // Stremio
            state.stremioSections.forEach { section ->
                section.items.forEach {
                    add(
                        TmdbMovie(
                            id = it.id.hashCode().toLong(),
                            title = it.name,
                            posterUrl = it.poster ?: "",
                            backdropUrl = it.poster ?: "",
                            voteAverage = 0.0,
                            releaseDate = it.releaseInfo ?: ""
                        )
                    )
                }
            }

            // Nuvio
            state.nuvioSections.forEach { section ->
                section.items.forEach {
                    add(
                        TmdbMovie(
                            id = it.id.hashCode().toLong(),
                            title = it.name,
                            posterUrl = it.poster ?: "",
                            backdropUrl = it.poster ?: "",
                            voteAverage = 0.0,
                            releaseDate = it.releaseInfo ?: ""
                        )
                    )
                }
            }

            // Plugins (CloudStream)
            state.pluginSections.forEach { section ->
                section.items.forEach {
                    add(
                        TmdbMovie(
                            id = it.url.hashCode().toLong(),
                            title = it.name,
                            posterUrl = it.posterUrl ?: "",
                            backdropUrl = it.posterUrl ?: "",
                            voteAverage = 0.0,
                            releaseDate = ""
                        )
                    )
                }
            }
        }
            .distinctBy { it.id }
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

            // 🔥 SINGLE SECTION ONLY
            item {
                SectionTitle("🔥 Trending")
            }

            item {
                PosterGrid(
                    movies = if (query.isBlank()) mergedMovies else state.searchResults,
                    onClick = onMovieClick
                )
            }
        }
    }
}