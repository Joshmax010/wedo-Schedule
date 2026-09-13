package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WedoWebViewSecurityContractTest {

    private val source = TestProjectFiles.read(
        "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"
    )

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
        val pageFinished = source.substringAfter("override fun onPageFinished")
            .substringBefore("loadUrl(url)")
        assertTrue(source.contains("jwxt.jlju.edu.cn"))
        assertTrue(pageFinished.contains("finishedHost !in VERIFIED_JS_BRIDGE_HOSTS"))
        assertTrue(pageFinished.contains("addJavascriptInterface(bridge, bridgeName)"))
        assertTrue(pageFinished.contains("view.reload()"))
        assertTrue(source.contains("UUID.randomUUID()"))
    }

    @Test
    fun `authentication pages never retain the javascript bridge`() {
        assertTrue(source.contains("lxr.jlju.edu.cn"))
        assertTrue(source.contains("cas.jlju.edu.cn"))
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
}
