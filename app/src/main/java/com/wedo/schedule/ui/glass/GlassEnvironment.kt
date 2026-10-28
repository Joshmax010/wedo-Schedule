package com.wedo.schedule.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.util.CourseColorUtil

/**
 * BASE 环境层：中性底色 + 固定明度场。
 *
 * 不采样，也不做逐帧动画；同一分辨率/主题下的绘制结果必须一致。
 * 纹理幅度刻意压得很低，只负责让 G0 卡片有“可感知的底”，
 * 不得变成彩色光斑或装饰背景。
 */
@Composable
fun WedoEnvironmentLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = CourseColorUtil.isPaletteDark(WedoTheme.palette)
    val base = if (dark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val topWash = if (dark) 0.012f else 0.028f
    val cornerWash = if (dark) 0.016f else 0.022f

    Box(
        modifier = modifier.drawWithCache {
            val vertical = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = topWash),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.62f,
            )
            val corner = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = cornerWash),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.18f, 0f),
                radius = size.width * 0.95f,
            )
            onDrawBehind {
                drawRect(base)
                drawRect(vertical)
                drawRect(corner)
            }
        },
        content = content,
    )
}
