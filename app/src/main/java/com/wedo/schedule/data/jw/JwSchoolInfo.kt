package com.wedo.schedule.data.jw

/**
 * 学校信息（教务入口元数据）。
 *
 * 字段含义：
 *   - sortKey：拼音首字母，用于分组排序
 *   - name：学校名（用户可见）
 *   - url：教务入口 URL，WebView 直接打开。未支持时为空
 *   - type：协议类型，见 [JwProtocol]。未支持时为 null
 *   - status：支持状态，见伴生对象常量
 *   - enableFetch：是否走 WebView 内 fetch 抓取（而非抓页面 HTML）
 *   - authHosts：**证据确认过的认证主机白名单**。这些主机可能渲染登录 UI，
 *     但绝不接收原生 JS Bridge —— 凭据只走它们自己的表单。
 *   - jsBridgeHosts：**证据确认过、允许安装原生 JS Bridge 的教务主机**。
 *     Bridge 只在顶层页面落到这些主机后短暂安装，离开即移除。
 *
 * 安全约定（白名单下沉数据层 · ADR-4 / §4.3）：
 *   - 两组 host 一律为**精确主机名**（如 `jwxt.jlju.edu.cn`），**严禁通配**
 *     （不得出现 `*.jlju.edu.cn`）；也不得为"将来多校"提前放宽。
 *   - 新增学校**必须**声明两组 host —— 由 `JwWhitelistContractTest` 兜底，
 *     未声明即测试失败。
 */
data class JwSchoolInfo(
    val sortKey: String,
    val name: String,
    val url: String = "",
    val type: String? = null,
    val status: String = STATUS_SUPPORTED,
    val aliases: List<String> = emptyList(),
    val sortKeyFull: String = "",
    /** 是否启用 WebView 内 fetch 模式。默认 false，避免对未验证的入口强行 fetch 出 0 课。 */
    val enableFetch: Boolean = false,
    /** 可渲染登录 UI 的认证主机（精确主机名，绝不安装 JS Bridge）。 */
    val authHosts: Set<String> = emptySet(),
    /** 允许安装原生 JS Bridge 的教务主机（精确主机名）。 */
    val jsBridgeHosts: Set<String> = emptySet(),
) {
    val isSupported: Boolean get() = status == STATUS_SUPPORTED

    /** 待适配状态 —— 点行提示，不进 WebView。 */
    val isPending: Boolean get() = status == STATUS_PENDING

    /** 是否配置了可打开的教务 URL。 */
    val hasUrl: Boolean get() = url.isNotBlank()

    companion object {
        const val STATUS_SUPPORTED = "supported"
        const val STATUS_PENDING = "pending"
    }
}
