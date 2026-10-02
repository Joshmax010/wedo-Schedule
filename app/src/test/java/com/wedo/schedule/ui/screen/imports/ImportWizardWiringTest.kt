package com.wedo.schedule.ui.screen.imports

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2「导入向导」接线契约测试（`ui/screen/imports` 包内，与 `ImportWizard` 同包）。
 *
 * 本机无真机/模拟器/Robolectric，全屏分步向导的**步骤模型、来源顺序、转场方式、
 * 是否复用 ImportFlow、宿主接线是否指向向导**，只能靠「读源码 + 断言」守住。
 *
 * 断言前**必须剥掉注释行** —— 源码里大量「REQ-xxx / 刻意不用淡入淡出」的说明文字
 * 会污染朴素的 `contains` 断言（例如 `ImportWizard` 顶部 KDoc 就写了「不要淡入淡出」）。
 */
class ImportWizardWiringTest {

    private val mainSrc by lazy { TestProjectFiles.read(MAIN_ACTIVITY) }
    private val wizardSrc by lazy { TestProjectFiles.read(IMPORT_WIZARD) }
    private val flowSrc by lazy { TestProjectFiles.read(IMPORT_FLOW) }

    /** 剥掉注释行（行注释 + 块注释两种前缀），避免断言命中说明文字。 */
    private fun executableLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    // ───────────────────────── 步骤 / 来源模型 ─────────────────────────

    @Test
    fun importStep_hasFourOrderedSteps() {
        // REQ-P2-01：来源 → 来源专属 → 预览确认 → 完成。
        assertTrue(
            "ImportStep 必须是 SOURCE/SOURCE_DETAIL/PREVIEW/DONE 四步且顺序固定",
            executableLines(wizardSrc).contains("enum class ImportStep { SOURCE, SOURCE_DETAIL, PREVIEW, DONE }")
        )
    }

    @Test
    fun step1_sourceList_putsJwImportFirst() {
        // REQ-P2-02：教务首选 —— 读源码确认**顺序**（不只是存在）。
        val src = executableLines(wizardSrc)
        assertTrue(
            "ImportSource 枚举须以 JW 开头",
            src.contains("enum class ImportSource { JW, FILE, TEXT, MANUAL }")
        )
        // 只在 Step1 来源列表（SourceStep 函数体）内比较顺序 —— 全局 indexOf 会被
        // 别处（如自动导入的 LaunchedEffect）先出现的 ImportSource.TEXT 干扰。
        val listRegion = src.substringAfter("fun SourceStep(").substringBefore("fun SourceRow(")
        val jw = listRegion.indexOf("ImportSource.JW")
        val file = listRegion.indexOf("ImportSource.FILE")
        val text = listRegion.indexOf("ImportSource.TEXT")
        val manual = listRegion.indexOf("ImportSource.MANUAL")
        assertTrue("教务行必须存在于来源列表", jw >= 0)
        assertTrue("教务行必须排在最前（先于文件）", jw < file)
        assertTrue("教务行必须先于文本", jw < text)
        assertTrue("教务行必须先于手动添加", jw < manual)
    }

    @Test
    fun jwRow_isUniformWithOtherSources_andGoesDirectToSchoolList() {
        // 2026-10-01 真机反馈：教务直连与另外三项**完全同款**——
        // ① 副标题为中性功能描述（不提学校名）；② 无任何特殊样式；
        // ③ 点击**直进学校候选列表**（onJwImport），不再先进 Step2 按钮页。
        val src = executableLines(wizardSrc)
        assertTrue(
            "教务直连行副标题须为中性描述（走 stringResource）",
            src.contains("R.string.import_wiz_jw_subtitle")
        )
        assertFalse("教务直连行不得再有角标参数", src.contains("badge ="))
        assertFalse("教务直连行不得再有高亮参数", src.contains("highlight ="))
        assertTrue(
            "教务直连点击须直进学校列表（onPick → onJwImport），不得再经 Step2",
            src.contains("ImportSource.JW -> onJwImport()")
        )
    }

    // ───────────────────────── 转场：水平滑动（非淡入淡出） ─────────────────────────

