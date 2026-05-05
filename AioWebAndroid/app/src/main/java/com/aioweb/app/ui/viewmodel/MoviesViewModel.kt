package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.data.collections.HomeCollections
import com.aioweb.app.data.library.LibraryDb
import com.aioweb.app.data.library.WatchProgressEntity
import com.aioweb.app.data.plugins.InstalledPlugin
import com.aioweb.app.data.plugins.PluginRepository
import com.aioweb.app.data.plugins.PluginRuntime
import com.aioweb.app.data.stremio.InstalledStremioAddon
import com.aioweb.app.data.stremio.StremioMetaPreview
import com.aioweb.app.data.stremio.StremioRepository
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PluginSection(
    val title: String,
    val items: List<SearchResponse>,
)

data class StremioSection(
    val title: String,
    val items: List<St