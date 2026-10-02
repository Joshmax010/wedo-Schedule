package com.wedo.schedule.ui.screen

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通用 i18n 反回归护栏（TASK-06）：P2/P3 新增与改动的 UI 文件，其**可执行代码**不得再出现
 * 硬编码中文文案 —— 必须走 `stringResource(R.string.…)`。
 *
 * 为什么需要它：文案抽取是「一次性修完就忘」的典型债务 —— 没有护栏时，下一个人复制粘贴一段
 * 带中文的 Composable，编译照样过、单测照样绿，而非中文用户看到的仍是中文。
 * 本护栏把「不许硬编码中文」变成一条机器可验的红线，也让 i18n 契约不再依赖「被某个功能测试
 * 的字符串字面量反向锁住」这种脆弱的循环依赖。
 *
 * 扫描范围（P2/P3 新增与改动、且已抽离文案的 UI 文件）：
 *  - `ui/screen/schedule/`：ScheduleScreen / WedoWeekHeader / WeekPicker / SemesterOverview / CourseListView
 *  - `ui/screen/imports/`：ImportWizard
 *
 * 断言前**剥掉注释**（整行注释 + 行尾注释），否则 KDoc 与说明文字里的中文会误报。
 * 无 Robolectric：靠「读源码 + 断言」守住。
 */
class I18nChineseLiteralGuardTest {

    private val cjk = Regex("[\\u4e00-\\u9fff]")

    /**
     * 剥掉注释后的「可执行行」文本。
     *
     *  - 整行注释：以 `//`、`*`、或块注释起始符开头的行（含 KDoc 的 `*` 续行）。
     *  - 行尾注释：仅当 `//` 之前不在字符串字面量内（引号成对）时截断，
     *    避免把 `"https://…"` 这类字符串里的 `//` 误当注释。
     */
    private fun executable(src: String): String = src.lineSequence()
        .map { stripInlineComment(it) }
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private fun stripInlineComment(line: String): String {
        val idx = line.indexOf("//")
        if (idx < 0) return line
        val before = line.substring(0, idx)
        // 引号成对 => `//` 处于代码位置（行尾注释）；否则视为字符串内部，整行保留。
        return if (before.count { it == '"' } % 2 == 0) before else line
    }

    private fun assertNoChinese(relative: String) {
        val src = executable(TestProjectFiles.read(relative))
        val hits = cjk.findAll(src).map { it.value }.toList()
        assertTrue(
            "$relative 可执行代码不得残留硬编码中文（应改走 stringResource），命中: $hits",
            hits.isEmpty()
        )
    }

    @Test
    fun `p3 schedule surface has no hardcoded chinese`() {
        // 2026-10-02 真机反馈：三视图（学期/课程）连同其文件一并删除，
        // 课表面只剩 ScheduleScreen / WedoWeekHeader / WeekPicker。
        val files = listOf(
            "app/src/main/java/com/wedo/schedule/ui/screen/schedule/ScheduleScreen.kt",
            "app/src/main/java/com/wedo/schedule/ui/screen/schedule/WedoWeekHeader.kt",
            "app/src/main/java/com/wedo/schedule/ui/screen/schedule/WeekPicker.kt"
        )
        files.forEach { assertNoChinese(it) }
    }

    @Test
    fun `p2 import wizard has no hardcoded chinese`() {
        assertNoChinese("app/src/main/java/com/wedo/schedule/ui/screen/imports/ImportWizard.kt")
    }
}
