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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aioweb.app.ui.screens.AiScreen
import com.aioweb.app.ui.screens.LibraryScreen
import com.aioweb.app.ui.screens.MovieDetailScreen
import com.aioweb.app.ui.screens.MoviesScreen
import com.aioweb.app.ui.screens.MusicScreen
import com.aioweb.app.ui.screens.SettingsScreen

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Movies   : Tab("movies",   "Movies",   Icons.Filled.Theaters)
    data object Music    : Tab("music",    "Music",    Icons.Filled.MusicNote)
    data object Ai       : Tab("ai",       "AI",       Icons.Filled.AutoAwesome)
    data object Library  : Tab("library",  "Library",  Icons.Filled.Bookmarks)
    data object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}
private val TABS = listOf(Tab.Movies, Tab.Music, Tab.Ai, Tab.Library, Tab.Settings)

@Composable
fun AioWebApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        bottomBar = {
            // Hide bottom bar on detail screens
            val showBar = currentRoute == null || TABS.any { it.route == currentRoute }
            if (showBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    TABS.forEach { tab ->
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
                composable(Tab.Movies.route) { MoviesScreen(onMovieClick = { id -> nav.navigate("movie/$id") }) }
                composable(
                    "movie/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) {
                    MovieDetailScreen(
                        movieId = it.arguments!!.getLong("id"),
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Tab.Music.route)    { MusicScreen() }
                composable(Tab.Ai.route)       { AiScreen() }
                composable(Tab.Library.route)  { LibraryScreen() }
                composable(Tab.Settings.route) { SettingsScreen() }
            }
        }
    }
}
