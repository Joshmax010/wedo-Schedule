package com.wedo.schedule.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 教务导入阶段状态机纯逻辑测试（REQ-P2-06 「会话上下文保留」）。
 *
 * 关键不变量：**登录 / 抓取 / 解析失败一律停在 `WEBVIEW_LOGIN`**，
 * 绝不退回 `SELECT_SCHOOL` 让用户从头重走 —— 会话上下文（已登录 WebView + 导航历史）必须保留。
 */
class JwImportStageMachineTest {

    @Test
    fun onSchoolSelected_entersLoginStage() {
        assertEquals(JwImportStage.WEBVIEW_LOGIN, JwImportStageMachine.onSchoolSelected())
    }

    @Test
    fun onCaptureFailed_fromLogin_staysOnLogin_neverBackToSelectSchool() {
        val next = JwImportStageMachine.onCaptureFailed(JwImportStage.WEBVIEW_LOGIN)
        assertEquals("抓取失败必须停在登录页", JwImportStage.WEBVIEW_LOGIN, next)
        assertNotEquals("绝不退回选校", JwImportStage.SELECT_SCHOOL, next)
    }

    @Test
    fun onCaptureFailed_fromConfig_fallsBackToLogin_notSelectSchool() {
        val next = JwImportStageMachine.onCaptureFailed(JwImportStage.CONFIGURE_CONFIRM)
        assertEquals("配置页失败退回登录页（可重抓）", JwImportStage.WEBVIEW_LOGIN, next)
        assertNotEquals("绝不退回选校", JwImportStage.SELECT_SCHOOL, next)
    }

    @Test
    fun onPayloadParsed_nonEmpty_goesToConfig() {
        assertEquals(JwImportStage.CONFIGURE_CONFIRM, JwImportStageMachine.onPayloadParsed(1))
        assertEquals(JwImportStage.CONFIGURE_CONFIRM, JwImportStageMachine.onPayloadParsed(42))
    }

    @Test
    fun onPayloadParsed_empty_staysOnLogin_notBackToSelectSchool() {
        val next = JwImportStageMachine.onPayloadParsed(0)
        assertEquals("解析为空停在登录页", JwImportStage.WEBVIEW_LOGIN, next)
        assertNotEquals("绝不退回选校", JwImportStage.SELECT_SCHOOL, next)
    }

    @Test
    fun onBack_isHierarchical() {
        assertEquals(JwImportStage.WEBVIEW_LOGIN, JwImportStageMachine.onBack(JwImportStage.CONFIGURE_CONFIRM))
        assertEquals(JwImportStage.SELECT_SCHOOL, JwImportStageMachine.onBack(JwImportStage.WEBVIEW_LOGIN))
        assertEquals(JwImportStage.SELECT_SCHOOL, JwImportStageMachine.onBack(JwImportStage.SELECT_SCHOOL))
    }

    @Test
    fun failureNeverProducesSelectSchool_fromAnyNonSelectStage() {
        // 穷举：任何「非选校」阶段的失败都不能把用户送回选校页。
        listOf(JwImportStage.WEBVIEW_LOGIN, JwImportStage.CONFIGURE_CONFIRM).forEach { st ->
            assertNotEquals(
                "阶段 $st 失败后不得退回选校",
                JwImportStage.SELECT_SCHOOL,
                JwImportStageMachine.onCaptureFailed(st)
            )
        }
    }
}
