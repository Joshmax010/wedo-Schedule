package com.wedo.schedule.ui.screen.mine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.wedo.schedule.BuildConfig
import com.wedo.schedule.R
import com.wedo.schedule.WedoApp
import com.wedo.schedule.ui.component.*
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.theme.*
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.FeedbackComposer
import com.wedo.schedule.util.UpdateInfo
import com.wedo.schedule.util.UpdateManager
import kotlinx.coroutines.launch

/**
 * 设置页（REQ-P4-01）—— V3 重设计后的**单一设置入口**。
 *
 * 由旧「我的」Tab + 「课表管理」过渡页合并而成，收敛为四组（iOS 设置分组语义）：
 *  1. **课表**：当前课表摘要 / 全部课表与学期 / 导入课表 / 导出课表 / 手动添加课程 / 编辑当前课表
 *  2. **显示**：主题模式与强调色 / 显示设置 / 节假日 / 冲突课程样式
 *  3. **通知**：上课前提醒开关（含 Android 13+ 通知权限申请）/ 提醒设置
 *  4. **关于**：检查更新 / 隐私 / 开源许可 / 反馈
 *
 * 设计约束：
 *  - 只读设计令牌（`WedoApple*` / `WedoTheme`），不硬编码颜色/间距；
 *  - 每个可点行 ≥44dp 触控目标（HIG），由 [SettingsItem] / [WedoLabeledToggle] 保证；
 *  - 开关本体统一 [WedoToggle]，全 app 同一观感。
 *
 * 覆页栈说明：本页「进入下一页」动作大多通过回调交给宿主（`MainActivity`）的覆盖页栈。
 * **2026-10-01 真机反馈**：原「关于」组 4 项里 3 项都开同一个 About 中间页（套娃），
 * 已改为**四项各自直达**——更新原地弹结果、隐私应用内弹层、反馈开 GitHub Issues、
 * 许可进 License 覆盖页；`About` 中间页与 `WedoAboutScreen` 一并删除。
 */
@Composable
fun WedoSettingsScreen(
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

    // 冲突样式（显示组内联快捷项）—— 与「显示设置」页的详细项共用同一 AppPrefs 键。
    var conflict by remember { mutableStateOf(AppPrefs.getConflictStyle(context)) }

    // 上课前提醒开关 —— master 提醒开关 + 课前子开关由 ReminderScreen 统一管理，
    // 此处仅做「一键开关 + 权限申请」快捷入口。
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
            // 用户拒绝 → 开关回退为关，配置不落库
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

    // ── 「关于」组：**三项**各自直达（2026-10-02 真机反馈）
    // 「获取更新」入口已移除——实测更新检查从未接线（UpdateNotifier 全仓零调用，
    // 原入口指向的 WedoAboutScreen 不含更新逻辑，带更新检查的 AboutScreen 本就是死代码）。
    var showPrivacy by remember { mutableStateOf(false) }

    // 「反馈与建议」→ 直接打开 GitHub Issues（附设备诊断信息，复用 FeedbackComposer）
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
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WedoAppleDimensions.pageMargin,
            end = WedoAppleDimensions.pageMargin,
            top = 20.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(WedoAppleDimensions.sectionGap)
    ) {
        item { Text(stringResource(R.string.action_settings), color = colors.onSurface, style = WedoAppleType.largeTitle()) }

        // ── 组① 课表 ──
        item {
            WedoSettingsGroup(stringResource(R.string.tab_schedule)) {
                if (table != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            stringResource(R.string.manage_current_table),
                            style = WedoAppleType.caption1(),
                            color = colors.onSurfaceVariant
                        )
                        Text(table.name, style = WedoAppleType.title3(), color = colors.onSurface)
                        Text(
                            stringResource(R.string.table_info, table.startDate, state.currentWeek, state.courses.size),
                            style = WedoAppleType.footnote(),
                            color = colors.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
                }
                SettingsItem(Icons.Outlined.CalendarMonth, stringResource(R.string.all_tables), onOpenAllTables)
                SettingsItem(Icons.Outlined.FileUpload, stringResource(R.string.import_title), onOpenImport)
                SettingsItem(Icons.Outlined.Share, stringResource(R.string.mine_export), onOpenExport)
                SettingsItem(Icons.Outlined.Add, stringResource(R.string.manage_manual_add), onManualAdd)
                SettingsItem(Icons.Outlined.Edit, stringResource(R.string.manage_edit_current), onEditCurrentTable)
            }
        }

        // ── 组② 显示 ──
        item {
            WedoSettingsGroup(stringResource(R.string.settings_group_display)) {
                SettingsItem(Icons.Outlined.Palette, stringResource(R.string.mine_appearance), onOpenAppearance)
                SettingsItem(Icons.Outlined.Tune, stringResource(R.string.mine_general), onOpenGeneral)
                SettingsItem(Icons.Outlined.EventBusy, stringResource(R.string.holiday_page_title), onOpenHoliday)
                Text(
                    stringResource(R.string.settings_conflict_style),
                    color = colors.onSurface,
                    // 2026-10-02 真机反馈：此处原来只有 padding(top=8dp)，**缺水平边距**，
                    // 导致标题贴到 x=0，与上方 16dp 的设置行左对齐不上。
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
                )
                Choices(
                    listOf(
                        "stack" to stringResource(R.string.settings_conflict_stack),
                        "fold" to stringResource(R.string.settings_conflict_fold),
                        "rail" to stringResource(R.string.settings_conflict_rail)
                    ),
                    conflict
                ) {
                    conflict = it
                    AppPrefs.setConflictStyle(context, it)
                }
            }
        }

        // ── 组③ 通知 ──
        item {
            WedoSettingsGroup(stringResource(R.string.settings_group_notify)) {
                WedoLabeledToggle(
                    stringResource(R.string.reminder_before_class_title),
                    beforeClassEnabled,
                    ::setBeforeClass
                )
                SettingsItem(Icons.Outlined.Notifications, stringResource(R.string.reminder_title), onOpenReminder)
            }
        }

        // ── 组④ 关于 ──
        item {
            WedoSettingsGroup(stringResource(R.string.about_title)) {
                // 三项各自直达（2026-10-02 真机反馈：移除从未接线的「获取更新」）
                SettingsItem(Icons.Outlined.PrivacyTip, stringResource(R.string.settings_privacy)) { showPrivacy = true }
                SettingsItem(Icons.Outlined.Code, stringResource(R.string.about_license_detail), onOpenLicense)
                SettingsItem(Icons.Outlined.Feedback, stringResource(R.string.about_feedback)) { openFeedback() }
            }
        }
    }

    // 「隐私」直达目的地：应用内隐私说明弹层（可离线查看，文本来自 PRIVACY.md 的 6 语言资源）
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            title = { Text(stringResource(R.string.settings_privacy_dialog_title)) },
            text = { Text(stringResource(R.string.settings_privacy_dialog_body), style = WedoAppleType.subheadline()) },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.jw_config_confirm), color = WedoApple.accentText) }
            }
        )
    }
}

