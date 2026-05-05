package com.aioweb.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector // ✅ FIXED IMPORT
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.player.NativePlayerScreen
import com.aioweb.app.ui.screens.*
import com.aioweb.app.ui.viewmodel.AdultViewModel
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
    val navOrderCsv by sl.settings.navTabOrderCsv.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        runCatching { com.aioweb.app.audio.PlaybackBus.ensureAttached(context) }
        runCatching { com.aioweb.app.ui.theme.AlbumArtThemeBus.attach(context) }
    }

    val tabs = remember(nsfwEnabled, navOrderCsv) {

        val pool = buildMap<String, Tab> {
            put(Tab.Movies.route, Tab.Movies)
            put(Tab.Music.route, Tab.Music)
            put(Tab.Ai.route, Tab.Ai)
            if (nsfwEnabled) put(Tab.Adult.route, Tab.Adult)
            else put(Tab.Library.route, Tab.Library)
        }

        val requested = navOrderCsv
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { pool[it.trim()] }
            ?.distinct()
            .orEmpty()

        val seen = requested.map { it.route }.toSet()
        val middle = requested + pool.values.filter { it.route !in seen }

        middle + Tab.Settings
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),

        bottomBar = {

            val showBar = currentRoute == null || tabs.any { it.route == currentRoute }

            if (showBar) {
                Column {

                    if (currentRoute != Tab.Music.route) {
                        com.aioweb.app.ui.player.GlobalMiniPlayer(
                            onExpand = {
                                com.aioweb.app.ui.player.PlayerExpandBus.requestExpand()
                            }
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
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
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors()
                            )
                        }
                    }
                }
            }
        }

    ) { padding ->

        Box(Modifier.fillMaxSize().padding(padding)) {

            NavHost(
                navController = nav,
                startDestination = Tab.Movies.route
            ) {

                // ================= MOVIES =================
                composable(Tab.Movies.route) {

                    MoviesScreen(
                        onMovieClick = { id ->
                            nav.navigate("movie/$id")
                        },
                        onPlayStream = { url, title ->   // ✅ FIXED (THIS WAS MISSING)
                            val u = URLEncoder.encode(url, "UTF-8")
                            val t = URLEncoder.encode(title, "UTF-8")
                            nav.navigate("player/url/$u/$t")
                        }
                    )
                }

                // ================= PLAYER =================
                composable(
                    "player/url/{url}/{title}",
                    arguments = listOf(
                        navArgument("url") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType }
                    )
                ) {
                    val url = URLDecoder.decode(it.arguments!!.getString("url")!!, "UTF-8")
                    val title = URLDecoder.decode(it.arguments!!.getString("title")!!, "UTF-8")

                    NativePlayerScreen(
                        streamUrl = url,
                        title = title,
                        onBack = { nav.popBackStack() }
                    )
                }

                // ================= SETTINGS =================
                composable(Tab.Settings.route) {
                    SettingsHubScreen(onOpenPlugins = { nav.navigate("plugins") })
                }

                composable("plugins") {
                    PluginsScreen(onBack = { nav.popBackStack() })
                }
            }
        }

        com.aioweb.app.ui.player.GlobalNowPlayingSheet()
    }
}