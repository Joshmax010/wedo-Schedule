package com.wedo.schedule.ui.settings

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 材质开关的纯逻辑与接线契约。
 *
 * 本机没有 Robolectric，不能直接驱动 SharedPreferences；这里锁住
 * 取值归一化，并确认三个开关都有设置页入口与至少一个生产消费方（Q-02）。
 */
class WedoSettingsStoreTest {

    @Test
    fun `quality normalizes known values`() {
        assertEquals(WedoSettingsState.QUALITY_SMOOTH, WedoSettingsStore.normalizeQuality("smooth"))
        assertEquals(WedoSettingsState.QUALITY_FINE, WedoSettingsStore.normalizeQuality("fine"))
        assertEquals(WedoSettingsState.QUALITY_BALANCED, WedoSettingsStore.normalizeQuality("balanced"))
    }

    @Test
    fun `quality falls back to balanced on dirty values`() {
        assertEquals(WedoSettingsState.QUALITY_BALANCED, WedoSettingsStore.normalizeQuality(""))
        assertEquals(WedoSettingsState.QUALITY_BALANCED, WedoSettingsStore.normalizeQuality("ultra"))
        assertEquals(WedoSettingsState.QUALITY_BALANCED, WedoSettingsStore.normalizeQuality("SMOOTH"))
    }

    @Test
    fun `defaults keep motion and haptics enabled`() {
        val state = WedoSettingsState()
        assertEquals(WedoSettingsState.QUALITY_BALANCED, state.quality)
        assertTrue(state.motion)
        assertTrue(state.haptics)
    }

    @Test
    fun `appearance page exposes all three switches through the store`() {
        val source = TestProjectFiles.read(APPEARANCE)
        assertTrue("画质必须经 WedoSettingsStore", source.contains("WedoSettingsStore.setQuality"))
        assertTrue("动效必须经 WedoSettingsStore", source.contains("WedoSettingsStore.setMotion"))
        assertTrue("触觉必须经 WedoSettingsStore", source.contains("WedoSettingsStore.setHaptics"))
    }

    @Test
    fun `three switches have production consumers`() {
        assertTrue(
            "画质必须被材质令牌消费",
            TestProjectFiles.read(GLASS_TOKENS).contains("LocalWedoDisplay.current.quality"),
        )
        assertTrue(
            "动效必须被按压反馈消费",
            TestProjectFiles.read(MOTION_PRESS).contains("LocalWedoDisplay.current.motion"),
        )
        assertTrue(
            "触觉必须被换周/长按消费",
            TestProjectFiles.read(SCHEDULE_SCREEN).contains("display.haptics"),
        )
    }

    private companion object {
        const val APPEARANCE =
            "app/src/main/java/com/wedo/schedule/ui/screens/mine/AppearanceScreen.kt"
        const val GLASS_TOKENS =
            "app/src/main/java/com/wedo/schedule/ui/theme/WedoGlassTokens.kt"
        const val MOTION_PRESS =
            "app/src/main/java/com/wedo/schedule/ui/motion/WedoPress.kt"
        const val SCHEDULE_SCREEN =
            "app/src/main/java/com/wedo/schedule/ui/screens/schedule/ScheduleScreen.kt"
    }
}
