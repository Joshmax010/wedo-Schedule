package com.wedo.schedule.ui.screen.mine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.R
import com.wedo.schedule.data.entity.SmartPeriodConfig
import com.wedo.schedule.data.entity.TimeTableEntity
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils
import com.wedo.schedule.ui.component.DatePickerField
import com.wedo.schedule.ui.component.TimeSlotEditor
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.noRippleClickable
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Time slot editing uses mutableStateListOf for reactive TextField binding

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditTableScreen(
    tableId: Long? = null,
    pendingNewTableId: Long? = null,
    onBack: () -> Unit,
    onDiscardPending: () -> Unit = onBack,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = WedoTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // tableId == null means edit current table
    val table = if (tableId != null) state.tables.find { it.id == tableId } else state.currentTable

    if (table == null) {
        Scaffold(containerColor = colors.background) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.edit_table_not_found), color = colors.onBackground)
            }
        }
        return
    }

    var name by remember(table.id) { mutableStateOf(table.name) }
    var startDate by remember(table.id) { mutableStateOf(table.startDate) }
    var maxWeekText by remember(table.id) { mutableStateOf(table.maxWeek.toString()) }
    var timeSlotsExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val timeJson = table.timeJson
    val slotRows = remember(table.id) {
        mutableStateListOf<TimeTableUtils.TimeSlotRow>().apply {
            addAll(TimeTableUtils.parseTimeSlotRows(timeJson))
        }
    }
    // v1.0.16 自动模式配置（编辑当前课表时使用）
    val smartConfig = remember(table.id) {
        mutableStateOf(
            // 如果表里已存 smartConfigJson，反序列化恢复；否则从现有 slotRows 推断初始值
            if (table.smartConfigJson.isNotBlank()) {
                try {
                    Json.decodeFromString<SmartPeriodConfig>(table.smartConfigJson)
                } catch (e: Exception) {
                    SmartPeriodConfig(
                        totalPeriods = slotRows.size.coerceAtLeast(1),
                        startTime = slotRows.firstOrNull()?.start?.takeIf { it.isNotBlank() } ?: "08:00"
                    )
                }
            } else {
                SmartPeriodConfig(
                    totalPeriods = slotRows.size.coerceAtLeast(1),
                    startTime = slotRows.firstOrNull()?.start?.takeIf { it.isNotBlank() } ?: "08:00"
                )
            }
        )
    }

    val fieldColors = WedoTheme.fieldColors()

    val handleBack = {
        if (pendingNewTableId != null) onDiscardPending() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_table_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 基础信息
            item {
                CardSection(stringResource(R.string.edit_table_basic_info), "") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.edit_table_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = WedoTheme.fieldShape,
                            colors = fieldColors
                        )
                        // 学期开始日期: 手输 + 日历图标弹原生 DatePicker(与导入确认弹窗同款组件)。
                        //   之前是裸 TextField 只能输数字, 用户没法直观改日期。
                        DatePickerField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = stringResource(R.string.edit_table_start_date),
                            modifier = Modifier.fillMaxWidth(),
                            isError = error != null
                        )
                        TextField(
                            value = maxWeekText,
                            onValueChange = { maxWeekText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(R.string.edit_table_max_week)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = WedoTheme.fieldShape,
                            colors = fieldColors
                        )
                    }
                }
            }

            // 节次时间表（可折叠）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WedoTheme.shapes.extraLarge)
                        .background(colors.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable { timeSlotsExpanded = !timeSlotsExpanded }
                            .padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.edit_table_time_slots),
                                style = WedoAppleType.headline().copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                            text = stringResource(R.string.n_periods, slotRows.size) + " · " + if (timeSlotsExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                style = WedoAppleType.footnote(),
                                color = colors.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.rotate(if (timeSlotsExpanded) 180f else 0f)
                        )
                    }

                    AnimatedVisibility(
                        visible = timeSlotsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            TimeSlotEditor(
                                rows = slotRows.toList(),
                                onRowsChange = { newRows ->
                                    slotRows.clear()
                                    slotRows.addAll(newRows)
                                },
                                smartConfig = smartConfig.value,
                                onSmartConfigChange = { smartConfig.value = it }
                            )
                        }
                    }
                }
            }

            error?.let { msg ->
                item {
                    Text(text = msg, color = colors.error, style = WedoAppleType.subheadline())
                }
            }

            // 保存
            item {
                Button(
                    onClick = {
                        val maxWeek = maxWeekText.toIntOrNull() ?: 20
                        val valid = startDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
                            slotRows.all { it.start.matches(Regex("\\d{2}:\\d{2}")) && it.end.matches(Regex("\\d{2}:\\d{2}")) } &&
                            slotRows.all { it.start < it.end }
                        if (!valid) {
                            error = context.getString(R.string.edit_table_validation_error)
                            return@Button
                        }
                        error = null
                        val smartConfigJson = try {
                            Json.encodeToString(smartConfig.value)
                        } catch (e: Exception) {
                            ""
                        }
                        val updated = table.copy(
                            name = name.ifBlank { table.name },
                            startDate = DateUtils.normalizeStartDate(startDate),
                            maxWeek = maxWeek,
                            timeJson = TimeTableUtils.buildTimeJsonFromRows(slotRows.toList()),
                            smartConfigJson = smartConfigJson
                        )
                        scope.launch {
                            viewModel.updateTable(updated)
                            onSaved()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(WedoTheme.Buttons.ctaHeight),
                    shape = WedoTheme.Buttons.shape
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_table_save))
                }
            }

            // 删除（新建未保存的表不显示此按钮——退出即丢弃; 用户 2026-09-03 指令: 允许删完,
            // 最后一张表也可删 — ScheduleScreen 的真空态(EmptyState)兜底)
            if (pendingNewTableId == null) {
                item {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(WedoTheme.Buttons.regularHeight),
                        shape = WedoTheme.Buttons.shape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.errorContainer)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_table_delete), color = colors.onErrorContainer)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.edit_table_delete_confirm), color = colors.onSurface) },
            // 防呆: 删表=连带删全部课程, 明示数量让用户知道要失去多少数据
            text = {
                Text(
                    stringResource(
                        if (state.courses.isNotEmpty()) R.string.edit_table_delete_msg_count
                        else R.string.edit_table_delete_msg,
                        table.name,
                        state.courses.size
                    ),
                    color = colors.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        viewModel.deleteTable(table.id)
                        onDeleted()
                    }
                }) { Text(stringResource(R.string.delete), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun CardSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    val colors = WedoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, WedoTheme.shapes.extraLarge)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = WedoAppleType.headline().copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            if (subtitle.isNotBlank()) Text(subtitle, style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
        }
        content()
    }
}


