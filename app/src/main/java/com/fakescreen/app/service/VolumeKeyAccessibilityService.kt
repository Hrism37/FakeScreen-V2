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
    private var startEnabled = true

    @Volatile
    private var stopEnabled = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Do NOT replace serviceInfo with a new instance: that drops the
        // canRequestFilterKeyEvents capability declared in XML and the service
        // silently stops receiving volume keys. Mutate the existing object.
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        scope.launch {
            appContainer.settingsRepository.settings.collectLatest {
                startEnabled = it.volumeShortcutStartEnabled
                stopEnabled = it.volumeShortcutStopEnabled
            }
        }
        AppLog.i("Volume key accessibility service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            return false
        }

        val triggered = detector.onKey(event.keyCode, event.action, event.eventTime)
        if (!triggered) return false

        val sessionActive = appContainer.sessionState.state.value.active
        val allowed = if (sessionActive) stopEnabled else startEnabled
        if (!allowed) {
            AppLog.i("Volume chord ignored (active=$sessionActive, allowed=false)")
            return false
        }

        AppLog.i("Volume chord detected (active=$sessionActive), toggling")
        return launchToggle()
    }

    private fun launchToggle(): Boolean {
        val serviceIntent = Intent(this, FakeScreenService::class.java).apply {
            action = AppConstants.ACTION_VOLUME_TOGGLE
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            return true
        } catch (t: Throwable) {
            AppLog.w("Foreground service start blocked, trying trampoline activity", t)
        }

        // Accessibility services are allowed to start activities. The
        // trampoline has no UI and immediately starts the session service,
        // so the shortcut still works when background FGS start is restricted.
        return try {
            startActivity(
                Intent(this, QuickToggleActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            true
        } catch (t: Throwable) {
            AppLog.e("Unable to toggle session from accessibility service", t)
            NotificationHelper.notifyAlert(
                this,
                "音量键快捷操作不可用",
                "系统阻止了后台启动，请先打开 FakeScreen 再试。",
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
