package com.fakescreen.app.data

import com.fakescreen.app.core.ScreenMode
import org.json.JSONObject
import java.util.UUID

/**
 * Everything that must survive a process death while a session is running.
 *
 * The record is written *before* the overlay is shown and before brightness is
 * changed, so a crash at any point still leaves enough information to restore
 * the device to a sane state.
 */
data class SessionRecord(
    val id: String,
    val mode: ScreenMode,
    val startedAt: Long,
    /** Raw Settings.System.SCREEN_BRIGHTNESS (0..255) captured before the session. */
    val originalBrightness: Int,
    /** Settings.System.SCREEN_BRIGHTNESS_MODE captured before the session. */
    val originalBrightnessMode: Int,
    /** Window-level brightness requested for the overlay window, null if untouched. */
    val appliedWindowBrightness: Float?,
    /** True only if FakeScreen really wrote the global system brightness. */
    val globalBrightnessApplied: Boolean,
    val appliedGlobalBrightness: Int,
    val appliedGlobalMode: Int,
    val recoveryAttempts: Int = 0,
) {
    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("id", id)
        put("mode", mode.storageKey)
        put("startedAt", startedAt)
        put("originalBrightness", originalBrightness)
        put("originalBrightnessMode", originalBrightnessMode)
        put("windowApplied", appliedWindowBrightness != null)
        put("windowBrightness", appliedWindowBrightness?.toDouble() ?: -1.0)
        put("globalApplied", globalBrightnessApplied)
        put("appliedGlobalBrightness", appliedGlobalBrightness)
        put("appliedGlobalMode", appliedGlobalMode)
        put("recoveryAttempts", recoveryAttempts)
    }.toString()

    fun ageMillis(now: Long = System.currentTimeMillis()): Long = (now - startedAt).coerceAtLeast(0L)

    companion object {
        const val VERSION = 1

        fun newSession(
            mode: ScreenMode,
            originalBrightness: Int,
            originalBrightnessMode: Int,
        ): SessionRecord = SessionRecord(
            id = UUID.randomUUID().toString(),
            mode = mode,
            startedAt = System.currentTimeMillis(),
            originalBrightness = originalBrightness,
            originalBrightnessMode = originalBrightnessMode,
            appliedWindowBrightness = null,
            globalBrightnessApplied = false,
            appliedGlobalBrightness = -1,
            appliedGlobalMode = -1,
        )

        fun fromJson(raw: String?): SessionRecord? {
            if (raw.isNullOrBlank()) return null
            return try {
                val json = JSONObject(raw)
                if (json.optInt("v", 0) != VERSION) return null
                val windowApplied = json.optBoolean("windowApplied", false)
                SessionRecord(
                    id = json.getString("id"),
                    mode = ScreenMode.fromKey(json.optString("mode")),
                    startedAt = json.getLong("startedAt"),
                    originalBrightness = json.optInt("originalBrightness", -1),
                    originalBrightnessMode = json.optInt("originalBrightnessMode", -1),
                    appliedWindowBrightness = if (windowApplied) {
                        json.optDouble("windowBrightness", -1.0).toFloat().takeIf { it >= 0f }
                    } else {
                        null
                    },
                    globalBrightnessApplied = json.optBoolean("globalApplied", false),
                    appliedGlobalBrightness = json.optInt("appliedGlobalBrightness", -1),
                    appliedGlobalMode = json.optInt("appliedGlobalMode", -1),
                    recoveryAttempts = json.optInt("recoveryAttempts", 0),
                )
            } catch (t: Throwable) {
                null
            }
        }
    }
}
