package com.aioweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aioweb.app.data.plugins.CloudStreamPlugin
import com.aioweb.app.data.plugins.CloudStreamRepo
import com.aioweb.app.data.plugins.InstalledPlugin
import com.aioweb.app.ui.viewmodel.PluginsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: PluginsViewModel = viewModel(factory = PluginsViewModel.factory(context))
    val state by vm.state.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("CloudStream Plugins") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, "Add repo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.info?.let {
                item {
                    StatusBanner(it, isError = false) { vm.clearMessages() }
                }
            }
            state.error?.let {
                item {
                    StatusBanner(it, isError = true) { vm.clearMessages() }
                }
            }

            if (state.installed.isNotEmpty()) {
                item {
                    SectionLabel("Installed (${state.installed.size})")
                }
                items(state.installed, key = { it.internalName }) { p ->
                    InstalledRow(p, onUninstall = { vm.uninstall(p) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item { SectionLabel("Repositories") }

            items(state.repos, key = { it.id }) { repo ->
                RepoCard(
                    repo = repo,
                    plugins = state.pluginsByRepo[repo.id].orEmpty(),
                    isLoading = state.loadingRepoIds.contains(repo.id),
                    installingNames = state.installingNames,
                    installedNames = state.installed.map { it.internalName }.toSet(),
                    onFetch = { vm.fetchRepo(repo) },
                    onRemove = { vm.removeRepo(repo.id) },
                    onInstall = { vm.install(repo, it) },
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add CloudStream Repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addName, onValueChange = { addName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = addUrl, onValueChange = { addUrl = it },
                        label = { Text("repo.json URL") },
                        placeholder = { Text("https://example.com/repo.json") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Tip: paste a CloudStream `repo.json` URL — e.g., the Cloudstream community extensions list, or any community fork.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addRepo(addName, addUrl)
                    addName = ""; addUrl = ""; showAdd = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatusBanner(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
            .clickable(onClick = onDismiss)
            .padding(12.dp),
    ) {
        Text(
            text,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InstalledRow(p: InstalledPlugin, onUninstall: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Icon(
            Icons.Default.Extension, null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("v${p.version} · ${p.internalName}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onUninstall) {
            Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RepoCard(
    repo: CloudStreamRepo,
    plugins: List<CloudStreamPlugin>,
    isLoading: Boolean,
    installingNames: Set<String>,
    installedNames: Set<String>,
    onFetch: () -> Unit,
    onRemove: () -> Unit,
    onInstall: (CloudStreamPlugin) -> Unit,
    onUninstall: (CloudStreamPlugin) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(repo.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(repo.url, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onFetch) {
                    Icon(Icons.Default.Refresh, "Fetch")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Remove repo", tint = MaterialTheme.colorScheme.error)
            }
        }
        if (plugins.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${plugins.size} plugins · tap to ${if (expanded) "collapse" else "expand"}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { expanded = !expanded }
            )
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                plugins.forEach { plugin ->
                    val internalKey = plugin.internalName ?: plugin.name
                    PluginRow(
                        plugin = plugin,
                        installing = plugin.name in installingNames,
                        installed = internalKey in installedNames,
                        onInstall = { onInstall(plugin) },
                        onUninstall = { onUninstall(plugin) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: CloudStreamPlugin,
    installing: Boolean,
    installed: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(plugin.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            plugin.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("v${plugin.version} · ${plugin.language ?: "?"} · ${plugin.tvTypes?.joinToString() ?: ""}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
        when {
            installing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            installed -> IconButton(onClick = onUninstall) {
                Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.error)
            }
            else -> IconButton(onClick = onInstall) {
                Icon(Icons.Default.CloudDownload, "Install", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
onClick = onInstall) {
                Icon(Icons.Default.CloudDownload, "Install", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
