// SPDX-License-Identifier: GPL-3.0-only
package com.wedo.jwimport.demo

import android.app.Activity
import android.app.AlertDialog
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.wedo.jwimport.*

/** Native Views demo: no dependency on wedo's Compose UI, Room, accounts or backend. */
class MainActivity : Activity(), ImportListener {
    private lateinit var controller: JwImportController
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var preview: TextView
    private lateinit var termsButton: Button
    private lateinit var fetchButton: Button
    private lateinit var saveButton: Button
    private lateinit var store: LocalScheduleStore
    private var term: TermSelection? = null
    private var pending: ScheduleResult? = null
    private var trusted = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val consent = getSharedPreferences("privacy", MODE_PRIVATE)
        if (!consent.getBoolean("accepted", false)) {
            AlertDialog.Builder(this).setTitle("教务导入开发示例")
                .setMessage("这是非官方开源示例。账号密码只在学校网页输入，示例不读取密码；确认后的标准化课程预览仅保存在本机。不要使用他人账号。")
                .setCancelable(false).setNegativeButton("退出") { _, _ -> finish() }
                .setPositiveButton("同意并继续") { _, _ -> consent.edit().putBoolean("accepted", true).apply(); setup() }.show()
        } else setup()
    }

    private fun setup() {
        store = LocalScheduleStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 8) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(16 + bars.left, 8 + bars.top, 16 + bars.right, 8 + bars.bottom)
            } else {
                @Suppress("DEPRECATION") view.setPadding(16, 8 + insets.systemWindowInsetTop, 16, 8 + insets.systemWindowInsetBottom)
            }
            insets
        }
        status = TextView(this).apply { text = "点击登录，在学校网页中完成认证，再读取学期。页面加载成功不等于登录成功。" }
        root.addView(status)
        fun row(): LinearLayout = LinearLayout(this).also(root::addView)
        fun action(row: LinearLayout, title: String, click: () -> Unit): Button = Button(this).apply {
            text = title; setOnClickListener { click() }
            row.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val first = row()
        action(first, "官方登录") { controller.openLogin() }
        termsButton = action(first, "读取学期") { controller.loadTerms() }
        fetchButton = action(first, "读取课表") { term?.let(controller::loadSchedule) }
        val second = row()
        saveButton = action(second, "确认保存") { confirmSave() }
        action(second, "离线缓存") { preview.text = store.read() ?: "尚无已确认的本地缓存" }
        action(second, "退出登录") {
            AlertDialog.Builder(this).setMessage("会清除本应用所有 WebView 的 Cookie，但不会删除已保存课程。继续？")
                .setNegativeButton("取消", null).setPositiveButton("继续") { _, _ ->
                    controller.clearAllWebViewCookies { status.text = "会话已清除，课程缓存保留" }
                }.show()
        }
        val third = row()
        action(third, "取消请求") { controller.cancel() }
        action(third, "脱敏样例") {
            controller.cancel()
            pending = ZhengfangParser.parseSchedule(assets.open("fixtures/schedule_response.json").bufferedReader().use { it.readText() }, TermSelection("2026", "3"))
            preview.text = format(pending!!)
            status.text = "离线脱敏样例，不是学校实时响应"
            updateButtons()
        }
        preview = TextView(this).apply { text = store.read() ?: "解析结果会先在这里预览，点击确认后才保存。"; setTextIsSelectable(true) }
        root.addView(ScrollView(this).apply { addView(preview) }, LinearLayout.LayoutParams(-1, 240))
        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
        controller = JwImportController(web, SchoolDefinition.JLJU, this)
        updateButtons()
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        }
    }

    override fun onBusyChanged(busy: Boolean) { this.busy = busy; updateButtons() }
    override fun onPageChanged(canRead: Boolean) { trusted = canRead; updateButtons() }
    override fun onTerms(options: TermOptions) {
        if (isFinishing || isDestroyed) return
        fun pickSemester(year: TermChoice) {
            AlertDialog.Builder(this).setTitle("选择学期（校方代码不等于 1/2）")
                .setItems(options.semesters.map { it.label }.toTypedArray()) { _, index ->
                    val semester = options.semesters[index]
                    term = TermSelection(year.value, semester.value)
                    status.text = "已选择 ${year.label} / ${semester.label}，再点击读取课表"
                    updateButtons()
                }.setNegativeButton("取消", null).show()
        }
        AlertDialog.Builder(this).setTitle("选择学年")
            .setItems(options.years.map { it.label }.toTypedArray()) { _, index -> pickSemester(options.years[index]) }
            .setNegativeButton("取消", null).show()
    }
    override fun onSchedule(result: ScheduleResult) {
        pending = result
        preview.text = format(result)
        status.text = "已取得并解析响应，请核对后确认保存"
        updateButtons()
    }
    override fun onError(error: ImportError) {
        status.text = when (error) {
            ImportError.SESSION_EXPIRED -> "会话可能过期，请通过官方网页重新登录"
            ImportError.NOT_ON_EDUCATION_PAGE -> "请先完成官方认证，回到教务系统页面"
            ImportError.TLS -> "学校连接证书异常，已拒绝继续"
            ImportError.INVALID_RESPONSE -> "接口结构无法识别，不会覆盖已有缓存"
            ImportError.TIMEOUT -> "学校请求超时，请稍后重试"
            ImportError.NETWORK -> "网络请求未完成，可能是断网或认证重定向"
            ImportError.SERVER -> "学校接口返回错误，请稍后重试"
            ImportError.CANCELLED -> "请求已取消"
            ImportError.BLOCKED_NAVIGATION -> "非白名单跳转已交给系统浏览器或阻止"
        } + " [${error.name}]"
    }
    private fun updateButtons() {
        if (!::termsButton.isInitialized) return
        termsButton.isEnabled = trusted && !busy
        fetchButton.isEnabled = trusted && !busy && term != null
        saveButton.isEnabled = !busy && pending?.let { it.emptySemester || it.courses.isNotEmpty() } == true
    }
    private fun confirmSave() {
        val result = pending ?: return
        AlertDialog.Builder(this).setTitle(if (result.emptySemester) "确认保存空学期？" else "确认保存课程？")
            .setMessage("将替换本示例的上一份确认缓存。跳过 ${result.skipped.size} 条异常记录；操作不会保存 Cookie 或原始响应。")
            .setNegativeButton("取消", null).setPositiveButton("保存") { _, _ ->
                try { store.save(format(result)); status.text = "本机保存成功，离线可查看" }
                catch (_: Exception) { status.text = "本地存储失败，原缓存保留" }
            }.show()
    }
    private fun format(result: ScheduleResult): String = buildString {
        append("${result.term.year} / ${result.term.semester}\n源记录 ${result.sourceCount}，课程时段 ${result.courses.size}，跳过 ${result.skipped.size}\n")
        if (result.emptySemester) append("校方明确返回 kbList:[]，本学期无课\n")
        result.courses.forEach { course ->
            append("\n${course.name} · 周${course.day} ${course.startNode}-${course.endNode}节\n")
            append("周次 ${course.weeks.sorted().joinToString(",")}\n${course.room} / ${course.teacher}\n")
        }
        result.skipped.forEach { append("\n跳过索引 ${it.index}: ${it.reason.name}") }
    }
    private fun handleBack() { if (::web.isInitialized && web.canGoBack()) web.goBack() else finish() }
    // Legacy API 26-32 only; API 33+ uses the registered native OnBackInvokedCallback.
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() = handleBack()
    override fun onDestroy() {
        if (::controller.isInitialized) controller.close()
        if (::web.isInitialized) web.destroy()
        if (::store.isInitialized) store.close()
        super.onDestroy()
    }
}
