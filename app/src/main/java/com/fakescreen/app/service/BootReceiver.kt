package com.fakescreen.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fakescreen.app.appContainer
import com.fakescreen.app.core.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Checks for a brightness value that FakeScreen changed before a reboot and
 * restores it when that is still safe.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.appContainer.recoveryManager.reconcile("boot:$action")
            } catch (t: Throwable) {
                AppLog.e("Boot recovery failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
