package com.fakescreen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakescreen.app.core.formatCountdown
import com.fakescreen.app.security.LockoutPolicy
import com.fakescreen.app.security.VerifyResult
import com.fakescreen.app.ui.components.PinDots
import com.fakescreen.app.ui.components.PinKeypad
import com.fakescreen.app.ui.components.PrimaryButton
import com.fakescreen.app.ui.components.TopBar
import com.fakescreen.app.ui.theme.Bad
import com.fakescreen.app.ui.theme.Ink
import com.fakescreen.app.ui.theme.TextPrimary
import com.fakescreen.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private enum class PinStage { VerifyCurrent, EnterNew, ConfirmNew }

@Composable
fun PinSetupScreen(
    changeMode: Boolean,
    knownPinLength: Int,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onSetPin: (String, (Boolean) -> Unit) -> Unit,
    onVerifyPin: (String, (VerifyResult) -> Unit) -> Unit,
    onChangePin: (String, String, (VerifyResult) -> Unit) -> Unit,
) {
    var stage by remember {
        mutableStateOf(if (changeMode) PinStage.VerifyCurrent else PinStage.EnterNew)
    }
    var input by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lockedMillis = (lockedUntil - now).coerceAtLeast(0L)
    val locked = lockedMillis > 0L
    val canType = !busy && !locked

    LaunchedEffect(lockedUntil) {
        while (true) {
            now = System.currentTimeMillis()
            if (lockedUntil <= now) break
            delay(250L)
        }
    }

    fun handleWrong(result: VerifyResult.Wrong) {
        input = ""
        error = when {
            result.privacyLock -> "错误次数过多，请稍后重试"
            result.lockMillis > 0 -> "PIN 错误，请稍后重试"
            else -> "PIN 错误，还可尝试 ${LockoutPolicy.attemptsUntilPrivacyLock(result.failures)} 次"
        }
        if (result.lockMillis > 0) {
            lockedUntil = System.currentTimeMillis() + result.lockMillis
        }
    }

    fun submitVerify(pin: String) {
        busy = true
        onVerifyPin(pin) { result ->
            busy = false
            when (result) {
                is VerifyResult.Success -> {
                    currentPin = pin
                    input = ""
                    error = null
                    stage = PinStage.EnterNew
                }

                is VerifyResult.Wrong -> handleWrong(result)

                is VerifyResult.Locked -> {
                    input = ""
                    lockedUntil = System.currentTimeMillis() + result.remainingMillis
                    error = "尝试次数过多，请稍后重试"
                }

                else -> {
                    input = ""
                    error = "无法验证 PIN，请重试"
                }
            }
        }
    }

    fun submitConfirm(pin: String) {
        busy = true
        if (changeMode) {
            onChangePin(currentPin, pin) { result ->
                busy = false
                when (result) {
                    is VerifyResult.Success -> onFinished()
                    is VerifyResult.Wrong -> {
                        handleWrong(result)
                        currentPin = ""
                        stage = PinStage.VerifyCurrent
                    }

                    is VerifyResult.Locked -> {
                        input = ""
                        lockedUntil = System.currentTimeMillis() + result.remainingMillis
                        error = "尝试次数过多，请稍后重试"
                        currentPin = ""
                        stage = PinStage.VerifyCurrent
                    }

                    is VerifyResult.InvalidFormat -> {
                        input = ""
                        error = "请输入 4–8 位数字"
                        stage = PinStage.EnterNew
                        firstPin = ""
                    }

                    else -> {
                        input = ""
                        error = "修改失败，请重试"
                        stage = PinStage.EnterNew
                        firstPin = ""
                    }
                }
            }
        } else {
            onSetPin(pin) { ok ->
                busy = false
                if (ok) {
                    onFinished()
                } else {
                    input = ""
                    error = "设置失败，请重试"
                }
            }
        }
    }

    LaunchedEffect(input, stage, busy, locked) {
        if (stage == PinStage.VerifyCurrent && !busy && !locked &&
            knownPinLength in 4..8 && input.length == knownPinLength
        ) {
            submitVerify(input)
        }
    }

    LaunchedEffect(input, stage, firstPin, busy, locked) {
        if (stage == PinStage.ConfirmNew && !busy && !locked &&
            firstPin.isNotEmpty() && input.length == firstPin.length
        ) {
            if (input != firstPin) {
                error = "两次输入不一致，请重新输入"
                input = ""
                firstPin = ""
                stage = PinStage.EnterNew
            } else {
                submitConfirm(input)
            }
        }
    }

    val title = when (stage) {
        PinStage.VerifyCurrent -> "验证当前 PIN"
        PinStage.EnterNew -> if (changeMode) "设置新 PIN" else "设置 PIN"
        PinStage.ConfirmNew -> "确认 PIN"
    }
    val subtitle = when (stage) {
        PinStage.VerifyCurrent -> "输入当前 PIN，验证通过后设置新密码。"
        PinStage.EnterNew -> "输入 4–8 位数字。PIN 只保护 FakeScreen 的退出流程。"
        PinStage.ConfirmNew -> "再次输入相同的数字以确认。"
    }
    val dotCount = when (stage) {
        PinStage.VerifyCurrent -> knownPinLength.coerceIn(4, 8)
        PinStage.EnterNew -> input.length.coerceIn(4, 8)
        PinStage.ConfirmNew -> firstPin.length.coerceIn(4, 8)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            TopBar(title = "PIN 安全", onBack = onBack)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                val wide = maxWidth >= 560.dp
                val short = maxHeight < 480.dp
                val buttonSize = if (short) 46.dp else 62.dp
                val gap = if (short) 7.dp else 12.dp

                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 30.dp)) {
                            Text(
                                text = title,
                                color = TextPrimary,
                                fontSize = if (short) 22.sp else 27.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = subtitle,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            ErrorText(error, locked, lockedMillis, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "PIN 仅用于退出模拟息屏，不是系统锁屏，也不会阻止电源键、来电或紧急呼叫。",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                            )
                        }
                        Column(
                            modifier = Modifier.width(300.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PinDots(length = dotCount, filled = input.length, error = error != null)
                            Spacer(Modifier.height(if (short) 8.dp else 14.dp))
                            if (stage == PinStage.EnterNew) {
                                Text(
                                    text = "4–8 位数字",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            PinKeypad(
                                enabled = canType,
                                onDigit = { if (input.length < 8) { input += it; error = null } },
                                onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) },
                                buttonSize = buttonSize,
                                gap = gap,
                            )
                            if (stage == PinStage.EnterNew) {
                                Spacer(Modifier.height(if (short) 8.dp else 14.dp))
                                Box(Modifier.width(200.dp)) {
                                    PrimaryButton(
                                        text = "继续",
                                        enabled = input.length >= 4 && canType,
                                        onClick = {
                                            firstPin = input
                                            input = ""
                                            error = null
                                            stage = PinStage.ConfirmNew
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = if (short) 20.sp else 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(if (short) 12.dp else 22.dp))
                        PinDots(length = dotCount, filled = input.length, error = error != null)
                        Spacer(Modifier.height(10.dp))
                        ErrorText(error, locked, lockedMillis, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        PinKeypad(
                            enabled = canType,
                            onDigit = { if (input.length < 8) { input += it; error = null } },
                            onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) },
                            buttonSize = buttonSize,
                            gap = gap,
                        )
                        if (stage == PinStage.EnterNew) {
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.width(200.dp)) {
                                PrimaryButton(
                                    text = "继续",
                                    enabled = input.length >= 4 && canType,
                                    onClick = {
                                        firstPin = input
                                        input = ""
                                        error = null
                                        stage = PinStage.ConfirmNew
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(
    error: String?,
    locked: Boolean,
    lockedMillis: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(22.dp), contentAlignment = Alignment.Center) {
        val text = when {
            locked -> "请等待 ${formatCountdown(lockedMillis)} 后重试"
            else -> error
        }
        if (!text.isNullOrBlank()) {
            Text(
                text = text,
                color = Bad,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
