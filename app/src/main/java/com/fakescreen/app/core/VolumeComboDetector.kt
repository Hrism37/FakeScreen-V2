package com.fakescreen.app.core

/**
 * Pure detector for the "Volume Up + Volume Down" chord.
 *
 * Kept free of Android APIs so the timing logic can be unit tested. The caller
 * passes key codes, actions and a monotonic timestamp.
 */
class VolumeComboDetector(private val windowMillis: Long = 400L) {

    private var upDownAt = 0L
    private var downDownAt = 0L
    private var triggered = false

    /**
     * @return true exactly once per chord, when the second volume key is
     * pressed within [windowMillis] of the first one.
     */
    fun onKey(keyCode: Int, action: Int, now: Long): Boolean {
        when (action) {
            ACTION_DOWN -> {
                when (keyCode) {
                    KEY_VOLUME_UP -> upDownAt = now
                    KEY_VOLUME_DOWN -> downDownAt = now
                    else -> return false
                }
                if (upDownAt > 0L && downDownAt > 0L &&
                    kotlin.math.abs(upDownAt - downDownAt) <= windowMillis
                ) {
                    if (triggered) return false
                    triggered = true
                    return true
                }
            }

            ACTION_UP -> {
                when (keyCode) {
                    KEY_VOLUME_UP -> upDownAt = 0L
                    KEY_VOLUME_DOWN -> downDownAt = 0L
                    else -> return false
                }
                if (upDownAt == 0L && downDownAt == 0L) {
                    triggered = false
                }
            }
        }
        return false
    }

    fun reset() {
        upDownAt = 0L
        downDownAt = 0L
        triggered = false
    }

    companion object {
        const val KEY_VOLUME_UP = 24
        const val KEY_VOLUME_DOWN = 25
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
    }
}
