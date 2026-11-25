package com.lastwave.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in switch for the full-screen lock-screen cover. It deliberately keeps
 * its own key and flow instead of joining [MiscSettings]: that mapping is
 * parsed on every DataStore write for every collector, and this flag is only
 * read by the playback service and the Appearance settings row.
 *
 * Off by default — the system lock screen keeps its normal media card until
 * the user explicitly asks for the full-screen cover.
 */
@Singleton
class LockScreenArtPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("lw_lock_screen_art_enabled")
    }

    val enabled: Flow<Boolean> = dataStore.data
        .recoverPreferences("LockScreenArtPreferences")
        .map { preferences -> preferences[Keys.ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.ENABLED] = enabled }
    }
}
