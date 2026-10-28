package com.wedo.schedule.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.noRippleClickable

/**
 * iOS 设置行：彩色图标块 + 标签 + 当前值 + 右侧控件/chevron。
 *
 * 分隔线从文字起点开始（左 inset 50dp），与 iOS 设置一致。
 * 开关等自定义右侧控件通过 [trailing] 传入，此时不再画 chevron。
 */
@Composable
fun WedoListRow(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val colors = WedoTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.noRippleClickable(onClick) else Modifier)
                .heightIn(min = 44.dp)
                .padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WedoIconTile(icon, iconColor)
            Text(
                label,
                Modifier.weight(1f).padding(start = 11.dp),
                style = WedoAppleType.body(),
                color = colors.onSurface
            )
            if (value != null) {
                Text(value, style = WedoAppleType.body(), color = colors.onSurfaceVariant)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            } else if (onClick != null) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp).size(15.dp),
                    tint = colors.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = 50.dp),
                thickness = 0.5.dp,
                color = colors.outlineVariant
            )
        }
    }
}
