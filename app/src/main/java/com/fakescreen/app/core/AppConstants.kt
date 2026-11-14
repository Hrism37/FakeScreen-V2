package com.fakescreen.app.core

import android.util.Log

/**
 * Central constants. Keeping them in one place makes the service/UI contract
 * easier to audit.
 */
object AppConstants {
    const val TAG = "FakeScreen"

    const val SESSION_CHANNEL_ID = "fakescreen_session"
    const val ALERT_CHANNEL_ID = "fakescreen_alerts"
    const val SESSION_NOTIFICATION_ID = 4101
    const val ALERT_NOTIFICATION_ID = 4102

    const val ACTION_START_SESSION = "com.fakescreen.app.action.START_SESSION"
    const val ACTION_STOP_SESSION = "com.fakescreen.app.action.STOP_SESSION"
    const val ACTION_VOLUME_TOGGLE = "com.fakescreen.app.action.VOLUME_TOGGLE"
    const val EXTRA_MODE = "extra_mode"

    const val SHIZUKU_REQUEST_CODE = 4103
    const val PIN_REQUEST_CODE = 4104

    const val DEFAULT_MAX_SESSION_MINUTES = 30
    /** 0 means "no automatic stop"; the UI additionally offers a no-limit switch. */
    const val MIN_SESSION_MINUTES = 0

    /** A session older than this is considered stale and is never "restored". */
    const val STALE_SESSION_MILLIS = 12L * 60L * 60L * 1000L

    /** Safety margin added to the configured maximum session length at startup. */
    const val SESSION_GRACE_MILLIS = 5L * 60L * 1000L

    const val PIN_MIN_LENGTH = 4
    const val PIN_MAX_LENGTH = 8

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
}

object AppLog {
    private const val MAX_TAG_LENGTH = 23

    private fun tagOf(tag: String): String =
        if (tag.length <= MAX_TAG_LENGTH) tag else tag.substring(0, MAX_TAG_LENGTH)

    fun d(message: String, tag: String = AppConstants.TAG) = Log.d(tagOf(tag), message)
    fun i(message: String, tag: String = AppConstants.TAG) = Log.i(tagOf(tag), message)
    fun w(message: String, throwable: Throwable? = null, tag: String = AppConstants.TAG) =
        Log.w(tagOf(tag), message, throwable)
    fun e(message: String, throwable: Throwable? = null, tag: String = AppConstants.TAG) =
        Log.e(tagOf(tag), message, throwable)
}
