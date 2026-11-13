package com.fakescreen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF05070A)
val Panel = Color(0xFF0C1015)
val PanelElevated = Color(0xFF121821)
val Line = Color(0xFF232B36)
val TextPrimary = Color(0xFFE9EDF3)
val TextSecondary = Color(0xFF98A3B3)
val Accent = Color(0xFFA8C0FF)
val AccentDim = Color(0xFF1A2740)
val Good = Color(0xFF7FD6B0)
val Warn = Color(0xFFE8C27A)
val Bad = Color(0xFFFF9A94)

private val FakeScreenDarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF091223),
    primaryContainer = AccentDim,
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Good,
    onSecondary = Color(0xFF052018),
    secondaryContainer = Color(0xFF11291F),
    onSecondaryContainer = Color(0xFFB8F0D8),
    tertiary = Warn,
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelElevated,
    onSurfaceVariant = TextSecondary,
    outline = Line,
    outlineVariant = Color(0xFF1A212B),
    error = Bad,
    onError = Color(0xFF2C0907),
)

@Composable
fun FakeScreenTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FakeScreenDarkColors,
        typography = Typography(),
        content = content,
    )
}
