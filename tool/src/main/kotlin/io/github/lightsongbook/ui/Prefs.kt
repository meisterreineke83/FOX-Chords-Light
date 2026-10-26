package io.github.lightsongbook.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thelightphone.sdk.ui.LightThemeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Small settings stored in the tool's DataStore. */
class Prefs(private val store: DataStore<Preferences>) {

    val fontStep: Flow<Int> = store.data.map { (it[FONT_STEP] ?: DEFAULT_FONT_STEP).coerceIn(0, MAX_FONT_STEP) }
    val scrollSpeed: Flow<Int> = store.data.map { (it[SCROLL_SPEED] ?: DEFAULT_SCROLL_SPEED).coerceIn(1, MAX_SCROLL_SPEED) }

    suspend fun setFontStep(step: Int) {
        store.edit { it[FONT_STEP] = step.coerceIn(0, MAX_FONT_STEP) }
    }

    suspend fun setScrollSpeed(speed: Int) {
        store.edit { it[SCROLL_SPEED] = speed.coerceIn(1, MAX_SCROLL_SPEED) }
    }

    /** Applies the saved light/dark choice to the shared theme controller. */
    suspend fun applyTheme() {
        val light = store.data.first()[LIGHT_THEME] ?: false
        if (light) LightThemeController.setLightTheme() else LightThemeController.setDarkTheme()
    }

    suspend fun toggleTheme() {
        LightThemeController.toggle()
        val light = !LightThemeController.isDarkTheme
        store.edit { it[LIGHT_THEME] = light }
    }

    /**
     * Transposition the user picked on the phone. Stored together with the shift from the
     * backup, so a new backup with a different shift from the iPad wins.
     */
    suspend fun transposition(key: String, baseShift: Int): Int {
        val raw = store.data.first()[stringPreferencesKey("shift:$key")] ?: return baseShift
        val base = raw.substringBefore('|').toIntOrNull()
        val total = raw.substringAfter('|').toIntOrNull()
        return if (base == baseShift && total != null) total else baseShift
    }

    suspend fun setTransposition(key: String, baseShift: Int, total: Int) {
        val prefKey = stringPreferencesKey("shift:$key")
        store.edit {
            if (total == baseShift) it.remove(prefKey) else it[prefKey] = "$baseShift|$total"
        }
    }

    /** Recording chosen by hand for a song: null = pick automatically by name, "" = none. */
    suspend fun audioAssignment(songId: String): String? =
        store.data.first()[stringPreferencesKey("audio:$songId")]

    /** All recordings chosen by hand, song id to file name ("" = none). */
    val audioAssignments: Flow<Map<String, String>> = store.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith("audio:") }
            .associate { it.key.name.removePrefix("audio:") to it.value.toString() }
    }

    suspend fun setAudioAssignment(songId: String, fileName: String?) {
        val key = stringPreferencesKey("audio:$songId")
        store.edit { if (fileName == null) it.remove(key) else it[key] = fileName }
    }

    companion object {
        const val MAX_FONT_STEP = 5
        const val DEFAULT_FONT_STEP = 2
        const val MAX_SCROLL_SPEED = 15
        const val DEFAULT_SCROLL_SPEED = 6

        private val FONT_STEP = intPreferencesKey("fontStep")
        private val SCROLL_SPEED = intPreferencesKey("scrollSpeed")
        private val LIGHT_THEME = booleanPreferencesKey("lightTheme")
    }
}
