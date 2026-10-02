package com.wedo.schedule.util

import com.wedo.schedule.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 「下一节课」判定纯 JVM 单测（REQ-P1-02）。
 *
 * 为什么必须单测：本机**无真机 / 无模拟器 / 无 Robolectric**，而倒计时是本页最容易出错的
 * 逻辑（正在上课、课间、当天已无课、跨天、冲突并课）。把判定抽成 [NextClassDecider] 纯函数后，
 * 就能在这里穷举这些边界，无需设备。
 *
 * 时间基准：2024-09-02 是**周一**（`dayOfWeek == 1`），与 `CourseEntity.day` 同口径。
 * 默认作息取 [TimeTableUtils.DEFAULT_TIME_JSON]：1 节 08:00–08:45，2 节 08:55–09:40。
 */
class NextClassDeciderTest {

    private val monday = java.time.LocalDate.of(2024, 9, 2)

    /** 周一 hh:mm 的当前时刻（含日期，供跨天/跨周语义）。 */
    private fun at(hour: Int, minute: Int): LocalDateTime = LocalDateTime.of(monday, LocalTime.of(hour, minute))

    private fun course(
        id: Long,
        day: Int = 1,
        startNode: Int = 1,
        step: Int = 1,
        name: String = "课$id",
        ownTime: Boolean = false,
        startTime: String = "",
        endTime: String = ""
    ) = CourseEntity(
        id = id,
        groupId = "grp-$id",
        tableId = 1L,
        courseName = name,
        day = day,
        startNode = startNode,
        step = step,
        startWeek = 1,
        endWeek = 16,
        color = "",
        ownTime = ownTime,
        startTime = startTime,
        endTime = endTime
    )

    // ───────────────────────── 时间区间解析 ─────────────────────────

    @Test
    fun sanity_monday_eight_am_is_weekday_one() {
        // 锚定 fixture：2024-09-02 必须是周一（day=1），否则后续 day 过滤断言失去意义。
        assertEquals(1, at(8, 0).dayOfWeek.value)
    }

    @Test
    fun timeRangeOf_fromTimeJson_spansFirstAndLastNode() {
        // 第 1 节起、连上 2 节 → 08:00（首节 start）~ 09:40（末节 end）。
        val c = course(id = 1, startNode = 1, step = 2)
        val range = NextClassDecider.timeRangeOf(c, TimeTableUtils.DEFAULT_TIME_JSON)
        assertEquals(LocalTime.of(8, 0) to LocalTime.of(9, 40), range)
    }

    @Test
    fun timeRangeOf_ownTime_usesCustomTimes() {
        // ownTime 课自带 HH:mm，不查作息表。
        val c = course(id = 1, ownTime = true, startTime = "18:30", endTime = "20:55")
        assertEquals(LocalTime.of(18, 30) to LocalTime.of(20, 55), NextClassDecider.timeRangeOf(c, ""))
    }

    @Test
    fun timeRangeOf_noUsableTime_returnsNull() {
        // 无作息 + 非自定义时间 → 无法判定，返回 null（调用方据此跳过）。
        val c = course(id = 1, startNode = 1, step = 1)
        assertNull(NextClassDecider.timeRangeOf(c, ""))
    }

    // ───────────────────────── isInProgress 边界 ─────────────────────────

    @Test
    fun isInProgress_boundaries_inclusiveOnBothEnds() {
        val c = course(id = 1, startNode = 1, step = 1) // 08:00–08:45
        val tj = TimeTableUtils.DEFAULT_TIME_JSON
        assertTrue("恰在开始时刻应算进行中", NextClassDecider.isInProgress(c, tj, LocalTime.of(8, 0)))
        assertTrue("恰在结束时刻应算进行中", NextClassDecider.isInProgress(c, tj, LocalTime.of(8, 45)))
        assertFalse("开始前一分钟不算进行中", NextClassDecider.isInProgress(c, tj, LocalTime.of(7, 59)))
        assertFalse("结束后一分钟不算进行中", NextClassDecider.isInProgress(c, tj, LocalTime.of(8, 46)))
    }

    @Test
    fun isInProgress_noTimeInfo_isFalse() {
        // 时间不可判定时，UI 不应误标「进行中」。
        assertFalse(NextClassDecider.isInProgress(course(id = 1), "", LocalTime.of(8, 0)))
    }

