package com.fakescreen.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.fakescreen.app.appContainer
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.core.ScreenMode
import com.fakescreen.app.session.ScreenSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the overlay session.
 *
 * It intentionally does not poll, does not hold a partial wake lock and does
 * not try to defeat the system's process management. The session survives as
 * long as the platform allows; the persisted record makes recovery possible if
 * it does not.
 */
class FakeScreenService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: ScreenSessionManager? = null

    override fun onCreate() {
        super.onCreate()
        session = ScreenSessionManager(this, appContainer)
        appContainer.sessionState.setServiceRunning(true)
        AppLog.i("Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = session
        if (manager == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val mode = ScreenMode.fromName(intent?.getStringExtra(AppConstants.EXTRA_MODE))
        val action = intent?.action

        // Fulfil the foreground contract immediately, regardless of the action.
        promoteToForeground(mode)

        when (action) {
            AppConstants.ACTION_START_SESSION -> scope.launch {
                if (!manager.start(mode)) finishIfIdle()
            }

            AppConstants.ACTION_VOLUME_TOGGLE -> scope.launch {
                if (manager.isActive()) {
                    manager.stop(ScreenSessionManager.StopReason.VOLUME_SHORTCUT)
                } else {
                    val settings = appContainer.settingsRepository.settings.first()
                    if (!settings.allowVolumeKeyShortcut) {
                        finishIfIdle()
                        return@launch
                    }
                    if (!manager.start(settings.defaultMode)) {
                        NotificationHelper.notifyAlert(
                            this@FakeScreenService,
                            "无法开始模拟息屏",
                            appContainer.sessionState.lastError.value
                                ?: "请检查 PIN、悬浮窗权限和设备状态。",
                        )
                        finishIfIdle()
                    }
                }
            }

            AppConstants.ACTION_STOP_SESSION -> scope.launch {
                manager.stop(ScreenSessionManager.StopReason.SERVICE_COMMAND)
            }

            // Null intent: the system restarted a sticky service after a kill.
            else -> scope.launch {
                if (!manager.resumeExisting()) finishIfIdle()
            }
        }
        return START_STICKY
    }

    /**
     * Swiping the app away must not silently drop the simulated screen. The
     * session keeps running and can be ended through the overlay or the
     * notification.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLog.i("Task removed, session stays alive")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.cleanupOnDestroy()
        session?.release()
        session = null
        appContainer.sessionState.setServiceRunning(false)
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        AppLog.i("Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(mode: ScreenMode) {
        val state = appContainer.sessionState.state.value
        val shownMode = if (state.active) state.mode else mode
        val notification = NotificationHelper.buildSessionNotification(this, shownMode)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                AppConstants.SESSION_NOTIFICATION_ID,
                notification,
                type,
            )
        } catch (t: Throwable) {
            AppLog.e("startForeground failed", t)
        }
    }

    private fun finishIfIdle() {
        if (session?.isActive() == true) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
