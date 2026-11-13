package com.fakescreen.app.shizuku

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.fakescreen.app.core.AppLog
import java.util.concurrent.TimeUnit

/**
 * Runs in the Shizuku-managed shell process (uid 2000), not in the app process.
 *
 * It is intentionally tiny: the only privileged action is "press the power
 * key". No shell strings are accepted from the app, so a compromised app
 * process cannot turn this into a general purpose root/shell bridge.
 */
class ScreenOffUserService : Service() {

    private val binder = object : IScreenOffService.Stub() {
        override fun destroy() {
            stopSelf()
        }

        override fun turnScreenOff(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("/system/bin/input", "keyevent", "26")
                )
                val finished = process.waitFor(5, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroy()
                    false
                } else {
                    process.exitValue() == 0
                }
            } catch (t: Throwable) {
                AppLog.w("Shizuku user service could not press power", t)
                false
            }
        }

        override fun shellUid(): Int = Process.myUid()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