    // ───────────────────────── decide：核心边界 ─────────────────────────

    @Test
    fun decide_inProgress_prefersRunningClass_andReportsRemaining() {
        // 08:10 正在上 1-2 节（08:00–09:40）：inProgress=true，距下课 90 分钟。
        val c = course(id = 1, startNode = 1, step = 2)
        val next = NextClassDecider.decide(listOf(c), at(8, 10), TimeTableUtils.DEFAULT_TIME_JSON)!!
        assertTrue("正在上课必须 inProgress", next.inProgress)
        assertEquals(c, next.course)
        assertEquals(90L, next.minutesUntilEnd)
        assertEquals("已在课时 minutesUntilStart 为负", -10L, next.minutesUntilStart)
    }

    @Test
    fun decide_breakTime_picksSoonestUpcoming() {
        // 08:50 课间：第 1 节已下课、第 2 节 08:55 尚未开始 → 选第 2 节，还有 5 分钟。
        val a = course(id = 1, startNode = 1, step = 1) // 08:00–08:45
        val b = course(id = 2, startNode = 2, step = 1) // 08:55–09:40
        val next = NextClassDecider.decide(listOf(a, b), at(8, 50), TimeTableUtils.DEFAULT_TIME_JSON)!!
        assertEquals(b, next.course)
        assertFalse(next.inProgress)
        assertEquals(5L, next.minutesUntilStart)
    }

    @Test
    fun decide_afterAllClassesToday_returnsNull() {
        // 23:00 当天已全部下课 → null（UI 显示「今天的课都上完啦」）。
        val a = course(id = 1, startNode = 1, step = 1)
        assertNull(NextClassDecider.decide(listOf(a), at(23, 0), TimeTableUtils.DEFAULT_TIME_JSON))
    }

    @Test
    fun decide_ignoresOtherWeekdays() {
        // 只过滤「今天」：周一的课在周二时刻不参与判定 → null（跨天语义）。
        val mondayCourse = course(id = 1, day = 1, startNode = 1, step = 2)
        val tuesday = at(8, 10).plusDays(1)
        assertEquals(2, tuesday.dayOfWeek.value)
        assertNull(NextClassDecider.decide(listOf(mondayCourse), tuesday, TimeTableUtils.DEFAULT_TIME_JSON))
    }

    @Test
    fun decide_runningBeatsUpcoming() {
        // 正在上 vs 将来上：一律先给正在上的那节。
        val running = course(id = 1, startNode = 1, step = 2) // 08:00–09:40 进行中
        val upcoming = course(id = 2, startNode = 3, step = 1) // 10:00–10:45 未开始
        val next = NextClassDecider.decide(listOf(upcoming, running), at(8, 30), TimeTableUtils.DEFAULT_TIME_JSON)!!
        assertEquals(running, next.course)
        assertTrue(next.inProgress)
    }

    @Test
    fun decide_conflictRunning_picksEarliestStart() {
        // 两节并课同时进行：取开始更早的一节（稳定、可预期）。
        val early = course(id = 1, ownTime = true, startTime = "08:00", endTime = "09:00")
        val late = course(id = 2, ownTime = true, startTime = "08:30", endTime = "09:30")
        val next = NextClassDecider.decide(listOf(late, early), at(8, 45), "")!!
        assertEquals(early, next.course)
        assertTrue(next.inProgress)
    }

    @Test
    fun decide_emptyToday_returnsNull() {
        assertNull(NextClassDecider.decide(emptyList(), at(9, 0), TimeTableUtils.DEFAULT_TIME_JSON))
    }

    @Test
    fun decide_skipsUnparseableCourse_butKeepsParseable() {
        // 无作息的课被跳过，不应导致整体判定失败。
        val broken = course(id = 1, ownTime = false, startNode = 1, step = 1)
        val ok = course(id = 2, ownTime = true, startTime = "10:00", endTime = "10:45")
        val next = NextClassDecider.decide(listOf(broken, ok), at(9, 0), "")!!
        assertEquals(ok, next.course)
        assertEquals(60L, next.minutesUntilStart)
    }
}
