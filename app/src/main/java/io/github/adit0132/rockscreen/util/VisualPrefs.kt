package io.github.adit0132.rockscreen.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class VisualPrefs(
    val blobIntensity: Float = 0.5f,        // 0.1 to 0.8
    val activeLyricSize: Float = 26f,       // 20f to 32f (sp)
    val focusIntensity: Float = 0.5f,       // 0.0 to 1.0, controls opacity cascade
    val lyricFont: LyricFont = LyricFont.DM_SANS,
    val showClock: Boolean = true
)

enum class LyricFont { DM_SANS, SPACE_GROTESK, PLAYFAIR_DISPLAY }

private object VisualPrefKeys {
    val BLOB_INTENSITY = floatPreferencesKey("blob_intensity")
    val ACTIVE_LYRIC_SIZE = floatPreferencesKey("active_lyric_size")
    val FOCUS_INTENSITY = floatPreferencesKey("focus_intensity")
    val LYRIC_FONT = stringPreferencesKey("lyric_font")
    val SHOW_CLOCK = booleanPreferencesKey("show_clock")
}

fun Context.visualPrefsFlow(): Flow<VisualPrefs> =
    safePrefs().map { prefs ->
        VisualPrefs(
            blobIntensity = prefs[VisualPrefKeys.BLOB_INTENSITY] ?: 0.5f,
            activeLyricSize = prefs[VisualPrefKeys.ACTIVE_LYRIC_SIZE] ?: 26f,
            focusIntensity = prefs[VisualPrefKeys.FOCUS_INTENSITY] ?: 0.5f,
            lyricFont = try {
                LyricFont.valueOf(prefs[VisualPrefKeys.LYRIC_FONT] ?: "DM_SANS")
            } catch (_: Exception) {
                LyricFont.DM_SANS
            },
            showClock = prefs[VisualPrefKeys.SHOW_CLOCK] ?: true
        )
    }

suspend fun Context.saveVisualPrefs(vp: VisualPrefs) {
    dataStore.edit { prefs ->
        prefs[VisualPrefKeys.BLOB_INTENSITY] = vp.blobIntensity
        prefs[VisualPrefKeys.ACTIVE_LYRIC_SIZE] = vp.activeLyricSize
        prefs[VisualPrefKeys.FOCUS_INTENSITY] = vp.focusIntensity
        prefs[VisualPrefKeys.LYRIC_FONT] = vp.lyricFont.name
        prefs[VisualPrefKeys.SHOW_CLOCK] = vp.showClock
    }
}
