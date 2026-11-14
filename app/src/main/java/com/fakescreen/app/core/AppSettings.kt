package com.fakescreen.app.core

import com.fakescreen.app.core.ScreenMode

/** User visible, persistently stored preferences. */
data class AppSettings(
    val defaultMode: ScreenMode = ScreenMode.PURE_BLACK,
    val maxSessionMinutes: Int = AppConstants.DEFAULT_MAX_SESSION_MINUTES,
    /** When true the safety timer is disabled entirely. */
    val noMaxSessionDuration: Boolean = false,
    /** Opt-in experimental silent-audio playback as an additional keep-awake hint. */
    val experimentalAudioKeepAwake: Boolean = false,
    /** Allow changing the global system brightness when window brightness is not enough. */
    val allowGlobalBrightnessFallback: Boolean = false,
    /** Restore brightness automatically when an old session is discovered after a crash/reboot. */
    val autoRestoreOnLaunch: Boolean = true,
    /** Volume Up + Down starts a session. */
    val volumeShortcutStartEnabled: Boolean = true,
    /** Volume Up + Down ends a session without a PIN. */
    val volumeShortcutStopEnabled: Boolean = true,
    val hasSeenSafetyNotice: Boolean = false,
)
