package com.wedo.schedule.ui.screen.imports

/**
 * 教务直连导入的页面阶段（REQ-P2-06 「会话上下文保留」）。
 *
 * 从 `JwImportActivity` 内原先的**私有 sealed `Stage`** 抽出为纯枚举 —— 目的很单纯：
 * 屏幕里的匿名对象无法被单测覆盖，而「失败后停在哪个阶段」正是本任务要守住的不变量，
 * 必须可被纯 JVM 单测穷举。
 */
enum class JwImportStage {
    /** 选择学校。 */
    SELECT_SCHOOL,

    /** WebView 登录 + 抓取。登录 / 抓取失败都**停留在此阶段**，保留会话上下文。 */
    WEBVIEW_LOGIN,

    /** 抓取成功 → 配置确认（起始周 / 作息 / 表名）。 */
    CONFIGURE_CONFIRM,
}

/**
 * 教务导入阶段状态机（纯函数，零 Android 依赖）。
 *
 * 核心不变量（REQ-P2-06）：**登录 / 抓取 / 解析失败一律停在
 * [JwImportStage.WEBVIEW_LOGIN]**，绝不退回 [JwImportStage.SELECT_SCHOOL] 让用户从头选校 ——
 * 会话上下文（已登录的 WebView 及其导航历史）必须保留，用户只需在页内停在「个人课表」页再试。
 */
object JwImportStageMachine {

    /** 选校完成 → 进入登录 / 抓取页。 */
    fun onSchoolSelected(): JwImportStage = JwImportStage.WEBVIEW_LOGIN

    /**
     * 抓取 / 解析 / 捕获失败后**应停留**的阶段。
     *
     * @param current 失败发生时所在阶段
     * @return 重试应停留的阶段：配置页失败退回登录页（可重抓）；登录页失败原地停留；
     *         选校页（理论上不产生抓取失败）原地停留。
     */
    fun onCaptureFailed(current: JwImportStage): JwImportStage = when (current) {
        JwImportStage.CONFIGURE_CONFIRM -> JwImportStage.WEBVIEW_LOGIN
        JwImportStage.WEBVIEW_LOGIN -> JwImportStage.WEBVIEW_LOGIN
        JwImportStage.SELECT_SCHOOL -> JwImportStage.SELECT_SCHOOL
    }

    /** 抓到载荷并解析后的去向：解析出 ≥1 门课 → 配置确认；否则停留登录页（空 / 失败）。 */
    fun onPayloadParsed(courseCount: Int): JwImportStage =
        if (courseCount > 0) JwImportStage.CONFIGURE_CONFIRM else JwImportStage.WEBVIEW_LOGIN

    /** 返回键：配置页 → 登录页；登录页 → 选校页；选校页原地（由宿主 finish）。 */
    fun onBack(current: JwImportStage): JwImportStage = when (current) {
        JwImportStage.CONFIGURE_CONFIRM -> JwImportStage.WEBVIEW_LOGIN
        JwImportStage.WEBVIEW_LOGIN -> JwImportStage.SELECT_SCHOOL
        JwImportStage.SELECT_SCHOOL -> JwImportStage.SELECT_SCHOOL
    }
}
