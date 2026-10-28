package com.wedo.schedule.ui.screens.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import com.wedo.schedule.ui.components.WedoChoiceSetting
import com.wedo.schedule.ui.components.WedoInsetCard
import com.wedo.schedule.ui.components.WedoSectionHeader
import com.wedo.schedule.ui.components.WedoSettingFooter
import com.wedo.schedule.ui.components.WedoSliderSetting
import com.wedo.schedule.ui.components.WedoToggleSetting
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.DateUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = WedoTheme.colors
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        content = content
    )
}

@Composable
fun VisibleDaysSettingsScreen(
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var visibleDays by remember { mutableStateOf(AppPrefs.getVisibleDays(context)) }
    SettingsDetailScaffold(stringResource(R.string.settings_visible_days), onBack) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                WedoInsetCard {
                    (1..7).forEach { day ->
                        WedoToggleSetting(
                            label = DateUtils.localizedDay(day, context),
                            checked = day in visibleDays,
                            onChange = { enabled ->
                                val updated = if (enabled) visibleDays + day else visibleDays - day
                                if (updated.isNotEmpty()) {
                                    visibleDays = updated
                                    AppPrefs.setVisibleDays(context, updated)
                                }
                            },
                            showDivider = day != 7
                        )
                    }
                }
            }
            item { WedoSettingFooter(stringResource(R.string.settings_visible_days_sub)) }
        }
    }
}

@Composable
fun ScheduleLayoutSettingsScreen(
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var gridScale by remember { mutableStateOf(AppPrefs.getGridScale(context)) }
    var weekScale by remember { mutableStateOf(AppPrefs.getWeekScale(context)) }
    var weekTwoColumn by remember { mutableStateOf(AppPrefs.isWeekTwoColumn(context)) }
    var weekHideEmptyDays by remember { mutableStateOf(AppPrefs.isWeekHideEmptyDays(context)) }
    var showDate by remember { mutableStateOf(AppPrefs.isShowDate(context)) }

    SettingsDetailScaffold(stringResource(R.string.settings_schedule_layout), onBack) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { WedoSectionHeader(stringResource(R.string.settings_view_scale)) }
            item {
                WedoInsetCard {
                    WedoSliderSetting(
                        label = stringResource(R.string.settings_pill_scale),
                        valueText = "${(gridScale * 100).roundToInt()}%",
                        value = gridScale,
                        range = 0.7f..1.3f,
                        onChange = { gridScale = (it * 20).roundToInt() / 20f },
                        onFinished = { AppPrefs.setGridScale(context, gridScale) }
                    )
                    WedoSliderSetting(
                        label = stringResource(R.string.settings_pill_week_scale),
                        valueText = "${(weekScale * 100).roundToInt()}%",
                        value = weekScale,
                        range = 0.7f..1.3f,
                        onChange = { weekScale = (it * 20).roundToInt() / 20f },
                        onFinished = { AppPrefs.setWeekScale(context, weekScale) },
                        showDivider = false
                    )
                }
            }
            item { WedoSectionHeader(stringResource(R.string.settings_week_layout)) }
            item {
                WedoInsetCard {
                    WedoToggleSetting(
                        label = stringResource(R.string.settings_week_two_column),
                        checked = weekTwoColumn,
                        onChange = {
                            weekTwoColumn = it
                            AppPrefs.setWeekTwoColumn(context, it)
                        }
                    )
                    WedoToggleSetting(
                        label = stringResource(R.string.settings_week_hide_empty),
                        checked = weekHideEmptyDays,
                        onChange = {
                            weekHideEmptyDays = it
                            AppPrefs.setWeekHideEmptyDays(context, it)
                        }
                    )
                    WedoToggleSetting(
                        label = stringResource(R.string.settings_show_date),
                        checked = showDate,
                        onChange = {
                            showDate = it
                            AppPrefs.setShowDate(context, it)
                        },
                        showDivider = false
                    )
                }
            }
        }
    }
}

