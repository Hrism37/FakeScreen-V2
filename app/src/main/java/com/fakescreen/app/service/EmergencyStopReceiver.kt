package com.fakescreen.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.fakescreen.app.appContainer
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Deliberate emergency exit from the session notification.
 *
 * This is the documented way out for a user who forgot the PIN. It does not
 * require the PIN, so it is only reachable through the system notification
 * shade, which the black overlay cannot cover.
 */
class EmergencyStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppConstants.ACTION_EMERGENCY_FROM_NOTIFICATION) return

        val serviceIntent = Intent(context, FakeScreenService::class.java).apply {
            action = AppConstants.ACTION_EMERGENCY_STOP
        }
        val started = runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
            true
        }.getOrElse {
            AppLog.w("Emergency stop service start failed", it)
            false
        }

        if (started) {
            Toast.makeText(context, "已结束模拟息屏", Toast.LENGTH_SHORT).show()
        } else {
            // Fallback: at least restore brightness if the service cannot start.
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    context.appContainer.recoveryManager.reconcile("emergency")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
