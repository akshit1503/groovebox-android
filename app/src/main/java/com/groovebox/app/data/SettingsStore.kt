package com.groovebox.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "groovebox_settings")

/** Mirrors the desktop app's EQ preset gain curves (10 bands, 60Hz–16kHz). */
object EqPresets {
    val FREQS = intArrayOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
    val PRESETS: Map<String, IntArray> = mapOf(
        "Flat" to intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        "Bass" to intArrayOf(7, 6, 5, 3, 1, 0, 0, 0, 0, 0),
        "Vocal" to intArrayOf(-2, -1, 0, 2, 4, 4, 3, 1, 0, 0),
        "Treble" to intArrayOf(0, 0, 0, 0, 0, 2, 4, 5, 6, 7),
        "Jazz" to intArrayOf(4, 3, 1, 1, -1, -1, 0, 2, 3, 4),
        "Rock" to intArrayOf(5, 4, 2, 0, -1, 0, 2, 3, 4, 5)
    )
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val VOLUME = floatPreferencesKey("volume")
        val PLAYBACK_RATE = floatPreferencesKey("playback_rate")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = booleanPreferencesKey("repeat")
        val AUTOPLAY_ON_LAUNCH = booleanPreferencesKey("autoplay_on_launch")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_CUSTOM_PREFIX = "eq_band_"
        val LAST_TRACK_URI = stringPreferencesKey("last_track_uri")
        val LAST_TRACK_POS = longPreferencesKey("last_track_pos")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val THEME_ID = stringPreferencesKey("theme_id")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val VISUALIZER_MODE = stringPreferencesKey("visualizer_mode")
        val RESUME_ON_HEADSET = booleanPreferencesKey("resume_on_headset")
        val RESUME_ON_BLUETOOTH = booleanPreferencesKey("resume_on_bluetooth")
        val PAUSE_RESUME_ON_VOLUME = booleanPreferencesKey("pause_resume_on_volume")
        val VISUALIZER_ENABLED = booleanPreferencesKey("visualizer_enabled")
        val VISUALIZER_COLOR_HUE = floatPreferencesKey("visualizer_color_hue")
        val FULL_PLAYER_LIGHT_BG = booleanPreferencesKey("full_player_light_bg")
    }

    val volume: Flow<Float> = context.dataStore.data.map { it[Keys.VOLUME] ?: 0.8f }
    val playbackRate: Flow<Float> = context.dataStore.data.map { it[Keys.PLAYBACK_RATE] ?: 1f }
    val shuffle: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHUFFLE] ?: false }
    val repeat: Flow<Boolean> = context.dataStore.data.map { it[Keys.REPEAT] ?: false }
    val autoplayOnLaunch: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTOPLAY_ON_LAUNCH] ?: false }
    val eqPreset: Flow<String> = context.dataStore.data.map { it[Keys.EQ_PRESET] ?: "Flat" }
    val sortMode: Flow<String> = context.dataStore.data.map { it[Keys.SORT_MODE] ?: "name" }
    val themeId: Flow<String> = context.dataStore.data.map { it[Keys.THEME_ID] ?: "midnight_violet" }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }
    val visualizerMode: Flow<String> = context.dataStore.data.map { it[Keys.VISUALIZER_MODE] ?: "bars" }
    val resumeOnHeadset: Flow<Boolean> = context.dataStore.data.map { it[Keys.RESUME_ON_HEADSET] ?: false }
    val resumeOnBluetooth: Flow<Boolean> = context.dataStore.data.map { it[Keys.RESUME_ON_BLUETOOTH] ?: false }
    val pauseResumeOnVolume: Flow<Boolean> = context.dataStore.data.map { it[Keys.PAUSE_RESUME_ON_VOLUME] ?: false }
    val visualizerEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.VISUALIZER_ENABLED] ?: true }
    // -1 means "use the app theme's colors" (the original purple/pink/white gradient)
    // rather than a single custom hue.
    val visualizerColorHue: Flow<Float> = context.dataStore.data.map { it[Keys.VISUALIZER_COLOR_HUE] ?: -1f }
    val fullPlayerLightBackground: Flow<Boolean> = context.dataStore.data.map { it[Keys.FULL_PLAYER_LIGHT_BG] ?: false }

    suspend fun setVolume(v: Float) = context.dataStore.edit { it[Keys.VOLUME] = v }
    suspend fun setPlaybackRate(v: Float) = context.dataStore.edit { it[Keys.PLAYBACK_RATE] = v }
    suspend fun setShuffle(v: Boolean) = context.dataStore.edit { it[Keys.SHUFFLE] = v }
    suspend fun setRepeat(v: Boolean) = context.dataStore.edit { it[Keys.REPEAT] = v }
    suspend fun setAutoplayOnLaunch(v: Boolean) = context.dataStore.edit { it[Keys.AUTOPLAY_ON_LAUNCH] = v }
    suspend fun setEqPreset(name: String) = context.dataStore.edit { it[Keys.EQ_PRESET] = name }
    suspend fun setSortMode(v: String) = context.dataStore.edit { it[Keys.SORT_MODE] = v }
    suspend fun setThemeId(id: String) = context.dataStore.edit { it[Keys.THEME_ID] = id }
    suspend fun setOnboardingComplete(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = v }
    suspend fun setVisualizerMode(id: String) = context.dataStore.edit { it[Keys.VISUALIZER_MODE] = id }
    suspend fun setResumeOnHeadset(v: Boolean) = context.dataStore.edit { it[Keys.RESUME_ON_HEADSET] = v }
    suspend fun setResumeOnBluetooth(v: Boolean) = context.dataStore.edit { it[Keys.RESUME_ON_BLUETOOTH] = v }
    suspend fun setPauseResumeOnVolume(v: Boolean) = context.dataStore.edit { it[Keys.PAUSE_RESUME_ON_VOLUME] = v }
    suspend fun setVisualizerEnabled(v: Boolean) = context.dataStore.edit { it[Keys.VISUALIZER_ENABLED] = v }
    suspend fun setVisualizerColorHue(v: Float) = context.dataStore.edit { it[Keys.VISUALIZER_COLOR_HUE] = v }
    suspend fun setFullPlayerLightBackground(v: Boolean) = context.dataStore.edit { it[Keys.FULL_PLAYER_LIGHT_BG] = v }

    suspend fun setEqCustomBands(bands: IntArray) = context.dataStore.edit { prefs ->
        bands.forEachIndexed { i, v -> prefs[intPreferencesKey(Keys.EQ_CUSTOM_PREFIX + i)] = v }
    }
    suspend fun getEqCustomBands(): IntArray {
        val prefs = context.dataStore.data.first()
        return IntArray(EqPresets.FREQS.size) { i -> prefs[intPreferencesKey(Keys.EQ_CUSTOM_PREFIX + i)] ?: 0 }
    }

    suspend fun setLastPlayed(uri: String?, positionMs: Long) = context.dataStore.edit {
        if (uri == null) { it.remove(Keys.LAST_TRACK_URI) } else { it[Keys.LAST_TRACK_URI] = uri }
        it[Keys.LAST_TRACK_POS] = positionMs
    }
    suspend fun getLastPlayed(): Pair<String, Long>? {
        val prefs = context.dataStore.data.first()
        val uri = prefs[Keys.LAST_TRACK_URI] ?: return null
        val pos = prefs[Keys.LAST_TRACK_POS] ?: 0L
        return uri to pos
    }
}
