package com.wedo.schedule.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wedo.schedule.R
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoSystemColor
import com.wedo.schedule.ui.theme.noRippleClickable
import com.wedo.schedule.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 外观页 —— Apple 化重写。
 *
 * **删掉的**：5 套 M3 预设主题卡片、「跟随系统」Material You 动态取色卡片。
 * 理由：Apple 的 app 没有「主题」概念 —— 只有**强调色**和**深浅模式**。
 * 一个 app 的骨架（背景/卡片/分隔线）永远中性，用户能改的只有强调色的那一抹。
 *
 * **保留的**：深浅模式三态切换（跟随系统 / 浅色 / 深色），以及主题变更后
 * 刷新小组件的管线。
 *
 * 强调色存的是 [WedoSystemColor] 的 enum name（如 "Blue"），复用原来存
 * preset key 的那个 SharedPreferences 键，旧值会被 byName 回落为默认系统蓝。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    /** 2026-10-06：滚动状态由 AppRoot 持有，返回本页时保持原位 */
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = WedoTheme.colors
    val currentAccentName by AppPrefs.themeKeyFlow(context)
        .collectAsState(initial = AppPrefs.getThemeKey(context))
    val selectedAccent = WedoSystemColor.byName(currentAccentName)
    val selectedMode = themeMode

    // 选主题/模式后立即刷小组件: 之前只写 SP 不刷 widget → 小组件不跟主题变
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { com.wedo.schedule.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_appearance), style = WedoAppleType.headline()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = com.wedo.schedule.ui.theme.WedoApple.accentIcon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(WedoAppleDimensions.pageMargin),
            verticalArrangement = Arrangement.spacedBy(WedoAppleDimensions.sectionGap)
        ) {
            // ── 强调色 ──
            item {
                Column {
                    Text(
                        stringResource(R.string.appearance_accent_title),
                        style = WedoAppleType.title3(),
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.appearance_accent_desc),
                        style = WedoAppleType.footnote(),
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item {
                // 11 个 iOS 系统色，4 列 × 3 行（末行 3 个；黄色已移除，见 WedoSystemColor）
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(216.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(WedoSystemColor.entries) { color ->
                        AccentSwatch(
                            color = color,
                            selected = color == selectedAccent,
                            onClick = {
                                AppPrefs.setThemeKey(context, color.name)
                                refreshWidgets()
                            }
                        )
                    }
                }
            }

            // ── 外观模式 ──
            item {
                Column {
                    Text(
                        stringResource(R.string.theme_appearance),
                        style = WedoAppleType.title3(),
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(WedoAppleDimensions.sectionGap))
                    val modes = listOf(
                        AppPrefs.THEME_MODE_SYSTEM to stringResource(R.string.theme_mode_system),
                        AppPrefs.THEME_MODE_LIGHT to stringResource(R.string.theme_mode_light),
                        AppPrefs.THEME_MODE_DARK to stringResource(R.string.theme_mode_dark)
                    )
                    // iOS 分段控件：外框圆角容器 + 选中项白/深色滑块
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(WedoAppleShapes.continuous(8.dp))
                            .background(colors.surfaceContainerHigh)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        modes.forEach { (mode, label) ->
                            val sel = mode == selectedMode
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(WedoAppleShapes.continuous(7.dp))
                                    .background(if (sel) colors.surfaceContainerLowest else Color.Transparent)
                                    .noRippleClickable {
                                        if (mode != selectedMode) {
                                            AppPrefs.setThemeMode(context, mode)
                                            onThemeModeChange(mode)
                                            refreshWidgets()
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style = WedoAppleType.subheadline().copy(
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (sel) colors.onSurface else colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 强调色色块。
 *
 * iOS 的取色器是**圆形色点**，选中时套一圈同色描边 —— 不是方形卡片。
 * 这里照此实现：36dp 圆点 + 选中态 2dp 同色环。
 */
@Composable
private fun AccentSwatch(color: WedoSystemColor, selected: Boolean, onClick: () -> Unit) {
    val dark = WedoApple.isDark
    val fill = color.color(dark)
    Box(
        modifier = Modifier.size(WedoAppleDimensions.minTouchTarget).noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.selected),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