    @Test
    fun stepTransition_isHorizontalSlide_notFade() {
        val src = executableLines(wizardSrc)
        assertTrue("步骤容器须用 AnimatedContent", src.contains("AnimatedContent("))
        assertTrue("前进须右滑入", src.contains("slideInHorizontally"))
        assertTrue("旧页须侧滑出", src.contains("slideOutHorizontally"))
        assertFalse("不得改用 fadeIn 淡入", src.contains("fadeIn"))
        assertFalse("不得改用 fadeOut 淡出", src.contains("fadeOut"))
        assertFalse("不得改用 Crossfade 交叉淡变", src.contains("Crossfade"))
    }

    // ───────────────────────── 逻辑复用（不在 UI 层重造轮子） ─────────────────────────

    @Test
    fun wizard_reusesImportFlow_doesNotReimplement() {
        val src = executableLines(wizardSrc)
        assertTrue("预览须复用 ImportFlow.buildImportPreview", src.contains("buildImportPreview("))
        assertTrue("落库须复用 ImportFlow.applyImportPreview", src.contains("applyImportPreview("))
        assertFalse("UI 层不得重定义 buildImportPreview", src.contains("fun buildImportPreview"))
        assertFalse("UI 层不得重定义 applyImportPreview", src.contains("fun applyImportPreview"))
        assertFalse("向导不得直接触碰解析内核 ScheduleParser", src.contains("ScheduleParser"))
    }

    @Test
    fun importFlow_isTheSingleWritePoint() {
        // 导入路径唯一写库点：解析 → 预览 → 落库都在 ImportFlow，且带撤回批边界。
        val src = executableLines(flowSrc)
        assertTrue("ImportFlow 须定义 buildImportPreview", src.contains("suspend fun buildImportPreview("))
        assertTrue("ImportFlow 须定义 applyImportPreview", src.contains("suspend fun applyImportPreview("))
        assertTrue(
            "落库须处于撤回批边界内",
            src.contains("UndoManager.beginBatch()") && src.contains("UndoManager.endBatch()")
        )
    }

    // ───────────────────────── 宿主接线 ─────────────────────────

    @Test
    fun mainActivity_routesImportEntries_toWizard_notSheet() {
        val src = executableLines(mainSrc)
        assertTrue("须新增并渲染 OverlayScreen.Import", src.contains("OverlayScreen.Import"))
        assertTrue("须挂载全屏 ImportWizard", src.contains("ImportWizard("))
        assertFalse("入口不得再是旧 ImportSheet", src.contains("ImportSheet("))
        assertTrue("今天页 CTA 进向导（onOpenImport = onAdd）", src.contains("onOpenImport = onAdd"))
        assertTrue("课表页 ＋ 进向导（onGoImport = onAdd → pushOverlay(Import)）", src.contains("onGoImport = onAdd"))
        assertTrue("onAdd 须把向导压入覆盖页栈", src.contains("pushOverlay(OverlayScreen.Import)"))
    }

    @Test
    fun mainActivity_wizard_callbacks_wireManualAdd_andJwImport() {
        val src = executableLines(mainSrc)
        assertTrue(
            "手动添加：先退向导再进 AddCourse（顺序不可换）",
            src.contains("onManualAdd = { popOverlay(); pushOverlay(OverlayScreen.AddCourse) }")
        )
        assertTrue("教务直连：启动 JwImportActivity", src.contains("JwImportActivity"))
        assertTrue("向导须暴露 onJwImport 回调", src.contains("onJwImport ="))
    }

    // ───────────────────────── 令牌化（不得硬编码颜色/间距） ─────────────────────────

    @Test
    fun wizard_usesDesignTokens() {
        val src = executableLines(wizardSrc)
        assertTrue("颜色须走 WedoTheme.colors", src.contains("WedoTheme.colors"))
        assertTrue("强调色须走 WedoApple.accent", src.contains("WedoApple.accent"))
        assertTrue("页边距须走令牌 pageMargin", src.contains("WedoAppleDimensions.pageMargin"))
        assertTrue("主 CTA 须复用 WedoPrimaryButton", src.contains("WedoPrimaryButton("))
    }

    private companion object {
        const val MAIN_ACTIVITY = "app/src/main/java/com/wedo/schedule/MainActivity.kt"
        const val IMPORT_WIZARD = "app/src/main/java/com/wedo/schedule/ui/screen/imports/ImportWizard.kt"
        const val IMPORT_FLOW = "app/src/main/java/com/wedo/schedule/ui/screen/imports/ImportFlow.kt"
    }
}
