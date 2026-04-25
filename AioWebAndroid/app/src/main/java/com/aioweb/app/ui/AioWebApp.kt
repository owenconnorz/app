package com.aioweb.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.player.NativePlayerScreen
import com.aioweb.app.ui.screens.AdultScreen
import com.aioweb.app.ui.screens.AiScreen
import com.aioweb.app.ui.screens.LibraryScreen
import com.aioweb.app.ui.screens.MovieDetailScreen
import com.aioweb.app.ui.screens.MoviesScreen
import com.aioweb.app.ui.screens.MusicScreen
import com.aioweb.app.ui.screens.PluginsScreen
import com.aioweb.app.ui.screens.SettingsScreen
import com.aioweb.app.ui.viewmodel.AdultViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URLDecoder
import java.net.URLEncoder

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Movies   : Tab("movies",   "Movies",   Icons.Filled.Theaters)
    data object Music    : Tab("music",    "Music",    Icons.Filled.MusicNote)
    data object Ai       : Tab("ai",       "AI",       Icons.Filled.AutoAwesome)
    data object Library  : Tab("library",  "Library",  Icons.Filled.Bookmarks)
    data object Adult    : Tab("adult",    "Adult",    Icons.Filled.Whatshot)
    data object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun AioWebApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val context = LocalContext.current
    val sl = remember { ServiceLocator.get(context) }
    val nsfwEnabled by sl.settings.nsfwEnabled.collectAsState(initial = false)

    val tabs = remember(nsfwEnabled) {
        if (nsfwEnabled) {
            listOf(Tab.Movies, Tab.Music, Tab.Ai, Tab.Adult, Tab.Settings)
        } else {
            listOf(Tab.Movies, Tab.Music, Tab.Ai, Tab.Library, Tab.Settings)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        bottomBar = {
            val showBar = currentRoute == null || tabs.any { it.route == currentRoute }
            if (showBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = Tab.Movies.route,
            ) {
                composable(Tab.Movies.route) {
                    MoviesScreen(onMovieClick = { id -> nav.navigate("movie/$id") })
                }
                composable(
                    "movie/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) {
                    MovieDetailScreen(
                        movieId = it.arguments!!.getLong("id"),
                        onBack = { nav.popBackStack() },
                        onPlayUrl = { url, title ->
                            val u = URLEncoder.encode(url, "UTF-8")
                            val t = URLEncoder.encode(title, "UTF-8")
                            nav.navigate("player/url/$u/$t")
                        },
                    )
                }
                composable(Tab.Music.route)    { MusicScreen() }
                composable(Tab.Ai.route)       { AiScreen() }
                composable(Tab.Library.route)  { LibraryScreen() }
                composable(Tab.Adult.route) {
                    AdultScreen(onPlay = { videoId, embed, title ->
                        val v = URLEncoder.encode(videoId, "UTF-8")
                        val e = URLEncoder.encode(embed, "UTF-8")
                        val t = URLEncoder.encode(title, "UTF-8")
                        nav.navigate("player/eporner/$v/$e/$t")
                    })
                }
                // Eporner-specific player: resolves direct MP4 by video id, then plays natively.
                composable(
                    "player/eporner/{id}/{embed}/{title}",
                    arguments = listOf(
                        navArgument("id")    { type = NavType.StringType },
                        navArgument("embed") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType },
                    )
                ) { entry ->
                    val id    = URLDecoder.decode(entry.arguments!!.getString("id")!!,    "UTF-8")
                    val embed = URLDecoder.decode(entry.arguments!!.getString("embed")!!, "UTF-8")
                    val title = URLDecoder.decode(entry.arguments!!.getString("title")!!, "UTF-8")
                    val ctx = LocalContext.current
                    val vm: AdultViewModel = viewModel(factory = AdultViewModel.factory(ctx))
                    var resolved by remember { mutableStateOf<String?>(null) }
                    var resolveFailed by remember { mutableStateOf(false) }
                    LaunchedEffect(id) {
                        val url = vm.resolveStreamUrl(id, embed)
                        if (url == null) resolveFailed = true else resolved = url
                    }
                    when {
                        resolved != null -> NativePlayerScreen(
                            streamUrl = resolved!!,
                            title = title,
                            onBack = { nav.popBackStack() },
                        )
                        resolveFailed -> Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            Text(
                                "No direct stream available for this video.",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        else -> Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) { androidx.compose.material3.CircularProgressIndicator() }
                    }
                }
                // Generic player route used by Movies (CloudStream/torrent/HTTP) and any other source.
                composable(
                    "player/url/{url}/{title}",
                    arguments = listOf(
                        navArgument("url")   { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType },
                    )
                ) { entry ->
                    val url   = URLDecoder.decode(entry.arguments!!.getString("url")!!,   "UTF-8")
                    val title = URLDecoder.decode(entry.arguments!!.getString("title")!!, "UTF-8")
                    NativePlayerScreen(
                        streamUrl = url,
                        title = title,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Tab.Settings.route) {
                    SettingsScreen(onOpenPlugins = { nav.navigate("plugins") })
                }
                composable("plugins") {
                    PluginsScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
