package com.wedo.schedule.ui.screens.mine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.ui.components.WedoDialog
import com.wedo.schedule.BuildConfig
import com.wedo.schedule.R
import com.wedo.schedule.WedoApp
import com.wedo.schedule.data.entity.TimeTableEntity
import com.wedo.schedule.ui.components.LocalTabBarVisibilityState
import com.wedo.schedule.ui.components.WedoIconColors
import com.wedo.schedule.ui.components.WedoIconTile
import com.wedo.schedule.ui.components.WedoListRow
import com.wedo.schedule.ui.components.WedoToggle
import com.wedo.schedule.ui.screens.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.noRippleClickable
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.FeedbackComposer

/**
 * 设置页（方案 A：iOS inset grouped + 彩色图标块）。
 *
 * 结构：大标题 → 当前课表（profile 行）→ 课表 / 显示 / 通知 / 关于 四组白卡。
 * 强调色不再是用户可选项：全局固定 iOS 蓝，彩色语义由列表图标块承载。
 */
@Composable
fun WedoSettingsScreen(
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onOpenAllTables: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenGeneral: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenLicense: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onManualAdd: () -> Unit = {},
    onEditCurrentTable: () -> Unit = {},
    onOpenReminder: () -> Unit = {},
    onOpenHoliday: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val table = state.currentTable
    val colors = WedoTheme.colors

    var beforeClassEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beforeClassEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            AppPrefs.setBeforeClassEnabled(context, true)
            WedoApp.get().notificationScheduler.scheduleAll()
        } else {
            beforeClassEnabled = false
            AppPrefs.setBeforeClassEnabled(context, false)
        }
    }

    fun setBeforeClass(on: Boolean) {
        beforeClassEnabled = on
        if (on) {
            AppPrefs.setReminderEnabled(context, true)
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
            if (granted) {
                AppPrefs.setBeforeClassEnabled(context, true)
                WedoApp.get().notificationScheduler.scheduleAll()
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            AppPrefs.setBeforeClassEnabled(context, false)
            WedoApp.get().notificationScheduler.scheduleAll()
        }
    }

    var showPrivacy by remember { mutableStateOf(false) }

    fun openFeedback() {
        val diag = FeedbackComposer.Diagnostic(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            androidVersion = android.os.Build.VERSION.RELEASE ?: android.os.Build.VERSION.SDK_INT.toString(),
            brand = android.os.Build.BRAND,
            model = android.os.Build.MODEL,
            resolution = "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}",
            locale = context.resources.configuration.locales[0].toLanguageTag(),
            isDebug = BuildConfig.DEBUG,
        )
        val uri = FeedbackComposer.githubIssueUrl(
            title = "[wedo] ",
            body = "请描述你遇到的问题或建议：",
            diag = diag,
            template = "bug_report.yml",
        )
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
            .nestedScroll(LocalTabBarVisibilityState.current.scrollConnection),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                stringResource(R.string.action_settings),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = WedoAppleType.largeTitle(),
                color = colors.onSurface
            )
        }

        item {
            Column {
                SectionHeader(stringResource(R.string.manage_current_table))
                WedoSettingsCard {
                    if (table != null) {
                        CurrentTableRow(
                            table = table,
                            currentWeek = state.currentWeek,
                            courseCount = state.courses.size,
                            onClick = onOpenAllTables
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionHeader(stringResource(R.string.tab_schedule))
                WedoSettingsCard {
                    WedoListRow(Icons.Outlined.CalendarMonth, WedoIconColors.blue, stringResource(R.string.all_tables), onClick = onOpenAllTables)
                    WedoListRow(Icons.Outlined.FileUpload, WedoIconColors.green, stringResource(R.string.import_title), onClick = onOpenImport)
                    WedoListRow(Icons.Outlined.Share, WedoIconColors.orange, stringResource(R.string.mine_export), onClick = onOpenExport)
                    WedoListRow(Icons.Outlined.Add, WedoIconColors.indigo, stringResource(R.string.manage_manual_add), onClick = onManualAdd)
                    WedoListRow(Icons.Outlined.Edit, WedoIconColors.gray, stringResource(R.string.manage_edit_current), onClick = onEditCurrentTable, showDivider = false)
                }
            }
        }

        item {
            Column {
                SectionHeader(stringResource(R.string.settings_group_display))
                WedoSettingsCard {
                    WedoListRow(Icons.Outlined.Palette, WedoIconColors.purple, stringResource(R.string.mine_appearance), onClick = onOpenAppearance)
                    WedoListRow(Icons.Outlined.Tune, WedoIconColors.gray, stringResource(R.string.mine_general), onClick = onOpenGeneral)
                    WedoListRow(Icons.Outlined.EventBusy, WedoIconColors.red, stringResource(R.string.holiday_page_title), onClick = onOpenHoliday, showDivider = false)
                }
            }
        }

        item {
            Column {
                SectionHeader(stringResource(R.string.settings_group_notify))
                WedoSettingsCard {
                    WedoListRow(
                        Icons.Outlined.Notifications,
                        WedoIconColors.pink,
                        stringResource(R.string.reminder_title),
                        onClick = onOpenReminder,
                        trailing = { WedoToggle(beforeClassEnabled, ::setBeforeClass) },
                        showDivider = false
                    )
                }
            }
        }

        item {
            Column {
                SectionHeader(stringResource(R.string.about_title))
                WedoSettingsCard {
                    WedoListRow(Icons.Outlined.PrivacyTip, WedoIconColors.blue, stringResource(R.string.settings_privacy), onClick = { showPrivacy = true })
                    WedoListRow(Icons.Outlined.Code, WedoIconColors.gray, stringResource(R.string.about_license_detail), onClick = onOpenLicense)
                    WedoListRow(Icons.Outlined.Feedback, WedoIconColors.green, stringResource(R.string.about_feedback), onClick = { openFeedback() }, showDivider = false)
                }
            }
        }

        item {
            Text(
                stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME +
                    if (BuildConfig.DEBUG) " (debug)" else "",
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                style = WedoAppleType.footnote(),
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showPrivacy) {
        WedoDialog(
            onDismissRequest = { showPrivacy = false },
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            title = { Text(stringResource(R.string.settings_privacy_dialog_title)) },
            text = { Text(stringResource(R.string.settings_privacy_dialog_body), style = WedoAppleType.subheadline()) },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) {
                    Text(stringResource(R.string.jw_config_confirm), color = WedoApple.accentText)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
        style = WedoAppleType.footnote(),
        color = WedoTheme.colors.onSurfaceVariant
    )
}

@Composable
private fun WedoSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    com.wedo.schedule.ui.components.WedoInsetCard(content = content)
}

@Composable
private fun CurrentTableRow(
    table: TimeTableEntity,
    currentWeek: Int,
    courseCount: Int,
    onClick: () -> Unit
) {
    val colors = WedoTheme.colors
    Row(
        Modifier.fillMaxWidth().noRippleClickable(onClick).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WedoIconTile(
            icon = Icons.Outlined.CalendarMonth,
            color = WedoIconColors.blue,
            size = 42.dp,
            corner = 10.dp,
            glyphSize = 22.dp
        )
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(table.name, style = WedoAppleType.body(), color = colors.onSurface)
            Text(
                stringResource(R.string.table_info, table.startDate, currentWeek, courseCount),
                style = WedoAppleType.footnote(),
                color = colors.onSurfaceVariant
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
