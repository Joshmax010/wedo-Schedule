package com.wedo.schedule.ui

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-02 真机反馈轮的接线契约。
 *
 * 覆盖三件「改错了会直接被用户看见」的事：
 *  1. 顶栏「⋯」→ 分享按钮，**直达导出课表页**，不再经底部弹窗；
 *  2. 手动添加课程**只有加号一个入口**（原 ⋯ 弹窗里的那份是重复入口）；
 *  3. 冲突课程样式标题的**左边距**与上方设置行对齐（原来漏了水平 padding，贴到 x=0）。
 */
class ScheduleHeaderLayoutContractTest {

    private fun read(rel: String) = TestProjectFiles.read(rel)

    private fun executableLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private val mainSrc by lazy { read("app/src/main/java/com/wedo/schedule/MainActivity.kt") }
    private val screenSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screens/schedule/ScheduleScreen.kt")
    }
    private val headerSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screens/schedule/WedoWeekHeader.kt")
    }
    private val tabBarSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/components/WedoTabBar.kt")
    }
    private val settingsSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screens/mine/WedoSettingsScreen.kt")
    }
    private val generalSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screens/mine/GeneralSettingsScreen.kt")
    }
    private val generalDetailSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screens/mine/GeneralDetailScreens.kt")
    }

    // ───────────── 顶栏：⋯ 改分享，直达导出页 ─────────────

    @Test
    fun header_usesShareIconNotMoreDots() {
        val src = executableLines(headerSrc)
        assertTrue("顶栏右侧须用分享图标", src.contains("Icons.Outlined.IosShare"))
        assertFalse("顶栏不应再有「⋯」三点图标", src.contains("Icons.Outlined.MoreHoriz"))
    }

    @Test
    fun shareButton_goesStraightToExportPage() {
        val screen = executableLines(screenSrc)
        val main = executableLines(mainSrc)
        assertTrue("分享出参名应为 onShare", screen.contains("onShare = { onGoExport() }"))
        assertTrue(
            "分享必须直达 Export 覆盖页（不再经底部弹窗）",
            main.contains("onGoExport = { pushOverlay(OverlayScreen.Export) }")
        )
    }

    @Test
    fun moreBottomSheet_isGone_avoidingDuplicateEntryPoints() {
        // 手动添加课程只保留在加号 → 导入向导里；底部弹窗整体删除
        assertFalse(
            "底部弹窗 MoreActionsSheet 应已删除",
            screenSrc.contains("MoreActionsSheet")
        )
        assertFalse(
            "更多弹层状态 showMoreSheet 应已删除",
            executableLines(screenSrc).contains("showMoreSheet")
        )
    }

    @Test
    fun addButton_stillOwnsManualAdd() {
        val main = executableLines(mainSrc)
        assertTrue(
            "加号仍进导入向导（其中含手动添加课程）",
            main.contains("onAdd = { pushOverlay(OverlayScreen.Import) }")
        )
    }

    // ───────────── 应用锁已整体移除（真机锁死，用户要求删除）─────────────

    @Test
    fun appLock_isFullyRemoved() {
        listOf(mainSrc, screenSrc, tabBarSrc, settingsSrc).forEach { src ->
            assertFalse("应用锁残留：AppLock", src.contains("AppLock"))
            assertFalse("应用锁残留：AppLockScreen", src.contains("AppLockScreen"))
            assertFalse("应用锁残留：双击手势", src.contains("onDoubleTap"))
        }
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertFalse("Manifest 不应再声明 USE_BIOMETRIC", manifest.contains("USE_BIOMETRIC"))
        val gradle = read("app/build.gradle.kts")
        assertFalse("不应再依赖 biometric", gradle.contains("androidx.biometric"))
        val prefs = read("app/src/main/java/com/wedo/schedule/util/AppPrefs.kt")
        assertFalse("AppPrefs 不应再有应用锁键", prefs.contains("KEY_APP_LOCK_ENABLED"))
    }

    @Test
    fun mainActivity_isComponentActivityAgain() {
        // 应用锁要求 FragmentActivity，已随功能删除一并回退
        assertTrue(
            "MainActivity 应回到 ComponentActivity 基类",
            executableLines(mainSrc).contains("class MainActivity : ComponentActivity()")
        )
    }

    // ───────────── 冲突课程样式：左边距对齐 ─────────────

    @Test
    fun conflictStyleEntry_livesInGeneralSettings() {
        // 设置信息架构收敛后，冲突样式不再是外层设置首页的弹窗，而是通用页里
        // 的一行入口，点击后进入独立的二级页。
        val src = executableLines(generalSrc)
        assertTrue(
            "通用设置必须保留冲突样式入口",
            src.contains("R.string.settings_conflict_style")
        )
        assertTrue(
            "入口必须显示当前样式值",
            src.contains("value = conflictStyleValue")
        )
        assertTrue(
            "入口必须打开二级页",
            src.contains("onClick = onOpenConflictStyle")
        )
    }

    @Test
    fun conflictStyleDetail_usesSharedSettingsControls() {
        // 旧实现用 WedoChip；新二级页统一走 WedoChoiceSetting + WedoSliderSetting，
        // 不能退回到页面私有 chip 或直接写裸颜色。
        val detail = executableLines(generalDetailSrc)
        assertTrue(
            "冲突样式二级页必须使用共享 WedoChoiceSetting",
            detail.contains("WedoChoiceSetting(")
        )
        assertTrue(
            "三种样式必须写入 AppPrefs",
            detail.contains("AppPrefs.setConflictStyle")
        )
        assertTrue(
            "间距微调必须使用共享 WedoSliderSetting",
            detail.contains("WedoSliderSetting(")
        )
    }
}
