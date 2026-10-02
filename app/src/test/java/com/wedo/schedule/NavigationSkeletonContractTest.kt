package com.wedo.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 导航骨架（[com.wedo.schedule.Tab] / `MainActivity` / `WedoTabBar`）契约测试。
 *
 * 为什么需要它：本机**无真机、无模拟器、无 Robolectric**，导航骨架的重构
 * （Tab 换轴、删 Dock、删下滑收起、挂载常驻 tab bar、返回键分层）只能靠
 * 「纯逻辑断言 + 读源码的接线契约」守住。这类结构一旦被后人改回旧写法，
 * 编译仍然通过，但产品行为已回退 —— 契约测试就是那道闸门。
 *
 * 两类缺一不可（见 `ARCH_V3_IMPLEMENTATION §7.5`）：
 *  1. **纯逻辑**：直接断言 [Tab] 枚举的成员与顺序（导航模型的规则本身）。
 *  2. **接线契约**：读 `MainActivity.kt` / `WedoTabBar.kt` / `WedoShell.kt`
 *     源码，断言「真的接上了」以及「旧结构真的清干净了」。
 *
 * 注意：契约断言前**必须剥掉注释行** —— 本项目源码习惯在调用点上方写
 * 「严禁/已删除」等说明，不剥注释会命中说明文字而误判。
 */
class NavigationSkeletonContractTest {

    private fun read(relative: String): String = TestProjectFiles.read(relative)

    private val mainSrc by lazy { read(MAIN_ACTIVITY) }
    private val tabBarSrc by lazy { read(WEDO_TAB_BAR) }
    private val shellSrc by lazy { read(WEDO_SHELL) }

    /** 剥掉注释行（行注释 + 块注释起始两种前缀），避免断言命中说明文字。 */
    private fun executableLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    // ───────────────────────── 纯逻辑：导航模型规则 ─────────────────────────

    @Test
    fun tabEnum_hasExactlyScheduleTodaySettings_inOrder() {
        // 2026-10-01 真机反馈：Tab = Schedule / Today / Settings，顺序为 课表 → 今天 → 设置。
        assertEquals(
            "Tab 三轴顺序必须是 课表 → 今天 → 设置",
            listOf("Schedule", "Today", "Settings"),
            Tab.entries.map { it.name }
        )
    }

    @Test
    fun tabEnum_defaultFirstScreenIsSchedule() {
        // 2026-10-01 真机反馈拍板：首屏默认「课表」（用户打开就是为了看课表，
        // 「今天」降级为课表的衍生待办视图）。
        assertEquals("默认首屏（枚举首项）必须是 Schedule", "Schedule", Tab.entries.first().name)
    }

    // ───────────────────────── 接线契约：MainActivity ─────────────────────────

    @Test
    fun tabEnum_reusesTabToday_andAddsTabSettings() {
        val src = executableLines(mainSrc)
        assertTrue(
            "今天 Tab 必须复用 tab_today（复活，不新增 tab_today_view）",
            src.contains("Today(R.string.tab_today")
        )
        assertFalse(
            "不得再出现 tab_today_view（主理人已改为复用 tab_today）",
            src.contains("tab_today_view")
        )
        assertTrue("设置 Tab 必须使用新增的 tab_settings", src.contains("Settings(R.string.tab_settings"))
        assertTrue("课表 Tab 必须复用 tab_schedule", src.contains("Schedule(R.string.tab_schedule"))
    }

    @Test
    fun tabEnum_defaultCurrentTabIsSchedule() {
        assertTrue(
            "currentTab 默认值必须是 Tab.Schedule（2026-10-01 真机反馈）",
            executableLines(mainSrc).contains("mutableStateOf(Tab.Schedule)")
        )
    }

    @Test
    fun mainActivity_hasNoWedoDockWiring() {
        // REQ-P0-02：移除浮动 Dock 挂载及其 AnimatedVisibility 容器。
        val src = executableLines(mainSrc)
        assertFalse("MainActivity 不得再调用 WedoDock", src.contains("WedoDock"))
        assertFalse("旧 Dock 的 AnimatedVisibility 容器应一并删除", src.contains("AnimatedVisibility"))
    }

    @Test
    fun mainActivity_hasNoScrollCollapseWiring() {
        // REQ-P0-03：移除「下滑收起」联动（collapsed 状态 / NestedScrollConnection / nestedScroll）。
        val src = executableLines(mainSrc)
        assertFalse("不得再挂载 nestedScroll", src.contains(".nestedScroll("))
        assertFalse("不得再声明 NestedScrollConnection", src.contains("NestedScrollConnection"))
        assertFalse("不得再有 scrollConnection 连接体", src.contains("scrollConnection"))
        assertTrue(
            "LocalWedoCollapsed 必须以恒 false 提供（解耦折叠）",
            src.contains("LocalWedoCollapsed provides false")
        )
    }

