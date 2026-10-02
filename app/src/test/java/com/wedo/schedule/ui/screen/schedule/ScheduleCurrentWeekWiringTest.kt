package com.wedo.schedule.ui.screen.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「打开 App 显示的不是当前周」的**接线契约测试**。
 *
 * 兄弟测试 [ScheduleCurrentWeekRefreshTest] 用复刻实现验证了校正**规则**本身，
 * 但它验证不了「ViewModel 里真的那么写了吗」以及「UI 有没有真的接上生命周期钩子」——
 * 那是这个 bug 的两个真实失效点。这里守的就是这段接线：
 *
 *  1. ViewModel 必须真的用 `DateUtils.currentWeek(table.startDate)` 重算，
 *     而不是继续沿用一个陈旧字段；
 *  2. UI 必须真的在 ON_RESUME 调 `refreshCurrentWeek()` —— 
 *     一个没人调用的刷新函数，等于没修。
 *
 * 为什么必须是契约测试：本机无 Robolectric、无真机，无法构造 Activity 走真实
 * 生命周期。源码契约是这里唯一能自动化守住的手段。
 */
class ScheduleCurrentWeekWiringTest {

    private fun read(relative: String): String {
        val hit = listOf(File(relative), File("../$relative")).firstOrNull { it.isFile }
            ?: error("找不到源文件: $relative")
        return hit.readText()
    }

    private val viewModelSrc: String by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screen/schedule/ScheduleViewModel.kt")
    }
    private val screenSrc: String by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screen/schedule/ScheduleScreen.kt")
    }
    private val gradleSrc: String by lazy { read("app/build.gradle.kts") }

    /** 剥掉注释行，避免断言命中说明文字（本项目源码注释里常提到要禁止的写法）。 */
    private fun executableLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    @Test
    fun viewModel_exposesRefreshCurrentWeek() {
        assertTrue(
            "ViewModel 必须提供 refreshCurrentWeek() 供前台恢复时调用",
            executableLines(viewModelSrc).contains("fun refreshCurrentWeek()")
        )
    }

    @Test
    fun refreshCurrentWeek_recomputesFromStartDateNotStaleField() {
        val body = viewModelSrc.substringAfter("fun refreshCurrentWeek()")
            .substringBefore("\n    fun ")
        assertTrue(
            "刷新必须真的重算：调用 DateUtils.currentWeek(table.startDate)",
            body.contains("DateUtils.currentWeek(table.startDate)")
        )
    }

    @Test
    fun screen_callsRefreshOnResume() {
        val src = executableLines(screenSrc)
        assertTrue(
            "UI 必须在 ON_RESUME 触发刷新 —— 没人调用的刷新函数等于没修",
            src.contains("LifecycleEventEffect") && src.contains("ON_RESUME")
        )
        assertTrue(
            "ON_RESUME 回调体里必须调用 viewModel.refreshCurrentWeek()",
            src.contains("viewModel.refreshCurrentWeek()")
        )
    }

    @Test
    fun changeWeek_stillOnlyMovesSelection() {
        // 守住职责边界：翻周不等于刷新"本周"。若 changeWeek 里出现 currentWeek 赋值，
        // 说明有人在用翻页冒充刷新，会把"用户翻到第 8 周"误写成"真实周是第 8 周"。
        val body = viewModelSrc.substringAfter("fun changeWeek(week: Int)")
            .substringBefore("\n    /**")
        val executable = executableLines(body)
        assertFalse(
            "changeWeek 不得写 currentWeek —— 那会把用户浏览位置污染成客观事实",
            executable.contains("currentWeek =") && !executable.contains("refreshCurrentWeek")
        )
    }

    @Test
    fun lifecycleRuntimeCompose_dependencyDeclared() {
        assertTrue(
            "LifecycleEventEffect 来自 lifecycle-runtime-compose，必须在 app/build.gradle.kts 声明",
            gradleSrc.contains("androidx.lifecycle:lifecycle-runtime-compose")
        )
    }
}
