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
        read("app/src/main/java/com/wedo/schedule/ui/screen/schedule/ScheduleScreen.kt")
    }
    private val headerSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screen/schedule/WedoWeekHeader.kt")
    }
    private val tabBarSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/component/WedoTabBar.kt")
    }
    private val settingsSrc by lazy {
        read("app/src/main/java/com/wedo/schedule/ui/screen/mine/WedoSettingsScreen.kt")
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
    fun conflictStyleTitle_hasHorizontalPaddingToAlignWithRows() {
        val src = executableLines(settingsSrc)
        // 两个坑叠在一起：
        //  1. padding 写在 modifier= 那一行，与 stringResource 不同行；
        //  2. 不能用「找到下一个 `)`」来界定块——`stringResource(R.string.x)` 自己就带右括号，
        //     会在 padding 之前就被截断（第一版就是这么写错的，第一轮全量测试直接 FAIL）。
        // 故取资源名之后的**定长窗口**来断言。
        val start = src.indexOf("R.string.settings_conflict_style")
        assertTrue("未找到冲突样式标题", start >= 0)
        // 2026-10-06：标题已改为「图标 + 文字」的 Row，padding 挂在 Row 上，
        // 故向前多取一段窗口（标题行在资源名之前）。
        // 窗口要足够大：modifier 里的 padding 与资源名之间还夹着 Icon(...) 整块（>400 字符）
        val from = maxOf(0, start - 900)
        val window = src.substring(from, minOf(start + 400, src.length))

        assertTrue(
            "标题行必须带水平左边距 16dp（原来只有 padding(top=8dp)，贴到 x=0 与上方设置行不对齐）：\n$window",
            window.contains("start = 16.dp")
        )
        assertTrue("标题须保留右边距：\n$window", window.contains("end = 16.dp"))
        assertTrue(
            "冲突样式标题须带图标（与上方三行 SettingsItem 对齐）：\n$window",
            window.contains("Icons.Outlined.CallSplit")
        )
    }

    @Test
    fun conflictStyleChips_useSharedWedoChipWithFixedHeight() {
        // 2026-10-06：chip 已抽成全 app 共享组件 ui/component/WedoChip.kt
        // （原先设置页私有 + SmartPeriodEditor 用 Material FilterChip，两套长相）。
        val settings = executableLines(settingsSrc)
        assertTrue(
            "设置页须使用共享 WedoChip",
            settings.contains("WedoChip(label = label")
        )
        val chip = executableLines(
            TestProjectFiles.read("app/src/main/java/com/wedo/schedule/ui/component/WedoChip.kt")
        )
        assertTrue(
            "chip 必须用固定高度（heightIn 会让选中/未选中因描边层叠而参差）",
            chip.contains(".height(WedoChipHeight)")
        )
        assertTrue(
            "选中态必须是 accent 实底 + 白字（2026-10-06 主题统一硬规则）",
            chip.contains("if (selected) WedoApple.accent else") && chip.contains("if (selected) Color.White else")
        )
    }
}
