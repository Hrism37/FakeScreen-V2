package com.fakescreen.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeComboDetectorTest {

    private fun detector() = VolumeComboDetector(windowMillis = 400L)

    @Test
    fun detectsChordWithinWindow() {
        val detector = detector()
        assertFalse(detector.onKey(VolumeComboDetector.KEY_VOLUME_UP, VolumeComboDetector.ACTION_DOWN, 1_000L))
        assertTrue(detector.onKey(VolumeComboDetector.KEY_VOLUME_DOWN, VolumeComboDetector.ACTION_DOWN, 1_200L))
        // Holding both keys must not trigger again.
        assertFalse(detector.onKey(VolumeComboDetector.KEY_VOLUME_DOWN, VolumeComboDetector.ACTION_DOWN, 1_400L))
    }

    @Test
    fun ignoresChordOutsideWindow() {
        val detector = detector()
        assertFalse(detector.onKey(VolumeComboDetector.KEY_VOLUME_UP, VolumeComboDetector.ACTION_DOWN, 1_000L))
        assertFalse(detector.onKey(VolumeComboDetector.KEY_VOLUME_DOWN, VolumeComboDetector.ACTION_DOWN, 2_000L))
    }

    @Test
    fun rearmsAfterBothKeysReleased() {
        val detector = detector()
        detector.onKey(VolumeComboDetector.KEY_VOLUME_UP, VolumeComboDetector.ACTION_DOWN, 1_000L)
        assertTrue(detector.onKey(VolumeComboDetector.KEY_VOLUME_DOWN, VolumeComboDetector.ACTION_DOWN, 1_100L))

        detector.onKey(VolumeComboDetector.KEY_VOLUME_UP, VolumeComboDetector.ACTION_UP, 1_200L)
        detector.onKey(VolumeComboDetector.KEY_VOLUME_DOWN, VolumeComboDetector.ACTION_UP, 1_250L)

        detector.onKey(VolumeComboDetector.KEY_VOLUME_DOWN, VolumeComboDetector.ACTION_DOWN, 2_000L)
        assertTrue(detector.onKey(VolumeComboDetector.KEY_VOLUME_UP, VolumeComboDetector.ACTION_DOWN, 2_100L))
    }

    @Test
    fun oneKeyAloneDoesNotTrigger() {
        val detector = detector()
        assertFalse(detector.onKey(VolumeComboDetector.KEY_VOLUME_UP, VolumeComboDetector.ACTION_DOWN, 1_000L))
        assertFalse(detector.onKey(VolumeComboDetector.KEY_VOLUME_UP, VolumeComboDetector.ACTION_UP, 1_100L))
    }

    @Test
    fun resetClearsState() {
        val detector = detector()
        detector.onKey(VolumeComboDetector.KEY_VOLUME_UP, VolumeComboDetector.ACTION_DOWN, 1_000L)
        detector.reset()
        assertFalse(detector.onKey(VolumeComboDetector.KEY_VOLUME_DOWN, VolumeComboDetector.ACTION_DOWN, 1_100L))
    }
}
