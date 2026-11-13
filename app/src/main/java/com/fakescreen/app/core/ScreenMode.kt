package com.fakescreen.app.core

/**
 * The two simulated screen-off strategies.
 *
 * PURE_BLACK only paints an opaque black overlay and never touches the system
 * brightness. DIM additionally asks the window (and optionally the system) for
 * the lowest usable brightness.
 */
enum class ScreenMode(val storageKey: String, val title: String, val shortTitle: String) {
    PURE_BLACK("pure_black", "纯黑覆盖", "OLED 纯黑"),
    DIM("dim", "黑屏 + 低亮度", "LCD 低亮度"),
    TRUE_OFF("true_off", "Shizuku 真熄屏", "真熄屏");

    companion object {
        fun fromKey(key: String?): ScreenMode =
            entries.firstOrNull { it.storageKey == key } ?: PURE_BLACK

        fun fromName(name: String?): ScreenMode =
            entries.firstOrNull { it.name == name } ?: PURE_BLACK
    }
}
