package com.wedo.schedule.ui.screen.imports

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebView 安全策略契约测试（UI 侧结构 / 时机 / 不变量）。
 *
 * TASK-03 变更：白名单（`authHosts` / `jsBridgeHosts`）**下沉数据层**后，
 * 本文件里原先断言 **域名字面量** 的三条（`lxr.` / `cas.` / `jwxt.jlju.edu.cn`）
 * 已**迁移**到 `data/jw/JwWhitelistContractTest`（域名的真值源在数据层）。
 * 这里保留全部**结构与安全时机**断言，并新增「UI 只从 school 读取白名单、不再硬编码域名」的反向断言。
 *
 * 断言源码前**先剥注释行** —— 文件顶部 KDoc 会出现 `authHosts` 等词，不剥注释会误判。
 */
class WedoWebViewSecurityContractTest {

    private val source = TestProjectFiles.read(
        "app/src/main/java/com/wedo/schedule/ui/screen/imports/JwWebViewLoginScreen.kt"
    )

    /** 剥掉注释行（行注释 + 块注释两种前缀），避免断言命中说明文字。 */
    private fun executableLines(src: String): String = src.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    @Test
    fun `certificate errors are always cancelled`() {
        val block = source.substringAfter("override fun onReceivedSslError(")
            .substringBefore("override fun onPageFinished")
        assertTrue(block.contains("handler.cancel()"))
        assertFalse(block.contains("handler.proceed()"))
    }

    @Test
    fun `mixed content and local file access are disabled`() {
        assertTrue(source.contains("MIXED_CONTENT_NEVER_ALLOW"))
        assertTrue(source.contains("allowFileAccess = false"))
        assertTrue(source.contains("allowContentAccess = false"))
        assertFalse(source.contains("MIXED_CONTENT_ALWAYS_ALLOW"))
    }

    @Test
    fun `javascript bridge is installed only after trusted teaching host loads`() {
        // 结构 / 时机断言（域名真值已迁至 JwWhitelistContractTest）。
        val pageFinished = source.substringAfter("override fun onPageFinished")
            .substringBefore("loadUrl(url)")
        assertTrue(pageFinished.contains("finishedHost !in VERIFIED_JS_BRIDGE_HOSTS"))
        assertTrue(pageFinished.contains("addJavascriptInterface(bridge, bridgeName)"))
        assertTrue(pageFinished.contains("view.reload()"))
        assertTrue(source.contains("UUID.randomUUID()"))
    }

    @Test
    fun `authentication pages never retain the javascript bridge`() {
        assertTrue(source.contains("targetHost !in VERIFIED_JS_BRIDGE_HOSTS"))
        assertTrue(source.contains("removeJavascriptInterface(bridgeName)"))
    }

    @Test
    fun `navigation is restricted to https allowlist`() {
        assertTrue(source.contains("target.scheme == \"https\""))
        assertTrue(source.contains("targetHost in VERIFIED_AUTH_HOSTS"))
        assertTrue(source.contains("target.scheme in setOf(\"http\", \"https\")"))
    }

    @Test
    fun `diagnostic logs never include full page URLs`() {
        assertFalse(source.contains("current url="))
        assertTrue(source.contains("capture tapped host="))
    }

    @Test
    fun `host allowlist is read from school data layer not hardcoded in UI`() {
        // 白名单下沉的**反向**断言：UI 不再持有域名，改从传入的 JwSchoolInfo 读取。
        val src = executableLines(source)
        assertTrue("认证白名单须从 school.authHosts 读取", src.contains("school.authHosts"))
        assertTrue("桥白名单须从 school.jsBridgeHosts 读取", src.contains("school.jsBridgeHosts"))
        assertFalse("UI 层不得再硬编码任何 jlju 主机名（已迁移数据层）", src.contains("jlju.edu.cn"))
        assertFalse("不得出现通配域名", src.contains("*.jlju") || src.contains("\"*."))
    }
}
