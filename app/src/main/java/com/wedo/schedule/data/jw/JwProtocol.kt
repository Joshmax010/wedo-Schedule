package com.wedo.schedule.data.jw

/**
 * 教务系统协议类型。
 *
 * wedo 目前只支持新版正方 `jwglxt`（吉林建筑大学在用的那套）。
 * 常量保留成对象而非 enum，是为了让持久化过的字符串（SharedPreferences、
 * 数据库里的历史值）在反序列化时不会崩 —— 遇到未知值一律回落
 * [TYPE_ZF_NEW] 交给 [JwParserRegistry] 处理。
 */
object JwProtocol {

    /** 新版正方 `jwglxt`：个人课表接口返回含 `kbList` 的 JSON。 */
    const val TYPE_ZF_NEW = "zf_new"

    /** 未识别的占位值。UI 上显示为「未知教务系统」。 */
    const val TYPE_UNKNOWN = "unknown"

    /**
     * URL 级判型：只看 host 与路径锚点，不做网络请求。
     *
     * 新版正方的部署形态差异很大（子域、端口、反向代理路径都可能不同），
     * 因此锚点刻意取宽：命中 `jwglxt` 或 `/xtgl/` 即认为是新版正方。
     * CAS 网关页（`/cas/login`、`/authserver/login`）只是一跳中转，
     * 其 `service=` 参数里带的业务路径不作为指纹，返回 null 交给页面级判定。
     *
     * @param url 已经 lowercase 过的 URL
     */
    fun detect(url: String): String? {
        if (url.isBlank()) return null
        if (url.contains("/cas/login") || url.contains("/authserver/login")) return null
        return when {
            url.contains("jwglxt") -> TYPE_ZF_NEW
            url.matches(Regex(""".*/xtgl(/|$).*""")) -> TYPE_ZF_NEW
            url.contains("/kbcx/") -> TYPE_ZF_NEW
            url.contains("xskbcx_cx") -> TYPE_ZF_NEW
            else -> null
        }
    }

    /**
     * 页级判型：URL 判不出来时看页面特征。
     *
     * `zftal-ui-` 是新版正方的前端资源前缀，`教学管理信息服务平台` 是其页面标题，
     * 这两个是最稳的锚点。老版本正方的 `__VIEWSTATE` 不作为命中依据 ——
     * 它太通用，很多 .NET 站点都有。
     *
     * @param html 原始 HTML
     */
    fun detectFromHtml(html: String): String? {
        if (html.isBlank()) return null
        val lower = html.lowercase()
        return when {
            lower.contains("zftal-ui-") -> TYPE_ZF_NEW
            extractTitle(html).contains("教学管理信息服务平台") -> TYPE_ZF_NEW
            lower.contains("login_slogin.html") -> TYPE_ZF_NEW
            else -> null
        }
    }

    /** 抽 `<title>` 文本；无标题返回空串。大文档用 indexOf 截窗，不引入 HTML 解析库。 */
    fun extractTitle(html: String): String {
        val start = html.indexOf("<title", ignoreCase = true)
        if (start < 0) return ""
        val openEnd = html.indexOf('>', start)
        if (openEnd < 0) return ""
        val closeStart = html.indexOf("</title>", openEnd + 1, ignoreCase = true)
        if (closeStart < 0) return ""
        return html.substring(openEnd + 1, closeStart).trim()
    }

    /** 命中的指纹特征，供导入失败时的诊断文案使用。 */
    fun hitFeatures(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val lower = html.lowercase()
        val title = extractTitle(html)
        val hits = mutableListOf<String>()
        if (lower.contains("zftal-ui-")) hits += "zftal-ui-"
        if (title.contains("教学管理信息服务平台")) hits += "title:教学管理信息服务平台"
        if (lower.contains("login_slogin.html")) hits += "login_slogin.html"
        if (lower.contains("jwglxt")) hits += "jwglxt"
        if (lower.contains("\"kblist\"")) hits += "kbList"
        if (lower.contains("kblist_table")) hits += "kblist_table"
        if (lower.contains("kbgrid_table_0")) hits += "kbgrid_table_0"
        return hits
    }

    /** UI 展示名。 */
    fun displayName(type: String?): String = when (type) {
        TYPE_ZF_NEW -> "正方教务（新版）"
        TYPE_UNKNOWN -> "未知教务系统"
        else -> type ?: ""
    }
}
