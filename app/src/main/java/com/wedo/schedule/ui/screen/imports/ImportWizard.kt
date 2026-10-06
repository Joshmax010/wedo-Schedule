package com.wedo.schedule.ui.screen.imports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.ui.component.DatePickerField
import com.wedo.schedule.ui.component.TimeSlotEditor
import com.wedo.schedule.ui.component.WedoBackground
import com.wedo.schedule.ui.component.WedoIconButton
import com.wedo.schedule.ui.component.WedoPrimaryButton
import com.wedo.schedule.ui.component.WedoSecondaryButton
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.noRippleClickable
import com.wedo.schedule.util.TimeTableUtils
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 全屏导入向导的四个步骤（REQ-P2-01）。
 *
 * 步骤之间用**水平滑动转场**（前进从右滑入、后退从左滑入），空间隐喻与 iOS push/pop 一致 ——
 * 刻意不用淡入淡出（`DESIGN_SPEC_V2 §6`「页面转场 300ms」）。
 */
enum class ImportStep { SOURCE, SOURCE_DETAIL, PREVIEW, DONE }

/** 导入来源（REQ-P2-02：教务首选，其后文件/文本/手动）。 */
enum class ImportSource { JW, FILE, TEXT, MANUAL }

/**
 * 全屏分步导入向导（REQ-P2-01 / REQ-P2-02）。
 *
 * 取代旧底部 `ImportSheet` 的「一屏平铺」：改为 **Step1 选择来源 → Step2 来源专属 →
 * Step3 预览 + 冲突 + 确认 → Step4 完成跳课表**。解析/预览/落库逻辑全部复用 [ImportFlow]，
 * 本组件只负责分步 UI 与转场。
 *
 * @param onDismiss 关闭向导（返回上一级）
 * @param onFinish  Step4「查看课表」→ 由宿主跳课表 Tab 并定位新学期
 * @param onManualAdd 选择「手动添加课程」→ 由宿主进入 AddCourse
 * @param onJwImport 选择教务直连「打开教务登录」→ 由宿主启动 JwImportActivity
 * @param viewModel 与课表页/今天页**共用**的同一实例（保证 state 一致）
 */
