package com.wedo.schedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.noRippleClickable
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme

/**
 * 全 app 唯一的 chip 组件（2026-10-06 真机反馈：不要第二套主题逻辑）。
 *
 * 此前项目里有两套长相：
 *  - 设置页的冲突样式 chips（自研，淡底 + accent 字）
 *  - `SmartPeriodEditor` 的 Material `FilterChip`（M3 容器色）
 *
 * 统一到本组件后，选中态一律 **accent 实底 + 白字**（与 [SegmentedSwitcher]、
 * [WedoToggle] 同源），未选中态为中性底 + 描边。
 *
 * @param selected 是否处于选中/激活态。**只有可点选中的选项才该传 true**；
 *   「+ 添加」这类动作按钮传 false，否则会谎报状态。
 */
@Composable
fun WedoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null
) {
    val colors = WedoTheme.colors
    Box(
        modifier = modifier
            .height(WedoChipHeight)
            .clip(CapsuleShape)
            .background(if (selected) WedoApple.accent else colors.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = if (selected) WedoApple.accent else colors.outlineVariant,
                shape = CapsuleShape
            )
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = leadingIconTint ?: if (selected) Color.White else colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Box(Modifier.size(4.dp))
            }
            Text(
                text = label,
                style = WedoAppleType.caption1(),
                color = if (selected) Color.White else colors.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * 固定高度 —— 两个状态必须**几何完全一致**，只换颜色。
 *
 * 曾经用 `heightIn(min = 36.dp)` + 纵向 padding，结果选中/未选中的实际高度
 * 因描边层叠而不一致，一排 chips 看着参差（真机反馈）。
 */
private val WedoChipHeight = 34.dp

private val CapsuleShape = WedoAppleShapes.capsule
