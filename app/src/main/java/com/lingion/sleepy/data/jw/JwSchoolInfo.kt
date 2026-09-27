package com.lingion.sleepy.data.jw

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
