package com.wedo.schedule.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme

/**
 * 全 app 唯一的对话框入口（L-20 / Q-71 / Q-72）。
 *
 * **为什么必须统一包装**：15 处 `AlertDialog` 各自写样式会出现 15 种细微差异
 * （圆角不一致、Material 抬高色残留、rim 深浅不一）。这是「禁止第二套实现」
 * 在对话框上的具体化。
 *
 * **材质结论：恒 G0，且完全不透**（卷 05b §5.21 + 2026-10-28 定稿）。
 * 理由链：
 *  1. 对话框跨窗口 → 无法实时采样；
 *  2. 点击即弹，无预兆 → 快照必然滞后 → 一次可见材质跳变；
 *  3. 对话框出现频率高 → 跳变被反复看到；
 *  4. 面积小、下方被 scrim 盖住 → 玻璃收益本来就低。
 *
 * 一次材质跳变比「一直不做玻璃」更糟，所以这里主动放弃采样。
 *
 * 「浮起来」靠三件事表达，不靠透明：**白卡片底 + 14pt 圆角 + 系统 scrim**。
 */
@Composable
fun WedoDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    containerColor: Color = if (WedoApple.isDark) Color(0xFF1C1C1E) else Color.White,
    titleContentColor: Color = WedoTheme.colors.onSurface,
    textContentColor: Color = WedoTheme.colors.onSurfaceVariant,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = containerColor,
        // Material 默认的 tonalElevation 会在深色下产生一块抬高的灰，
        // 与 G0 的「靠 rim 表达厚度」冲突；显式归零。
        tonalElevation = 0.dp,
        // iOS alert 圆角 14pt；连续曲率换算后取 12dp 视觉半径。
        shape = WedoAppleShapes.continuous(12.dp),
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
    )
}

/**
 * 对话框标题。统一字重与字号，避免 15 处各写一套。
 */
@Composable
fun WedoDialogTitle(text: String) {
    Text(
        text = text,
        style = WedoAppleType.headline(),
        color = WedoTheme.colors.onSurface,
        textAlign = TextAlign.Start,
    )
}

/**
 * 对话框按钮。统一用 `TextButton` + 强调色，取消 Material 的默认内边距差异。
 */
@Composable
fun WedoDialogConfirm(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, color = WedoTheme.colors.primary)
    }
}

@Composable
fun WedoDialogDismiss(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, color = WedoTheme.colors.onSurfaceVariant)
    }
}

/** 对话框正文的默认行距（供 15 处复用同一套排版）。 */
val WedoDialogBodySpacing: Dp = WedoAppleDimensions.sectionGap
