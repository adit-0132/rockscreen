package io.github.adit0132.rockscreen.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// A corrupt settings file is replaced with defaults instead of crashing the app.
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** Settings data that falls back to defaults on read errors. */
fun Context.safePrefs(): Flow<Preferences> =
    dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

object PrefKeys {
    val LOCK_SCREEN_ENABLED = booleanPreferencesKey("lock_screen_enabled")
}

suspend fun Context.setLockScreenEnabled(enabled: Boolean) {
    dataStore.edit { it[PrefKeys.LOCK_SCREEN_ENABLED] = enabled }
}

fun Context.lockScreenEnabledFlow(): Flow<Boolean> =
    safePrefs().map { it[PrefKeys.LOCK_SCREEN_ENABLED] ?: true }
