package com.wedo.schedule.data.jw

/**
 * WebView 内 fetch 脚本的选择表（纯函数）。
 *
 * 吉林建筑大学的新版正方 `jwglxt` 课表数据不在 DOM 里 —— 页面渲染后由前端 JS
 * 异步拉 `kbList` JSON。因此抓取策略是：在 WebView 里执行 fetch 脚本直接拿 JSON，
 * 而不是读 `document.documentElement.outerHTML`。
 *
 * [pick] 返回 `false` 表示走 outerHTML 兜底路径。
 */
object JwFetchProtocol {

    /** WebVPN 重写形态：`/http/<hex4-8>/...` */
    private val WEBVPN_HTTP_HEX = Regex("""/http/[0-9a-f]{4,8}/""")

    /**
     * 判断当前页面是否该走 fetch 抓取。
     *
     * @param school 学校配置
     * @param currentUrl WebView 当前地址；null 时用学校配置的入口 URL
     */
    fun shouldFetch(school: JwSchoolInfo, currentUrl: String?): Boolean {
        val u = (currentUrl ?: school.url).lowercase()
        return u.contains("/jwglxt/") || WEBVPN_HTTP_HEX.containsMatchIn(u)
    }

    /** 从 URL 抓 `gnmkdm` 参数；没有则返回 default。 */
    fun extractGnmkdm(url: String, default: String): String {
        val m = Regex("""gnmkdm=([A-Za-z0-9]+)""").find(url)
        return m?.groupValues?.get(1) ?: default
    }

    /** 去掉 WebVPN 前缀（`/http/<hex>/` 与 `/webvpn/<host>/` 两种）；无前缀原样返回。 */
    fun stripWebvpnPrefix(pathname: String): String {
        val httpHex = Regex("""^/http/[0-9a-f]{4,8}""")
        httpHex.find(pathname)?.let { return pathname.substringAfter(it.value) }
        val webHost = Regex("""^/webvpn/[^/]+""")
        webHost.find(pathname)?.let { return pathname.substringAfter(it.value) }
        return pathname
    }
}
