package com.fakescreen.app.brightness

import com.fakescreen.app.data.SessionRecord

/**
 * Pure decision logic for "what should happen to the brightness of a session
 * that was not closed normally". Kept Android-free so it is unit tested.
 */
object RecoveryPlanner {

    enum class Action {
        /** Nothing to restore, only clean the persisted record. */
        NOTHING_TO_RESTORE,

        /** Restore the captured original brightness and mode. */
        RESTORE,

        /** Never touch the brightness, the user changed it after the session. */
        SKIP_USER_CHANGED,

        /** Too old to be safely restored. */
        SKIP_EXPIRED,

        /** User disabled automatic recovery. */
        SKIP_DISABLED,
    }

    data class Plan(
        val action: Action,
        val restoreRaw: Int = -1,
        val restoreMode: Int = -1,
        val detail: String = "",
    )

    fun plan(
        record: SessionRecord,
        currentRaw: Int,
        currentMode: Int,
        nowMillis: Long,
        staleAfterMillis: Long,
        autoRestore: Boolean,
    ): Plan {
        if (!autoRestore) {
            return Plan(Action.SKIP_DISABLED, detail = "自动恢复已关闭")
        }
        if (nowMillis - record.startedAt > staleAfterMillis) {
            return Plan(Action.SKIP_EXPIRED, detail = "会话记录已过期")
        }
        if (!record.globalBrightnessApplied) {
            return Plan(Action.NOTHING_TO_RESTORE, detail = "上次会话未修改系统亮度")
        }
        if (record.originalBrightness !in 1..255) {
            return Plan(Action.NOTHING_TO_RESTORE, detail = "没有可恢复的原始亮度")
        }
        // The value we wrote is still there only if nobody else changed it.
        if (record.appliedGlobalBrightness in 0..255 &&
            currentRaw != record.appliedGlobalBrightness
        ) {
            return Plan(Action.SKIP_USER_CHANGED, detail = "亮度已被用户修改，不覆盖")
        }
        if (record.appliedGlobalMode in 0..1 && currentMode != record.appliedGlobalMode) {
            return Plan(Action.SKIP_USER_CHANGED, detail = "亮度模式已被用户修改，不覆盖")
        }
        return Plan(
            action = Action.RESTORE,
            restoreRaw = record.originalBrightness,
            restoreMode = record.originalBrightnessMode,
            detail = "恢复进入前的亮度",
        )
    }
}
