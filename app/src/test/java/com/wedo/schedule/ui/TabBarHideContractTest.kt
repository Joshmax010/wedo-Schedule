package com.wedo.schedule.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.wedo.schedule.TestProjectFiles
import com.wedo.schedule.ui.component.TabBarVisibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏「上滑隐藏 / 下滑回来」（X 式）契约。
 *
 * 2026-10-06 真机反馈引入，**推翻了** V3 原决策「底栏常驻不隐藏」——
 * 理由见 [TabBarVisibilityState] 的 KDoc。
 *
 * 两类断言缺一不可：
 *  - **纯逻辑**：滚动方向 → 可见性 的映射（含抖动阈值），这是行为本身；
 *  - **接线**：三页真的挂了监听、MainActivity 真的下发了状态。
 *    光有纯逻辑测试钉不住「某页忘了挂」这种错误。
 */
class TabBarHideContractTest {

    private fun read(rel: String) = TestProjectFiles.read(rel)

    private fun executableLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    // ───────────────────────── 纯逻辑：方向 → 可见性 ─────────────────────────

    @Test
    fun startsVisible() {
        assertTrue("初始必须可见，否则一进 app 就没有导航", TabBarVisibilityState().visible)
    }

    @Test
    fun scrollUp_hidesBar() {
        val st = TabBarVisibilityState()
        st.scrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        assertFalse("向上滑（看后续内容）应隐藏底栏", st.visible)
    }

    @Test
    fun scrollDown_showsBar() {
        val st = TabBarVisibilityState()
        st.scrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        st.scrollConnection.onPreScroll(Offset(0f, 30f), NestedScrollSource.UserInput)
        assertTrue("向下滑（回前面的内容）应把底栏拉回来", st.visible)
    }

    @Test
    fun jitterBelowThreshold_doesNotFlip() {
        val st = TabBarVisibilityState()
        // 手指抖动与惯性回弹会产生 1~5dp 的零碎位移，阈值 6dp 必须挡住
        st.scrollConnection.onPreScroll(Offset(0f, -3f), NestedScrollSource.UserInput)
        assertTrue("小幅抖动不应隐藏底栏", st.visible)
        st.scrollConnection.onPreScroll(Offset(0f, 3f), NestedScrollSource.UserInput)
        assertTrue("小幅抖动不应改变可见性", st.visible)
    }

    @Test
    fun connectionConsumesNothing() {
        // 必须返回 Offset.Zero：把滚动量吃掉会导致内容页滚不动
        val st = TabBarVisibilityState()
        val consumed = st.scrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        assertTrue("不得消费滚动量（会连带内容页滚不动）", consumed == Offset.Zero)
    }

    @Test
    fun reset_restoresVisibility() {
        val st = TabBarVisibilityState()
        st.scrollConnection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        assertFalse(st.visible)
        st.reset()
        assertTrue("切 Tab 后必须复位，否则换页时底栏停在隐藏态", st.visible)
    }

    // ───────────────────────── 接线 ─────────────────────────

    @Test
    fun mainActivity_providesStateAndResetsOnTabSwitch() {
        val src = executableLines(read("app/src/main/java/com/wedo/schedule/MainActivity.kt"))
        assertTrue("必须创建 TabBarVisibilityState", src.contains("TabBarVisibilityState()"))
        assertTrue(
            "必须通过 CompositionLocal 下发给三页",
            src.contains("LocalTabBarVisibilityState provides tabBarState")
        )
        assertTrue("底栏必须消费 visible", src.contains("visible = tabBarState.visible"))
        assertTrue("切 Tab 必须复位", src.contains("tabBarState.reset()"))
    }

    @Test
    fun allThreeTabPages_attachScrollConnection() {
        listOf(
            "app/src/main/java/com/wedo/schedule/ui/screen/schedule/ScheduleScreen.kt",
            "app/src/main/java/com/wedo/schedule/ui/screen/today/TodayScreen.kt",
            "app/src/main/java/com/wedo/schedule/ui/screen/mine/WedoSettingsScreen.kt"
        ).forEach { path ->
            assertTrue(
                "$path 未挂 scrollConnection（该页底栏不会隐藏）",
                executableLines(read(path)).contains("LocalTabBarVisibilityState.current.scrollConnection")
            )
        }
    }

    @Test
    fun tabBar_usesOwnLayerSize_notHardcodedHeight() {
        val src = executableLines(read("app/src/main/java/com/wedo/schedule/ui/component/WedoTabBar.kt"))
        assertTrue(
            "滑出位移必须用自身图层高度（size.height），不得写死高度魔数",
            src.contains("translationY = size.height * hideFraction")
        )
    }
}
