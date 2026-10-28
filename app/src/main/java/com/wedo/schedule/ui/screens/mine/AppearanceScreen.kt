package com.wedo.schedule.ui.screens.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wedo.schedule.R
import com.wedo.schedule.ui.components.WedoInsetCard
import com.wedo.schedule.ui.components.WedoSectionHeader
import com.wedo.schedule.ui.components.WedoSegmentedSetting
import com.wedo.schedule.ui.components.WedoSliderSetting
import com.wedo.schedule.ui.components.WedoToggleSetting
import com.wedo.schedule.ui.settings.WedoSettingsState
import com.wedo.schedule.ui.settings.WedoSettingsStore
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.util.AppPrefs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 深浅模式与课程胶囊外观；预览实时跟随当前设置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = WedoTheme.colors
    val settings by WedoSettingsStore.state.collectAsState()
    var courseColorless by remember { mutableStateOf(AppPrefs.isCourseColorless(context)) }
    var gridCorner by remember { mutableStateOf(AppPrefs.getGridCornerRatio(context)) }

    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { com.wedo.schedule.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_appearance), style = WedoAppleType.headline()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.back),
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
            item {
                WedoInsetCard {
                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        ThemePreview(cornerRatio = gridCorner, colorless = courseColorless)
                    }
                }
            }

            item { WedoSectionHeader(stringResource(R.string.theme_appearance)) }
            item {
                WedoInsetCard {
                    WedoSegmentedSetting(
                        label = stringResource(R.string.theme_appearance),
                        options = listOf(
                            AppPrefs.THEME_MODE_SYSTEM to stringResource(R.string.theme_mode_system),
                            AppPrefs.THEME_MODE_LIGHT to stringResource(R.string.theme_mode_light),
                            AppPrefs.THEME_MODE_DARK to stringResource(R.string.theme_mode_dark)
                        ),
                        selected = themeMode,
                        onSelect = { mode ->
                            if (mode != themeMode) {
                                AppPrefs.setThemeMode(context, mode)
                                onThemeModeChange(mode)
                                refreshWidgets()
                            }
                        },
                        showDivider = false
                    )
                }
            }

            item { WedoSectionHeader(stringResource(R.string.appearance_section_display)) }
            item {
                WedoInsetCard {
                    WedoToggleSetting(
                        label = stringResource(R.string.settings_course_colorless),
                        subtitle = stringResource(R.string.settings_course_colorless_sub),
                        checked = courseColorless,
                        onChange = {
                            courseColorless = it
                            AppPrefs.setCourseColorless(context, it)
                        }
                    )
                    WedoSliderSetting(
                        label = stringResource(R.string.settings_pill_corner),
                        valueText = "${(gridCorner * 100).roundToInt()}%",
                        value = gridCorner,
                        range = 0f..2f,
                        onChange = { gridCorner = (it * 20).roundToInt() / 20f },
                        onFinished = { AppPrefs.setGridCornerRatio(context, gridCorner) },
                        showDivider = false
                    )
                }
            }

            item { WedoSectionHeader(stringResource(R.string.appearance_section_material)) }
            item {
                WedoInsetCard {
                    WedoSegmentedSetting(
                        label = stringResource(R.string.settings_glass_quality),
                        options = listOf(
                            WedoSettingsState.QUALITY_SMOOTH to stringResource(R.string.settings_glass_quality_smooth),
                            WedoSettingsState.QUALITY_BALANCED to stringResource(R.string.settings_glass_quality_balanced),
                            WedoSettingsState.QUALITY_FINE to stringResource(R.string.settings_glass_quality_fine)
                        ),
                        selected = settings.quality,
                        onSelect = { WedoSettingsStore.setQuality(context, it) }
                    )
                    WedoToggleSetting(
                        label = stringResource(R.string.settings_motion),
                        subtitle = stringResource(R.string.settings_motion_sub),
                        checked = settings.motion,
                        onChange = { WedoSettingsStore.setMotion(context, it) }
                    )
                    WedoToggleSetting(
                        label = stringResource(R.string.settings_haptics),
                        subtitle = stringResource(R.string.settings_haptics_sub),
                        checked = settings.haptics,
                        onChange = { WedoSettingsStore.setHaptics(context, it) },
                        showDivider = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreview(cornerRatio: Float, colorless: Boolean) {
    val colors = WedoTheme.colors
    val fills = listOf(
        WedoApple.accent,
        Color(0xFF34C759),
        Color(0xFFFF9500),
        Color(0xFFAF52DE)
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { day ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { slot ->
                    val index = (day * 3 + slot) % 10
                    val filled = index == 1 || index == 5 || index == 8
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(WedoAppleShapes.continuous(4.dp * cornerRatio))
                            .background(
                                when {
                                    !filled -> colors.surfaceContainerHigh
                                    colorless -> colors.surfaceVariant
                                    else -> fills[index % fills.size]
                                }
                            )
                    )
                }
            }
        }
    }
}
