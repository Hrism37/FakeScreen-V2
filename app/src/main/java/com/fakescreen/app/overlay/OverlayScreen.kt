package com.fakescreen.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakescreen.app.core.formatCountdown
import com.fakescreen.app.ui.components.PinDots
import com.fakescreen.app.ui.components.PinKeypad
import com.fakescreen.app.ui.theme.Bad
import com.fakescreen.app.ui.theme.TextPrimary
import com.fakescreen.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * The overlay content.
 *
 * Normal state is a completely black screen with no visible widgets. A tap
 * reveals the PIN pad; it hides itself again after a period without input.
 * The reveal state is reported to the session so that the window brightness can
 * be raised temporarily in the low-brightness mode (otherwise the keypad would
 * be invisible at 1%).
 */
@Composable
fun OverlayScreen(
    state: PinOverlayState,
    onSubmit: (String) -> Unit,
    onRevealChanged: (Boolean) -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    var lastSubmitted by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lockedMillis = (state.lockedUntil - now).coerceAtLeast(0L)
    val locked = lockedMillis > 0L
    val canType = revealed && !locked && !state.verifying && input.length < state.pinLength

    LaunchedEffect(state.lockedUntil) {
        while (true) {
            now = System.currentTimeMillis()
            if (state.lockedUntil <= now) break
            delay(250L)
        }
    }

    LaunchedEffect(revealed) {
        onRevealChanged(revealed)
    }

    // Hide the keypad after 20 seconds without interaction, so the screen
    // returns to a pure black state even if the user walks away mid-entry.
    LaunchedEffect(revealed, interaction, state.clearTick, state.pinLength) {
        if (!revealed) return@LaunchedEffect
        delay(REVEAL_TIMEOUT_MS)
        if (!state.verifying) {
            input = ""
            lastSubmitted = ""
            revealed = false
        }
    }

    LaunchedEffect(state.clearTick) {
        input = ""
        lastSubmitted = ""
        if (state.isError) interaction++
    }

    LaunchedEffect(state.pinLength) {
        if (input.length > state.pinLength) input = input.take(state.pinLength)
    }

    LaunchedEffect(input, locked, state.verifying, revealed) {
        if (revealed && input.length == state.pinLength && !locked &&
            !state.verifying && input != lastSubmitted
        ) {
            lastSubmitted = input
            onSubmit(input)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!revealed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        interaction++
                        revealed = true
                    }
            )
        }

        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(160)),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp)
            ) {
                val wide = maxWidth >= 600.dp
                val short = maxHeight < 520.dp
                val buttonSize = when {
                    short -> 54.dp
                    wide -> 66.dp
                    else -> 72.dp
                }
                val gap = if (short) 9.dp else 14.dp

                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InfoBlock(state = state, modifier = Modifier.width(280.dp))
                        Spacer(Modifier.width(56.dp))
                        PinBlock(
                            state = state,
                            input = input,
                            lockedMillis = lockedMillis,
                            locked = locked,
                            canType = canType,
                            buttonSize = buttonSize,
                            gap = gap,
                            compact = short,
                            onDigit = { digit ->
                                if (input.length < state.pinLength) {
                                    input += digit
                                    interaction++
                                }
                            },
                            onBackspace = {
                                if (input.isNotEmpty()) {
                                    input = input.dropLast(1)
                                    interaction++
                                }
                            },
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color(0xFF4C5665),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "输入 PIN 退出",
                                color = Color(0xFF56606E),
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp,
                            )
                        }
                        Spacer(Modifier.height(if (short) 14.dp else 28.dp))
                        PinBlock(
                            state = state,
                            input = input,
                            lockedMillis = lockedMillis,
                            locked = locked,
                            canType = canType,
                            buttonSize = buttonSize,
                            gap = gap,
                            compact = short,
                            onDigit = { digit ->
                                if (input.length < state.pinLength) {
                                    input += digit
                                    interaction++
                                }
                            },
                            onBackspace = {
                                if (input.isNotEmpty()) {
                                    input = input.dropLast(1)
                                    interaction++
                                }
                            },
                        )
                        if (state.failures >= 2) {
                            Spacer(Modifier.height(18.dp))
                            EmergencyHint()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinBlock(
    state: PinOverlayState,
    input: String,
    lockedMillis: Long,
    locked: Boolean,
    canType: Boolean,
    buttonSize: Dp,
    gap: Dp,
    compact: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PinDots(
            length = state.pinLength,
            filled = input.length,
            error = state.isError && !locked,
            enabled = !locked,
        )
        Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
        Box(
            modifier = Modifier.height(if (compact) 20.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val message = when {
                locked -> "尝试次数过多，请 ${formatCountdown(lockedMillis)} 后重试"
                state.verifying -> "正在验证…"
                else -> state.message
            }
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    color = if (state.isError || locked) Bad else TextSecondary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        PinKeypad(
            enabled = canType,
            onDigit = onDigit,
            onBackspace = onBackspace,
            buttonSize = buttonSize,
            gap = gap,
        )
    }
}

@Composable
private fun InfoBlock(state: PinOverlayState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = Color(0xFF596373),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "FAKESCREEN",
                color = Color(0xFF596373),
                fontSize = 10.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(26.dp))
        Text(
            text = "模拟息屏中",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.hint,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "再次轻触黑色区域可重新显示键盘；20 秒无操作自动隐藏。",
            color = Color(0xFF3F4855),
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
        if (state.failures >= 2) {
            Spacer(Modifier.height(22.dp))
            EmergencyHint()
        }
    }
}

@Composable
private fun EmergencyHint() {
    Text(
        text = "忘记 PIN：同时按音量加和音量减可结束会话（需在设置中开启音量键快捷开关）。",
        color = Color(0xFF4A5361),
        fontSize = 10.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}

private const val REVEAL_TIMEOUT_MS = 20_000L
