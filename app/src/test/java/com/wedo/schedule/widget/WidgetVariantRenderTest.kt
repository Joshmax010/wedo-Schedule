package com.wedo.schedule.widget

import com.wedo.schedule.R
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * WidgetVariant 紧凑档文本选取逻辑 — 纯 JVM 单测。
 *
 * 仓库无 Robolectric(先例: AppPrefsIsolationTest "degraded from Robolectric"),
 * Bitmap 像素管线无法在纯 JVM 断言(Bitmap.createBitmap 返回 null 桩)。
 * 因此这里断言渲染与测试共用的单一事实来源 todayCompactTexts 的
 * 资源解析无关核心重载((Int)->String resolver 版):
 *   - 课表内 + 学期内: 保留全部课程名，不能因 SMALL 变体截断数据
 *   - 无课表 / 学期前 / 学期后 / 无课: 各自状态文案分支选中正确资源
 *
 * REQ-P5-01：V3 重设计删除 WeekList / WeekView / TwoDay 三种小组件后，
 * 对应 compact-texts / compact-columns 断言一并移除，只保留 Today / WeekGrid。
 *
 * Bitmap 尺寸断言(SMALL 档输出宽高)需 Robolectric — 本仓库未引入该依赖且
 * 任务约束禁新增, 交由 assembleDebug 编译 + 源码审查保障。
 */
class WidgetVariantRenderTest {

    private val data = WidgetData(
        date = LocalDate.of(2026, 9, 1),
        courses = listOf(
            testCourse(name = "高等数学", startNode = 1),
            testCourse(name = "大学英语", startNode = 3),
            testCourse(name = "数据结构", startNode = 5)
        ),
        timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
        hasTable = true
    )

    /** resId → 资源名字面量, 证明分支选中的是"哪个资源"而非具体文案 */
    private val resNames = mapOf(
        R.string.widget_create_schedule to "widget_create_schedule",
        R.string.semester_not_started to "semester_not_started",
        R.string.semester_ended to "semester_ended",
        R.string.today_no_course to "today_no_course",
        R.string.no_course to "no_course"
    )

    private fun resolve(resId: Int): String = resNames.getValue(resId)

    @Test
    fun `compact texts keep all courses`() {
        val texts = WidgetBitmapRenderers.todayCompactTexts(::resolve, data)
        assertEquals(listOf("高等数学", "大学英语", "数据结构"), texts)
    }

    @Test
    fun `compact texts cover no-table out-of-semester and empty states`() {
        val noTable = data.copy(hasTable = false)
        assertEquals(
            listOf("widget_create_schedule"),
            WidgetBitmapRenderers.todayCompactTexts(::resolve, noTable)
        )

        val beforeStart = data.copy(semesterStatus = DateUtils.SemesterStatus.BEFORE_START)
        assertEquals(
            listOf("semester_not_started"),
            WidgetBitmapRenderers.todayCompactTexts(::resolve, beforeStart)
        )

        val afterEnd = data.copy(semesterStatus = DateUtils.SemesterStatus.AFTER_END)
        assertEquals(
            listOf("semester_ended"),
            WidgetBitmapRenderers.todayCompactTexts(::resolve, afterEnd)
        )

        val empty = data.copy(courses = emptyList())
        assertEquals(
            listOf("today_no_course"),
            WidgetBitmapRenderers.todayCompactTexts(::resolve, empty)
        )
    }

    @Test
    fun `variant enum exposes regular and small`() {
        assertEquals(
            listOf(WidgetVariant.REGULAR, WidgetVariant.SMALL),
            WidgetVariant.values().toList()
        )
    }

    @Test
    fun `small receiver declares SMALL variant`() {
        assertEquals(WidgetVariant.SMALL, TodaySmallWidgetReceiver().variantHint)
        assertEquals(WidgetVariant.REGULAR, com.wedo.schedule.widget.TodayWidgetReceiver().variantHint)
    }

    @Test
    fun `weekGrid small provider declares SMALL variant`() {
        assertEquals(WidgetVariant.SMALL, WeekGridSmallWidgetProvider().variantHint)
        assertEquals(WidgetVariant.REGULAR, com.wedo.schedule.widget.WeekGridWidgetProvider().variantHint)
    }

