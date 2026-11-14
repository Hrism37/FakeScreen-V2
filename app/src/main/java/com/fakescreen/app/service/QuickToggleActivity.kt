package com.fakescreen.app.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog

/**
 * Invisible trampoline.
 *
 * Accessibility services may start activities even when a direct background
 * foreground-service start is restricted by the OEM. This activity has a
 * translucent theme, starts the session service and finishes immediately.
 */
class QuickToggleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FakeScreenService::class.java).apply {
                    action = AppConstants.ACTION_VOLUME_TOGGLE
                },
            )
        } catch (t: Throwable) {
            AppLog.e("Quick toggle trampoline failed", t)
            NotificationHelper.notifyAlert(
                this,
                "音量键快捷操作不可用",
                "系统阻止了后台启动，请先打开 FakeScreen 再试。",
            )
        }
        finish()
    }
}
