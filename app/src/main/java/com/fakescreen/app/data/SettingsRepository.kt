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
            prefs[Keys.MAX_SESSION_MINUTES] = next.maxSessionMinutes.coerceIn(
                AppConstants.MIN_SESSION_MINUTES,
                AppConstants.MAX_SESSION_MINUTES,
            )
            prefs[Keys.EXPERIMENTAL_AUDIO] = next.experimentalAudioKeepAwake
            prefs[Keys.ALLOW_GLOBAL_BRIGHTNESS] = next.allowGlobalBrightnessFallback
            prefs[Keys.AUTO_RESTORE] = next.autoRestoreOnLaunch
            prefs[Keys.SEEN_SAFETY_NOTICE] = next.hasSeenSafetyNotice
        }
    }

    suspend fun setDefaultMode(mode: ScreenMode) = update { it.copy(defaultMode = mode) }

    suspend fun setMaxSessionMinutes(minutes: Int) = update {
        it.copy(
            maxSessionMinutes = minutes.coerceIn(
                AppConstants.MIN_SESSION_MINUTES,
                AppConstants.MAX_SESSION_MINUTES,
            )
        )
    }

    suspend fun setExperimentalAudio(enabled: Boolean) = update {
        it.copy(experimentalAudioKeepAwake = enabled)
    }

    suspend fun setAllowGlobalBrightness(enabled: Boolean) = update {
        it.copy(allowGlobalBrightnessFallback = enabled)
    }

    suspend fun setAutoRestore(enabled: Boolean) = update { it.copy(autoRestoreOnLaunch = enabled) }

    suspend fun markSafetyNoticeSeen() = update { it.copy(hasSeenSafetyNotice = true) }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        defaultMode = ScreenMode.fromKey(this[Keys.DEFAULT_MODE]),
        maxSessionMinutes = (this[Keys.MAX_SESSION_MINUTES]
            ?: AppConstants.DEFAULT_MAX_SESSION_MINUTES).coerceIn(
            AppConstants.MIN_SESSION_MINUTES,
            AppConstants.MAX_SESSION_MINUTES,
        ),
        experimentalAudioKeepAwake = this[Keys.EXPERIMENTAL_AUDIO] ?: false,
        allowGlobalBrightnessFallback = this[Keys.ALLOW_GLOBAL_BRIGHTNESS] ?: false,
        autoRestoreOnLaunch = this[Keys.AUTO_RESTORE] ?: true,
        hasSeenSafetyNotice = this[Keys.SEEN_SAFETY_NOTICE] ?: false,
    )

    private object Keys {
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val MAX_SESSION_MINUTES = intPreferencesKey("max_session_minutes")
        val EXPERIMENTAL_AUDIO = booleanPreferencesKey("experimental_audio_keep_awake")
        val ALLOW_GLOBAL_BRIGHTNESS = booleanPreferencesKey("allow_global_brightness")
        val AUTO_RESTORE = booleanPreferencesKey("auto_restore_on_launch")
        val SEEN_SAFETY_NOTICE = booleanPreferencesKey("seen_safety_notice")
    }
}
