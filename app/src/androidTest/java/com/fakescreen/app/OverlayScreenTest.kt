package com.fakescreen.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fakescreen.app.overlay.OverlayScreen
import com.fakescreen.app.overlay.PinOverlayState
import com.fakescreen.app.ui.theme.FakeScreenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose tests. They require a device or emulator:
 *   ./gradlew :app:connectedDebugAndroidTest
 */
class OverlayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enteringFullPinSubmitsExactlyOnce() {
        var submitted: String? = null
        var submissions = 0
        composeRule.setContent {
            FakeScreenTheme {
                OverlayScreen(
                    state = PinOverlayState(pinLength = 4),
                    onSubmit = {
                        submitted = it
                        submissions++
                    },
                )
            }
        }

        listOf("1", "2", "3", "4").forEach { digit ->
            composeRule.onNodeWithText(digit).performClick()
        }
        composeRule.waitForIdle()

        assertEquals("1234", submitted)
        assertEquals(1, submissions)
    }

    @Test
    fun lockedKeypadDoesNotAcceptInput() {
        var submitted: String? = null
        composeRule.setContent {
            FakeScreenTheme {
                OverlayScreen(
                    state = PinOverlayState(
                        pinLength = 4,
                        isError = true,
                        lockedUntil = System.currentTimeMillis() + 30_000L,
                    ),
                    onSubmit = { submitted = it },
                )
            }
        }

        composeRule.onNodeWithText("1").assertIsNotEnabled()
        assertNull(submitted)
    }
}
