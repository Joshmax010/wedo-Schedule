package com.wedo.schedule.ui.screen.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 今天页**时间轴**（方案 B）的几何纯逻辑测试。
 *
 * 时间轴靠「分钟数 → dp 偏移」定位课程块，这块算术如果出错，
 * 表现是「课程块跑到别的时间上」——在无真机的环境下只能靠纯函数测试钉住。
 *
 * 另一半（接线）由 [TodayScreenWiringTest] 负责：证明 TodayScreen 真的用了这些函数。
 */
class TodayTimelineMathTest {

    // ───────────────────────── hmToMinutes ─────────────────────────

    @Test
    fun hmToMinutes_parsesValidTimes() {
        assertEquals(480, hmToMinutes("08:00"))
        assertEquals(510, hmToMinutes("08:30"))
        assertEquals(0, hmToMinutes("00:00"))
        assertEquals(1439, hmToMinutes("23:59"))
        assertEquals(540, hmToMinutes("9:00"))   // 单位数小时也接受
    }

    @Test
    fun hmToMinutes_trimsSurroundingSpace() {
        assertEquals(495, hmToMinutes("  08:15 "))
    }

    @Test
    fun hmToMinutes_rejectsMalformedInput() {
        assertNull(hmToMinutes(""))
        assertNull(hmToMinutes("8"))
        assertNull(hmToMinutes("08-00"))
        assertNull(hmToMinutes("aa:bb"))
        assertNull(hmToMinutes("24:00"))   // 小时越界
        assertNull(hmToMinutes("08:60"))   // 分钟越界
    }

    // ───────────────────────── timelineBounds ─────────────────────────

    @Test
    fun timelineBounds_alignsToWholeHours() {
        // 08:00–09:30 与 13:30–14:20 → 08:00 起，15:00 止（向上取整到整点）
        val bounds = timelineBounds(listOf(480 until 570, 810 until 860))
        assertEquals(480 to 900, bounds)
    }

    @Test
    fun timelineBounds_expandsPartialHours() {
        // 08:10–08:50 → 08:00 起，09:00 止
        assertEquals(480 to 540, timelineBounds(listOf(490 until 530)))
    }

    @Test
    fun timelineBounds_singleCourseTouchingHourBoundary() {
        // 09:00–10:00 恰好整点：起点不再向下扩，终点是 10:00
        assertEquals(540 to 600, timelineBounds(listOf(540 until 600)))
    }

    @Test
    fun timelineBounds_emptyReturnsNull() {
        assertNull(timelineBounds(emptyList()))
    }

    @Test
    fun timelineBounds_endIsExclusiveOfLastMinute() {
        // 区间是 [起, 止)：08:00 until 08:59 只到 08:59，向上取整仍为 09:00
        assertEquals(480 to 540, timelineBounds(listOf(480 until 539)))
    }
}