@Composable
fun ImportWizard(
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
    onManualAdd: () -> Unit,
    onJwImport: () -> Unit,
    viewModel: ScheduleViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fieldColors = WedoTheme.fieldColors()
    val colors = WedoTheme.colors
    val snackbar = remember { SnackbarHostState() }

    var step by remember { mutableStateOf(ImportStep.SOURCE) }
    var source by remember { mutableStateOf<ImportSource?>(null) }
    var inputText by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Step3 的确认参数（起始日 / 表名 / 作息）—— 与旧流程一致, 默认值由解析结果 + 现表推导。
    var confirmedStartDate by remember { mutableStateOf("") }
    var confirmedTableName by remember { mutableStateOf("") }
    var confirmedTimeJson by remember { mutableStateOf("") }

    fun seedConfirm(p: ImportPreview) {
        val existing = state.currentTable
        confirmedStartDate = p.parseResult.startDate.ifBlank {
            existing?.startDate ?: LocalDate.now().toString()
        }
        confirmedTableName = p.parseResult.tableName.ifBlank {
            existing?.name ?: context.getString(R.string.default_table_name)
        }
        confirmedTimeJson = TimeTableUtils.mergeMostComplete(
            currentJson = existing?.timeJson ?: "",
            incomingJson = p.parseResult.timeJson,
            requiredNodeCount = p.parseResult.nodesPerDay
        )
    }

    fun parseText(text: String) {
        scope.launch {
            isLoading = true
            try {
                val p = buildImportPreview(text, state, context) { msg -> errorMsg = msg }
                if (p != null) {
                    preview = p
                    seedConfirm(p)
                    step = ImportStep.PREVIEW
                }
            } finally {
                isLoading = false
            }
        }
    }

    // 外部 app（文件管理器 / 其他课表 app）通过 Intent 传来课表文本 → 自动进入预览。
    LaunchedEffect(Unit) {
        val text = com.wedo.schedule.MainActivity.pendingImportText
        if (!text.isNullOrBlank()) {
            com.wedo.schedule.MainActivity.pendingImportText = null
            inputText = text
            source = ImportSource.TEXT
        }
    }
    // 文本就绪后（同一次挂载内）自动解析。
    LaunchedEffect(inputText, source) {
        if (source == ImportSource.TEXT && inputText.isNotBlank() && preview == null) {
            parseText(inputText)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw Exception(context.getString(R.string.cannot_read_file))
                val p = buildImportPreview(text, state, context) { msg -> errorMsg = msg }
                if (p != null) {
                    preview = p
                    seedConfirm(p)
                    step = ImportStep.PREVIEW
                }
            } catch (e: Exception) {
                errorMsg = context.getString(R.string.read_failed, e.message)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbar.showSnackbar(it)
            errorMsg = null
        }
    }

    fun apply(mode: ImportApplyMode) {
        val p = preview ?: return
        scope.launch {
            isLoading = true
            try {
                applyImportPreview(
                    preview = p,
                    mode = mode,
                    confirmedStartDateRaw = confirmedStartDate,
                    confirmedTableName = confirmedTableName,
                    confirmedTimeJson = confirmedTimeJson,
                    context = context,
                    onImported = { step = ImportStep.DONE }
                ) { msg -> errorMsg = msg }
            } finally {
                isLoading = false
            }
        }
    }

    WedoBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            WizardTopBar(
                title = when (step) {
                    ImportStep.SOURCE -> stringResource(R.string.import_title)
                    ImportStep.SOURCE_DETAIL -> stringResource(R.string.import_wiz_title_detail)
                    ImportStep.PREVIEW -> stringResource(R.string.import_wiz_title_preview)
                    ImportStep.DONE -> stringResource(R.string.import_wiz_title_done)
                },
                onClose = onDismiss
            )
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    // REQ-P2-01: 步骤间**水平滑动**（前进右滑入 / 后退左滑入），空间隐喻同 iOS push/pop。
                    // 刻意**不加淡入淡出**（DESIGN_SPEC_V2 §6「页面转场 300ms」）。
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    slideInHorizontally(animationSpec = tween(300)) { w -> dir * w } togetherWith
                        slideOutHorizontally(animationSpec = tween(300)) { w -> -dir * w }
                },
                modifier = Modifier.weight(1f),
                label = "import-step"
            ) { s ->
                when (s) {
                    ImportStep.SOURCE -> SourceStep(
                        onPick = { picked ->
                            when (picked) {
                                ImportSource.MANUAL -> onManualAdd()
                                // 2026-10-01 真机反馈：教务直连**直进学校候选列表**，
                                // 跳过 Step2 的「打开教务登录」按钮页（那一步是纯中转，无信息量）。
                                ImportSource.JW -> onJwImport()
                                else -> { source = picked; step = ImportStep.SOURCE_DETAIL }
                            }
                        }
                    )
                    ImportStep.SOURCE_DETAIL -> DetailStep(
                        source = source,
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        isLoading = isLoading,
                        fieldColors = fieldColors,
                        onBack = { step = ImportStep.SOURCE },
                        onPickFile = { filePicker.launch(arrayOf("application/json", "text/calendar", "text/plain", "text/csv", "text/html", "*/*")) },
                        onParseText = { parseText(inputText) }
                    )
                    ImportStep.PREVIEW -> PreviewStep(
                        preview = preview,
                        confirmedStartDate = confirmedStartDate,
                        confirmedTableName = confirmedTableName,
                        confirmedTimeJson = confirmedTimeJson,
                        onStartDateChange = { confirmedStartDate = it },
                        onTableNameChange = { confirmedTableName = it },
                        onTimeJsonChange = { confirmedTimeJson = it },
                        isLoading = isLoading,
                        onBack = { step = ImportStep.SOURCE_DETAIL },
                        onApply = { apply(it) }
                    )
                    ImportStep.DONE -> DoneStep(onFinish = onFinish, onAgain = {
                        preview = null; inputText = ""; source = null; step = ImportStep.SOURCE
                    })
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )
    }
}

