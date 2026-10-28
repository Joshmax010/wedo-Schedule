package com.wedo.schedule.util

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M-36..M-38：系统动画倍率与 App 内动效开关是「与」关系；
 * 高对比度文字开关先只做读取与监听，不在这里决定材质参数。
 */
class SystemAccessibilityTest {

    @Test
    fun `animator scale zero disables motion`() {
        assertFalse(SystemAccessibility.animatorScaleAllowsMotion(0f))
    }

    @Test
    fun `positive animator scale allows motion`() {
        assertTrue(SystemAccessibility.animatorScaleAllowsMotion(1f))
        assertTrue(SystemAccessibility.animatorScaleAllowsMotion(0.5f))
        assertTrue(SystemAccessibility.animatorScaleAllowsMotion(2f))
    }

    @Test
    fun `unreadable animator scale fails closed`() {
        assertFalse(SystemAccessibility.animatorScaleAllowsMotion(null))
    }

    @Test
    fun `high contrast reads one as enabled`() {
        assertTrue(SystemAccessibility.highContrastEnabled(1))
        assertFalse(SystemAccessibility.highContrastEnabled(0))
    }

    @Test
    fun `observer watches both system settings`() {
        val source = TestProjectFiles.read(SOURCE)
        assertTrue(source.contains("ContentObserver"))
        assertTrue(source.contains("ANIMATOR_DURATION_SCALE"))
        assertTrue(source.contains("KEY_HIGH_TEXT_CONTRAST"))
        assertTrue(source.contains("registerContentObserver"))
    }

    private companion object {
        const val SOURCE = "app/src/main/java/com/wedo/schedule/util/SystemAccessibility.kt"
    }
}
