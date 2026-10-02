package com.wedo.schedule.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wedo.schedule.R
import com.wedo.schedule.ui.component.WedoIconButton
import com.wedo.schedule.ui.component.WedoPrimaryButton
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme

/**
 * 全屏周次选择器（REQ-P3-05）—— 替换旧的底部弹层周次选择。
 *
 * 与旧实现的差别（为什么必须换掉）：
 *  1. **全屏呈现**。旧的是一个 ModalBottomSheet + 自适应网格，用户翻到第 12 周时
 *     既看不到「我离学期结束还有多远」，也说不清「当前在第几周」。全屏给了表达
 *     这两个问题的空间。
 *  2. **学期进度条**。进度 = `currentWeek / maxWeek` —— 一眼看清学期走到了哪。
 *  3. **回到本周**。翻远了以后不用手动数格子，一个按钮跳回客观事实周。
 *
 * 语义边界（铁律 7.3）：本组件**只**通过 [onSelect] 把用户选的周回传；
 * 调用方以 `viewModel.changeWeek(week)` 写入 `selectedWeek`（浏览位置），
 * **绝不**触碰 `currentWeek`（客观事实）。
 * 「回到本周」的目标周 = [currentWeek]，同样走 [onSelect] 通道。
 *
 * 无障碍：网格为单选组（`selectableGroup`），每个格子是 `Role.RadioButton` +
 * 语义标签「第 N 周（，本周）」，触控 ≥ [WedoAppleDimensions.minTouchTarget]。
 */
@Composable
fun WeekPicker(
    selectedWeek: Int,
    currentWeek: Int,
    maxWeek: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WedoTheme.colors
    val safeMax = maxWeek.coerceAtLeast(1)
    val progressWeek = currentWeek.coerceIn(0, safeMax)
    val progressFraction = progressWeek.toFloat() / safeMax.toFloat()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // ── 标题栏 ──
                Row(
                    Modifier.fillMaxWidth()
                        .height(WedoAppleDimensions.minTouchTarget)
                        .padding(start = WedoAppleDimensions.pageMargin),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.week_picker_title),
                        style = WedoAppleType.title2(),
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    WedoIconButton(Icons.Outlined.Close, stringResource(R.string.action_close), onClick = onDismiss)
                }

                // ── 学期进度 ──
                Column(Modifier.fillMaxWidth().padding(horizontal = WedoAppleDimensions.pageMargin)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.week_picker_progress), style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.week_picker_progress_detail, progressWeek, safeMax),
                            style = WedoAppleType.footnote(),
                            color = colors.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SemesterProgressBar(fraction = progressFraction)
                }

                Spacer(Modifier.height(WedoAppleDimensions.sectionGap))

                // ── 周次网格（单选组） ──
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(60.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().selectableGroup(),
                    contentPadding = PaddingValues(
                        horizontal = WedoAppleDimensions.pageMargin,
                        vertical = 4.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(safeMax) { index ->
                        val week = index + 1
                        WeekChip(
                            week = week,
                            isSelected = week == selectedWeek,
                            isCurrent = week == currentWeek,
                            onClick = { onSelect(week) }
                        )
                    }
                }

                // ── 回到本周 ──
                Box(Modifier.fillMaxWidth().padding(WedoAppleDimensions.pageMargin)) {
                    WedoPrimaryButton(
                        text = stringResource(R.string.week_picker_back_to_current),
                        onClick = { onSelect(currentWeek.coerceIn(1, safeMax)) }
                    )
                }
            }
        }
    }
}

/**
 * 周次格子。
 *
 * 选中态用「强调色淡底 + 强调色文字」表达（不是玻璃高光）；「本周」另加一行小字，
 * 使「当前周」与「用户刚选中的周」两个概念可同时被看见。
 */
@Composable
private fun WeekChip(
    week: Int,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val colors = WedoTheme.colors
    val bg = if (isSelected) WedoApple.accent.copy(alpha = WedoTheme.Alpha.tinted) else colors.surfaceContainer
    val fg = if (isSelected) WedoApple.accentText else colors.onSurface
    val label = if (isCurrent) {
        stringResource(R.string.week_picker_grid_current, week)
    } else {
        stringResource(R.string.week_picker_grid_week, week)
    }
    Column(
        modifier = Modifier
            .heightIn(min = WedoAppleDimensions.minTouchTarget)
            .fillMaxWidth()
            .clip(WedoAppleShapes.continuous(WedoAppleDimensions.cardCorner))
            .background(bg)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$week",
            style = WedoAppleType.headline().copy(fontWeight = FontWeight.SemiBold),
            color = fg
        )
        Text(
            if (isCurrent) stringResource(R.string.week_picker_chip_current) else stringResource(R.string.week_picker_chip_week),
            style = WedoAppleType.caption2(),
            color = if (isCurrent && !isSelected) WedoApple.accentText else colors.onSurfaceVariant
        )
    }
}

/**
 * 学期进度条。
 *
 * 进度 = currentWeek / maxWeek（REQ-P3-05）。纯自绘（两个 Box），
 * 不依赖 Material `LinearProgressIndicator` —— 后者在各 M3 版本的 progress API
 * 有过签名漂移，自绘永不受影响，且能直接复用令牌。
 */
@Composable
private fun SemesterProgressBar(fraction: Float) {
    val colors = WedoTheme.colors
    Box(
        Modifier.fillMaxWidth()
            .height(8.dp)
            .clip(WedoAppleShapes.capsule)
            .background(colors.surfaceContainerHigh)
    ) {
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(WedoAppleShapes.capsule)
                .background(WedoApple.accent)
        )
    }
}
