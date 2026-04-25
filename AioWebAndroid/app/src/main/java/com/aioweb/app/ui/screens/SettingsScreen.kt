package com.aioweb.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aioweb.app.BuildConfig
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.plugins.PluginRepository
import com.aioweb.app.data.updater.UpdateChecker
import com.aioweb.app.data.updater.UpdateInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenPlugins: () -> Unit) {
    val context = LocalContext.current
    val sl = remember { ServiceLocator.get(context) }
    val pluginRepo = remember { PluginRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var nsfw by remember { mutableStateOf(false) }
    var videoQuality by remember { mutableStateOf("auto") }
    var audioQuality by remember { mutableStateOf("high") }
    var extLinks by remember { mutableStateOf(true) }
    var autoplay by remember { mutableStateOf(true) }
    var subs by remember { mutableStateOf(true) }
    var dlWifi by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(false) }
    var pluginsCacheBytes by remember { mutableStateOf(0L) }
    var showQualityVideoDialog by remember { mutableStateOf(false) }
    var showQualityAudioDialog by remember { mutableStateOf(false) }
    var falKey by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        url = sl.settings.backendUrl.first()
        provider = sl.settings.aiProvider.first()
        model = sl.settings.aiModel.first()
        nsfw = sl.settings.nsfwEnabled.first()
        videoQuality = sl.settings.videoQuality.first()
        audioQuality = sl.settings.audioQuality.first()
        extLinks = sl.settings.externalLinksInBrowser.first()
        autoplay = sl.settings.autoplayNext.first()
        subs = sl.settings.subtitlesEnabled.first()
        dlWifi = sl.settings.downloadOverWifiOnly.first()
        falKey = sl.settings.hfToken.first()
        pluginsCacheBytes = pluginRepo.pluginsCacheSize()
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(20.dp))

        Section("Sources") {
            NavRow(
                icon = Icons.Default.Extension,
                title = "CloudStream Plugins",
                subtitle = "${formatBytes(pluginsCacheBytes)} on device",
                onClick = onOpenPlugins,
            )
        }

        Section("Content") {
            ToggleRow(
                icon = Icons.Default.Visibility,
                title = "Show Adult tab (18+)",
                subtitle = "Replaces Library with Adult section",
                checked = nsfw,
                onChange = {
                    nsfw = it
                    scope.launch { sl.settings.setNsfwEnabled(it) }
                },
            )
        }

        Section("Playback") {
            NavRow(
                icon = Icons.Default.HighQuality,
                title = "Default video quality",
                subtitle = videoQuality.replaceFirstChar { c -> c.uppercase() } + (if (videoQuality.matches(Regex("\\d+"))) "p" else ""),
                onClick = { showQualityVideoDialog = true },
            )
            NavRow(
                icon = Icons.Default.GraphicEq,
                title = "Audio quality",
                subtitle = audioQuality.replaceFirstChar { c -> c.uppercase() },
                onClick = { showQualityAudioDialog = true },
            )
            ToggleRow(
                icon = Icons.Default.PlayCircle,
                title = "Autoplay next",
                subtitle = "Continue with the next song / episode automatically",
                checked = autoplay,
                onChange = { autoplay = it; scope.launch { sl.settings.setAutoplayNext(it) } },
            )
            ToggleRow(
                icon = Icons.Default.Subtitles,
                title = "Subtitles",
                subtitle = "Show subtitles when available",
                checked = subs,
                onChange = { subs = it; scope.launch { sl.settings.setSubtitlesEnabled(it) } },
            )
        }

        Section("Downloads") {
            ToggleRow(
                icon = Icons.Default.Wifi,
                title = "Download over Wi-Fi only",
                subtitle = "Avoid using mobile data for downloads",
                checked = dlWifi,
                onChange = { dlWifi = it; scope.launch { sl.settings.setDownloadOverWifiOnly(it) } },
            )
            NavRow(
                icon = Icons.Default.Download,
                title = "Manage downloads",
                subtitle = "Open Library tab",
                onClick = { /* defer to Library tab */ },
            )
        }

        Section("Backend") {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Backend URL") },
                supportingText = { Text("Your StreamCloud FastAPI deployment.") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = settingsTfColors(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = falKey,
                onValueChange = { falKey = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HuggingFace token (NSFW image gen + image editing)") },
                supportingText = { Text("Free at huggingface.co/settings/tokens. Stored on-device only.") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = settingsTfColors(),
            )
        }

        Section("AI defaults") {
            ProviderRow(
                label = "OpenAI · gpt-5.1",
                selected = provider == "openai",
                onClick = { provider = "openai"; model = "gpt-5.1"; saved = false },
            )
            ProviderRow(
                label = "Anthropic · Claude Sonnet 4.5",
                selected = provider == "anthropic",
                onClick = { provider = "anthropic"; model = "claude-sonnet-4-5-20250929"; saved = false },
            )
            ProviderRow(
                label = "Google · Gemini 2.5 Pro",
                selected = provider == "gemini",
                onClick = { provider = "gemini"; model = "gemini-2.5-pro"; saved = false },
            )
        }

        Section("Privacy & Behavior") {
            ToggleRow(
                icon = Icons.Default.OpenInBrowser,
                title = "Open external links in browser",
                subtitle = "Otherwise opens inside an in-app webview",
                checked = extLinks,
                onChange = { extLinks = it; scope.launch { sl.settings.setExternalLinksInBrowser(it) } },
            )
        }

        Section("Storage") {
            NavRow(
                icon = Icons.Default.DeleteSweep,
                title = "Clear app cache",
                subtitle = "Free up temporary files",
                onClick = {
                    scope.launch {
                        val cleared = pluginRepo.clearAppCache()
                        pluginsCacheBytes = pluginRepo.pluginsCacheSize()
                        saved = false
                    }
                },
            )
        }

        Section("About") {
            UpdaterRow()
            NavRow(
                icon = Icons.Default.Info,
                title = "StreamCloud",
                subtitle = "Version ${BuildConfig.VERSION_NAME} · code ${BuildConfig.VERSION_CODE}",
                onClick = {},
            )
            NavRow(
                icon = Icons.Default.AutoAwesome,
                title = "Source code",
                subtitle = "github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}",
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}"))
                    )
                },
            )
            NavRow(
                icon = Icons.Default.BugReport,
                title = "Report a bug",
                subtitle = "Open an issue on GitHub",
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/issues/new"))
                    )
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    sl.settings.setBackendUrl(url.trim().trimEnd('/'))
                    sl.settings.setAiProvider(provider)
                    sl.settings.setAiModel(model)
                    sl.settings.setHfToken(falKey.trim())
                    saved = true
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(if (saved) Icons.Default.Check else Icons.Default.Cloud, null)
            Spacer(Modifier.width(8.dp))
            Text(if (saved) "Saved" else "Save backend, AI defaults & HF token")
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showQualityVideoDialog) {
        QualityDialog(
            title = "Default video quality",
            options = listOf("auto" to "Auto (recommended)", "1080" to "1080p", "720" to "720p", "480" to "480p"),
            selected = videoQuality,
            onSelect = {
                videoQuality = it
                scope.launch { sl.settings.setVideoQuality(it) }
                showQualityVideoDialog = false
            },
            onDismiss = { showQualityVideoDialog = false },
        )
    }
    if (showQualityAudioDialog) {
        QualityDialog(
            title = "Audio quality",
            options = listOf("high" to "High (best available)", "medium" to "Medium", "low" to "Low (data saver)"),
            selected = audioQuality,
            onSelect = {
                audioQuality = it
                scope.launch { sl.settings.setAudioQuality(it) }
                showQualityAudioDialog = false
            },
            onDismiss = { showQualityAudioDialog = false },
        )
    }
}

@Composable
private fun QualityDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = selected == value, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProviderRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            label, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun settingsTfColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun UpdaterRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { UpdateChecker(context.applicationContext) }

    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !checking && !downloading) {
                checking = true; status = null; update = null
                scope.launch {
                    try {
                        val info = checker.fetchLatest(includeOlder = false)
                        update = info
                        status = if (info == null) "You're on the latest build."
                                 else "${info.title} available · ${formatBytes(info.sizeBytes)}"
                    } catch (e: Exception) {
                        status = "Check failed: ${e.message}"
                    } finally {
                        checking = false
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Check for updates", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                status ?: "Pulls from github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloading) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        when {
            checking || downloading -> CircularProgressIndicator(
                Modifier.size(20.dp), strokeWidth = 2.dp,
            )
            update?.isNewerThanInstalled == true -> Button(
                onClick = {
                    val info = update ?: return@Button
                    downloading = true; progress = 0f
                    scope.launch {
                        try {
                            val apk = checker.downloadApk(info) { progress = it }
                            checker.launchInstaller(apk)
                            status = "Launching installer…"
                        } catch (e: Exception) {
                            status = "Download failed: ${e.message}"
                        } finally {
                            downloading = false
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
            ) { Text("Install") }
        }
    }
}