@Composable
private fun WizardTopBar(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = WedoAppleDimensions.pageMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 2026-10-06 真机反馈：全 app 返回图标统一为**蓝色 ← 箭头**（原为 × 关闭）。
        // WedoIconButton 的默认 tint 即 WedoApple.accentIcon，故只需换图标形状。
        WedoIconButton(Icons.AutoMirrored.Outlined.ArrowBack, description = stringResource(R.string.back), onClick = onClose)
        Spacer(Modifier.size(8.dp))
        Text(title, style = WedoAppleType.title2(), color = WedoTheme.colors.onSurface)
    }
}

@Composable
private fun SourceStep(onPick: (ImportSource) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = WedoAppleDimensions.pageMargin)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.import_wiz_pick_way), style = WedoAppleType.subheadline(),
            color = WedoTheme.colors.onSurfaceVariant
        )
        // 2026-10-01 真机反馈：教务直连与另外三项**完全同款**——无角标、无高亮、
        // 不提学校名（定位是「优先适配」而非「只支持」）；副标题为中性功能描述。
        SourceRow(
            icon = Icons.Outlined.QrCode2,
            title = stringResource(R.string.import_wiz_source_jw),
            subtitle = stringResource(R.string.import_wiz_jw_subtitle),
            onClick = { onPick(ImportSource.JW) }
        )
        SourceRow(
            icon = Icons.Outlined.FileUpload,
            title = stringResource(R.string.import_wiz_source_file),
            subtitle = stringResource(R.string.import_wiz_file_sub),
            onClick = { onPick(ImportSource.FILE) }
        )
        SourceRow(
            icon = Icons.Outlined.Description,
            title = stringResource(R.string.import_wiz_source_text),
            subtitle = stringResource(R.string.import_wiz_text_sub),
            onClick = { onPick(ImportSource.TEXT) }
        )
        SourceRow(
            icon = Icons.Outlined.Edit,
            title = stringResource(R.string.import_wiz_source_manual),
            subtitle = null,
            onClick = { onPick(ImportSource.MANUAL) }
        )
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    val colors = WedoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(WedoAppleShapes.card)
            .background(colors.surfaceContainerLow)
            .noRippleClickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = WedoApple.accentIcon, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = WedoAppleType.headline(), color = colors.onSurface)
            subtitle?.let {
                Text(it, style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailStep(
    source: ImportSource?,
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onParseText: () -> Unit
) {
    val colors = WedoTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = WedoAppleDimensions.pageMargin)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            // 教务直连在 Step1 已直进学校列表，DetailStep 只剩 文件 / 粘贴 两种来源。
            when (source) {
                ImportSource.FILE -> stringResource(R.string.import_wiz_detail_file)
                ImportSource.TEXT -> stringResource(R.string.import_wiz_detail_text)
                else -> ""
            },
            style = WedoAppleType.subheadline(),
            color = colors.onSurfaceVariant
        )
        when (source) {
            ImportSource.FILE -> WedoPrimaryButton(text = stringResource(R.string.import_wiz_pick_file), onClick = onPickFile)
            ImportSource.TEXT -> {
                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    placeholder = { Text(stringResource(R.string.import_paste_hint), color = colors.onSurfaceVariant) },
                    enabled = !isLoading,
                    shape = WedoTheme.fieldShape,
                    colors = fieldColors
                )
                WedoPrimaryButton(
                    text = if (isLoading) stringResource(R.string.import_parsing) else stringResource(R.string.import_wiz_parse_preview),
                    onClick = onParseText,
                    enabled = !isLoading && inputText.isNotBlank()
                )
            }
            else -> Unit
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back), color = colors.onSurfaceVariant) }
    }
}

