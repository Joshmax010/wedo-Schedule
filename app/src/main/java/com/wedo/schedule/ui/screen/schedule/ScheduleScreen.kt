package com.wedo.schedule.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.IosShare
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.data.entity.TimeTableEntity
import com.wedo.schedule.ui.component.CardsGridView
import com.wedo.schedule.ui.component.CourseDetailSheet
import com.wedo.schedule.ui.component.FullWeekView
import com.wedo.schedule.ui.component.SectionHead
import com.wedo.schedule.ui.component.SegmentedSwitcher
import com.wedo.schedule.ui.component.ShareScheduleSheet
import com.wedo.schedule.ui.component.WedoPrimaryButton
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.noRippleClickable
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.HolidayManager
import com.wedo.schedule.util.TimeTableUtils

@Composable
fun ScheduleScreen(
    onGoImport: () -> Unit = {},
    onManualAdd: () -> Unit = {},
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val display = com.wedo.schedule.ui.theme.LocalWedoDisplay.current
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var actionCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var topOverrides by remember { mutableStateOf(mapOf<String, Long>()) }
    var rotationSteps by remember { mutableStateOf(mapOf<String, Int>()) }
    val visibleDays = AppPrefs.getVisibleDays(context).filter { it in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    if (state.currentTable == null) {
        // 空状态：iOS 的做法是「中性灰图标 + 大标题 + 说明 + 一个实心强调色按钮」，
        // 图标不染强调色（避免整屏都在喊），明确只让 CTA 抢注意力。
        Column(Modifier.fillMaxSize().padding(WedoAppleDimensions.pageMargin * 2),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(56.dp),
                tint = WedoTheme.colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.hairline))
            Spacer(Modifier.height(20.dp))
            Text("把这一周，安排得清清楚楚", style = WedoAppleType.title3(), color = WedoTheme.colors.onSurface)
            Text("从教务系统、日历文件或手动添加开始", Modifier.padding(vertical = 12.dp),
                style = WedoAppleType.subheadline(), color = WedoTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            WedoPrimaryButton(text = "添加课表", onClick = onGoImport)
        }
    } else androidx.compose.runtime.key(state.selectedTableId) {
        val maxWeek = (state.currentTable?.maxWeek ?: 20).coerceAtLeast(1)
        val pager = rememberPagerState(initialPage = (state.selectedWeek - 1).coerceIn(0, maxWeek - 1),
            pageCount = { maxWeek })
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var requestJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        fun selectWeek(week: Int) {
            if (week !in 1..maxWeek) return
            requestJob?.cancel()
            requestJob = scope.launch {
                if (display.motion) pager.animateScrollToPage(week - 1,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = .86f, stiffness = 380f))
                else pager.scrollToPage(week - 1)
            }
        }
        LaunchedEffect(pager) {
            androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page ->
                viewModel.changeWeek(page + 1)
            }
        }
        var lastSettled by remember { mutableStateOf(pager.settledPage) }
        LaunchedEffect(pager.settledPage) {
            if (lastSettled != pager.settledPage && display.haptics)
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            lastSettled = pager.settledPage
        }
        Column(Modifier.fillMaxSize()) {
            WedoWeekHeader(pager.currentPage + 1, maxWeek, ::selectWeek)
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val active = state.courses.filter { it.inWeek(page + 1) }
                    .map { it.normalizeNode(state.currentTable!!.timeJson) }
                val ghosts = if (display.ghostCourses) state.courses.filterNot { it.inWeek(page + 1) }
                    .distinctBy { listOf(it.groupId, it.day, it.startNode, it.step) }
                    .map { it.normalizeNode(state.currentTable!!.timeJson) } else emptyList()
                androidx.compose.runtime.CompositionLocalProvider(com.wedo.schedule.ui.component.LocalWedoCourseLongClick provides { course -> actionCourse = course }) {
                Box(Modifier.fillMaxSize()) {
                    CardsGridView(
                        courses = active, timeSlots = TimeTableUtils.timeSlotsFor(state.currentTable),
                        visibleDays = visibleDays, showDate = true,
                        startDate = state.currentTable!!.startDate, currentWeek = page + 1,
                        today = if (page + 1 == state.currentWeek) DateUtils.todayDayOfWeek() else -1,
                        onCourseClick = { selectedCourse = it },
                        onCourseLongClick = {
                            actionCourse = it
                            if (display.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        },
                        wedo = true, ghostCourses = ghosts,
                        topOverrides = topOverrides,
                        onSetTopOverride = { key, id -> topOverrides = if (id == null) topOverrides - key else topOverrides + (key to id) },
                        rotationSteps = rotationSteps,
                        onRotationStep = { key, step -> rotationSteps = rotationSteps + (key to step) }
                    )
                    if (active.isEmpty()) Text(
                        if (state.courses.isEmpty()) "这张课表还没有课程 · 点击 + 添加" else "本周没有课程，好好享受自由时间",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = WedoTheme.colors.onSurfaceVariant, style = WedoAppleType.subheadline())
                }
                }
            }
        }
    }
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
}
