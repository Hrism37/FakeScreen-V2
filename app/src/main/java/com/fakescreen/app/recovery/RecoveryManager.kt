package com.fakescreen.app.recovery

import android.content.Context
import com.fakescreen.app.brightness.BrightnessManager
import com.fakescreen.app.brightness.RecoveryPlanner
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.data.SessionStore
import com.fakescreen.app.data.SettingsRepository
import com.fakescreen.app.service.NotificationHelper
import com.fakescreen.app.session.SessionStateHolder
import kotlinx.coroutines.flow.first

/**
 * Reconciles a persisted session record that was left behind by a crash, a
 * reboot or a force-stop.
 *
 * Safety rules:
 *  - never restore when a live session is currently running;
 *  - never restore a record older than [AppConstants.STALE_SESSION_MILLIS];
 *  - never overwrite a brightness the user changed afterwards.
 */
class RecoveryManager(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val brightnessManager: BrightnessManager,
    private val settingsRepository: SettingsRepository,
    private val sessionState: SessionStateHolder,
) {

    data class Outcome(
        val restored: Boolean,
        val message: String,
        val sessionStartedAt: Long?,
    )

    suspend fun reconcile(source: String): Outcome? {
        if (sessionState.state.value.active) return null
        val record = sessionStore.load() ?: return null
        if (sessionState.state.value.active) return null

        val settings = settingsRepository.settings.first()
        val current = brightnessManager.readSnapshot()
        val plan = RecoveryPlanner.plan(
            record = record,
            currentRaw = current.raw,
            currentMode = current.mode,
            nowMillis = System.currentTimeMillis(),
            staleAfterMillis = AppConstants.STALE_SESSION_MILLIS,
            autoRestore = settings.autoRestoreOnLaunch,
        )

        AppLog.i("Recovery($source): action=${plan.action} detail=${plan.detail}")

        val restored = when (plan.action) {
            RecoveryPlanner.Action.RESTORE -> {
                val ok = brightnessManager.restoreGlobal(plan.restoreRaw, plan.restoreMode)
                if (ok) {
                    NotificationHelper.notifyAlert(
                        context,
                        "亮度已恢复",
                        "检测到未正常结束的模拟息屏，已恢复进入前的亮度。",
                    )
                }
                ok
            }

            else -> false
        }

        if (plan.action == RecoveryPlanner.Action.RESTORE && !restored) {
            // Keep the record: without WRITE_SETTINGS the value cannot be restored yet.
            // A later launch (after the user re-grants the permission) retries safely.
            return Outcome(
                restored = false,
                message = "检测到未完成的会话，但恢复亮度失败（可能缺少“修改系统设置”权限）。授权后重新打开应用即可重试。",
                sessionStartedAt = record.startedAt,
            )
        }

        sessionStore.clear()

        val message = when (plan.action) {
            RecoveryPlanner.Action.RESTORE ->
                if (restored) "已恢复上次模拟息屏前的亮度。"
                else "检测到未完成的会话，但恢复亮度失败，请手动检查系统亮度。"

            RecoveryPlanner.Action.SKIP_USER_CHANGED ->
                "检测到未完成的会话，但亮度之后已被修改，已保留当前设置。"

            RecoveryPlanner.Action.SKIP_EXPIRED ->
                "检测到过期的会话记录，已安全清理，未改动亮度。"

            RecoveryPlanner.Action.SKIP_DISABLED ->
                "检测到未完成的会话记录，自动恢复已关闭，已清理记录。"

            RecoveryPlanner.Action.NOTHING_TO_RESTORE ->
                "已清理上次未正常结束的会话记录（系统亮度未被修改）。"
        }

        return Outcome(
            restored = restored,
            message = message,
            sessionStartedAt = record.startedAt,
        )
    }
}
