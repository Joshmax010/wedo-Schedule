package com.wedo.schedule.ui.screen.imports

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.MainActivity
import com.wedo.schedule.WedoApp
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.component.WedoIconButton
import com.wedo.schedule.ui.component.WedoPrimaryButton
import com.wedo.schedule.data.jw.JwCourse
import com.wedo.schedule.data.jw.JwImportViewModel
import com.wedo.schedule.data.jw.JwParseDiagnostics
import com.wedo.schedule.data.jw.JwProtocol
import com.wedo.schedule.data.jw.JwSchoolInfo
import com.wedo.schedule.data.parser.ScheduleParser
import com.wedo.schedule.ui.component.DatePickerField
import com.wedo.schedule.ui.component.TimeSlotEditor
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoThemeProvider
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.TimeTableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.wedo.schedule.R

/**
 * 教务直连导入主屏
 *
 * 流程：选择学校 → WebView 登录并抓取课表数据 → 解析 → 复用 ImportScreen 预览 → 落库
 */
class JwImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 跟随 app 主题设置(此前硬编码 default+只跟系统深色,选春绿/海蓝后此页不跟随)
            val systemDark = isSystemInDarkTheme()
            val dark = remember(systemDark) {
                AppPrefs.isDarkMode(this@JwImportActivity, systemDark)
            }
            val themeKey by AppPrefs.themeKeyFlow(this@JwImportActivity)
                .collectAsState(initial = AppPrefs.getThemeKey(this@JwImportActivity))
            WedoThemeProvider(darkTheme = dark, themeKey = themeKey) {
                val jwViewModel: JwImportViewModel = viewModel()
                val scheduleViewModel: ScheduleViewModel = viewModel()
                val scope = rememberCoroutineScope()

                var selectedSchool by remember { mutableStateOf<JwSchoolInfo?>(null) }
                var stage by remember { mutableStateOf(JwImportStage.SELECT_SCHOOL) }
                var errorMsg by remember { mutableStateOf<String?>(null) }
                var statusMsg by remember { mutableStateOf<String?>(null) }
                var importFinished by remember { mutableStateOf(false) }
                // 解析后的课程暂存 + 配置确认状态
                var parsedCourses by remember { mutableStateOf<List<JwCourse>>(emptyList()) }
                var parsedSchool by remember { mutableStateOf<JwSchoolInfo?>(null) }
                var configStartDate by remember { mutableStateOf("") }
                var configTimeJson by remember { mutableStateOf("") }
                var configRows by remember { mutableStateOf(emptyList<TimeTableUtils.TimeSlotRow>()) }
                // 用户可改的导入课表名; 初值 = "教务导入 - {学校名}"; 留空 = 沿用初值
                var configTableName by remember(parsedSchool) {
                    mutableStateOf(
                        parsedSchool?.let { getString(R.string.jw_import_title, it.name) } ?: ""
                    )
                }

                when {
                    importFinished -> {
                        LaunchedEffect(Unit) { finish() }
                    }

                    stage == JwImportStage.CONFIGURE_CONFIRM && parsedCourses.isNotEmpty() -> {
                        val school = parsedSchool
                        if (school == null) {
                            stage = JwImportStage.WEBVIEW_LOGIN
                            parsedCourses = emptyList()
                        } else {
                        val colors = WedoTheme.colors
                        var confirmError by remember { mutableStateOf<String?>(null) }
                        // 2026-10-01 真机反馈：确认页从 Material AlertDialog 改为**全屏页**——
                        // 日期选择 + 表名 + 12 节课作息编辑在弹窗里挤成一团，且完全没走令牌体系。
                        Column(Modifier.fillMaxSize()) {
                            // 顶栏：返回 + 标题 + 课程数
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = WedoAppleDimensions.pageMargin, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WedoIconButton(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    description = getString(R.string.back),
                                    onClick = {
                                        stage = JwImportStage.WEBVIEW_LOGIN
                                        parsedCourses = emptyList()
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(getString(R.string.jw_config_title), style = WedoAppleType.title3(), color = colors.onSurface)
                                    Text(
                                        text = "${parsedCourses.size} ${getString(R.string.import_courses)}",
                                        style = WedoAppleType.footnote(),
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            // 内容（可滚动）
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = WedoAppleDimensions.pageMargin),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DatePickerField(
                                    value = configStartDate,
                                    onValueChange = { configStartDate = it },
                                    label = getString(R.string.import_week_start),
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = confirmError != null
                                )
                                // 用户可改的导入课表名 — 教务直连此前无任何命名入口,
                                // 硬编码成 "教务导入 - {学校名}" 后用户改名要进课表管理.
                                // 此次把命名入口放到导入前, 落库前最后一次修改机会.
                                TextField(
                                    value = configTableName,
                                    onValueChange = { configTableName = it },
                                    label = { Text(getString(R.string.jw_table_name_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (confirmError != null) {
                                    Text(text = confirmError!!, color = colors.error, style = WedoAppleType.footnote())
                                }
                                TimeSlotEditor(
                                    rows = configRows,
                                    onRowsChange = { newRows ->
                                        configRows = newRows
                                        configTimeJson = TimeTableUtils.buildTimeJsonFromRows(newRows)
                                    }
                                )
                            }
                            // 底部动作：返回（次级）+ 确认导入（主 CTA）
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = WedoAppleDimensions.pageMargin, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    stage = JwImportStage.WEBVIEW_LOGIN
                                    parsedCourses = emptyList()
                                }) {
                                    Text(getString(R.string.back), color = colors.onSurfaceVariant)
                                }
                                WedoPrimaryButton(
                                    text = getString(R.string.jw_config_confirm),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (configStartDate.isBlank() || !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(configStartDate)) {
                                            confirmError = getString(R.string.start_date_format)
                                            return@WedoPrimaryButton
                                        }
                                        val emptyRows = configRows.filter { it.start.isBlank() || it.end.isBlank() }
                                        if (emptyRows.isNotEmpty()) {
                                            confirmError = getString(R.string.slot_time_required, emptyRows.first().node)
                                            return@WedoPrimaryButton
                                        }
                                        val invalidRows = configRows.filter {
                                            !Regex("""^\d{2}:\d{2}$""").matches(it.start) || !Regex("""^\d{2}:\d{2}$""").matches(it.end) || it.start >= it.end
                                        }
                                        if (invalidRows.isNotEmpty()) {
                                            confirmError = getString(R.string.slot_time_invalid, invalidRows.first().node)
                                            return@WedoPrimaryButton
                                        }
                                        confirmError = null
                                        configTimeJson = TimeTableUtils.buildTimeJsonFromRows(configRows)
                                        // 落库
                                        statusMsg = getString(R.string.import_parsing)
                                        scope.launch {
                                            try {
                                                val maxNode = configRows.maxOfOrNull { it.node } ?: 0
                                                val tableId = jwViewModel.importAsNewTable(
                                                    courses = parsedCourses,
                                                    tableName = configTableName.ifBlank {
                                                        getString(R.string.jw_import_title, school.name)
                                                    },
                                                    startDate = configStartDate,
                                                    timeJson = configTimeJson,
                                                    nodesPerDay = maxNode
                                                )
                                                Log.d("JwImport", "importAsNewTable tableId=$tableId courses=${parsedCourses.size}")
                                                statusMsg = getString(R.string.jw_import_success, parsedCourses.size)
                                                importFinished = true
                                            } catch (_: Exception) {
                                                Log.e("JwImport", "import failed code=E_LOCAL_STORAGE")
                                                errorMsg = getString(R.string.jw_parse_failed, "E_LOCAL_STORAGE")
                                                statusMsg = null
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        } // end else (school != null)
                    }

                    stage == JwImportStage.SELECT_SCHOOL -> {
                        SchoolSelectScreen(
                            onSchoolSelected = { school ->
                                if (school.url.isBlank()) {
                                    errorMsg = getString(R.string.jw_no_url)
                                    return@SchoolSelectScreen
                                }
                                selectedSchool = school
                                stage = JwImportStageMachine.onSchoolSelected()
                            },
                            onBack = { finish() }
                        )
                    }

                    stage == JwImportStage.WEBVIEW_LOGIN -> {
                        val school = selectedSchool
                        if (school == null) {
                            // 选校丢失（不应发生）：退回上一有效阶段（选校）。
                            stage = JwImportStageMachine.onBack(stage)
                        } else {
                            JwWebViewLoginScreen(
                                school = school,
                                onPayloadCaptured = { payload, sch ->
                                    val effectiveType = sch.type?.takeIf { it.isNotBlank() }
                                        ?: JwProtocol.detectFromHtml(payload)
                                    Log.d("JwImport", "payload captured len=${payload.length} effectiveType=$effectiveType")
                                    statusMsg = getString(R.string.import_parsing)
                                    scope.launch {
                                        try {
                                            val courses = jwViewModel.parseHtml(payload, effectiveType ?: "")
                                            Log.d("JwImport", "parseHtml returned ${courses.size} courses")
                                            // REQ-P2-06：解析为空/失败一律停在登录页（保留会话上下文），绝不退回选校。
                                            stage = JwImportStageMachine.onPayloadParsed(courses.size)
                                            if (courses.isEmpty()) {
                                                errorMsg = getString(R.string.jw_err_empty_semester)
                                                statusMsg = null
                                                return@launch
                                            }
                                            // 不直接落库，进配置确认页
                                            parsedCourses = courses
                                            parsedSchool = sch
                                            // 课表页通常不带节次时间，留空行让用户在确认页填
                                            val maxNode = courses.maxOf { maxOf(it.startNode, it.endNode) }
                                            configRows = (1..maxNode).map { node ->
                                                TimeTableUtils.TimeSlotRow(
                                                    node = node,
                                                    start = "",
                                                    end = ""
                                                )
                                            }
                                            configStartDate = ""
                                            configTimeJson = ""
                                            statusMsg = null
                                        } catch (_: Exception) {
                                            // Parser 异常可能含响应片段：只回显错误 code，绝不回显异常内容/响应片段。
                                            Log.e("JwImport", "parse failed code=E_PARSE_FORMAT")
                                            stage = JwImportStageMachine.onCaptureFailed(stage)
                                            errorMsg = getString(R.string.jw_parse_failed, "E_PARSE_FORMAT") + getString(R.string.jw_parse_failed_hint)
                                            statusMsg = null
                                        }
                                    }
                                },
                                onCaptureError = { hint ->
                                    Log.w("JwImport", "capture failed hint=$hint")
                                    // REQ-P2-06：抓取失败停在登录页以上下文重试。
                                    stage = JwImportStageMachine.onCaptureFailed(stage)
                                    errorMsg = getString(R.string.jw_parse_empty)
                                    statusMsg = null
                                },
                                onBack = { stage = JwImportStageMachine.onBack(stage) }
                            )
                        }
                    }
                }

                // 失败提示（REQ-P2-03）：抓取/解析失败 → **可操作失败页**（原因 + 下一步动作）；
                // 非抓取失败（选校无 URL / 落库失败）→ 沿用最小提示卡。
                val msg = errorMsg
                if (msg != null && stage == JwImportStage.WEBVIEW_LOGIN) {
                    JwFailureCard(
                        reason = msg,
                        onRetry = {
                            // REQ-P2-07：关闭失败页回到 WebView（仍停在原页面），提示用户停在个人课表页。
                            errorMsg = null
                            stage = JwImportStageMachine.onCaptureFailed(stage)
                        },
                        onUseFileImport = {
                            // REQ-P2-03：跨 Activity 进 TASK-02 的 ImportWizard（复用 MainActivity extra 通道）。
                            startActivity(MainActivity.intentForImportWizard(this@JwImportActivity))
                            finish()
                        },
                        onManualAdd = {
                            // REQ-P2-03：跨 Activity 进 AddCourse（复用 MainActivity extra 通道）。
                            startActivity(MainActivity.intentForManualAdd(this@JwImportActivity))
                            finish()
                        },
                    )
                } else if (msg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = WedoTheme.colors.errorContainer
                            )
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                color = WedoTheme.colors.onErrorContainer
                            )
                        }
                    }
                }
                statusMsg?.let { msg ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Snackbar(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(msg)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 可操作失败页（REQ-P2-03 / REQ-P2-07）。
 *
 * 结构固定为「**原因**（来自 DiagMapper 的诊断文案）+ **下一步动作**」：
 *   - 重试抓取（回到 WebView，提示停在「个人课表」页）
 *   - 改用文件导入（进 `ImportWizard`）
 *   - 手动添加课程（进 `AddCourse`）
 *
 * 红线：这里只显示 [reason]（调用方已保证只含错误 code 与诊断特征），
 * **绝不**回显响应内容 / 学号 / 姓名 / Cookie / token / HTML。
 * 三个按钮均为带文字标签的 Material3 [Button]（TalkBack 可读），触控目标 ≥ [WedoAppleDimensions.minTouchTarget]。
 */
@Composable
private fun JwFailureCard(
    reason: String,
    onRetry: () -> Unit,
    onUseFileImport: () -> Unit,
    onManualAdd: () -> Unit,
) {
    val colors = WedoTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(WedoAppleDimensions.pageMargin),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.jw_failure_title),
                style = WedoAppleType.title3(),
                color = colors.onSurface
            )
            Text(
                text = reason,
                style = WedoAppleType.subheadline(),
                color = colors.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.jw_failure_retry_hint),
                style = WedoAppleType.footnote(),
                color = colors.onSurfaceVariant
            )
            FailureActionButton(
                text = stringResource(R.string.jw_failure_retry),
                primary = true,
                onClick = onRetry
            )
            FailureActionButton(
                text = stringResource(R.string.jw_failure_use_file),
                primary = false,
                onClick = onUseFileImport
            )
            FailureActionButton(
                text = stringResource(R.string.jw_failure_manual_add),
                primary = false,
                onClick = onManualAdd
            )
        }
    }
}

/** 失败页动作按钮 —— 引用主题令牌，触控目标 ≥ `WedoAppleDimensions.minTouchTarget`。 */
@Composable
private fun FailureActionButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val colors = WedoTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WedoAppleDimensions.minTouchTarget),
        shape = WedoTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) colors.primary else colors.surfaceContainerHigh,
            contentColor = if (primary) colors.onPrimary else colors.onSurface
        )
    ) {
        Text(text = text, style = WedoAppleType.headline())
    }
}

