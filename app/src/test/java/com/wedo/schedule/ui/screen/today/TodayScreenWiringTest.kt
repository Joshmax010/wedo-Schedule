package com.wedo.schedule.ui.screen.today

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1「今天页」接线契约测试（`ui/screen/today` 包内，与 [com.wedo.schedule.ui.screen.today.TodayScreen] 同包）。
 *
 * 与 `NavigationSkeletonContractTest` 同款思路：本机**无真机/模拟器/Robolectric**，
 * 今天页的接线（默认首屏、复用同一 `ScheduleViewModel`、周次即时重算、倒计时卡、
 * 无课表引导、三态空态）只能靠「读源码 + 断言」守住 —— 这类结构被改回旧写法时编译仍过，
 * 但产品行为已回退，契约测试就是那道闸门。
 *
 * 断言前**必须剥掉注释行**：本项目习惯在调用点上方写「严禁/已删除/REQ-xxx」等说明，
 * 不剥注释会命中说明文字而误判（例如 `TodayScreen.kt` 的倒计时卡片说明文字里就出现了
 * `selectedWeek` 一词）。
 */
class TodayScreenWiringTest {

    private val mainSrc by lazy { TestProjectFiles.read(MAIN_ACTIVITY) }
    private val todaySrc by lazy { TestProjectFiles.read(TODAY_SCREEN) }
    private val deciderSrc by lazy { TestProjectFiles.read(NEXT_CLASS_DECIDER) }

    /** 剥掉注释行（行注释 + 块注释两种前缀），避免断言命中说明文字。 */
    private fun executableLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    // ───────────────────────── 接线契约：MainActivity ─────────────────────────

    @Test
    fun todayTab_mountsTodayScreen_withImportEntry() {
        // REQ-P1-05：今天页无课表引导的 CTA 直接进导入向导（onOpenImport = onAdd）。
        val src = executableLines(mainSrc)
        assertTrue(
            "Tab.Today 必须挂载 TodayScreen 且把导入入口接上",
            src.contains("Tab.Today -> TodayScreen(onOpenImport = onAdd")
        )
    }

    // ───────────────────────── 接线契约：TodayScreen ─────────────────────────

    @Test
    fun todayScreen_sharesScheduleViewModelInstance() {
        // REQ-P1-01：今天页与课表页共用同一 ScheduleViewModel 实例，保证 state 一致。
        assertTrue(
            "TodayScreen 默认参数必须是 ScheduleViewModel = viewModel()（同一实例语义）",
            executableLines(todaySrc).contains("ScheduleViewModel = viewModel()")
        )
    }

    @Test
    fun todayScreen_recomputesWeekFromStartDate_withSecondArg() {
        // ADR-3：周次必须由 DateUtils.currentWeek(startDate, today) **即时重算**（带第二参数），
        // 而不是直接读缓存的 state.currentWeek。
        val src = executableLines(todaySrc)
        assertTrue(
            "必须调用 DateUtils.currentWeek(it.startDate, today) 按今天即时重算",
            src.contains("DateUtils.currentWeek(it.startDate, today)")
        )
    }

    @Test
    fun todayScreen_executableLines_neverTouchSelectedWeek() {
        // 周次语义归课表页；今天页只按 startDate 即时推算，**不得**读/写 selectedWeek。
        val src = executableLines(todaySrc)
        assertFalse(
            "今天页可执行行中不得出现 selectedWeek",
            src.contains("selectedWeek")
        )
    }

    @Test
    fun todayScreen_hasNextClassCountdownLine_notCard() {
        // REQ-P1-02 + 2026-10-02 真机反馈：倒计时仍在，但**从卡片降级为一行小字**
        // （用户嫌「白卡片」难看，倒计时属当天的提醒信息，不该独占一张卡）。
        val src = executableLines(todaySrc)
        assertTrue("必须调用纯函数 NextClassDecider.decide", src.contains("NextClassDecider.decide("))
        assertTrue("倒计时须渲染在扁平头部 TodayFlatHeader 内", src.contains("TodayFlatHeader("))
        assertFalse("倒计时卡片 NextClassCard 已移除（不再是卡片）", src.contains("NextClassCard("))
        assertTrue("倒计时文案须用令牌化字符串 today_countdown", src.contains("R.string.today_countdown"))
        assertTrue("倒计时行须标注「下一节课」", src.contains("R.string.today_next_class"))
    }