    // ── weekGrid 最小档: 今日数据映射(渲染走 renderToday(SMALL), 与今日课程·小同一张脸) ──

    /** 全周 DayData fixture(带课) — 2026-08-31 起的周一…周日 */
    private fun fullWeekWithCourses(): WeekData {
        val timeJson = TimeTableUtils.DEFAULT_TIME_JSON
        return WeekData(
            days = (1..7).map { dow ->
                DayData(
                    date = LocalDate.of(2026, 8, 31).plusDays(dow.toLong() - 1),
                    dayOfWeek = dow,
                    courses = listOf(testCourse(name = "周$dow 课", startNode = 1)),
                    timeJson = timeJson
                )
            },
            hasTable = true
        )
    }

    @Test
    fun `weekGrid minimum maps today day to WidgetData`() {
        // 周三(3)锚点: 取周三的 DayData → WidgetData(date=周三, courses=周三课程)
        val wd = fullWeekWithCourses()
        val today = LocalDate.of(2026, 9, 2)  // 周三
        val mapped = WidgetBitmapRenderers.weekGridMinimumTodayData(wd, today)
        assertEquals(today, mapped.date)
        assertEquals(listOf("周3 课"), mapped.courses.map { it.courseName })
        assertEquals(wd.days[2].timeJson, mapped.timeJson)
        assertTrue(mapped.hasTable)
        assertEquals(wd.themeKey, mapped.themeKey)
        assertEquals(wd.isDark, mapped.isDark)
        assertEquals(wd.semesterStatus, mapped.semesterStatus)
    }

    @Test
    fun `weekGrid minimum maps missing today to empty courses not no-table`() {
        // 今天无课: courses 为空但 hasTable 仍为 true(映射 days.first 失败时的回退分支)
        val emptyDays = fullWeekWithCourses().copy(
            days = fullWeekWithCourses().days.map { it.copy(courses = emptyList()) }
        )
        val mapped = WidgetBitmapRenderers.weekGridMinimumTodayData(emptyDays, LocalDate.of(2026, 9, 2))
        assertTrue(mapped.hasTable)
        assertTrue(mapped.courses.isEmpty())
    }

    @Test
    fun `weekGrid minimum no-table passes through`() {
        val noTable = fullWeekWithCourses().copy(hasTable = false)
        val mapped = WidgetBitmapRenderers.weekGridMinimumTodayData(noTable, LocalDate.of(2026, 9, 2))
        assertFalse(mapped.hasTable)
    }

    // ── drawCourse meta 行拆分: 时间一行/地点一行(宽度不够时) ──

    @Test
    fun `courseMetaLines single line fits time and location`() {
        // 宽度足够 → 保持旧行为: "3-4节 · 教3-101" 一行
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { _ -> 10f },
            maxWidth = 100f,
            timeStr = "3-4节",
            room = "教3-101"
        )
        assertEquals(listOf("3-4节 · 教3-101"), lines)
    }

    @Test
    fun `courseMetaLines splits into time line and room line when overflow`() {
        // 拼行放不下 → 拆两行: 时间/地点
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { t -> t.length * 10f },
            maxWidth = 50f,
            timeStr = "3-4节",
            room = "教3-101"
        )
        assertEquals(listOf("3-4节", "教3-101"), lines)
    }

    @Test
    fun `courseMetaLines no room returns time only`() {
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { _ -> 10f },
            maxWidth = 100f,
            timeStr = "3-4节",
            room = ""
        )
        assertEquals(listOf("3-4节"), lines)
    }
}

/** 测试用最小 CourseEntity — 字段以实体真实定义为准(参照 CourseColorUtilTest 同款 fixture) */
private fun testCourse(name: String, startNode: Int): CourseEntity = CourseEntity(
    id = 0L,
    groupId = "grp-widget-test",
    tableId = 1L,
    courseName = name,
    day = 2,
    startNode = startNode,
    step = 2,
    startWeek = 1,
    endWeek = 16,
    color = "#FF6750A4"
)