/**
 * 诊断结果 → 用户文案映射。
 * Activity 实例走 [mapImpl] 带 Context 拉 strings.xml；
 * 纯 JVM 单测走 [mapForTest]，context=null 时用静态拼接（VPN/hint 类提示不依赖资源）。
 */
@androidx.annotation.VisibleForTesting
internal object DiagMapper {

    @JvmStatic
    fun mapForTest(diag: JwParseDiagnostics.Result, school: com.wedo.schedule.data.jw.JwSchoolInfo): String =
        mapImpl(diag, school, context = null)

    /**
     * 诊断分类 → 用户可读文案。
     *
     * 单校场景下不再需要按学校域名给差异化提示 —— 教务入口只有一个，
     * 学生遇到的绝大多数情况就是「没登录」或「没停在课表页」。
     */
    fun mapImpl(
        diag: JwParseDiagnostics.Result,
        school: com.wedo.schedule.data.jw.JwSchoolInfo,
        context: android.content.Context?
    ): String {
        fun str(resId: Int, vararg args: Any): String =
            context?.getString(resId, *args)
                ?: when (resId) {
                    R.string.jw_diag_session_expired ->
                        "${school.name} 的会话已过期或未登录。请重新登录后停留到「个人课表」页再点抓取"
                    R.string.jw_diag_no_container ->
                        "${school.name} 的页面未找到课表数据。可能原因：①抓取时机过早，数据未加载完；②未停留在「个人课表」页"
                    R.string.jw_diag_header_no_node ->
                        "${school.name} 的课表缺少逐节行头。请确认当前是课表页面"
                    R.string.jw_diag_image_cells ->
                        "${school.name} 的课表内容无法识别。请改用文件导入或手动添加课程"
                    R.string.jw_diag_empty_semester ->
                        "${school.name} 的页面声明本学期暂无课程。请确认已选对学期"
                    R.string.jw_diag_wrong_protocol ->
                        "${school.name} 抓到的页面不含课表数据。请停留在「个人课表」页后重试"
                    else ->
                        "${school.name} 解析结果为空。诊断特征：${diag.matchedFeatures.take(5).joinToString("/")}"
                }
        val catResId = when (diag.category) {
            JwParseDiagnostics.Category.SESSION_EXPIRED -> R.string.jw_diag_session_expired
            JwParseDiagnostics.Category.NO_TABLE_CONTAINER -> R.string.jw_diag_no_container
            JwParseDiagnostics.Category.HEADER_NO_NODE -> R.string.jw_diag_header_no_node
            JwParseDiagnostics.Category.IMAGE_OR_EMPTY_CELLS -> R.string.jw_diag_image_cells
            JwParseDiagnostics.Category.EMPTY_SEMESTER -> R.string.jw_diag_empty_semester
            JwParseDiagnostics.Category.WRONG_PROTOCOL -> R.string.jw_diag_wrong_protocol
            JwParseDiagnostics.Category.UNKNOWN_EMPTY -> R.string.jw_diag_unknown_empty
        }
        val base = str(catResId, school.name)
        // UNKNOWN_EMPTY 回显诊断特征，便于定位
        return if (diag.category == JwParseDiagnostics.Category.UNKNOWN_EMPTY) {
            "$base（${diag.matchedFeatures.take(5).joinToString("/")}）"
        } else {
            base
        }
    }
}
