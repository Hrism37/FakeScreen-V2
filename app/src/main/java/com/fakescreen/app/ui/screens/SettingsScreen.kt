package com.fakescreen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakescreen.app.BuildConfig
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppSettings
import com.fakescreen.app.device.DeviceCompatibility
import com.fakescreen.app.shizuku.ShizukuManager
import com.fakescreen.app.ui.components.ClickableRow
import com.fakescreen.app.ui.components.SectionCard
import com.fakescreen.app.ui.components.SectionLabel
import com.fakescreen.app.ui.components.SwitchRow
import com.fakescreen.app.ui.components.TopBar
import com.fakescreen.app.ui.theme.Accent
import com.fakescreen.app.ui.theme.Bad
import com.fakescreen.app.ui.theme.Good
import com.fakescreen.app.ui.theme.Ink
import com.fakescreen.app.ui.theme.Line
import com.fakescreen.app.ui.theme.Panel
import com.fakescreen.app.ui.theme.TextPrimary
import com.fakescreen.app.ui.theme.TextSecondary
import com.fakescreen.app.ui.theme.Warn
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    hasPin: Boolean,
    shizukuStatus: ShizukuManager.Status,
    shizukuDetail: String,
    deviceInfo: DeviceCompatibility.Info,
    onBack: () -> Unit,
    onOpenModes: () -> Unit,
    onChangePin: () -> Unit,
    onMaxSessionChange: (Int) -> Unit,
    onAutoRestoreChange: (Boolean) -> Unit,
    onExperimentalAudioChange: (Boolean) -> Unit,
    onGlobalBrightnessChange: (Boolean) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenNotificationPermission: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenBattery: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onOpenCompatibility: () -> Unit,
) {
    var showEmergency by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(settings.maxSessionMinutes.toFloat()) }
    LaunchedEffect(settings.maxSessionMinutes) {
        sliderValue = settings.maxSessionMinutes.toFloat()
    }

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
        ) {
            TopBar(title = "设置", onBack = onBack)
            Column(Modifier.padding(horizontal = 20.dp)) {

                SectionLabel("会话")
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
                        subtitle = if (hasPin) "修改需要先验证当前 PIN" else "尚未设置",
                        value = if (hasPin) "修改" else "设置",
                        valueColor = if (hasPin) TextSecondary else Warn,
                        onClick = onChangePin,
                    )
                    HorizontalDivider(color = Line.copy(alpha = 0.6f))
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "最长会话时长",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${sliderValue.roundToInt()} 分钟",
                                color = Accent,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = AppConstants.MIN_SESSION_MINUTES.toFloat()..
                                AppConstants.MAX_SESSION_MINUTES.toFloat(),
                            steps = (AppConstants.MAX_SESSION_MINUTES -
                                AppConstants.MIN_SESSION_MINUTES) / 5 - 1,
                            onValueChangeFinished = {
                                onMaxSessionChange(sliderValue.roundToInt())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Accent,
                                activeTrackColor = Accent,
                                inactiveTrackColor = Color(0xFF1E2530),
                            ),
                        )
                        Text(
                            text = "达到时长后自动退出并恢复亮度，防止忘记退出。",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    HorizontalDivider(color = Line.copy(alpha = 0.6f))
                    SwitchRow(
                        title = "异常后自动恢复亮度",
                        subtitle = "应用重新启动或设备重启后，检查未完成的会话；仅当亮度仍是本应用写入的值时才恢复，不覆盖用户后来的修改。",
                        checked = settings.autoRestoreOnLaunch,
                        onCheckedChange = onAutoRestoreChange,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("屏幕与亮度")
                SectionCard {
                    Text(
                        text = "亮度控制优先使用悬浮窗的窗口级亮度，不写入系统设置，退出时随窗口自动失效。只有在开启下方备用开关并授予“修改系统设置”权限后，才会写入系统亮度。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "系统级亮度备用",
                        subtitle = "窗口级亮度在部分机型上可能不生效。开启后会临时修改系统亮度，并在退出时恢复原值和自动/手动模式；绝不低于 1/255。",
                        checked = settings.allowGlobalBrightnessFallback,
                        onCheckedChange = onGlobalBrightnessChange,
                    )
                    if (!deviceInfo.writeSettingsGranted) {
                        HorizontalDivider(color = Line.copy(alpha = 0.6f))
                        ClickableRow(
                            title = "修改系统设置权限",
                            subtitle = "仅在使用系统级亮度备用时需要",
                            value = "去授权",
                            valueColor = Warn,
                            onClick = onOpenWriteSettings,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("防自动息屏")
                SectionCard {
                    Text(
                        text = "会话期间给黑色悬浮窗加上 FLAG_KEEP_SCREEN_ON，让屏幕保持点亮。这不会真正熄灭屏幕，因此比系统息屏更耗电；这是模拟息屏的必然代价。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "实验性静音音频保活",
                        subtitle = "默认关闭。循环播放 1 秒静音音频，部分机型会因此更积极地保持会话。可能增加耗电，且不保证生效，失败会自动回退。",
                        checked = settings.experimentalAudioKeepAwake,
                        onCheckedChange = onExperimentalAudioChange,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("系统权限")
                SectionCard {
                    ClickableRow(
                        title = "悬浮窗权限",
                        subtitle = "全屏黑色覆盖层必需",
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
                        title = "电池优化",
                        subtitle = "请求忽略电池优化，降低后台被清理的概率",
                        value = if (deviceInfo.batteryOptimizationIgnored) "已忽略" else "去设置",
                        valueColor = if (deviceInfo.batteryOptimizationIgnored) Good else TextSecondary,
                        onClick = onOpenBattery,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "荣耀 / MagicOS：设置 → 应用 → 应用启动管理 → FakeScreen，关闭自动管理并允许自启动、关联启动、后台活动。不同系统版本路径可能不同。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("Shizuku（可选）")
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "状态",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = shizukuStatus.label,
                            color = shizukuColor(shizukuStatus),
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = shizukuDetail,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (shizukuStatus == ShizukuManager.Status.NO_PERMISSION) {
                            OutlinedButton(onClick = onRequestShizuku) {
                                Text("授权 Shizuku", fontSize = 13.sp)
                            }
                        }
                        if (shizukuStatus == ShizukuManager.Status.NOT_RUNNING ||
                            shizukuStatus == ShizukuManager.Status.NOT_INSTALLED
                        ) {
                            OutlinedButton(onClick = onOpenShizukuApp) {
                                Text("打开 Shizuku", fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "FakeScreen 只在“PIN 错误次数过多”时通过 Shizuku 执行系统电源键，让屏幕真正熄灭。密码验证、悬浮窗和核心会话都不依赖 Shizuku。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("设备与兼容性")
                SectionCard {
                    ClickableRow(
                        title = deviceInfo.manufacturer + " " + deviceInfo.model,
                        subtitle = "Android ${deviceInfo.androidRelease} · API ${deviceInfo.sdkInt}" +
                            (deviceInfo.magicOsVersion?.let { " · MagicOS $it" } ?: ""),
                        value = null,
                    )
                    HorizontalDivider(color = Line.copy(alpha = 0.6f))
                    ClickableRow(
                        title = "兼容性报告",
                        subtitle = "屏幕尺寸、权限、Shizuku、已知限制",
                        value = "查看",
                        onClick = onOpenCompatibility,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("安全")
                SectionCard {
                    ClickableRow(
                        title = "紧急退出方式",
                        subtitle = "忘记 PIN 时如何安全退出（不验证密码）",
                        value = "查看",
                        onClick = { showEmergency = true },
                    )
                    HorizontalDivider(color = Line.copy(alpha = 0.6f))
                    ClickableRow(
                        title = "关于 FakeScreen",
                        subtitle = "版本 ${BuildConfig.VERSION_NAME} · 不收集、不上传任何数据",
                        value = null,
                    )
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showEmergency) {
        AlertDialog(
            onDismissRequest = { showEmergency = false },
            containerColor = Panel,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("紧急退出方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. 下拉通知栏，点“紧急结束”。这是应用提供的官方无密码退出方式。")
                    Text("2. 长按电源键关机或重启。系统电源键始终有效，模拟息屏不会拦截。")
                    Text("3. 连接电脑执行：adb shell am force-stop com.fakescreen.app。下次打开应用时会自动检查并恢复亮度。")
                    Text("4. 等待“最长会话时长”到期，应用会自动退出并恢复亮度。")
                    Text("5. PIN 连续错误 10 次时，若 Shizuku 可用，应用会调用系统电源键真正熄屏；不会阻止紧急通话。")
                    Text(
                        "FakeScreen 不是系统锁屏，不会拦截来电、闹钟或紧急呼叫。",
                        color = TextPrimary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showEmergency = false }) {
                    Text("知道了", color = Accent)
                }
            },
        )
    }
}

private fun shizukuColor(status: ShizukuManager.Status): Color = when (status) {
    ShizukuManager.Status.READY -> Good
    ShizukuManager.Status.NO_PERMISSION -> Warn
    ShizukuManager.Status.ERROR -> Bad
    else -> TextSecondary
}