    @Test
    fun mainActivity_mountsPersistentWedoTabBar() {
        // REQ-P0-04：常驻底部 tab bar（不隐藏、不收起）。
        val src = executableLines(mainSrc)
        assertTrue("必须挂载 WedoTabBar", src.contains("WedoTabBar("))
        assertTrue("tab bar 应贴在底部中心", src.contains("Alignment.BottomCenter"))
        assertTrue(
            "内容区底部留白须读 tab bar 高度令牌（不写死魔数）",
            src.contains("WedoTabBarDefaults.contentHeight")
        )
    }

    @Test
    fun mainActivity_backHandlerThreeWayBranches() {
        // REQ-P0-05：① 有覆盖页/编辑会话逐层退；② 非课表 Tab 返回 → 回课表；③ 课表 Tab 双击退出。
        val src = executableLines(mainSrc)
        assertTrue(
            "分支①：有 overlay 或编辑会话时拦截",
            src.contains("hasOverlay() || editingCourse != null")
        )
        assertTrue(
            "分支②：非课表 Tab 按返回先回课表",
            src.contains("currentTab != Tab.Schedule") && src.contains("currentTab = Tab.Schedule")
        )
        assertTrue(
            "分支③：课表 Tab 双击退出",
            src.contains("currentTab == Tab.Schedule") && src.contains("elapsedRealtime()")
        )
    }

    @Test
    fun mainActivity_noLegacyTabStringReferences() {
        // REQ-P0-01 验收：代码不再引用 tab_manage / tab_mine（字符串本身保留）。
        val src = executableLines(mainSrc)
        assertFalse("不得再引用 tab_manage", src.contains("R.string.tab_manage"))
        assertFalse("不得再引用 tab_mine", src.contains("R.string.tab_mine"))
    }

    // ───────────────────────── 接线契约：WedoTabBar / WedoShell ─────────────────────────

    @Test
    fun wedoTabBar_usesTokensNotHardcodedValues() {
        val src = executableLines(tabBarSrc)
        assertTrue("tab bar 必须使用强调色令牌（图标态）", src.contains("WedoApple.accentIcon"))
        assertTrue("tab bar 必须使用强调色令牌（文字态）", src.contains("WedoApple.accentText"))
        assertTrue(
            "触控目标须引用 minTouchTarget（≥44dp 硬约束）",
            src.contains("WedoAppleDimensions.minTouchTarget")
        )
        assertTrue(
            "贴底需吸收系统导航栏安全区",
            src.contains("navigationBarsPadding()")
        )
    }

    @Test
    fun wedoTabBar_rendersAllThreeTabsFromEnum() {
        val src = executableLines(tabBarSrc)
        assertTrue("tab bar 必须遍历 Tab 枚举渲染全部项", src.contains("Tab.entries"))
    }

    @Test
    fun wedoShell_noLongerDefinesWedoDock() {
        // ADR-2：删函数、不删文件（WedoBackground 仍存活）。
        val src = executableLines(shellSrc)
        assertFalse("WedoDock 函数应已从 WedoShell 删除", src.contains("fun WedoDock"))
        assertTrue("WedoBackground 必须保留", src.contains("fun WedoBackground"))
        assertTrue(
            "底栏占位令牌须以 tab bar 高度为单一事实来源",
            src.contains("WedoTabBarDefaults.contentHeight")
        )
    }

    // ───────────────────────── 接线契约：字符串资源 ─────────────────────────

    @Test
    fun strings_haveTabSettings_andTabToday_inAllSixLocales() {
        val locales = listOf(
            "values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW"
        )
        for (locale in locales) {
            val text = read("app/src/main/res/$locale/strings.xml")
            assertTrue("$locale 缺少 tab_settings", text.contains("name=\"tab_settings\""))
            assertTrue("$locale 缺少 tab_today（应复活保留）", text.contains("name=\"tab_today\""))
            assertTrue("$locale 应保留 tab_manage（存活的页面仍用作标题）", text.contains("name=\"tab_manage\""))
        }
    }

    private companion object {
        const val MAIN_ACTIVITY = "app/src/main/java/com/wedo/schedule/MainActivity.kt"
        const val WEDO_TAB_BAR = "app/src/main/java/com/wedo/schedule/ui/component/WedoTabBar.kt"
        const val WEDO_SHELL = "app/src/main/java/com/wedo/schedule/ui/component/WedoShell.kt"
    }
}
