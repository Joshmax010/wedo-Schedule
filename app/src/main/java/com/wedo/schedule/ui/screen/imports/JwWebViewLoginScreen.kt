package com.wedo.schedule.ui.screen.imports

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.data.jw.JwImportViewModel
import com.wedo.schedule.data.jw.JwProtocol
import com.wedo.schedule.data.jw.JwSchoolInfo
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoAppleType
import kotlinx.coroutines.launch

/** fetch JS 注入超时：教务宕机时 20s 无桥回调即报超时，不无限 pending。 */
private const val FETCH_TIMEOUT_MS = 20_000L

/**
 * 教务 WebView 登录页。
 *
 * 安全约定：
 *   - 登录页不注册原生 Bridge，也不读取账号密码输入框。
 *   - 顶层页面落到已确认的教务主机后，才以随机接口名安装 Bridge 并重载一次。
 *   - 只在教务主机上执行同源 fetch；离开该主机立即移除 Bridge。
 *   - SSL 错误一律 cancel，不做任何例外放行。
 *
 * 白名单（`authHosts` / `jsBridgeHosts`）已**下沉数据层**（ADR-4 / §4.3），
 * 本 UI 层不再硬编码任何主机名，改从传入的 [JwSchoolInfo] 读取 —— 仅保留局部名
 * `VERIFIED_AUTH_HOSTS` / `VERIFIED_JS_BRIDGE_HOSTS` 以最小化改动面。
 *
 * 流程：加载入口 → 用户输账号密码 + 验证码 → 导航到个人课表 → 点「导入此页」
 *      → 页内 fetch 拿 kbList JSON → 桥回调 → 解析落库
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwWebViewLoginScreen(
    school: JwSchoolInfo,
    onPayloadCaptured: (payload: String, school: JwSchoolInfo) -> Unit,
    onCaptureError: (hint: String) -> Unit,
    onBack: () -> Unit,
    viewModel: JwImportViewModel = viewModel()
) {
    val colors = WedoTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val logToken = remember { java.util.concurrent.atomic.AtomicLong(0) }
    val webviewNotReadyMsg = stringResource(R.string.jw_webview_not_ready)
    val fetchingMsg = stringResource(R.string.jw_fetching)
    val fetchFailedNoResponseMsg = stringResource(R.string.jw_fetch_failed_no_response)
    val fetchFormatErrorMsg = stringResource(R.string.jw_fetch_format_error)
    val fetchFailedFmt = stringResource(R.string.jw_fetch_failed)
    val fetchTimeoutMsg = stringResource(R.string.jw_fetch_timeout)

    /**
     * 页内 fetch 结果回调。桥回调已切到主线程。
     * 载荷形如 `{ok:true, data:"<kbList JSON>"}` 或 `{ok:false, err:"..."}`。
     */
    val handleFetchResult: (String) -> Unit = { json ->
        try {
            val obj = org.json.JSONObject(json)
            if (obj.optBoolean("ok", false)) {
                val data = obj.optString("data", "")
                if (data.isBlank()) {
                    scope.launch { snackbar.showSnackbar(fetchFailedNoResponseMsg) }
                } else {
                    Log.d("JwWebView", "fetch ok payloadLen=${data.length}")
                    onPayloadCaptured(data, school)
                }
            } else {
                scope.launch { snackbar.showSnackbar(fetchFailedFmt.format("E_FETCH_RESPONSE")) }
            }
        } catch (_: Exception) {
            Log.e("JwWebView", "fetch result invalid code=E_FETCH_FORMAT")
            scope.launch { snackbar.showSnackbar(fetchFormatErrorMsg) }
        }
    }

    // evaluateJavascript 无内建超时：教务挂起时桥回调永远不来，用户只见「正在抓取」。
    fun evaluateFetchWithTimeout(wv: WebView, js: String) {
        var answered = false
        val attemptId = logToken.incrementAndGet()
        wv.evaluateJavascript(js) {
            answered = true
            Log.d("JwWebView", "fetch js done attempt=$attemptId")
        }
        wv.postDelayed({
            if (!answered) {
                Log.w("JwWebView", "fetch js timeout attempt=$attemptId")
                scope.launch { snackbar.showSnackbar(fetchTimeoutMsg) }
            }
        }, FETCH_TIMEOUT_MS)
    }

    BackHandler {
        webViewRef?.let { wv ->
            if (wv.canGoBack()) wv.goBack() else onBack()
        } ?: onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(school.name, style = WedoAppleType.headline())
                        Text(
                            text = JwProtocol.displayName(school.type),
                            style = WedoAppleType.footnote(),
                            color = colors.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            CaptureBar(
                enabled = webViewRef != null,
                onCapture = {
                    val wv = webViewRef
                    if (wv == null) {
                        Log.w("JwWebView", "capture tapped but webViewRef is null")
                        scope.launch { snackbar.showSnackbar(webviewNotReadyMsg) }
                        return@CaptureBar
                    }
                    Log.d("JwWebView", "capture tapped host=${wv.url?.toUri()?.host.orEmpty()}")
                    scope.launch { snackbar.showSnackbar(fetchingMsg) }
                    // 新版正方的课表数据由页内 JS 异步拉 kbList JSON，DOM 里没有课程。
                    evaluateFetchWithTimeout(wv, ZF_NEW_FETCH_JS)
                }
            )
        },
        containerColor = colors.background
    ) { padding ->
        JwWebView(
            school = school,
            onWebViewCreated = { wv -> webViewRef = wv },
            onFetchResult = handleFetchResult
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun JwWebView(
    school: JwSchoolInfo,
    onWebViewCreated: (WebView) -> Unit,
    onFetchResult: (String) -> Unit,
) {
    // 白名单来自数据层（ADR-4 / §4.3）；保留局部名，改动面最小。
    val url = school.url
    val VERIFIED_AUTH_HOSTS: Set<String> = school.authHosts
    val VERIFIED_JS_BRIDGE_HOSTS: Set<String> = school.jsBridgeHosts
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val schoolHost = url.toUri().host.orEmpty().lowercase()
            val bridgeName = "__wedoBridge_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            val bridge = FetchBridge(onFetchResult)
            var bridgeInstalled = false
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = false
                    allowContentAccess = false
                    setGeolocationEnabled(false)
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean = true
                }
                webViewClient = object : android.webkit.WebViewClient() {
                    private fun removeBridge(view: WebView) {
                        if (bridgeInstalled) {
                            view.removeJavascriptInterface(bridgeName)
                            bridgeInstalled = false
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean {
                        val target = request.url
                        val targetHost = target.host.orEmpty().lowercase()
                        val allowed = target.scheme == "https" &&
                            (targetHost == schoolHost || targetHost in VERIFIED_AUTH_HOSTS)
                        if (allowed && targetHost !in VERIFIED_JS_BRIDGE_HOSTS) {
                            removeBridge(view)
                        }
                        if (!allowed && target.scheme in setOf("http", "https")) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, target))
                            }
                        }
                        return !allowed
                    }

                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        if (url?.toUri()?.host.orEmpty().lowercase() !in VERIFIED_JS_BRIDGE_HOSTS) {
                            removeBridge(view)
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView,
                        handler: android.webkit.SslErrorHandler,
                        error: android.net.http.SslError
                    ) {
                        // 证书被 Android 拒绝的连接绝不能承载凭据，无任何学校例外。
                        handler.cancel()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        val finishedHost = url?.toUri()?.host.orEmpty().lowercase()
                        Log.d("JwWebView", "page finished host=$finishedHost")
                        if (view == null || finishedHost !in VERIFIED_JS_BRIDGE_HOSTS) return

                        // addJavascriptInterface 在下一个文档加载时才生效：
                        // 只在顶层页面落到已确认教务主机后安装，然后 reload 一次。
                        // 随机接口名只在可信顶层帧内被别名成稳定名，登录页与无关帧拿不到。
                        if (!bridgeInstalled) {
                            view.addJavascriptInterface(bridge, bridgeName)
                            bridgeInstalled = true
                            view.reload()
                            return
                        }
                        view.evaluateJavascript(
                            "window.__wedoBridge = window['$bridgeName'];",
                            null,
                        )
                    }
                }
                loadUrl(url)
                onWebViewCreated(this)
            }
        }
    )
}

/**
 * JS 桥：页内 fetch 脚本通过 `window.__wedoBridge` 回传结果。
 *
 * 方法名与 [ZF_NEW_FETCH_JS] 内的调用点一致 —— 改任一侧必须同步。
 */
private class FetchBridge(private val onResult: (String) -> Unit) {
    @JavascriptInterface
    fun onFetchResult(payload: String) = onResult(payload)

    @JavascriptInterface
    fun onNeedTermSelection(
        optionsJson: String,
        defaultIndex: Int,
        callback: android.webkit.ValueCallback<String>
    ) {
        // 单校场景下学期由接口默认值决定，不打断用户。
        callback.onReceiveValue("null")
    }
}

@Composable
private fun CaptureBar(enabled: Boolean, onCapture: () -> Unit) {
    val colors = WedoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.jw_after_login),
                style = WedoAppleType.footnote(),
                color = colors.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.jw_nav_hint),
                style = WedoAppleType.subheadline().copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
        }
        Button(
            onClick = onCapture,
            enabled = enabled,
            shape = WedoTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(stringResource(R.string.jw_import_page), color = colors.onPrimary)
        }
    }
}
