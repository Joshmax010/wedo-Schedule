package com.wedo.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.noRippleClickable

/**
 * iOS 分段控件（灰色滑块，不是蓝色药丸）。
 *
 * 用于设置类页面：外观模式、启动默认页、课程时间显示等。
 * 与课表页的 `SegmentedSwitcher` 区分：后者是页面级的视图切换，保留强调色。
 */
@Composable
fun WedoSegmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WedoTheme.colors
    val dark = com.wedo.schedule.ui.theme.WedoApple.isDark
    val track = if (dark) Color(0xFF2C2C2E) else Color(0xFFE9E9EA)
    val thumb = if (dark) Color(0xFF636366) else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(track)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (sel) thumb else Color.Transparent)
                    .noRippleClickable { onSelect(key) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = WedoAppleType.caption1().copy(
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (sel) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
