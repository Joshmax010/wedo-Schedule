package com.wedo.schedule.ui.screen.imports

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务导入失败页 / 跨 Activity 接线契约测试（REQ-P2-03 / REQ-P2-07）。
 *
 * 无真机 / 模拟器 / Robolectric：只能靠「读源码 + 断言」守住
 * 「失败页确有 3 个动作按钮且接线非空」「改用文件导入真的进 ImportWizard（经 MainActivity extra 通道）」
 * 「状态流转真的走 JwImportStageMachine 而非临时 sealed Stage」。
 *
 * 断言前**先剥注释行** —— `JwImportActivity.kt` 注释里出现大量 `REQ-xxx` / 按钮名。
 */
class JwImportFailureWiringTest {

    private val activitySrc by lazy { TestProjectFiles.read(JW_IMPORT_ACTIVITY) }
    private val mainSrc by lazy { TestProjectFiles.read(MAIN_ACTIVITY) }

    /** 剥掉注释行（行注释 + 块注释两种前缀），避免断言命中说明文字。 */
    private fun executableLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    @Test
    fun failurePage_hasThreeActions_allWired() {
        val src = executableLines(activitySrc)
        assertTrue("失败页须有「重试抓取」", src.contains("R.string.jw_failure_retry"))
        assertTrue("失败页须有「改用文件导入」", src.contains("R.string.jw_failure_use_file"))
        assertTrue("失败页须有「手动添加课程」", src.contains("R.string.jw_failure_manual_add"))
        assertTrue("失败页须有标题", src.contains("R.string.jw_failure_title"))
        // 重试 = 关掉失败页 + 停在登录页（REQ-P2-07 提示用户停在个人课表页）。
        assertTrue("重试须清空 errorMsg", src.contains("errorMsg = null"))
        assertTrue("重试须经状态机停在登录页", src.contains("JwImportStageMachine.onCaptureFailed(stage)"))
        assertTrue("须有重试提示（停在个人课表页）", src.contains("R.string.jw_failure_retry_hint"))
        assertFalse("不得出现空 lambda 的按钮", src.contains("onClick = {}"))
        assertFalse("onRetry 不得为空 lambda", src.contains("onRetry = {}"))
        assertFalse("onUseFileImport 不得为空 lambda", src.contains("onUseFileImport = {}"))
        assertFalse("onManualAdd 不得为空 lambda", src.contains("onManualAdd = {}"))
    }

    @Test
    fun useFileImport_routesToImportWizard_viaMainActivityExtraChannel() {
        val activity = executableLines(activitySrc)
        assertTrue(
            "「改用文件导入」须真接线到 MainActivity.intentForImportWizard（非空 lambda）",
            activity.contains("MainActivity.intentForImportWizard(")
        )
        assertTrue(
            "「手动添加课程」须真接线到 MainActivity.intentForManualAdd（非空 lambda）",
            activity.contains("MainActivity.intentForManualAdd(")
        )
        assertTrue("跳转后须结束自身 Activity", activity.contains("finish()"))
        assertFalse("不得新增 Activity 声明", activity.contains("class JwImportFailureActivity"))

        val main = executableLines(mainSrc)
        assertTrue("MainActivity 须提供 intentForImportWizard", main.contains("fun intentForImportWizard("))
        assertTrue("MainActivity 须提供 intentForManualAdd", main.contains("fun intentForManualAdd("))
        assertTrue("extra 通道须被消费", main.contains("MainActivity.PENDING_OVERLAY_IMPORT ->"))
        assertTrue("extra 通道须被消费（手动添加）", main.contains("MainActivity.PENDING_OVERLAY_ADD_COURSE ->"))
        assertTrue("导入向导须经 pushOverlay(OverlayScreen.Import) 打开", main.contains("pushOverlay(OverlayScreen.Import)"))
        assertTrue("手动添加须经 pushOverlay(OverlayScreen.AddCourse) 打开", main.contains("pushOverlay(OverlayScreen.AddCourse)"))
        assertTrue("向导真接线：MainActivity 挂载 ImportWizard", main.contains("ImportWizard("))
    }

    @Test
    fun activityUsesStageMachine_notAdHocSealedStage() {
        val src = executableLines(activitySrc)
        assertTrue("Activity 须用 JwImportStage 枚举承载阶段", src.contains("mutableStateOf(JwImportStage.SELECT_SCHOOL)"))
        assertTrue("状态流转须经 JwImportStageMachine", src.contains("JwImportStageMachine."))
        assertFalse("不得残留私有 sealed Stage", src.contains("sealed class Stage"))
        assertFalse("不得残留旧 Stage.SelectSchool 引用", src.contains("Stage.SelectSchool"))
    }

    @Test
    fun failurePage_buttons_useTokensAndMinTouchTarget() {
        val src = executableLines(activitySrc)
        assertTrue(
            "按钮触控目标须引用 minTouchTarget 令牌（≥44dp）",
            src.contains("WedoAppleDimensions.minTouchTarget")
        )
        assertTrue("按钮须用带文字标签的 Button（TalkBack 可读）", src.contains("Button("))
        assertTrue("颜色须走令牌", src.contains("WedoTheme.colors"))
    }

    private companion object {
        const val JW_IMPORT_ACTIVITY =
            "app/src/main/java/com/wedo/schedule/ui/screen/imports/JwImportActivity.kt"
        const val MAIN_ACTIVITY = "app/src/main/java/com/wedo/schedule/MainActivity.kt"
    }
}
