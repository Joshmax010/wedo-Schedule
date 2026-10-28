package com.wedo.schedule.ui.screens.mine

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wedo.schedule.R
import com.wedo.schedule.ui.components.WedoIconColors
import com.wedo.schedule.ui.components.WedoInsetCard
import com.wedo.schedule.ui.components.WedoListRow
import com.wedo.schedule.ui.components.WedoSectionHeader
import com.wedo.schedule.ui.components.WedoSegmentedSetting
import com.wedo.schedule.ui.components.WedoToggle
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.util.AppPrefs

/** 常用设置首层；低频布局与冲突微调分别收进独立二级页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onOpenVisibleDays: () -> Unit = {},
    onOpenScheduleLayout: () -> Unit = {},
    onOpenConflictStyle: () -> Unit = {},
    onOpenLanguage: () -> Unit = {}
) {
    val colors = WedoTheme.colors
    val context = LocalContext.current
    var displayMode by remember { mutableStateOf(AppPrefs.getDisplayMode(context)) }
    var gridSubInfo by remember { mutableStateOf(AppPrefs.getGridSubInfo(context)) }
    var startView by remember { mutableStateOf(AppPrefs.getStartView(context)) }
    var highRefresh by remember { mutableStateOf(AppPrefs.isHighRefresh(context)) }

    val visibleDays = AppPrefs.getVisibleDays(context)
    val visibleDaysValue = when (visibleDays) {
        (1..7).toSet() -> stringResource(R.string.settings_visible_days_all)
        (1..5).toSet() -> stringResource(R.string.settings_visible_days_weekdays)
        else -> stringResource(R.string.settings_visible_days_count, visibleDays.size)
    }
    val conflictStyle = AppPrefs.getConflictStyle(context)
    val conflictStyleValue = stringResource(
        when (conflictStyle) {
            "fold" -> R.string.settings_conflict_fold
            "rail" -> R.string.settings_conflict_rail
            else -> R.string.settings_conflict_stack
        }
    )
    val language = AppPrefs.getLanguage(context)
    val languageValue = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "es" to "Español"
    ).firstOrNull { it.first == language }?.second ?: "English"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_general)) },
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
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { WedoSectionHeader(stringResource(R.string.appearance_section_display)) }
            item {
                WedoInsetCard {
                    WedoSegmentedSetting(
                        label = stringResource(R.string.settings_display_mode),
                        options = listOf(
                            "node" to stringResource(R.string.settings_display_node),
                            "time" to stringResource(R.string.settings_display_time)
                        ),
                        selected = displayMode,
                        onSelect = { value ->
                            displayMode = value
                            AppPrefs.setDisplayMode(context, value)
                        }
                    )
                    WedoSegmentedSetting(
                        label = stringResource(R.string.settings_grid_sub_info),
                        options = listOf(
                            "room" to stringResource(R.string.settings_grid_sub_room),
                            "teacher" to stringResource(R.string.settings_grid_sub_teacher),
                            "none" to stringResource(R.string.settings_grid_sub_none)
                        ),
                        selected = gridSubInfo,
                        onSelect = { value ->
                            gridSubInfo = value
                            AppPrefs.setGridSubInfo(context, value)
                        }
                    )
                    WedoSegmentedSetting(
                        label = stringResource(R.string.settings_start_view),
                        options = listOf(
                            "full" to stringResource(R.string.settings_start_view_full),
                            "cards" to stringResource(R.string.settings_start_view_cards)
                        ),
                        selected = startView,
                        onSelect = { value ->
                            startView = value
                            AppPrefs.setStartView(context, value)
                        },
                        showDivider = false
                    )
                }
            }

            item { WedoSectionHeader(stringResource(R.string.settings_section_date_layout)) }
            item {
                WedoInsetCard {
                    WedoListRow(
                        icon = Icons.Outlined.CalendarToday,
                        iconColor = WedoIconColors.orange,
                        label = stringResource(R.string.settings_visible_days),
                        value = visibleDaysValue,
                        onClick = onOpenVisibleDays
                    )
                    WedoListRow(
                        icon = Icons.Outlined.Tune,
                        iconColor = WedoIconColors.blue,
                        label = stringResource(R.string.settings_schedule_layout),
                        onClick = onOpenScheduleLayout
                    )
                    WedoListRow(
                        icon = Icons.Outlined.Layers,
                        iconColor = WedoIconColors.indigo,
                        label = stringResource(R.string.settings_conflict_style),
                        value = conflictStyleValue,
                        onClick = onOpenConflictStyle,
                        showDivider = false
                    )
                }
            }

            item { WedoSectionHeader(stringResource(R.string.settings_section_system)) }
            item {
                WedoInsetCard {
                    WedoListRow(
                        icon = Icons.Outlined.Language,
                        iconColor = WedoIconColors.purple,
                        label = stringResource(R.string.settings_language),
                        value = languageValue,
                        onClick = onOpenLanguage
                    )
                    WedoListRow(
                        icon = Icons.Outlined.Speed,
                        iconColor = WedoIconColors.green,
                        label = stringResource(R.string.settings_high_refresh),
                        trailing = {
                            WedoToggle(
                                checked = highRefresh,
                                onCheckedChange = { enabled ->
                                    highRefresh = enabled
                                    AppPrefs.setHighRefresh(context, enabled)
                                    val activity = context as? android.app.Activity
                                    if (activity != null) {
                                        com.wedo.schedule.util.HighRefreshRate.apply(activity, enabled)
                                    } else {
                                        Log.w("GeneralSettings", "high refresh toggle: context is not Activity, apply skipped")
                                    }
                                }
                            )
                        },
                        showDivider = false
                    )
                }
            }
        }
    }
}
