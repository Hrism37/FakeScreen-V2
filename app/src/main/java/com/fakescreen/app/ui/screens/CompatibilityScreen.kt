package com.fakescreen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakescreen.app.ui.components.PrimaryButton
import com.fakescreen.app.ui.components.TopBar
import com.fakescreen.app.ui.theme.Accent
import com.fakescreen.app.ui.theme.Ink
import com.fakescreen.app.ui.theme.Panel
import com.fakescreen.app.ui.theme.TextSecondary

@Composable
fun CompatibilityScreen(report: String, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

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
            TopBar(title = "兼容性报告", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = report,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Panel)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = if (copied) "已复制到剪贴板" else "复制报告",
                    onClick = {
                        clipboard.setText(AnnotatedString(report))
                        copied = true
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "报告只包含设备与权限状态，不包含密码、亮度历史或个人信息。",
                    color = Accent.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
