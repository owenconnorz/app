package com.aioweb.app.ui.screens

import androidx.compose.runtime.Composable

/**
 * Public entry point used by [com.aioweb.app.ui.AioWebApp]. Delegates to
 * [SettingsHubScreen] — the full implementation lives there so this file
 * stays tiny and easy to review / diff.
 */
@Composable
fun SettingsScreen(onOpenPlugins: () -> Unit) {
    SettingsHubScreen(onOpenPlugins = onOpenPlugins)
}
