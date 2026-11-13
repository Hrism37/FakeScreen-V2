package com.fakescreen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakescreen.app.core.ScreenMode
import com.fakescreen.app.ui.components.PrimaryButton
import com.fakescreen.app.ui.components.SectionCard
import com.fakescreen.app.ui.components.TopBar
import com.fakescreen.app.ui.theme.Accent
import com.fakescreen.app.ui.theme.Ink
import com.fakescreen.app.ui.theme.Line
import com.fakescreen.app.ui.theme.Panel
import com.fakescreen.app.ui.theme.TextPrimary
import com.fakescreen.app.ui.theme.TextSecondary
import com.fakescreen.app.ui.theme.Warn

@Composable
fun ModeSelectScreen(
    current: ScreenMode,
    onSelect: (ScreenMode) -> Unit,
    onBack: () -> Unit,
) {
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
            TopBar(title = "默认模式", onBack = onBack)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "选择模拟息屏的显示方式。修改只影响下一次会话。纯黑模式进入后只有黑屏，轻触才会显示 PIN 键盘。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
                ModeCard(
                    mode = ScreenMode.PURE_BLACK,
                    tag = "OLED",
                    description = "全屏纯黑覆盖，OLED 屏幕的黑色像素不发光。不读取、不修改系统亮度。",
                    bullets = listOf(
                        "最稳定：只需要悬浮窗权限",
                        "保持进入前的屏幕亮度",
                        "适合 OLED，也适用于任何屏幕",
                    ),
                    selected = current == ScreenMode.PURE_BLACK,
                    onClick = { onSelect(ScreenMode.PURE_BLACK) },
                )
                Spacer(Modifier.height(14.dp))
                ModeCard(
                    mode = ScreenMode.DIM,
                    tag = "LCD / OLED",
                    description = "在纯黑覆盖的基础上尽可能降低屏幕亮度，减少 LCD 背光漏光。",
                    bullets = listOf(
                        "优先使用窗口级亮度，不写系统设置",
                        "可选系统级亮度备用，退出时恢复原值",
                        "亮度控制失败时自动降级为纯黑模式",
                    ),
                    selected = current == ScreenMode.DIM,
                    onClick = { onSelect(ScreenMode.DIM) },
                )
                Spacer(Modifier.height(14.dp))
                ModeCard(
                    mode = ScreenMode.TRUE_OFF,
                    tag = "SHIZUKU",
                    description = "通过 Shizuku 的 shell 用户服务执行系统电源键，让屏幕真正熄灭，而不是画一层黑色。",
                    bullets = listOf(
                        "需要 Shizuku 已安装、运行并授权",
                        "熄屏后按电源键唤醒，设备锁屏仍由系统管理",
                        "Shizuku 不可用时自动降级为纯黑覆盖",
                    ),
                    selected = current == ScreenMode.TRUE_OFF,
                    onClick = { onSelect(ScreenMode.TRUE_OFF) },
                )
                Spacer(Modifier.height(18.dp))
                SectionCard {
                    Text(
                        text = "亮度最低值不会设为 0。窗口级亮度使用 1%，系统级亮度最低为 4/255，避免屏幕进入无法恢复的状态。真熄屏模式不修改亮度。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.height(22.dp))
                PrimaryButton(text = "完成", onClick = onBack)
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: ScreenMode,
    tag: String,
    description: String,
    bullets: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Panel else Panel.copy(alpha = 0.75f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Accent else Line,
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mode.title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = tag,
                        color = Accent,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Accent.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            Spacer(Modifier.size(14.dp))
            SelectionMark(selected)
        }
        Spacer(Modifier.height(14.dp))
        bullets.forEach { bullet ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) Accent else TextSecondary)
                )
                Spacer(Modifier.size(9.dp))
                Text(
                    text = bullet,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        if (mode == ScreenMode.DIM) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "部分机型可能忽略窗口级亮度；此时可在设置中开启系统级备用。",
                color = Warn,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (selected) Accent else Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Accent)
            )
        }
    }
}
