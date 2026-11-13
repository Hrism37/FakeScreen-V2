package com.fakescreen.app.security

/**
 * Pure lockout policy, kept free of Android APIs so it can be unit tested.
 *
 * The first four wrong PINs are free (normal typo tolerance). From the fifth
 * wrong attempt onwards the keypad is frozen for an increasing amount of time.
 * At ten wrong attempts the app additionally asks the system to really turn the
 * screen off (via Shizuku) so that a bystander cannot keep reading the screen.
 */
object LockoutPolicy {

    const val FREE_ATTEMPTS = 4
    const val PRIVACY_LOCK_THRESHOLD = 10
    const val PRIVACY_LOCK_MILLIS = 5L * 60L * 1000L

    /** How long the keypad stays locked *after* the given number of failures. */
    fun lockMillisForFailures(failures: Int): Long = when {
        failures <= FREE_ATTEMPTS -> 0L
        failures == 5 -> 5_000L
        failures == 6 -> 15_000L
        failures == 7 -> 30_000L
        failures == 8 -> 60_000L
        failures == 9 -> 120_000L
        else -> PRIVACY_LOCK_MILLIS
    }

    fun shouldTriggerPrivacyLock(failures: Int): Boolean = failures >= PRIVACY_LOCK_THRESHOLD

    fun attemptsUntilPrivacyLock(failures: Int): Int =
        (PRIVACY_LOCK_THRESHOLD - failures).coerceAtLeast(0)
}
