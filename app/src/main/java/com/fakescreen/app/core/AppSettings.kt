package com.fakescreen.app.core

import com.fakescreen.app.core.ScreenMode

/** User visible, persistently stored preferences. */
data class AppSettings(
    val defaultMode: ScreenMode = ScreenMode.PURE_BLACK,
    val maxSessionMinutes: Int = AppConstants.DEFAULT_MAX_SESSION_MINUTES,
    /** Opt-in experimental silent-audio playback as an additional keep-awake hint. */
    val experimentalAudioKeepAwake: Boolean = false,
    /** Allow changing the global system brightness when window brightness is not enough. */
    val allowGlobalBrightnessFallback: Boolean = false,
    /** Restore brightness automatically when an old session is discovered after a crash/reboot. */
    val autoRestoreOnLaunch: Boolean = true,
    /** Volume Up + Down toggles a session; the exit path intentionally bypasses the PIN. */
    val allowVolumeKeyShortcut: Boolean = true,
    val hasSeenSafetyNotice: Boolean = false,
)
