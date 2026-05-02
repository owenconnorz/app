package com.aioweb.app.player.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

val Context.playerSettingsDataStore by preferencesDataStore("player_settings")

enum class TopBarStyle { A, B, C }

class PlayerSettingsManager(private val context: Context) {

    private val KEY_TOP_BAR = stringPreferencesKey("top_bar_style")

    val topBarStyleFlow: Flow<TopBarStyle> =
        context.playerSettingsDataStore.data.map { prefs ->
            when (prefs[KEY_TOP_BAR]) {
                "A" -> TopBarStyle.A
                "B" -> TopBarStyle.B
                "C" -> TopBarStyle.C
                else -> TopBarStyle.A
            }
        }

    suspend fun setTopBarStyle(style: TopBarStyle) {
        context.playerSettingsDataStore.edit { prefs ->
            prefs[KEY_TOP_BAR] = style.name
        }
    }
}