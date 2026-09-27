package com.wedo.schedule.ui.screen.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.component.CourseDetailSheet
import com.wedo.schedule.ui.component.SectionHead
import com.wedo.schedule.ui.component.wedoCourseColor
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoCourseBlockColors
import com.wedo.schedule.ui.theme.noRippleClickable
import com.wedo.schedule.ui.theme.wedoCourseBlockColors
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.CourseColorUtil
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils
import java.time.LocalDate

@Composable
fun TodayScreen(
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val today = LocalDate.now()
    val dayOfWeek = DateUtils.todayDayOfWeek(today)
    val actualWeek = state.currentTable?.let { DateUtils.currentWeek(it.startDate, today) } ?: state.currentWeek
    // 学期外感知: BEFORE_START/AFTER_END 时今日课不按周过滤展示
    val semesterStatus = state.currentTable?.let {
        DateUtils.semesterStatus(it.startDate, it.maxWeek, today)
    } ?: DateUtils.SemesterStatus.IN_RANGE
    val isOutOfSemester = semesterStatus != DateUtils.SemesterStatus.IN_RANGE
    val todayCourses = if (isOutOfSemester) emptyList() else state.courses.filter {
        it.day == dayOfWeek && it.inWeek(actualWeek)
    }.sortedBy { it.startNode }

    // v7.10.10 今日页冲突分栏 — 与周视图同一引擎同一分组(weekLaneRows):
    // 冲突区域一行内并排分栏(栏间浅细竖线), 无冲突课整宽单行。
    // 分组在 LazyColumn 外 remember(LazyListScope 非 composable 上下文)。
    val laneRows = remember(todayCourses) {
        com.wedo.schedule.util.ConflictLayoutEngine.weekLaneRows(todayCourses)
    }

    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }

    // Dock 悬浮底栏: 滚动尾部多留 Dock 总高, 最后一项能滚到 Dock 上方(FAB 语义)
    val navExtra = com.wedo.schedule.ui.component.LocalNavExtraBottomPadding.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WedoTheme.colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + navExtra
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TodayHeader(date = today, week = actualWeek, count = todayCourses.size, semesterStatus = semesterStatus) }

        if (todayCourses.isEmpty()) {
            item { EmptyToday(semesterStatus = semesterStatus) }
        } else {
            item {
                SectionHead(title = stringResource(R.string.widget_today_label), action = stringResource(R.string.n_periods, todayCourses.size))
            }
            // v7.10.10 今日页冲突分栏 — 分组已提至 LazyColumn 外
            laneRows.forEach { row ->
                if (row.laneCount == 1) {
                    item(key = row.courses[0].id) {
                        TodayCourseCard(
                            course = row.courses[0],
                            timeJson = state.currentTable?.timeJson,
                            onClick = { selectedCourse = row.courses[0] },
                            groupRows = todayCourses.filter { it.groupId == row.courses[0].groupId }
                        )
                    }
                } else {
                    item(key = "conflict-${row.courses.first().id}") {
                        val laneGap = 6.dp
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(laneGap)
                        ) {
                            repeat(row.laneCount) { li ->
                                if (li > 0) {
                                    // 栏间浅细竖线 — 与周视图分栏同款
                                    Box(
                                        modifier = Modifier
                                            .width(0.5.dp)
                                            .fillMaxHeight()
                                            .background(
                                                WedoTheme.colors.onSurface.copy(alpha = WedoTheme.Alpha.hairline)
                                            )
                                    )
                                }
                                val laneCourses = row.courses.filter { row.laneOf[it.id] == li }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    laneCourses.forEach { laneCourse ->
                                        TodayCourseCard(
                                            course = laneCourse,
                                            timeJson = state.currentTable?.timeJson,
                                            onClick = { selectedCourse = laneCourse },
                                            groupRows = todayCourses.filter { it.groupId == laneCourse.groupId }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 详情 Bottom Sheet — 与课表页同一组件同一交互
    CourseDetailSheet(
        course = selectedCourse,
        timeString = selectedCourse?.let { it.nodeString(LocalContext.current) },
        onDismiss = { selectedCourse = null },
        onEdit = { course ->
            selectedCourse = null
            onEditCourse(course)
        }
    )
}

@Composable
private fun TodayHeader(date: LocalDate, week: Int, count: Int, semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE) {
    val colors = WedoTheme.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WedoAppleShapes.card)
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.today_today),
            style = WedoAppleType.footnote(),
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.date_long_format, date.monthValue, date.dayOfMonth),
                style = WedoAppleType.largeTitle(),
                color = colors.onSurface
            )
            Text(
                text = DateUtils.localizedDay(date.dayOfWeek.value, context),
                style = WedoAppleType.title3(),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 学期外: 周次 chip 换学期状态, 不再显示误导性的"第 1 周"
            // 周次 chip 用强调色底(唯一有彩色的 metadata), 学期状态用中性灰 pill ——
            // 原来三个 chip 各占一个 M3 container 色(primary/secondary/tertiary),
            // 是 Material 的「多色 chip」语汇, iOS 的元信息胶囊只有一档中性灰。
            when (semesterStatus) {
                DateUtils.SemesterStatus.BEFORE_START ->
                    Stat(label = stringResource(R.string.semester_not_started), highlight = false)
                DateUtils.SemesterStatus.AFTER_END ->
                    Stat(label = stringResource(R.string.semester_ended), highlight = false)
                else ->
                    Stat(label = stringResource(R.string.schedule_current_week, week), highlight = true)
            }
            Stat(
                label = if (count == 0) stringResource(R.string.no_course) else stringResource(R.string.n_course_periods, count),
                highlight = false
            )
        }
    }
}

/**
 * 元信息胶囊。
 *
 * @param highlight true = 强调色实底 + 白字（当前周次），false = 中性灰底 + 次级文字色。
 * 不再接受任意 bg/fg —— 之前调用方各传一个 M3 container 色，导致同屏出现三种颜色。
 */
@Composable
private fun Stat(label: String, highlight: Boolean) {
    val colors = WedoTheme.colors
    val bg = if (highlight) WedoApple.accent else colors.surfaceContainerHighest
    val fg = if (highlight) Color.White else colors.onSurfaceVariant
    Text(
        text = label,
        style = WedoAppleType.caption1(),
        color = fg,
        modifier = Modifier
            .clip(WedoAppleShapes.capsule)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun EmptyToday(semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE) {
    val colors = WedoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WedoAppleShapes.card)
            .background(colors.surfaceContainer)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 空状态图标用中性灰（不染强调色），避免空屏也在喊
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.hairline),
            modifier = Modifier.size(44.dp)
        )
        val title: String
        when (semesterStatus) {
            DateUtils.SemesterStatus.BEFORE_START -> title = stringResource(R.string.semester_not_started)
            DateUtils.SemesterStatus.AFTER_END -> title = stringResource(R.string.semester_ended)
            else -> title = stringResource(R.string.schedule_no_course_today)
        }
        Text(text = title, style = WedoAppleType.title3(), color = colors.onSurface)
        Text(
            text = if (semesterStatus == DateUtils.SemesterStatus.IN_RANGE) stringResource(R.string.today_no_course)
            else stringResource(R.string.today_semester_out_hint),
            style = WedoAppleType.subheadline(),
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayCourseCard(course: CourseEntity, timeJson: String? = null, onClick: (() -> Unit)? = null, groupRows: List<CourseEntity> = listOf(course)) {
    val colors = WedoTheme.colors
    val palette = WedoTheme.palette
    val context = LocalContext.current
    // Apple 化：与课表页共用同一套配色语汇（淡底 + 左色条 + 同色系字）。
    // 原来是整卡铺饱和色 + 白字，与课表页改完后会明显不一致。
    val isDarkPalette = CourseColorUtil.isPaletteDark(palette)
    val colorless = AppPrefs.isCourseColorless(context)
    val block = if (colorless && !CourseColorUtil.hasCustomColor(course)) {
        WedoCourseBlockColors(
            tint = colors.surfaceContainerHigh,
            bar = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive),
            title = colors.onSurface,
            subtitle = colors.onSurfaceVariant
        )
    } else {
        wedoCourseBlockColors(
            base = wedoCourseColor(course, isDarkPalette),
            dark = isDarkPalette,
            surface = colors.surfaceContainerLow
        )
    }
    val fg = block.title
    val time = if (course.ownTime && course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
        "${course.startTime}-${course.endTime}"
    } else {
        timeJson?.let { TimeTableUtils.courseTimeString(course.startNode, course.step, it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WedoAppleShapes.continuous(WedoAppleDimensions.cardCorner))
            .background(block.tint)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左侧 4px 实色条 —— 课程辨识载体
        Box(
            Modifier
                .width(WedoAppleDimensions.courseBarWidth)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(block.bar)
        )
        // 时间槽 — 固定宽度避免 "10:20-12:45" 被截断
        Column(
            modifier = Modifier.width(76.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = course.shortNodeString(context),
                style = WedoAppleType.subheadline().copy(fontWeight = FontWeight.SemiBold),
                color = fg
            )
            if (time != null) {
                Text(
                    text = time,
                    style = WedoAppleType.caption1(),
                    color = block.subtitle,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                style = WedoAppleType.headline(),
                color = fg,
                maxLines = 2
            )
            if (course.teacher.isNotBlank() || course.room.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val meta = buildString {
                    if (course.teacher.isNotBlank()) append(course.teacher)
                    if (course.room.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(course.room)
                    }
                }
                Text(
                    text = meta,
                    style = WedoAppleType.caption1(),
                    color = fg.copy(alpha = WedoTheme.Alpha.highContent)
                )
            }
        }
    }
}

// findCourseTime 空函数已删（死代码清理: 注释自述被 TimeTableUtils.courseTimeString 取代, 全库零调用）。
// pickCourseColor / isPaletteDark / hslToColor 三函数已收敛至 util/CourseColorUtil.kt（决策 D3 单一事实来源）