package com.fakescreen.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {

    @Test
    fun firstFourFailuresAreFree() {
        for (failures in 0..4) {
            assertEquals(0L, LockoutPolicy.lockMillisForFailures(failures))
        }
    }

    @Test
    fun delaysIncreaseWithFailures() {
        val delays = (5..10).map { LockoutPolicy.lockMillisForFailures(it) }
        assertEquals(listOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L), delays)
    }

    @Test
    fun privacyLockStartsAtTenFailures() {
        assertFalse(LockoutPolicy.shouldTriggerPrivacyLock(9))
        assertTrue(LockoutPolicy.shouldTriggerPrivacyLock(10))
        assertTrue(LockoutPolicy.shouldTriggerPrivacyLock(11))
    }

    @Test
    fun remainingAttemptsNeverNegative() {
        assertEquals(0, LockoutPolicy.attemptsUntilPrivacyLock(14))
        assertEquals(1, LockoutPolicy.attemptsUntilPrivacyLock(9))
    }
}