/**
 * 设置分组卡片。
 *
 * Apple 化：去掉玻璃底，改为**实心卡片 + 0.5pt 描边**。
 * iOS 设置页的分组是「实色卡片浮在 groupBackground 上」，不是半透明玻璃。
 * 这里用 surfaceContainerLow 作卡面（比页面背景亮一档），靠明度差分层次。
 */
@Composable
private fun WedoSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(WedoAppleShapes.card)
            .background(WedoTheme.colors.surfaceContainerLow)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = WedoAppleType.footnote(),
            color = WedoTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
        )
        content()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Choices(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (key, label) ->
            WedoChoiceChip(label = label, selected = selected == key, onClick = { onSelect(key) })
        }
    }
}

/**
 * 选中态 chip —— 2026-10-01 真机反馈定调的**全 App 强调色硬规则**载体：
 *
 * **accent 只属于「可交互且处于选中/激活态」的元素**。
 * 选中 = accent 描边 + 15% 浅底 + accent 文字；未选中 = 中性描边 + 实色底 + 普通文字。
 *
 * 2026-10-02 真机反馈「样式没排列好」：原实现用 `heightIn(min=36dp)` + 纵向 padding，
 * 选中/未选中的**实际高度会因描边与背景层叠而不一致**，一排 chip 看着参差。
 * 现改为**固定高度 + 固定内边距**，两个状态几何完全一致，只换颜色。
 */
@Composable
private fun WedoChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = WedoTheme.colors
    Box(
        modifier = Modifier
            .height(WedoChoiceChipHeight)
            .clip(WedoAppleShapes.capsule)
            .background(if (selected) WedoApple.accent.copy(alpha = WedoTheme.Alpha.tinted) else colors.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = if (selected) WedoApple.accent else colors.outlineVariant,
                shape = WedoAppleShapes.capsule
            )
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = WedoAppleType.caption1(),
            color = if (selected) WedoApple.accentText else colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 冲突样式 chip 的固定高度——两个状态共用，保证一排 chip 等高。 */
private val WedoChoiceChipHeight = 34.dp

/**
 * 带标签的设置开关行。
 *
 * 只负责「标签 + 右对齐开关」这一层布局，开关本体一律用共享 [WedoToggle]，
 * 保证全 app 一个样子。行高按 HIG 撑到 44dp 最小触控。
 */
@Composable
private fun WedoLabeledToggle(label: String, checked: Boolean, set: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = WedoAppleDimensions.minTouchTarget).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = WedoTheme.colors.onSurface)
        WedoToggle(checked = checked, onCheckedChange = set)
    }
}

/**
 * iOS 设置行。
 *
 * 与旧 Material 写法三处差别：
 *  1. 图标**不套 40dp 圆角色块**（iOS 设置行就是一个裸的彩色 SF Symbol）
 *  2. 行高按 HIG 撑到 44pt 最小触控
 *  3. 可跳转行右侧补 chevron —— iOS 的「这行能进去」是靠箭头表达的
 *
 * 从 `MineScreen.kt` 迁移至此（TASK-05 删除 MineScreen，本函数仍有 `WedoAboutScreen`
 * 与本页两个调用点，故落在设置页作为宿主）。
 */
@Composable
internal fun SettingsItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    val colors = WedoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WedoAppleDimensions.minRowHeight)
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = WedoApple.accentIcon,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = WedoAppleType.body(),
            color = colors.onSurface,
            modifier = Modifier.weight(1f).padding(start = 14.dp)
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive),
            modifier = Modifier.size(16.dp)
        )
    }
}
