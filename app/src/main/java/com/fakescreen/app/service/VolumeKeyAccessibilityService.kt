package com.fakescreen.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.fakescreen.app.appContainer
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.core.VolumeComboDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Optional global listener for the Volume Up + Volume Down chord.
 *
 * It does **not** read window content (see res/xml/volume_key_accessibility.xml);
 * it only requests filtered key events, and only reacts to the two volume keys.
 * The chord toggles a simulated screen-off session, including when FakeScreen is
 * in the background.
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = VolumeComboDetector()

    @Volatile
    private var shortcutEnabled = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        scope.launch {
            appContainer.settingsRepository.settings.collectLatest {
                shortcutEnabled = it.allowVolumeKeyShortcut
            }
        }
        AppLog.i("Volume key accessibility service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!shortcutEnabled || event.repeatCount > 0) return false
        val triggered = detector.onKey(event.keyCode, event.action, event.eventTime)
        if (!triggered) return false

        val intent = Intent(this, FakeScreenService::class.java).apply {
            action = AppConstants.ACTION_VOLUME_TOGGLE
        }
        return try {
            ContextCompat.startForegroundService(this, intent)
            true
        } catch (t: Throwable) {
            AppLog.e("Unable to toggle session from accessibility service", t)
            NotificationHelper.notifyAlert(
                this,
                "音量键快捷操作不可用",
                "系统限制了从后台启动前台服务，请先打开 FakeScreen 再试。",
            )
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
