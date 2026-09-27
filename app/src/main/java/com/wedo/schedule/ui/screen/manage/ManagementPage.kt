package com.wedo.schedule.ui.screen.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.R
import com.wedo.schedule.ui.screen.imports.ImportSheet
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.noRippleClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementPage(
    onJwImportRequested: () -> Unit,
    onCreateNewTableRequested: () -> Unit,
    onManualAdd: () -> Unit,
    onEditCurrentTable: () -> Unit,
    onImported: () -> Unit,
    viewModel: ScheduleViewModel = viewModel(),
    autoShowImportSheet: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val colors = WedoTheme.colors
    val table = state.currentTable

    var showImportSheet by remember { mutableStateOf(autoShowImportSheet) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 底栏占位: 滚动尾部多留标签栏高度, 最后一项才能完全滚出
        // contentPadding(非 Modifier.padding): 内容能滚到屏幕边缘自然滑出, 禁列表整体内缩硬裁
        val navExtra = com.wedo.schedule.ui.component.LocalNavExtraBottomPadding.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = WedoAppleDimensions.pageMargin,
                end = WedoAppleDimensions.pageMargin,
                top = 16.dp,
                bottom = 16.dp + navExtra
            ),
            verticalArrangement = Arrangement.spacedBy(WedoAppleDimensions.sectionGap)
        ) {
            item {
                Text(
                    text = stringResource(R.string.tab_manage),
                    style = WedoAppleType.largeTitle(),
                    color = colors.onBackground
                )
            }

            // 当前课表摘要
            item {
                if (table != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(WedoAppleShapes.card)
                            .background(colors.surfaceContainer)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.manage_current_table),
                            style = WedoAppleType.caption1(),
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = table.name,
                            style = WedoAppleType.title3(),
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.table_info, table.startDate, state.currentWeek, state.courses.size),
                            style = WedoAppleType.footnote(),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // 管理按钮（4 个：导入 / 新建 / 手动添加 / 编辑）
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ManageCard(
                        icon = Icons.Outlined.FileUpload,
                        title = stringResource(R.string.manage_import),
                        subtitle = stringResource(R.string.manage_import_sub),
                        onClick = { showImportSheet = true }
                    )
                    ManageCard(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(R.string.manage_new_table),
                        subtitle = stringResource(R.string.manage_new_table_sub),
                        onClick = onCreateNewTableRequested
                    )
                    ManageCard(
                        icon = Icons.Outlined.Add,
                        title = stringResource(R.string.manage_manual_add),
                        subtitle = stringResource(R.string.manage_manual_add_sub),
                        onClick = onManualAdd
                    )
                    ManageCard(
                        icon = Icons.Outlined.Edit,
                        title = stringResource(R.string.manage_edit_current),
                        subtitle = stringResource(R.string.manage_edit_current_sub),
                        onClick = onEditCurrentTable
                    )
                }
            }
        }
    }

    if (showImportSheet) {
        ImportSheet(
            sheetState = sheetState,
            onDismiss = { showImportSheet = false },
            onJwImportRequested = {
                showImportSheet = false
                onJwImportRequested()
            },
            onImported = onImported,
            viewModel = viewModel
        )
    }
}

/**
 * 管理入口卡片。
 *
 * 与之前的 Material 写法两处差别：
 *  1. 图标去掉 44dp `primaryContainer` 圆角块，改为裸的 accent 色图标 ——
 *     iOS 没有「每个入口都套一个色块」的做法（色块只用在真正的 App 图标上）
 *  2. 右侧补 chevron，明确「点了会进入下一页」
 */
@Composable
private fun ManageCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = WedoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(WedoAppleShapes.card)
            .background(colors.surfaceContainer)
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = WedoApple.accentIcon,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = WedoAppleType.headline(), color = colors.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive),
            modifier = Modifier.size(16.dp)
        )
    }
}
