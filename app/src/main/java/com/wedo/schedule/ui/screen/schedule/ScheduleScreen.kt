package com.wedo.schedule.ui.screen.schedule

import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.component.CardsGridView
import com.wedo.schedule.ui.component.CourseDetailSheet
import com.wedo.schedule.ui.component.LocalWedoCourseLongClick
import com.wedo.schedule.ui.component.ShareScheduleSheet
import com.wedo.schedule.ui.component.WedoPrimaryButton
import com.wedo.schedule.ui.component.wedoPress
import com.wedo.schedule.ui.theme.LocalWedoDisplay
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils

/**
 * 课表页视图模式（REQ-P3-01）。
 *
 * 顺序即分段控件顺序；首项 = 默认「周」视图。仅会话级持久（`rememberSaveable`），
 * **不落库** —— 视图偏好不是业务数据。
 */

@Composable
fun ScheduleScreen(
    onGoImport: () -> Unit = {},
    onManualAdd: () -> Unit = {},
    /** 2026-10-02 真机反馈：顶栏「⋯」改分享按钮后，直达导出课表页（不再经底部弹窗）。 */
    onGoExport: () -> Unit = {},
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val display = LocalWedoDisplay.current
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var actionCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var topOverrides by remember { mutableStateOf(mapOf<String, Long>()) }
    var rotationSteps by remember { mutableStateOf(mapOf<String, Int>()) }

    // 全屏周次选择器 / 分享弹层的开合
    var showWeekPicker by rememberSaveable { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    val visibleDays = AppPrefs.getVisibleDays(context).filter { it in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
    val haptic = LocalHapticFeedback.current

    // 每次页面回到前台都重算真实周：跨天/跨周打开时顶部周次不再停留在上次加载那天。
    // （根治「打开不显示当前周」，详见 ScheduleViewModel.refreshCurrentWeek 的说明）
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCurrentWeek()
    }
    if (state.currentTable == null) {
        // 空状态：iOS 的做法是「中性灰图标 + 大标题 + 说明 + 一个实心强调色按钮」，
        // 图标不染强调色（避免整屏都在喊），明确只让 CTA 抢注意力。
        Column(Modifier.fillMaxSize().padding(WedoAppleDimensions.pageMargin * 2),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(56.dp),
                tint = WedoTheme.colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.hairline))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.schedule_v3_empty_title), style = WedoAppleType.title3(), color = WedoTheme.colors.onSurface)
            Text(stringResource(R.string.schedule_v3_empty_hint), Modifier.padding(vertical = 12.dp),
                style = WedoAppleType.subheadline(), color = WedoTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            WedoPrimaryButton(text = stringResource(R.string.schedule_v3_add_cta), onClick = onGoImport)
        }
    } else key(state.selectedTableId) {
        val table = state.currentTable!!
        val maxWeek = table.maxWeek.coerceAtLeast(1)
        val pager = rememberPagerState(initialPage = (state.selectedWeek - 1).coerceIn(0, maxWeek - 1),
            pageCount = { maxWeek })
        val scope = rememberCoroutineScope()

        /** 唯一的「选周」入口：只写 selectedWeek（浏览位置），绝不触碰 currentWeek（铁律 7.3）。 */
        fun goToWeek(week: Int) {
            if (week in 1..maxWeek) viewModel.changeWeek(week)
        }

        // state.selectedWeek → pager：无论从箭头、周次选择器还是学期视图选周，
        // 都由这一处把 pager 定位过去（切视图不丢失当前周的关键）。
        LaunchedEffect(state.selectedWeek, maxWeek) {
            val target = (state.selectedWeek - 1).coerceIn(0, maxWeek - 1)
            if (pager.currentPage != target) {
                if (display.motion) pager.animateScrollToPage(target,
                    animationSpec = spring(dampingRatio = .86f, stiffness = 380f))
                else pager.scrollToPage(target)
            }
        }
        // pager → state.selectedWeek（仅落定后回写，拖动过程不发散）
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page ->
                viewModel.changeWeek(page + 1)
            }
        }
        var lastSettled by remember { mutableStateOf(pager.settledPage) }
        LaunchedEffect(pager.settledPage) {
            if (lastSettled != pager.settledPage && display.haptics)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastSettled = pager.settledPage
        }

        val shownWeek = pager.currentPage + 1

        Column(Modifier.fillMaxSize()) {
            // 2026-10-02 真机反馈：顶栏压成**单行**——左「第 N 周 + 日期」（点开全屏周次选择器），
            // 右「＋ / ⋯」。原大标题块、左右翻周箭头、以及「周/学期/课程」三视图分段控件全部移除
            // （用户要的就是课表一种视图；换周靠左右滑动，与 WakeUp 一致）。
            WedoWeekHeader(
                week = shownWeek,
                startDate = table.startDate,
                onOpenPicker = { showWeekPicker = true },
                onAdd = onGoImport,
                onShare = { onGoExport() }
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        val active = state.courses.filter { it.inWeek(page + 1) }
                            .map { it.normalizeNode(table.timeJson) }
                        val ghosts = if (display.ghostCourses) state.courses.filterNot { it.inWeek(page + 1) }
                            .distinctBy { listOf(it.groupId, it.day, it.startNode, it.step) }
                            .map { it.normalizeNode(table.timeJson) } else emptyList()
                        CompositionLocalProvider(LocalWedoCourseLongClick provides { course -> actionCourse = course }) {
                            Box(Modifier.fillMaxSize()) {
                                CardsGridView(
                                    courses = active, timeSlots = TimeTableUtils.timeSlotsFor(table),
                                    visibleDays = visibleDays, showDate = true,
                                    startDate = table.startDate, currentWeek = page + 1,
                                    today = if (page + 1 == state.currentWeek) DateUtils.todayDayOfWeek() else -1,
                                    onCourseClick = { selectedCourse = it },
                                    onCourseLongClick = {
                                        actionCourse = it
                                        if (display.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    wedo = true, ghostCourses = ghosts,
                                    topOverrides = topOverrides,
                                    onSetTopOverride = { key, id -> topOverrides = if (id == null) topOverrides - key else topOverrides + (key to id) },
                                    rotationSteps = rotationSteps,
                                    onRotationStep = { key, step -> rotationSteps = rotationSteps + (key to step) }
                                )
                                if (active.isEmpty()) Text(
                                    if (state.courses.isEmpty()) stringResource(R.string.schedule_week_empty_no_courses) else stringResource(R.string.schedule_week_empty_free),
                                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                    color = WedoTheme.colors.onSurfaceVariant, style = WedoAppleType.subheadline())
                            }
                        }
                }
            }
        }

        // ── 覆盖层 ──
        CourseDetailSheet(
            course = selectedCourse,
            timeString = selectedCourse?.nodeString(context),
            allCourses = state.courses.filter { it.inWeek(state.selectedWeek) },
            onDismiss = { selectedCourse = null },
            onEdit = { selectedCourse = null; onEditCourse(it) },
            onDefaultTopChanged = { key, id ->
                rotationSteps = rotationSteps - key
                topOverrides = if (id == null) topOverrides - key else topOverrides + (key to id)
                AppPrefs.putConflictDefaultTop(context, key, id)
            }
        )
        actionCourse?.let { course ->
            WedoCourseActions(course, onDismiss = { actionCourse = null },
                onEdit = { actionCourse = null; onEditCourse(course) }, viewModel = viewModel)
        }
        if (showWeekPicker) {
            WeekPicker(
                selectedWeek = state.selectedWeek,
                currentWeek = state.currentWeek,
                maxWeek = maxWeek,
                onSelect = { week -> goToWeek(week); showWeekPicker = false },
                onDismiss = { showWeekPicker = false }
            )
        }
        if (showShareSheet) {
            ShareScheduleSheet(table = table, courses = state.courses, onDismiss = { showShareSheet = false })
        }
    }
}

/**
 * 「⋯」更多动作底部弹层（REQ-P3-06）。
 *
 * 只放低频动作：手动添加课程 / 导出分享 / 选择周次。
 * 高频的「导入课表」放在 `＋`（`onGoImport`）—— iOS 的 action sheet 语义，
 * 不做成常驻工具条，也不引 Material `DropdownMenu`（本页三视图切换后不再需要它）。
 */
