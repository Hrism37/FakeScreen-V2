package com.fakescreen.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakescreen.app.ui.theme.Bad
import com.fakescreen.app.ui.theme.TextPrimary

@Composable
fun PinDots(
    length: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    enabled: Boolean = true,
) {
    val activeColor = when {
        error -> Bad
        !enabled -> Color(0xFF4A5361)
        else -> TextPrimary
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length.coerceAtLeast(1)) { index ->
            if (index < filled) {
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(activeColor)
                )
            } else {
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .border(1.4.dp, Color(0xFF3A4351), CircleShape)
                )
            }
        }
    }
}

@Composable
fun PinKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 72.dp,
    gap: Dp = 14.dp,
) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "\u232B"),
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { label ->
                    if (label.isEmpty()) {
                        Spacer(Modifier.size(buttonSize))
                    } else {
                        KeyButton(
                            label = label,
                            size = buttonSize,
                            enabled = enabled,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (label == "\u232B") onBackspace() else onDigit(label[0])
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) Color(0xFF0C1015) else Color(0xFF080A0D))
            .border(
                width = 1.dp,
                color = if (enabled) Color(0xFF202834) else Color(0xFF141A22),
                shape = CircleShape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = Color(0xFFA8C0FF)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else Color(0xFF4A5361),
            fontSize = if (label == "\u232B") 22.sp else 26.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
