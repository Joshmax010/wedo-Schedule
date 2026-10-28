package com.wedo.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple 设置风格的彩色图标块（全 app 列表行统一入口）。
 *
 * 26×26 / 圆角 7 / 白色 glyph —— iOS 设置列表行最鲜明的识别点。
 * 顶栏、底栏、按钮等「操作图标」仍走单色强调色，不套这个块。
 */
object WedoIconColors {
    val blue = Color(0xFF007AFF)
    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9500)
    val purple = Color(0xFFAF52DE)
    val red = Color(0xFFFF3B30)
    val pink = Color(0xFFFF2D55)
    val indigo = Color(0xFF5856D6)
    val gray = Color(0xFF8E8E93)
}

@Composable
fun WedoIconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    corner: Dp = 7.dp,
    glyphSize: Dp = 15.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(glyphSize)
        )
    }
}
