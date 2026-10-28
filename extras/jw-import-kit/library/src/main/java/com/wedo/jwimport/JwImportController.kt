// SPDX-License-Identifier: GPL-3.0-only
// Portable adaptation of wedo's official-WebView import flow. No addJavascriptInterface.
package com.wedo.jwimport

import android.annotation.SuppressLint
import android.content.Intent
import android.net.http.SslError
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Use a dedicated WebView. Every public method is main-thread only.
 * close() detaches this controller but does not destroy the host-owned WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
class JwImportController(
    private val webView: WebView,
    val school: SchoolDefinition,
    private val listener: ImportListener,
) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0L
    private var pendingKey: String? = null
    private var poll: Runnable? = null
    private var closed = false

    init {
        mainThread()
        webView.settings.apply {
            javaScriptEnabled = true // Only official pages; never inspect password fields/values.
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            @Suppress("DEPRECATION") setSaveFormData(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (school.isAllowedNavigation(request.url.toString())) return false
                // Never open custom schemes; never pass authentication sessions into native HTTP clients.
                if (request.isForMainFrame && request.url.scheme == "https") {
                    runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                listener.onError(ImportError.BLOCKED_NAVIGATION)
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                // Navigation invalidates ALL outstanding responses, even between trusted pages.
                invalidate()
                listener.onPageChanged(false)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                listener.onPageChanged(school.isEducationPage(url)) // Not an authenticated-success event.
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                invalidate()
                listener.onError(ImportError.TLS)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { invalidate(); listener.onError(ImportError.NETWORK) }
            }
        }
    }

    fun openLogin() { mainThread(); check(!closed); cancel(); webView.loadUrl(school.loginUrl) }
    fun loadTerms() = request(FetchScripts.terms(school)) { html ->
        val terms = ZhengfangParser.parseTerms(html)
        val notify: () -> Unit = { listener.onTerms(terms) }
        notify
    }
    fun loadSchedule(term: TermSelection) = request(FetchScripts.schedule(school, term)) { json ->
        val result = ZhengfangParser.parseSchedule(json, term)
        val notify: () -> Unit = { listener.onSchedule(result) }
        notify
    }

    /** Does not change the host's saved courses. */
    fun cancel() {
        mainThread()
        val wasPending = pendingKey != null
        cleanSlot()
        invalidate()
        if (wasPending) listener.onError(ImportError.CANCELLED)
    }

    /** WARNING: Android CookieManager is app-wide: this clears ALL WebView cookies in this app. */
    fun clearAllWebViewCookies(onCleared: () -> Unit) {
        mainThread(); check(!closed); cancel(); webView.stopLoading()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            if (!closed) { webView.clearHistory(); listener.onPageChanged(false); onCleared() }
        }
    }

    private fun request(script: String, parse: (String) -> (() -> Unit)) {
        mainThread(); check(!closed)
        if (!school.isEducationPage(webView.url)) { listener.onError(ImportError.NOT_ON_EDUCATION_PAGE); return }
        cancel()
        val ticket = ++generation
        val key = "__wedoJw_" + UUID.randomUUID().toString().replace("-", "")
        pendingKey = key
        listener.onBusyChanged(true)
        val quoted = JSONObject.quote(key)
        webView.evaluateJavascript("""
            (function(){
              const key=$quoted, abort=new AbortController();
              window[key]={state:'pending', abort:abort};
              $script
              read(abort).then(function(payload){window[key]={state:'done',payload:payload};})
                .catch(function(error){
                  const codes=['SESSION_EXPIRED','SERVER','INVALID_RESPONSE'];
                  window[key]={state:'error',code:codes.indexOf(error)>=0?error:'NETWORK'};
                });
            })();
        """.trimIndent(), null)
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
        val task = object : Runnable {
            override fun run() {
                if (closed || generation != ticket) return
                if (!school.isEducationPage(webView.url)) { finishError(ImportError.NOT_ON_EDUCATION_PAGE); return }
                if (android.os.SystemClock.elapsedRealtime() >= deadline) { finishError(ImportError.TIMEOUT); return }
                webView.evaluateJavascript("JSON.stringify(window[$quoted] ? {state:window[$quoted].state,payload:window[$quoted].payload,code:window[$quoted].code} : null)") { raw ->
                    if (closed || generation != ticket || !school.isEducationPage(webView.url)) return@evaluateJavascript
                    val envelope = decodeJavascriptResult(raw)
                    when (envelope?.optString("state")) {
                        "done" -> {
                            val payload = envelope.optString("payload")
                            cleanSlot()
                            poll = null
                            worker.execute {
                                val parsed = runCatching { parse(payload) }
                                webView.post {
                                    if (!closed && generation == ticket && school.isEducationPage(webView.url)) {
                                        pendingKey = null
                                        listener.onBusyChanged(false)
                                        parsed.fold(onSuccess = { it() }, onFailure = {
                                            listener.onError((it as? ImportException)?.code ?: ImportError.INVALID_RESPONSE)
                                        })
                                    }
                                }
                            }
                        }
                        "error" -> finishError(runCatching { ImportError.valueOf(envelope.optString("code")) }.getOrDefault(ImportError.NETWORK))
                        else -> webView.postDelayed(this, 200)
                    }
                }
            }
        }
        poll = task
        webView.postDelayed(task, 200)
    }

    private fun finishError(error: ImportError) { cleanSlot(); invalidate(); listener.onError(error) }
    private fun cleanSlot() {
        pendingKey?.let { key ->
            if (school.isEducationPage(webView.url)) webView.evaluateJavascript(
                "(function(){var s=window[${JSONObject.quote(key)}];if(s&&s.abort)s.abort.abort();delete window[${JSONObject.quote(key)}];})();", null)
        }
    }
    private fun invalidate() {
        generation++
        poll?.let(webView::removeCallbacks)
        poll = null
        pendingKey = null
        listener.onBusyChanged(false)
    }
    override fun close() {
        mainThread()
        if (closed) return
        cancel()
        closed = true
        worker.shutdownNow()
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
    }
    private fun mainThread() = check(Looper.myLooper() == Looper.getMainLooper()) { "Use the main thread" }

    companion object {
        internal fun decodeJavascriptResult(raw: String?): JSONObject? = runCatching {
            val value = raw?.let { JSONTokener(it).nextValue() } as? String ?: return null
            if (value == "null") null else JSONObject(value)
        }.getOrNull()
    }
}