@Composable
private fun PreviewStep(
    preview: ImportPreview?,
    confirmedStartDate: String,
    confirmedTableName: String,
    confirmedTimeJson: String,
    onStartDateChange: (String) -> Unit,
    onTableNameChange: (String) -> Unit,
    onTimeJsonChange: (String) -> Unit,
    isLoading: Boolean,
    onBack: () -> Unit,
    onApply: (ImportApplyMode) -> Unit
) {
    val colors = WedoTheme.colors
    val p = preview ?: return
    var rows by remember(p) { mutableStateOf(TimeTableUtils.parseTimeSlotRows(confirmedTimeJson)) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = WedoAppleDimensions.pageMargin)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 概览
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(stringResource(R.string.import_wiz_metric_course), p.incomingCount.toString(), Modifier.weight(1f))
            if (p.targetTableId != 0L) {
                MetricCard(stringResource(R.string.import_wiz_metric_conflict), p.conflictCount.toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.import_appendable), p.cleanCount.toString(), Modifier.weight(1f))
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(WedoAppleShapes.card)
                .background(colors.surfaceContainerLow).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow(stringResource(R.string.import_table_name), confirmedTableName.ifBlank { p.parseResult.tableName })
            InfoRow(stringResource(R.string.import_start_date), confirmedStartDate)
        }
        if (p.conflicts.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(WedoAppleShapes.card)
                    .background(colors.surfaceContainerLow).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = colors.error)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.import_conflicts), style = WedoAppleType.headline(), color = colors.onSurface)
                }
                p.conflicts.take(3).forEach { c ->
                    Text(
                        "• ${c.incoming.courseName} ↔ ${c.existing.courseName}",
                        style = WedoAppleType.footnote(), color = colors.onSurfaceVariant
                    )
                }
            }
        }
        // 确认参数
        DatePickerField(value = confirmedStartDate, onValueChange = onStartDateChange, label = stringResource(R.string.import_week_start), modifier = Modifier.fillMaxWidth())
        TextField(
            value = confirmedTableName,
            onValueChange = onTableNameChange,
            label = { Text(stringResource(R.string.import_table_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = WedoTheme.fieldShape,
            colors = WedoTheme.fieldColors()
        )
        TimeSlotEditor(rows = rows, onRowsChange = { rows = it; onTimeJsonChange(TimeTableUtils.buildTimeJsonFromRows(it)) })

        // 应用方式
        if (p.targetTableId == 0L) {
            WedoPrimaryButton(text = stringResource(R.string.import_as_new), onClick = { onApply(ImportApplyMode.ImportAsNew) }, enabled = !isLoading)
        } else {
            WedoPrimaryButton(text = stringResource(R.string.import_append_only), onClick = { onApply(ImportApplyMode.AppendNonConflict) }, enabled = !isLoading)
            WedoPrimaryButton(text = stringResource(R.string.import_as_new), onClick = { onApply(ImportApplyMode.ImportAsNew) }, enabled = !isLoading)
            WedoPrimaryButton(text = stringResource(R.string.import_append_as_new), onClick = { onApply(ImportApplyMode.AppendAsNew) }, enabled = !isLoading)
            WedoPrimaryButton(text = stringResource(R.string.import_append_conflict), onClick = { onApply(ImportApplyMode.AppendAll) }, enabled = !isLoading)
            WedoPrimaryButton(text = stringResource(R.string.import_overwrite), onClick = { onApply(ImportApplyMode.ReplaceCurrent) }, enabled = !isLoading, destructive = true)
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back), color = colors.onSurfaceVariant) }
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit, onAgain: () -> Unit) {
    val colors = WedoTheme.colors
    Column(
        Modifier.fillMaxSize().padding(WedoAppleDimensions.pageMargin),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.import_success), style = WedoAppleType.title3(), color = colors.onSurface)
        Spacer(Modifier.height(20.dp))
        WedoPrimaryButton(text = stringResource(R.string.import_wiz_view_schedule), onClick = onFinish)
        Spacer(Modifier.height(8.dp))
        WedoSecondaryButton(text = stringResource(R.string.import_wiz_again), onClick = onAgain)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(WedoAppleShapes.card).background(WedoTheme.colors.surfaceContainerLow).padding(vertical = 12.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = WedoAppleType.caption2(), color = WedoTheme.colors.onSurfaceVariant)
        Text(value, style = WedoAppleType.title3().copy(fontWeight = FontWeight.Bold), color = WedoTheme.colors.onSurface)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = WedoAppleType.caption2(), color = WedoTheme.colors.onSurfaceVariant)
        Text(value, style = WedoAppleType.subheadline(), color = WedoTheme.colors.onSurface)
    }
}
