package com.wedo.schedule.ui.screens.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.data.entity.TimeTableEntity
import com.wedo.schedule.ui.components.WedoIconColors
import com.wedo.schedule.ui.components.WedoIconTile
import com.wedo.schedule.ui.components.WedoInsetCard
import com.wedo.schedule.ui.components.WedoSectionHeader
import com.wedo.schedule.ui.screens.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.noRippleClickable

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AllTablesScreen(
    /** 滚动状态由 AppRoot 持有，返回本页时保持原位 */
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onCreateNewTable: () -> Unit,
    onOpenEditTable: (Long) -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = WedoTheme.colors
    val current = state.tables.find { it.id == state.selectedTableId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.all_tables)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = WedoApple.accentIcon
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (current != null) {
                item { WedoSectionHeader(stringResource(R.string.manage_current_table)) }
                item {
                    WedoInsetCard {
                        TableProfileRow(
                            table = current,
                            currentWeek = state.currentWeek,
                            isCurrent = true,
                            onDuplicate = { viewModel.duplicateTable(current.id) },
                            onEdit = { onOpenEditTable(current.id) }
                        )
                    }
                }
            }

            item { WedoSectionHeader(stringResource(R.string.all_tables)) }
            item {
                WedoInsetCard {
                    state.tables.forEachIndexed { index, table ->
                        TableRow(
                            table = table,
                            isCurrent = table.id == state.selectedTableId,
                            currentWeek = state.currentWeek,
                            onSelect = {
                                if (table.id != state.selectedTableId) {
                                    viewModel.selectTable(table.id)
                                    onBack()
                                }
                            },
                            onDuplicate = { viewModel.duplicateTable(table.id) },
                            onEdit = { onOpenEditTable(table.id) },
                            showDivider = index != state.tables.lastIndex
                        )
                    }
                }
            }

            item {
                WedoInsetCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .noRippleClickable(onCreateNewTable)
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WedoIconTile(Icons.Outlined.Add, WedoIconColors.green)
                        Text(
                            stringResource(R.string.all_tables_new),
                            Modifier.weight(1f).padding(start = 11.dp),
                            style = WedoAppleType.body(),
                            color = colors.onSurface
                        )
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = colors.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TableProfileRow(
    table: TimeTableEntity,
    currentWeek: Int,
    isCurrent: Boolean,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = WedoTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableTile(isCurrent)
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(table.name, style = WedoAppleType.body(), color = colors.onSurface)
            Text(
                stringResource(R.string.current_table_week, currentWeek),
                style = WedoAppleType.footnote(),
                color = colors.onSurfaceVariant
            )
        }
        IconButton(onClick = onDuplicate) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.all_tables_duplicate),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TableRow(
    table: TimeTableEntity,
    isCurrent: Boolean,
    currentWeek: Int,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    showDivider: Boolean
) {
    val colors = WedoTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .noRippleClickable(onSelect)
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableTile(isCurrent)
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(table.name, style = WedoAppleType.body(), color = colors.onSurface)
                Text(
                    if (isCurrent) stringResource(R.string.current_table_week, currentWeek)
                    else stringResource(R.string.table_start_date, table.startDate),
                    style = WedoAppleType.footnote(),
                    color = colors.onSurfaceVariant
                )
                if (table.createdAt > 0) {
                    Text(
                        stringResource(
                            R.string.table_created_at,
                            java.time.Instant.ofEpochMilli(table.createdAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        ),
                        style = WedoAppleType.footnote(),
                        color = colors.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDuplicate) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.all_tables_duplicate),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = 50.dp),
                thickness = 0.5.dp,
                color = colors.outlineVariant
            )
        }
    }
}

@Composable
private fun TableTile(isCurrent: Boolean) {
    Box {
        WedoIconTile(
            icon = Icons.Outlined.CalendarMonth,
            color = WedoIconColors.blue,
            size = 40.dp,
            corner = 10.dp,
            glyphSize = 21.dp
        )
        if (isCurrent) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(WedoIconColors.blue)
                    .border(2.dp, WedoTheme.colors.surfaceContainerLow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = Color.White
                )
            }
        }
    }
}
