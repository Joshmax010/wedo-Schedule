package com.wedo.schedule.ui.screen.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.component.CourseDetailSheet
import com.wedo.schedule.ui.component.SectionHead
import com.wedo.schedule.ui.component.WedoPrimaryButton
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
import com.wedo.schedule.util.NextClass
import com.wedo.schedule.util.NextClassDecider
import com.wedo.schedule.util.TimeTableUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    onOpenImport: () -> Unit = {},
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    // REQ-P1-02: 倒计时 / 日期 / 周次共用同一「当前时刻」并每 ≤60s 刷新，
    // 避免"今天的课"与"倒计时"跨 00:00 时各自取到不同 now。
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            kotlinx.coroutines.delay(60_000)
        }
    }
    // REQ-P1-05: 无任何课表 → 全屏引导（区别于"本日无课"三态）。
    if (state.tables.isEmpty()) {
        NoTableGuide(onOpenImport = onOpenImport)
        return
    }
    val today = now.toLocalDate()
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

    // REQ-P1-02 「下一节课」倒计时 —— 判定逻辑全在纯函数 NextClassDecider，此处只把 now 递进去。
    val timeJson = state.currentTable?.timeJson ?: ""
    val nextClass = NextClassDecider.decide(todayCourses, now, timeJson)
    // REQ-P1-04 三态空态之一: 今天无课但本周仍有课 → 给「本周还有 N 天有课」的上下文。
    // 仅统计**今天之后**（day > 今天）的余下天，避免把「今天」算进去造成误导。
    val remainingCourseDays = if (isOutOfSemester) 0 else state.courses
        .filter { it.inWeek(actualWeek) && it.day > dayOfWeek }
        .map { it.day }
        .distinct()
        .size

    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }

    // Dock 悬浮底栏: 滚动尾部多留 Dock 总高, 最后一项能滚到 Dock 上方(FAB 语义)
    val navExtra = com.wedo.schedule.ui.component.LocalNavExtraBottomPadding.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WedoTheme.colors.background)
            // 2026-10-06：上滑隐藏底栏
            .nestedScroll(com.wedo.schedule.ui.component.LocalTabBarVisibilityState.current.scrollConnection),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + navExtra
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 2026-10-02 真机反馈：头部去卡片——大日期直接落在页面底色上，
        // 周次/节数压成一行小字，倒计时降级为一行 accent 小字。
        item {
            TodayFlatHeader(
                date = today,
                week = actualWeek,
                count = todayCourses.size,
                next = nextClass,
                semesterStatus = semesterStatus
            )
        }

        if (todayCourses.isEmpty()) {
            item { EmptyToday(semesterStatus = semesterStatus, remainingDays = remainingCourseDays) }
        } else {
            // 方案 B · 时间轴：课程按真实起止时间定位，含整点刻度与「现在」指示线。
            item(key = "today-timeline") {
                TodayTimeline(
                    rows = laneRows,
                    timeJson = timeJson,
                    now = now.toLocalTime(),
                    onCourseClick = { selectedCourse = it }
                )
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

/**
 * 今天页头部（2026-10-02 真机反馈**重做**：去卡片）。
 *
 * 原实现是一张 `surfaceContainer` 白卡片（"今天"/大日期/两个 chip），用户原话
 * 「底部一个白色卡片样的形式，我觉得并不好看，因为它属于当天的提醒信息」。
 * 现在整块**直接落在页面底色上**：
 *
 * ```
 * 10月2日 周五
 * 第 5 周 · 2 节课
 * 下一节 · 3-4 节 · 12 分钟后      ← accent 小字，倒计时不再独占卡片
 * ```
 */
@Composable
private fun TodayFlatHeader(
    date: LocalDate,
    week: Int,
    count: Int,
    next: NextClass?,
    semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE
) {
    val colors = WedoTheme.colors
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
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
        Spacer(modifier = Modifier.height(6.dp))
        // 一行状态小字：周次（或学期状态）· 节数
        val weekLabel = when (semesterStatus) {
            DateUtils.SemesterStatus.BEFORE_START -> stringResource(R.string.semester_not_started)
            DateUtils.SemesterStatus.AFTER_END -> stringResource(R.string.semester_ended)
            else -> stringResource(R.string.schedule_current_week, week)
        }
        val countLabel = if (count == 0) stringResource(R.string.no_course)
        else stringResource(R.string.n_course_periods, count)
        Text(
            text = "$weekLabel · $countLabel",
            style = WedoAppleType.footnote(),
            color = colors.onSurfaceVariant
        )
        // 倒计时降级为一行 accent 小字（活跃信息才用强调色）；
        // 仍带状态标签（「下一节课」/「正在上课」），信息量不减，只是不再独占卡片。
        val active = next != null
        val countdown = when {
            next == null -> stringResource(R.string.today_no_more_class)
            else -> {
                val label = if (next.inProgress) stringResource(R.string.today_in_progress)
                else stringResource(R.string.today_next_class)
                val detail = when {
                    next.inProgress && next.minutesUntilEnd <= 5 -> stringResource(R.string.today_running_soon_end)
                    next.inProgress -> stringResource(R.string.today_running_remaining, next.minutesUntilEnd.toInt().coerceAtLeast(0))
                    next.minutesUntilStart <= 0 -> stringResource(R.string.today_countdown_soon)
                    else -> stringResource(R.string.today_countdown, next.minutesUntilStart.toInt())
                }
                "$label · $detail"
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = countdown,
            style = WedoAppleType.footnote(),
            color = if (active) WedoApple.accentText else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyToday(
    semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE,
    remainingDays: Int = 0
) {
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
        // REQ-P1-04: 今天无课但本周仍有课 → 给「本周还有 N 天有课」上下文, 让空屏不是死路。
        if (semesterStatus == DateUtils.SemesterStatus.IN_RANGE && remainingDays > 0) {
            Text(
                text = stringResource(R.string.today_week_has_days, remainingDays),
                style = WedoAppleType.footnote(),
                color = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * REQ-P1-05 无课表全屏引导 —— 与「本日无课」三态区分：这里连课表都没有。
 *
 * 空态图标不染强调色（中性灰），页面上**唯一**彩色元素是主 CTA「从教务系统导入」，
 * 把用户直接导向 REQ-P2 的教务直连向导；文案同时提示「或文件导入 / 手动添加」。
 */
@Composable
private fun NoTableGuide(onOpenImport: () -> Unit) {
    val colors = WedoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WedoTheme.colors.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.today_no_table_title),
            style = WedoAppleType.title2(),
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.today_no_table_hint),
            style = WedoAppleType.subheadline(),
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        WedoPrimaryButton(
            text = stringResource(R.string.today_import_cta),
            onClick = onOpenImport
        )
    }
}

/**
 * 今天页**时间轴**（方案 B，2026-10-02 真机反馈采用）。
 *
 * 与「扁平列表」的差别：课程块**按真实起止时间定位**在垂直时间轴上——
 * 左侧整点刻度、一条贯穿的时间轴细线，当前时刻有一条 accent 的「现在」指示线。
 * 冲突课程在同一时间段内**并排分栏**（沿用 [ConflictLayoutEngine.weekLaneRows] 的分栏结果）。
 *
 * 几何：每分钟 [TIMELINE_DP_PER_MIN] dp（默认 1dp/分钟 = 60dp/小时），
 * 上下界对齐到整点，避免首尾悬空。
 */
private const val TIMELINE_DP_PER_MIN = 1f

/** `HH:mm` → 当日分钟数；解析失败返回 null。 */
internal fun hmToMinutes(hm: String): Int? {
    val m = Regex("""^(\d{1,2}):(\d{2})$""").find(hm.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (h > 23 || min > 59) return null
    return h * 60 + min
}

/** 课程在该 timeJson 下的 `[起, 止)` 分钟区间；取不到时间返回 null（不参与定位）。 */
internal fun courseMinuteRange(course: CourseEntity, timeJson: String): IntRange? {
    val parts = TimeTableUtils.courseTimeParts(
        course.startNode, course.step, timeJson,
        ownTime = course.ownTime, startTime = course.startTime, endTime = course.endTime
    ) ?: return null
    val s0 = hmToMinutes(parts.first) ?: return null
    val e0 = hmToMinutes(parts.second) ?: return null
    if (e0 <= s0) return null
    return s0 until e0
}

/** 时间轴上下界（整点对齐）。无可定位区间返回 null。 */
internal fun timelineBounds(ranges: List<IntRange>): Pair<Int, Int>? {
    if (ranges.isEmpty()) return null
    val lo = ranges.minOf { it.first }
    val hi = ranges.maxOf { it.last + 1 }
    return (lo / 60) * 60 to ((hi + 59) / 60) * 60
}

@Composable
private fun TodayTimeline(
    rows: List<com.wedo.schedule.util.ConflictLayoutEngine.WeekLaneRow>,
    timeJson: String,
    now: java.time.LocalTime,
    onCourseClick: (CourseEntity) -> Unit
) {
    val colors = WedoTheme.colors
    val placed = rows.map { row ->
        row to row.courses.mapNotNull { c -> courseMinuteRange(c, timeJson)?.let { c to it } }
    }
    val bounds = timelineBounds(placed.flatMap { it.second.map { pr -> pr.second } }) ?: return
    val (startMin, endMin) = bounds
    val totalH = ((endMin - startMin) * TIMELINE_DP_PER_MIN).dp
    val gutterW = 44.dp
    val nowMin = now.hour * 60 + now.minute

    Box(Modifier.fillMaxWidth().height(totalH)) {
        // 整点刻度（右对齐贴轴）
        var h = startMin / 60
        while (h <= endMin / 60) {
            val y = ((h * 60 - startMin) * TIMELINE_DP_PER_MIN).dp
            Text(
                text = String.format(java.util.Locale.ROOT, "%02d:00", h),
                style = WedoAppleType.caption1(),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.offset(y = y - 7.dp).width(gutterW - 10.dp)
            )
            h++
        }
        // 时间轴主线
        Box(
            Modifier
                .offset(x = gutterW)
                .width(0.5.dp)
                .height(totalH)
                .background(colors.onSurface.copy(alpha = WedoTheme.Alpha.hairline))
        )
        // 课程块：按栏位横向分槽，纵向按时间定位
        placed.forEach { (row, entries) ->
            val laneCount = row.laneCount.coerceAtLeast(1)
            entries.forEach { (course, range) ->
                val lane = if (laneCount > 1) (row.laneOf[course.id] ?: 0) else 0
                val top = ((range.first - startMin) * TIMELINE_DP_PER_MIN).dp
                val blockH = ((range.last + 1 - range.first) * TIMELINE_DP_PER_MIN).dp
                Row(
                    Modifier
                        .offset(y = top)
                        .fillMaxWidth()
                        .padding(start = gutterW + 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(lane) { Spacer(Modifier.weight(1f)) }
                    Box(Modifier.weight(1f)) {
                        TimelineCourseBlock(
                            course = course,
                            blockHeight = blockH,
                            inProgress = NextClassDecider.isInProgress(course, timeJson, now),
                            onClick = { onCourseClick(course) }
                        )
                    }
                    repeat(laneCount - 1 - lane) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // 「现在」指示线
        if (nowMin in startMin until endMin) {
            val y = ((nowMin - startMin) * TIMELINE_DP_PER_MIN).dp
            Box(
                Modifier
                    .offset(x = gutterW - 3.dp, y = y - 3.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(WedoApple.accent)
            )
            Box(
                Modifier
                    .offset(x = gutterW, y = y)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WedoApple.accent)
            )
        }
    }
}

/** 时间轴上的一个课程块：左色条 + 课名 + 地点/教师（+ 上课中标签）。 */
@Composable
private fun TimelineCourseBlock(
    course: CourseEntity,
    blockHeight: androidx.compose.ui.unit.Dp,
    inProgress: Boolean,
    onClick: () -> Unit
) {
    val colors = WedoTheme.colors
    val palette = WedoTheme.palette
    val context = LocalContext.current
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
    val inProgressLabel = stringResource(R.string.today_in_progress)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(blockHeight)
            .clip(WedoAppleShapes.continuous(WedoAppleDimensions.cardCorner))
            .background(block.tint)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            // 无障碍：进行中的课向读屏器声明状态（不依赖颜色）
            .then(
                if (inProgress) Modifier.semantics { stateDescription = inProgressLabel }
                else Modifier
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 2026-10-06 真机反馈：左侧色条已移除（底色改为 55% 实底后不再需要）。
        Column(Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                style = WedoAppleType.subheadline().copy(fontWeight = FontWeight.SemiBold),
                color = block.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOf(course.room, course.teacher).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = WedoAppleType.caption1(),
                    color = block.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (inProgress) {
                Text(
                    text = inProgressLabel,
                    style = WedoAppleType.caption1(),
                    color = WedoApple.accentText
                )
            }
        }
    }
}

// findCourseTime 空函数已删（死代码清理: 注释自述被 TimeTableUtils.courseTimeString 取代, 全库零调用）。
// pickCourseColor / isPaletteDark / hslToColor 三函数已收敛至 util/CourseColorUtil.kt（决策 D3 单一事实来源）