package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.plugins.CloudStreamPlugin
import com.aioweb.app.data.plugins.CloudStreamRepo
import com.aioweb.app.data.plugins.InstalledPlugin
import com.aioweb.app.data.plugins.PluginRepository
import com.aioweb.app.data.stremio.InstalledStremioAddon
import com.aioweb.app.data.stremio.StremioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PluginsState(
    val repos: List<CloudStreamRepo> = emptyList(),
    val installed: List<InstalledPlugin> = emptyList(),
    val pluginsByRepo: Map<String, List<CloudStreamPlugin>> = emptyMap(),
    val loadingRepoIds: Set<String> = emptySet(),
    val installingNames: Set<String> = emptySet(),
    val stremioAddons: List<InstalledStremioAddon> = emptyList(),
    val addingStremio: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class PluginsViewModel(
    private val repo: PluginRepository,
    private val stremio: StremioRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PluginsState())
    val state: StateFlow<PluginsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.repos, repo.installed, stremio.addons) { r, i, s -> Triple(r, i, s) }
                .collect { (r, i, s) -> _state.update { it.copy(repos = r, installed = i, stremioAddons = s) } }
        }
    }

    fun addRepo(name: String, url: String) = viewModelScope.launch {
        if (name.isBlank() || url.isBlank()) {
            _state.update { it.copy(error = "Name and URL are required") }
            return@launch
        }
        try {
            repo.addRepo(name.trim(), url.trim())
            _state.update { it.copy(info = "Repo '$name' added", error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed: ${e.message}") }
        }
    }

    fun removeRepo(id: String) = viewModelScope.launch {
        repo.removeRepo(id)
        _state.update { s -> s.copy(pluginsByRepo = s.pluginsByRepo - id) }
    }

    fun fetchRepo(r: CloudStreamRepo) = viewModelScope.launch {
        _state.update { it.copy(loadingRepoIds = it.loadingRepoIds + r.id, error = null) }
        try {
            val plugins = repo.fetchPluginList(r.url)
            _state.update {
                it.copy(
                    pluginsByRepo = it.pluginsByRepo + (r.id to plugins),
                    loadingRepoIds = it.loadingRepoIds - r.id,
                    info = "Loaded ${plugins.size} plugins from ${r.name}",
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    loadingRepoIds = it.loadingRepoIds - r.id,
                    error = "Fetch failed: ${e.message}",
                )
            }
        }
    }

    fun install(repoModel: CloudStreamRepo, plugin: CloudStreamPlugin) = viewModelScope.launch {
        _state.update { it.copy(installingNames = it.installingNames + plugin.name) }
        try {
            repo.installPlugin(repoModel, plugin)
            _state.update {
                it.copy(
                    installingNames = it.installingNames - plugin.name,
                    info = "Installed: ${plugin.name}",
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    installingNames = it.installingNames - plugin.name,
                    error = "Install failed: ${e.message}",
                )
            }
        }
    }

    fun uninstall(p: InstalledPlugin) = viewModelScope.launch {
        repo.uninstallPlugin(p.internalName, p.sourceRepoId)
    }

    /** Add a Stremio addon by manifest URL (or any URL — we'll auto-append /manifest.json). */
    fun addStremioAddon(url: String) = viewModelScope.launch {
        if (url.isBlank()) {
            _state.update { it.copy(error = "Manifest URL is required") }
            return@launch
        }
        _state.update { it.copy(addingStremio = true, error = null) }
        try {
            val a = stremio.addAddon(url.trim())
            _state.update { it.copy(addingStremio = false, info = "Stremio addon added: ${a.name}") }
        } catch (e: Exception) {
            _state.update { it.copy(addingStremio = false, error = "Stremio: ${e.message}") }
        }
    }

    fun removeStremioAddon(manifestUrl: String) = viewModelScope.launch {
        stremio.removeAddon(manifestUrl)
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, info = null) }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PluginsViewModel(
                    PluginRepository(context.applicationContext),
                    StremioRepository(context.applicationContext),
                ) as T
            }
        }
    }
}
