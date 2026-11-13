package com.fakescreen.app.overlay

/** State rendered by the simulated screen-off overlay. */
data class PinOverlayState(
    val pinLength: Int = 4,
    val failures: Int = 0,
    val verifying: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** Wall clock time until which the keypad is frozen. 0 means not locked. */
    val lockedUntil: Long = 0L,
    /** Incremented by the session manager whenever the typed digits must be cleared. */
    val clearTick: Int = 0,
    val hint: String = "输入 PIN 结束模拟息屏",
)
