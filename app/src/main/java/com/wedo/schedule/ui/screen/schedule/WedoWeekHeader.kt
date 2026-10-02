package com.wedo.schedule.ui.screen.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wedo.schedule.R
import com.wedo.schedule.ui.component.WedoIconButton
import com.wedo.schedule.ui.component.wedoPress
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme

/**
 * 课表页顶栏（2026-10-02 真机反馈**重做**）。
 *
 * 用户原话：「把第 5 周和这个日期放到左上角，跟那个加号和三个点放在同一个高度」。
 * 于是从「两行（动作行 + 大标题块）」压成**单行**：
 *
 * ```
 * ┌──────────────────────────────────────────┐
 * │ 第 5 周                       ＋    ⋯     │
 * │ 9月28日 – 10月4日                          │
 * └──────────────────────────────────────────┘
 * ```
 *
 * 移除的东西及理由：
 *  - **大标题块（LargeTitle 34sp + 滚动收起）**：占掉近 90dp 垂直空间，用户嫌「上面留白太多」；
 *  - **左右翻周箭头**：换周靠左右滑动即可，与 WakeUp 一致，箭头纯属冗余；
 *  - 「第 N 周」区块**整体可点** → 打开全屏周次选择器（20 周时靠滑动翻太慢，
 *    选择器保留，只是不再占 ⋯ 菜单项）。
 *
 * 动作仍在导航区右侧，属 HIG 允许的「内容层动作」，与「底栏只导航」不冲突。
 */
@Composable
fun WedoWeekHeader(
    week: Int,
    startDate: String,
    onOpenPicker: () -> Unit,
    onAdd: () -> Unit,
    onShare: () -> Unit
) {
    val colors = WedoTheme.colors
    val dateRange = weekDateRangeLong(startDate, week)

    Row(
        Modifier.fillMaxWidth()
            .height(WedoAppleDimensions.minTouchTarget)
            .padding(start = WedoAppleDimensions.pageMargin, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：周次 + 日期范围（整块可点 → 全屏周次选择器）
        Column(
            Modifier
                .weight(1f)
                .wedoPress { onOpenPicker() }
        ) {
            Text(
                stringResource(R.string.week_header_title, week),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (dateRange.isNotBlank()) {
                Text(
                    dateRange,
                    style = WedoAppleType.caption1(),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 右：添加课程 / 更多
        // 右侧：＋（添加/导入）与 分享（2026-10-02 真机反馈：原「⋯」底部弹窗改为直达导出页）
        WedoIconButton(Icons.Outlined.Add, stringResource(R.string.schedule_add_course), onClick = onAdd)
        Spacer(Modifier.width(2.dp))
        WedoIconButton(Icons.Outlined.IosShare, stringResource(R.string.schedule_more_export), onClick = onShare)
        Spacer(Modifier.size(4.dp))
    }
}

/**
 * 第 N 周的日期范围（如「9月28日 – 10月4日」）。
 *
 * 原定义在 `SemesterOverview.kt` 内，该文件已随「三视图」一并删除（2026-10-02 真机反馈），
 * 遂迁至本文件——顶栏是它唯一的消费方。
 */
@Composable
internal fun weekDateRangeLong(startDate: String, week: Int): String {
    val start = runCatching { com.wedo.schedule.util.DateUtils.dateOfWeek(startDate, week, 1) }.getOrNull()
        ?: return ""
    val end = runCatching { com.wedo.schedule.util.DateUtils.dateOfWeek(startDate, week, 7) }.getOrNull()
        ?: return ""
    return stringResource(
        R.string.week_range_long_format,
        start.monthValue, start.dayOfMonth, end.monthValue, end.dayOfMonth
    )
}
