package com.fakescreen.app.brightness

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.fakescreen.app.core.AppLog
import kotlin.math.roundToInt

/**
 * Brightness capture / apply / restore.
 *
 * Priority order used by the session:
 *   1. window level override (no permission, automatically reverted with the window);
 *   2. global Settings.System value (only with explicit WRITE_SETTINGS permission
 *      and only when the user enabled the fallback switch).
 *
 * A value of 0 is never written to the global setting: on several OEM skins 0
 * means "screen off" and would leave the device in an unrecoverable state.
 */
class BrightnessManager(context: Context) {

    private val appContext = context.applicationContext

    data class Snapshot(val raw: Int, val mode: Int) {
        val isKnown: Boolean get() = raw in 0..255
        val percent: Int
            get() = if (!isKnown) -1 else (raw * 100f / 255f).roundToInt().coerceIn(0, 100)
        val modeLabel: String
            get() = when (mode) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> "自动"
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL -> "手动"
                else -> "未知"
            }
    }

    fun readSnapshot(): Snapshot = try {
        Snapshot(
            raw = Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                -1,
            ),
            mode = Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                -1,
            ),
        )
    } catch (t: Throwable) {
        AppLog.w("Unable to read system brightness", t)
        Snapshot(-1, -1)
    }

    fun canWriteGlobal(): Boolean = runCatching { Settings.System.canWrite(appContext) }
        .getOrDefault(false)

    fun targetGlobalRaw(originalRaw: Int): Int {
        val base = originalRaw.coerceIn(1, 255)
        return base.coerceAtMost(GLOBAL_MIN_RAW).coerceAtLeast(1)
    }

    /**
     * Writes the global brightness and switches the system to manual mode.
     * Returns true only if both writes reported success.
     */
    fun applyGlobal(raw: Int): Boolean {
        if (!canWriteGlobal()) return false
        val value = raw.coerceIn(1, 255)
        return try {
            val resolver = appContext.contentResolver
            val modeOk = Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            val valueOk = Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value,
            )
            AppLog.i("Global brightness applied: $value (modeOk=$modeOk valueOk=$valueOk)")
            modeOk && valueOk
        } catch (t: Throwable) {
            AppLog.w("Unable to apply global brightness", t)
            false
        }
    }

    /**
     * Restores the captured brightness. Unknown values (-1) are skipped so the
     * app never invents a brightness the user never had.
     */
    fun restoreGlobal(snapshot: Snapshot): Boolean {
        if (!canWriteGlobal()) return false
        var allOk = true
        try {
            val resolver = appContext.contentResolver
            if (snapshot.raw in 1..255) {
                allOk = Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    snapshot.raw,
                ) && allOk
            }
            if (snapshot.mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC ||
                snapshot.mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) {
                allOk = Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    snapshot.mode,
                ) && allOk
            }
            AppLog.i("Global brightness restored: raw=${snapshot.raw} mode=${snapshot.mode} ok=$allOk")
        } catch (t: Throwable) {
            AppLog.w("Unable to restore global brightness", t)
            allOk = false
        }
        return allOk
    }

    fun restoreGlobal(raw: Int, mode: Int): Boolean = restoreGlobal(Snapshot(raw, mode))

    fun openWriteSettingsPermission(context: Context = appContext) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    companion object {
        /** Lowest brightness used at window level. Never 0.0 (some panels treat it as off). */
        const val WINDOW_MIN_BRIGHTNESS = 0.01f

        /** Lowest raw value written to Settings.System (0..255 scale). Never 0. */
        const val GLOBAL_MIN_RAW = 4
    }
}
