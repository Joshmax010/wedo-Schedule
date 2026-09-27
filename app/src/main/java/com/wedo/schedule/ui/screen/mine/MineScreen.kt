package com.wedo.schedule.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.noRippleClickable

@Composable
fun MineScreen(
    viewModel: ScheduleViewModel = viewModel(),
    onOpenAllTables: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenGeneral: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val colors = WedoTheme.colors
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        // 底栏占位: 滚动尾部多留标签栏高度, 最后一行才能完全滚出
        val navExtra = com.wedo.schedule.ui.component.LocalNavExtraBottomPadding.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = WedoAppleDimensions.pageMargin,
                end = WedoAppleDimensions.pageMargin,
                top = 16.dp,
                bottom = 16.dp + navExtra
            ),
            verticalArrangement = Arrangement.spacedBy(WedoAppleDimensions.sectionGap)
        ) {
            // Large Title —— 与课表页同一档（34pt Bold），不再是 M3 headlineMedium
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tab_mine),
                        style = WedoAppleType.largeTitle(),
                        color = colors.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.mine_subtitle),
                        style = WedoAppleType.subheadline(),
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // 数据统计卡
            item {
                StatsCard(
                    tableCount = state.tables.size,
                    courseCount = state.courses.distinctBy { it.courseName }.size,
                    week = state.currentWeek
                )
            }

            // 设置项 (5 个导航项扁平列表)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WedoAppleShapes.card)
                        .background(colors.surfaceContainer)
                ) {
                    SettingsItem(icon = Icons.Outlined.Edit, label = stringResource(R.string.all_tables), onClick = onOpenAllTables)
                    Divider()
                    SettingsItem(icon = Icons.Outlined.Share, label = stringResource(R.string.mine_export), onClick = onOpenExport)
                    Divider()
                    SettingsItem(icon = Icons.Outlined.Palette, label = stringResource(R.string.mine_appearance), onClick = onOpenAppearance)
                    Divider()
                    SettingsItem(icon = Icons.Outlined.Tune, label = stringResource(R.string.mine_general), onClick = onOpenGeneral)
                    Divider()
                    SettingsItem(icon = Icons.Outlined.Info, label = stringResource(R.string.about_title), onClick = onOpenAbout)
                }
            }

        }
    }
}

@Composable
private fun StatsCard(tableCount: Int, courseCount: Int, week: Int) {
    val colors = WedoTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(WedoAppleShapes.card).background(colors.surfaceContainer).padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(value = tableCount.toString(), label = stringResource(R.string.mine_stat_tables))
        Divider(vertical = true)
        StatItem(value = courseCount.toString(), label = stringResource(R.string.mine_stat_courses))
        Divider(vertical = true)
        StatItem(value = week.toString(), label = stringResource(R.string.mine_stat_week))
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    val colors = WedoTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 数值用 accent（Apple 统计数值着色），字号走 Apple 档位，不用 Bold headlineMedium
        Text(text = value, style = WedoAppleType.title2(), color = WedoApple.accentText)
        Text(text = label, style = WedoAppleType.caption1(), color = colors.onSurfaceVariant)
    }
}

/**
 * iOS 设置行。
 *
 * 与之前的 Material 写法三处差别：
 *  1. 图标**不再套 40dp 圆角色块**（`primaryContainer` 底 + `onPrimaryContainer` 图标）
 *     —— iOS 设置行就是一个裸的彩色 SF Symbol，加色块是 Android 的容器语汇
 *  2. 行高按 HIG 撑到 44pt 最小触控
 *  3. 可跳转行右侧补 chevron —— iOS 的「这行能进去」是靠箭头表达的，
 *     之前五行能点但没有任何指引，属于交互暗示缺失
 */
@Composable
internal fun SettingsItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    val colors = WedoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WedoAppleDimensions.minRowHeight)
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = WedoApple.accentIcon,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = WedoAppleType.body(),
            color = colors.onSurface,
            modifier = Modifier.weight(1f).padding(start = 14.dp)
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 分隔线。
 *
 * 横线左侧内缩 52dp = 16dp 页边距 + 22dp 图标 + 14dp 间距，
 * 让首字符与上方文字左缘对齐 —— 与 iOS 设置列表一致。
 * （此前缩进 72dp 是因为图标外面还套着 40dp 色块；色块去掉后必须同步收窄，
 * 否则分隔线会明显短一截、对不齐文字。）
 */
@Composable
private fun Divider(vertical: Boolean = false) {
    val colors = WedoTheme.colors
    if (vertical) {
        androidx.compose.material3.VerticalDivider(
            Modifier.height(36.dp).width(WedoAppleDimensions.hairline),
            color = colors.outline.copy(alpha = WedoTheme.Alpha.hairline)
        )
    } else {
        androidx.compose.material3.HorizontalDivider(
            Modifier.padding(start = 52.dp),
            thickness = WedoAppleDimensions.hairline,
            color = colors.outline.copy(alpha = WedoTheme.Alpha.hairline)
        )
    }
}
