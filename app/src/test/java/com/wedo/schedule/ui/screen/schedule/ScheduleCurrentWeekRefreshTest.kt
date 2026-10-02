package com.wedo.schedule.ui.screen.schedule

import com.wedo.schedule.data.entity.TimeTableEntity
import com.wedo.schedule.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 「打开 App 显示的不是当前周」回归测试。
 *
 * 缺陷原貌：`currentWeek` 只在 `loadCourses` 里课程流 emit 时计算一次，
 * `changeWeek` 又只改 `selectedWeek`。于是 App 关掉过一夜再打开，
 * 顶部周次仍是**上次加载那天**算出来的值 —— 用户视角就是"不随日期变动"。
 *
 * 这里把校正规则钉死在纯逻辑层：`refreshCurrentWeek` 的核心是两条
 * 「currentWeek 无条件刷新 / selectedWeek 仅跟随态下刷新」的判定。
 * 由于 `ScheduleState` 是纯数据类、`currentTable` 由 `tables + selectedTableId` 派生，
 * 无需 Android 环境即可验证。
 */
class ScheduleCurrentWeekRefreshTest {

    private fun table(startDate: String, maxWeek: Int = 20) =
        TimeTableEntity(id = 1L, name = "测试表", startDate = startDate, maxWeek = maxWeek, isDefault = true)

    /** 复刻 ScheduleViewModel.refreshCurrentWeek 的判定，用于纯 JVM 验证规则本身。 */
    private fun applyRefresh(
        state: ScheduleState,
        today: LocalDate
    ): ScheduleState {
        val table = state.tables.find { it.id == state.selectedTableId } ?: return state
        val realWeek = DateUtils.currentWeek(table.startDate, today)
        if (realWeek == state.currentWeek) return state
        val following = state.selectedWeek == state.currentWeek
        return state.copy(
            currentWeek = realWeek,
            selectedWeek = if (following) realWeek else state.selectedWeek
        )
    }

    @Test
    fun staleCurrentWeek_isRefreshedAfterWeekRollover() {
        // 加载发生在第 1 周；再看时已是第 3 周 —— 旧实现在这里会一直卡在 1
        val semester = "2026-09-07"
        val loadedOn = LocalDate.parse("2026-09-08")   // 第 1 周周二
        val reopenOn = LocalDate.parse("2026-09-22")   // 第 3 周周二

        val stale = ScheduleState(
            tables = listOf(table(semester)),
            selectedTableId = 1L,
            currentWeek = DateUtils.currentWeek(semester, loadedOn),
            selectedWeek = DateUtils.currentWeek(semester, loadedOn),
            initialWeekSettled = true
        )
        assertEquals("前置：加载当天应算出第 1 周", 1, stale.currentWeek)

        val refreshed = applyRefresh(stale, reopenOn)
        assertEquals("跨周后 currentWeek 必须刷新到第 3 周", 3, refreshed.currentWeek)
        assertEquals("用户停在'本周'，selectedWeek 应随之前进", 3, refreshed.selectedWeek)
    }

    @Test
    fun userPickedWeek_isNotYankedBackAfterResume() {
        // 用户手动翻到第 8 周看后面的课，切出去再回来不该被拽回本周
        val semester = "2026-09-07"
        val reopenOn = LocalDate.parse("2026-09-22")   // 真实第 3 周

        val browsing = ScheduleState(
            tables = listOf(table(semester)),
            selectedTableId = 1L,
            currentWeek = 1,
            selectedWeek = 8,            // 手动翻过去的
            initialWeekSettled = true
        )

        val refreshed = applyRefresh(browsing, reopenOn)
        assertEquals("currentWeek 是客观事实，应刷新到 3", 3, refreshed.currentWeek)
        assertEquals("用户手动选的周必须守住，不能被拽回本周", 8, refreshed.selectedWeek)
    }

    @Test
    fun sameWeek_isNoOp() {
        val semester = "2026-09-07"
        val day = LocalDate.parse("2026-09-09")        // 第 1 周

        val state = ScheduleState(
            tables = listOf(table(semester)),
            selectedTableId = 1L,
            currentWeek = 1,
            selectedWeek = 1,
            initialWeekSettled = true
        )
        assertEquals("同一周内刷新应为恒等变换", state, applyRefresh(state, day))
    }

    @Test
    fun summerHoliday_doesNotInflateWeekBeyondMax() {
        // 学期早已结束，真实周数会远超 maxWeek —— currentWeek 本身只保底 1，
        // 上限责任在 UI（pager 的 maxWeek）与 changeWeek 的钳制，这里确认不炸。
        val semester = "2026-09-07"
        val farFuture = LocalDate.parse("2027-06-01")
        val state = ScheduleState(
            tables = listOf(table(semester)),
            selectedTableId = 1L,
            currentWeek = 1,
            selectedWeek = 1,
            initialWeekSettled = true
        )
        val refreshed = applyRefresh(state, farFuture)
        assertEquals("远在学期外也应算出真实周数（非负、可预期）", 39, refreshed.currentWeek)
    }

    @Test
    fun noSelectedTable_refreshIsSafe() {
        val state = ScheduleState(tables = emptyList(), selectedTableId = null)
        assertEquals("空态下刷新不得抛异常", state, applyRefresh(state, LocalDate.now()))
    }
}
