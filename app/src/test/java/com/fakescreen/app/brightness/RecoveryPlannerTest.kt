package com.fakescreen.app.brightness

import com.fakescreen.app.core.ScreenMode
import com.fakescreen.app.data.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryPlannerTest {

    private val now = 1_700_000_000_000L

    private fun record(
        startedAt: Long = now - 60_000,
        originalBrightness: Int = 180,
        originalMode: Int = 1,
        globalApplied: Boolean = true,
        appliedGlobal: Int = 4,
        appliedMode: Int = 0,
    ) = SessionRecord(
        id = "test",
        mode = ScreenMode.DIM,
        startedAt = startedAt,
        originalBrightness = originalBrightness,
        originalBrightnessMode = originalMode,
        appliedWindowBrightness = 0.01f,
        globalBrightnessApplied = globalApplied,
        appliedGlobalBrightness = appliedGlobal,
        appliedGlobalMode = appliedMode,
    )

    @Test
    fun restoresWhenNothingChanged() {
        val plan = RecoveryPlanner.plan(
            record = record(),
            currentRaw = 4,
            currentMode = 0,
            nowMillis = now,
            staleAfterMillis = 12 * 60 * 60 * 1000,
            autoRestore = true,
        )
        assertEquals(RecoveryPlanner.Action.RESTORE, plan.action)
        assertEquals(180, plan.restoreRaw)
        assertEquals(1, plan.restoreMode)
    }

    @Test
    fun skipsWhenUserChangedBrightness() {
        val plan = RecoveryPlanner.plan(
            record = record(),
            currentRaw = 120,
            currentMode = 0,
            nowMillis = now,
            staleAfterMillis = 12 * 60 * 60 * 1000,
            autoRestore = true,
        )
        assertEquals(RecoveryPlanner.Action.SKIP_USER_CHANGED, plan.action)
    }

    @Test
    fun skipsWhenUserChangedMode() {
        val plan = RecoveryPlanner.plan(
            record = record(),
            currentRaw = 4,
            currentMode = 1,
            nowMillis = now,
            staleAfterMillis = 12 * 60 * 60 * 1000,
            autoRestore = true,
        )
        assertEquals(RecoveryPlanner.Action.SKIP_USER_CHANGED, plan.action)
    }

    @Test
    fun skipsExpiredSessions() {
        val plan = RecoveryPlanner.plan(
            record = record(startedAt = now - 13 * 60 * 60 * 1000),
            currentRaw = 4,
            currentMode = 0,
            nowMillis = now,
            staleAfterMillis = 12 * 60 * 60 * 1000,
            autoRestore = true,
        )
        assertEquals(RecoveryPlanner.Action.SKIP_EXPIRED, plan.action)
    }

    @Test
    fun windowOnlySessionNeedsNoBrightnessWrite() {
        val plan = RecoveryPlanner.plan(
            record = record(globalApplied = false, appliedGlobal = -1, appliedMode = -1),
            currentRaw = 180,
            currentMode = 1,
            nowMillis = now,
            staleAfterMillis = 12 * 60 * 60 * 1000,
            autoRestore = true,
        )
        assertEquals(RecoveryPlanner.Action.NOTHING_TO_RESTORE, plan.action)
    }

    @Test
    fun disabledRecoveryDoesNothing() {
        val plan = RecoveryPlanner.plan(
            record = record(),
            currentRaw = 4,
            currentMode = 0,
            nowMillis = now,
            staleAfterMillis = 12 * 60 * 60 * 1000,
            autoRestore = false,
        )
        assertEquals(RecoveryPlanner.Action.SKIP_DISABLED, plan.action)
    }

    @Test
    fun unknownOriginalBrightnessIsNeverWritten() {
        val plan = RecoveryPlanner.plan(
            record = record(originalBrightness = -1),
            currentRaw = 4,
            currentMode = 0,
            nowMillis = now,
            staleAfterMillis = 12 * 60 * 60 * 1000,
            autoRestore = true,
        )
        assertEquals(RecoveryPlanner.Action.NOTHING_TO_RESTORE, plan.action)
    }
}