    @Test
    fun todayFlatHeader_hasNoCardBackground() {
        // 「去卡片」是本轮的核心诉求：截取 TodayFlatHeader 函数体，
        // 断言其中既不画背景、也不用卡片容器色——真断言，不是占位。
        val body = todaySrc
            .substringAfter("private fun TodayFlatHeader(")
            .substringBefore("\n@Composable")
        val code = executableLines(body)
        assertFalse("扁平头部不得有卡片背景 .background(", code.contains(".background("))
        assertFalse("扁平头部不得使用 surfaceContainer 卡片底", code.contains("surfaceContainer"))
    }

    @Test
    fun todayScreen_marksInProgress_notByColorAlone() {
        // 无障碍：进行中的节次用**文字标签** + 状态语义表达，不能只靠颜色。
        val src = executableLines(todaySrc)
        assertTrue("时间轴节次须调用 NextClassDecider.isInProgress", src.contains("NextClassDecider.isInProgress("))
        assertTrue("「进行中」须以可见文字标签呈现", src.contains("R.string.today_in_progress"))
        assertTrue("进行中还需向读屏器声明 stateDescription", src.contains("stateDescription"))
    }

    @Test
    fun todayScreen_noTable_showsFullScreenGuideWithImportCta() {
        // REQ-P1-05：无任何课表 → 全屏引导（区别于「本日无课」）。
        val src = executableLines(todaySrc)
        assertTrue("无课表分支须以 state.tables.isEmpty() 判定", src.contains("state.tables.isEmpty()"))
        assertTrue("须有 NoTableGuide 引导", src.contains("NoTableGuide("))
        assertTrue("引导标题文案", src.contains("R.string.today_no_table_title"))
        assertTrue("引导 CTA 文案「从教务系统导入」", src.contains("R.string.today_import_cta"))
    }

    @Test
    fun todayScreen_emptyToday_hasRemainingDaysContext() {
        // REQ-P1-04：今天无课但本周仍有课 → 「本周还有 N 天有课」上下文。
        assertTrue(
            "空态须带「本周还有 N 天有课」",
            executableLines(todaySrc).contains("R.string.today_week_has_days")
        )
    }

    @Test
    fun todayScreen_hasThreeStateEmptyBranch() {
        // REQ-P1-04：三态空态 —— ① 无课表（NoTableGuide 提前 return）② 学期外 ③ 今天无课。
        val src = executableLines(todaySrc)
        assertTrue("① 无课表分支", src.contains("state.tables.isEmpty()"))
        assertTrue("② 学期外状态判定", src.contains("DateUtils.semesterStatus("))
        assertTrue("③ 今日无课分支", src.contains("todayCourses.isEmpty()"))
    }

    @Test
    fun nextClassDecider_isPureUtil_noAndroidDependency() {
        // 纯函数约束：不得引入任何 Android/Context 依赖，否则无法纯 JVM 单测。
        val src = executableLines(deciderSrc)
        assertTrue("须为 object NextClassDecider", src.contains("object NextClassDecider"))
        assertFalse("纯 util 不得 import android.*", src.contains("import android"))
        assertTrue("判定结果类型 NextClass 须存在", src.contains("data class NextClass("))
    }

    // ───────────────────────── 接线契约：字符串资源 ─────────────────────────

    @Test
    fun strings_haveTodayKeys_inAllSixLocales() {
        val locales = listOf(
            "values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW"
        )
        val required = listOf(
            "today_next_class", "today_in_progress", "today_countdown",
            "today_no_more_class", "today_no_table_title", "today_no_table_hint",
            "today_import_cta", "today_week_has_days"
        )
        for (locale in locales) {
            val text = TestProjectFiles.read("app/src/main/res/$locale/strings.xml")
            for (key in required) {
                assertTrue("$locale 缺少 $key", text.contains("name=\"$key\""))
            }
        }
    }

    private companion object {
        const val MAIN_ACTIVITY = "app/src/main/java/com/wedo/schedule/MainActivity.kt"
        const val TODAY_SCREEN = "app/src/main/java/com/wedo/schedule/ui/screen/today/TodayScreen.kt"
        const val NEXT_CLASS_DECIDER = "app/src/main/java/com/wedo/schedule/util/NextClassDecider.kt"
    }
}
