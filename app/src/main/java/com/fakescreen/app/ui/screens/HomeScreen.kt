package com.fakescreen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakescreen.app.core.AppSettings
import com.fakescreen.app.core.formatElapsed
import com.fakescreen.app.device.DeviceCompatibility
import com.fakescreen.app.session.SessionUiState
import com.fakescreen.app.shizuku.ShizukuManager
import com.fakescreen.app.ui.components.ClickableRow
import com.fakescreen.app.ui.components.PrimaryButton
import com.fakescreen.app.ui.components.SectionCard
import com.fakescreen.app.ui.components.SectionLabel
import com.fakescreen.app.ui.components.StatusDot
import com.fakescreen.app.ui.theme.Bad
import com.fakescreen.app.ui.theme.Good
import com.fakescreen.app.ui.theme.Ink
import com.fakescreen.app.ui.theme.Line
import com.fakescreen.app.ui.theme.TextPrimary
import com.fakescreen.app.ui.theme.TextSecondary
import com.fakescreen.app.ui.theme.Warn
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    session: SessionUiState,
    settings: AppSettings,
    hasPin: Boolean,
    shizukuStatus: ShizukuManager.Status,
    deviceInfo: DeviceCompatibility.Info,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModes: () -> Unit,
    onOpenPin: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenWriteSettings: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.active, session.startedAt) {
        while (session.active) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val elapsed = if (session.active) formatElapsed(now - session.startedAt) else ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "FakeScreen",
                        color = TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "V2 · 本地模拟息屏",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        color = if (session.active) Good else TextSecondary.copy(alpha = 0.55f),
                        size = 9,
                    )
                    Text(
                        text = if (session.active) "模拟息屏进行中" else "当前未模拟息屏",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (session.active) {
                    Text(
                        text = "模式：${session.mode.title} · 已运行 $elapsed",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                    if (session.brightnessSummary.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = session.brightnessSummary,
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "屏幕现在是纯黑；轻触黑屏才会显示 PIN 键盘，输入正确 PIN 退出。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        text = "开始后屏幕保持点亮，显示纯黑覆盖层，但不会真正关闭屏幕。",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            PrimaryButton(
                text = if (session.active) "会话进行中" else "开始模拟息屏",
                onClick = onStart,
                enabled = !session.active,
            )
            if (!hasPin) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "首次使用需要先设置 4–8 位数字 PIN。",
                    color = Warn,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("会话设置")
            SectionCard {
                ClickableRow(
                    title = "默认模式",
                    subtitle = settings.defaultMode.title,
                    value = "修改",
                    onClick = onOpenModes,
                )
                HorizontalDivider(color = Line.copy(alpha = 0.6f))
                ClickableRow(
                    title = "PIN 密码",
                    subtitle = if (hasPin) "已设置，用于结束模拟息屏" else "尚未设置",
                    value = if (hasPin) "修改" else "设置",
                    valueColor = if (hasPin) TextSecondary else Warn,
                    onClick = onOpenPin,
                )
                HorizontalDivider(color = Line.copy(alpha = 0.6f))
                ClickableRow(
                    title = "Shizuku",
                    subtitle = "可选增强；不可用时核心功能正常",
                    value = shizukuStatus.label,
                    valueColor = shizukuColor(shizukuStatus),
                    onClick = onOpenShizuku,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("系统状态")
            SectionCard {
                ClickableRow(
                    title = "悬浮窗权限",
                    subtitle = "纯黑覆盖层必需",
                    value = if (deviceInfo.overlayGranted) "已授权" else "去授权",
                    valueColor = if (deviceInfo.overlayGranted) Good else Bad,
                    onClick = onOpenOverlayPermission,
                )
                HorizontalDivider(color = Line.copy(alpha = 0.6f))
                ClickableRow(
                    title = "通知权限",
                    subtitle = "前台服务通知与紧急退出入口",
                    value = if (deviceInfo.notificationsEnabled) "已授权" else "去授权",
                    valueColor = if (deviceInfo.notificationsEnabled) Good else Warn,
                    onClick = onOpenNotificationPermission,
                )
                HorizontalDivider(color = Line.copy(alpha = 0.6f))
                ClickableRow(
                    title = "忽略电池优化",
                    subtitle = "MagicOS 等系统可能限制后台运行",
                    value = if (deviceInfo.batteryOptimizationIgnored) "已忽略" else "去设置",
                    valueColor = if (deviceInfo.batteryOptimizationIgnored) Good else TextSecondary,
                    onClick = onOpenBatterySettings,
                )
                HorizontalDivider(color = Line.copy(alpha = 0.6f))
                ClickableRow(
                    title = "系统亮度写入",
                    subtitle = "仅在开启“系统级亮度备用”时使用",
                    value = if (deviceInfo.writeSettingsGranted) "已授权" else "未授权",
                    valueColor = if (deviceInfo.writeSettingsGranted) Good else TextSecondary,
                    onClick = onOpenWriteSettings,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionCard {
                Text(
                    text = "安全提示",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "屏幕保持点亮会增加耗电。忘记 PIN 时，可同时按音量加/减（需在设置开启）、长按电源键，或等待计时器；通知栏不再提供免 PIN 退出。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text("查看完整说明", color = com.fakescreen.app.ui.theme.Accent, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun shizukuColor(status: ShizukuManager.Status) = when (status) {
    ShizukuManager.Status.READY -> Good
    ShizukuManager.Status.NO_PERMISSION -> Warn
    ShizukuManager.Status.NOT_INSTALLED, ShizukuManager.Status.NOT_RUNNING -> TextSecondary
    ShizukuManager.Status.ERROR -> Bad
}
