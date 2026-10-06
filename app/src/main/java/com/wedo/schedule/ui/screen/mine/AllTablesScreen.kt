package com.wedo.schedule.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.R
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.noRippleClickable

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AllTablesScreen(
    /** 2026-10-06：滚动状态由 AppRoot 持有，返回本页时保持原位 */
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onCreateNewTable: () -> Unit,
    onOpenEditTable: (Long) -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()
    val colors = WedoTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.all_tables)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = com.wedo.schedule.ui.theme.WedoApple.accentIcon)
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
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            itemsIndexed(state.tables) { _, table ->
                val isCurrent = table.id == state.selectedTableId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WedoTheme.shapes.large)
                        .background(if (isCurrent) colors.primaryContainer else colors.surfaceContainer)
                        .noRippleClickable {
                            if (!isCurrent) {
                                viewModel.selectTable(table.id)
                                onBack()
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrent) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(WedoTheme.shapes.medium)
                                .background(colors.outlineVariant)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // M3 对比度修正：当前行背景是 primaryContainer，文字/副标题应配对
                        // onPrimaryContainer 系（之前用 onSurface/onSurfaceVariant，自定义高对比主题下对比度不足）。
                        // 非当前行背景 surfaceContainer 维持 onSurface/onSurfaceVariant。
                        val (titleColor, subtitleColor) = if (isCurrent) {
                            colors.onPrimaryContainer to colors.onPrimaryContainer.copy(alpha = WedoTheme.Alpha.highContent)
                        } else {
                            colors.onSurface to colors.onSurfaceVariant
                        }
                        Text(
                            text = table.name,
                            style = WedoAppleType.headline().copy(fontWeight = FontWeight.SemiBold),
                            color = titleColor
                        )
                        Text(
                            text = if (isCurrent) stringResource(R.string.current_table_week, state.currentWeek) else stringResource(R.string.table_start_date, table.startDate),
                            style = WedoAppleType.footnote(),
                            color = subtitleColor
                        )
                        // v7.10.15 每表显示导入时间(年月日 时分秒) — 方便用户分辨多张课表
                        if (table.createdAt > 0) {
                            Text(
                                text = stringResource(
                                    R.string.table_created_at,
                                    java.time.Instant.ofEpochMilli(table.createdAt)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                ),
                                style = WedoAppleType.footnote(),
                                color = subtitleColor
                            )
                        }
                    }
                    // v7.10.15 duplicate 图标 — 创建课表副本, 置于设置图标左边
                    IconButton(onClick = { viewModel.duplicateTable(table.id) }) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.all_tables_duplicate),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { onOpenEditTable(table.id) }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = onCreateNewTable,
                    modifier = Modifier.fillMaxWidth().height(WedoTheme.Buttons.regularHeight),
                    shape = WedoTheme.Buttons.shape
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.all_tables_new))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
