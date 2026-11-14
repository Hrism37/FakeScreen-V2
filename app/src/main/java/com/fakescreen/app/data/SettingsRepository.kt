package com.fakescreen.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppSettings
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.core.ScreenMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fakescreen_settings"
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                AppLog.e("Failed to read settings", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.DEFAULT_MODE] = next.defaultMode.storageKey
            prefs[Keys.MAX_SESSION_MINUTES] = next.maxSessionMinutes.coerceAtLeast(0)
            prefs[Keys.NO_MAX_SESSION] = next.noMaxSessionDuration
            prefs[Keys.EXPERIMENTAL_AUDIO] = next.experimentalAudioKeepAwake
            prefs[Keys.ALLOW_GLOBAL_BRIGHTNESS] = next.allowGlobalBrightnessFallback
            prefs[Keys.AUTO_RESTORE] = next.autoRestoreOnLaunch
            prefs[Keys.VOLUME_START] = next.volumeShortcutStartEnabled
            prefs[Keys.VOLUME_STOP] = next.volumeShortcutStopEnabled
            prefs[Keys.SEEN_SAFETY_NOTICE] = next.hasSeenSafetyNotice
        }
    }

    suspend fun setDefaultMode(mode: ScreenMode) = update { it.copy(defaultMode = mode) }

    suspend fun setMaxSessionMinutes(minutes: Int) = update {
        it.copy(maxSessionMinutes = minutes.coerceAtLeast(0))
    }

    suspend fun setNoMaxSessionDuration(enabled: Boolean) = update {
        it.copy(noMaxSessionDuration = enabled)
    }

    suspend fun setExperimentalAudio(enabled: Boolean) = update {
        it.copy(experimentalAudioKeepAwake = enabled)
    }

    suspend fun setAllowGlobalBrightness(enabled: Boolean) = update {
        it.copy(allowGlobalBrightnessFallback = enabled)
    }

    suspend fun setAutoRestore(enabled: Boolean) = update { it.copy(autoRestoreOnLaunch = enabled) }

    suspend fun setVolumeShortcutStart(enabled: Boolean) = update {
        it.copy(volumeShortcutStartEnabled = enabled)
    }

    suspend fun setVolumeShortcutStop(enabled: Boolean) = update {
        it.copy(volumeShortcutStopEnabled = enabled)
    }

    suspend fun markSafetyNoticeSeen() = update { it.copy(hasSeenSafetyNotice = true) }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        defaultMode = ScreenMode.fromKey(this[Keys.DEFAULT_MODE]),
        maxSessionMinutes = (this[Keys.MAX_SESSION_MINUTES]
            ?: AppConstants.DEFAULT_MAX_SESSION_MINUTES).coerceAtLeast(0),
        noMaxSessionDuration = this[Keys.NO_MAX_SESSION] ?: false,
        experimentalAudioKeepAwake = this[Keys.EXPERIMENTAL_AUDIO] ?: false,
        allowGlobalBrightnessFallback = this[Keys.ALLOW_GLOBAL_BRIGHTNESS] ?: false,
        autoRestoreOnLaunch = this[Keys.AUTO_RESTORE] ?: true,
        volumeShortcutStartEnabled = this[Keys.VOLUME_START]
            ?: this[Keys.LEGACY_VOLUME_SHORTCUT] ?: true,
        volumeShortcutStopEnabled = this[Keys.VOLUME_STOP]
            ?: this[Keys.LEGACY_VOLUME_SHORTCUT] ?: true,
        hasSeenSafetyNotice = this[Keys.SEEN_SAFETY_NOTICE] ?: false,
    )

    private object Keys {
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val MAX_SESSION_MINUTES = intPreferencesKey("max_session_minutes")
        val EXPERIMENTAL_AUDIO = booleanPreferencesKey("experimental_audio_keep_awake")
        val ALLOW_GLOBAL_BRIGHTNESS = booleanPreferencesKey("allow_global_brightness")
        val AUTO_RESTORE = booleanPreferencesKey("auto_restore_on_launch")
        val NO_MAX_SESSION = booleanPreferencesKey("no_max_session_duration")
        val VOLUME_START = booleanPreferencesKey("volume_shortcut_start")
        val VOLUME_STOP = booleanPreferencesKey("volume_shortcut_stop")
        /** Read-only migration from 2.1.0. */
        val LEGACY_VOLUME_SHORTCUT = booleanPreferencesKey("allow_volume_key_shortcut")
        val SEEN_SAFETY_NOTICE = booleanPreferencesKey("seen_safety_notice")
    }
}