@Composable
fun ConflictStyleSettingsScreen(
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var conflictStyle by remember { mutableStateOf(AppPrefs.getConflictStyle(context)) }
    var conflictStackInset by remember { mutableStateOf(AppPrefs.getConflictStackInset(context)) }
    var conflictRailInset by remember { mutableStateOf(AppPrefs.getConflictRailInset(context)) }
    var conflictFoldSize by remember { mutableStateOf(AppPrefs.getConflictFoldSize(context)) }

    SettingsDetailScaffold(stringResource(R.string.settings_conflict_style), onBack) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                WedoInsetCard {
                    WedoChoiceSetting(
                        label = stringResource(R.string.settings_conflict_stack),
                        subtitle = stringResource(R.string.settings_conflict_stack_sub),
                        selected = conflictStyle == "stack",
                        onClick = {
                            conflictStyle = "stack"
                            AppPrefs.setConflictStyle(context, "stack")
                        }
                    )
                    WedoChoiceSetting(
                        label = stringResource(R.string.settings_conflict_fold),
                        subtitle = stringResource(R.string.settings_conflict_fold_sub),
                        selected = conflictStyle == "fold",
                        onClick = {
                            conflictStyle = "fold"
                            AppPrefs.setConflictStyle(context, "fold")
                        }
                    )
                    WedoChoiceSetting(
                        label = stringResource(R.string.settings_conflict_rail),
                        subtitle = stringResource(R.string.settings_conflict_rail_sub),
                        selected = conflictStyle == "rail",
                        onClick = {
                            conflictStyle = "rail"
                            AppPrefs.setConflictStyle(context, "rail")
                        }
                    )
                    when (conflictStyle) {
                        "fold" -> WedoSliderSetting(
                            label = stringResource(R.string.settings_conflict_fold_size),
                            valueText = "${conflictFoldSize.roundToInt()}dp",
                            value = conflictFoldSize,
                            range = AppPrefs.CONFLICT_FOLD_SIZE_RANGE.start..AppPrefs.CONFLICT_FOLD_SIZE_RANGE.endInclusive,
                            onChange = { conflictFoldSize = it.roundToInt().toFloat() },
                            onFinished = { AppPrefs.setConflictFoldSize(context, conflictFoldSize) },
                            showDivider = false
                        )
                        "stack" -> WedoSliderSetting(
                            label = stringResource(R.string.settings_conflict_stack_inset),
                            valueText = "${conflictStackInset.roundToInt()}dp",
                            value = conflictStackInset,
                            range = AppPrefs.CONFLICT_TOP_INSET_RANGE.start..AppPrefs.CONFLICT_TOP_INSET_RANGE.endInclusive,
                            onChange = { conflictStackInset = it.roundToInt().toFloat() },
                            onFinished = { AppPrefs.setConflictStackInset(context, conflictStackInset) },
                            showDivider = false
                        )
                        "rail" -> WedoSliderSetting(
                            label = stringResource(R.string.settings_conflict_rail_inset),
                            valueText = "${conflictRailInset.roundToInt()}dp",
                            value = conflictRailInset,
                            range = AppPrefs.CONFLICT_TOP_INSET_RANGE.start..AppPrefs.CONFLICT_TOP_INSET_RANGE.endInclusive,
                            onChange = { conflictRailInset = it.roundToInt().toFloat() },
                            onFinished = { AppPrefs.setConflictRailInset(context, conflictRailInset) },
                            showDivider = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSettingsScreen(
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPrefs.getLanguage(context)) }
    val languages = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "es" to "Español"
    )
    SettingsDetailScaffold(stringResource(R.string.settings_language), onBack) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item {
                WedoInsetCard {
                    languages.forEachIndexed { index, (code, label) ->
                        WedoChoiceSetting(
                            label = label,
                            subtitle = "",
                            selected = language == code,
                            onClick = {
                                language = code
                                AppPrefs.setLanguage(context, code)
                                (context as? android.app.Activity)?.recreate()
                            },
                            showDivider = index != languages.lastIndex
                        )
                    }
                }
            }
        }
    }
}
