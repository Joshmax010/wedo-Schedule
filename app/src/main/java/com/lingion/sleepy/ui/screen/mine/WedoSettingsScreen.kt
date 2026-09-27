package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.*
import com.lingion.sleepy.ui.theme.*
import com.lingion.sleepy.util.AppPrefs

@Composable
fun WedoSettingsScreen(onManage: () -> Unit, onOpenAllTables: () -> Unit,
    onOpenAppearance: () -> Unit, onOpenGeneral: () -> Unit,
    onOpenExport: () -> Unit, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val display = LocalWedoDisplay.current
    var days by remember { mutableStateOf(AppPrefs.getVisibleDays(context)) }
    var conflict by remember { mutableStateOf(AppPrefs.getConflictStyle(context)) }
    val colors = SleepyTheme.colors
    fun update(value: WedoDisplay) = WedoPreferences.write(context, value)
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WedoAppleDimensions.pageMargin,
            end = WedoAppleDimensions.pageMargin,
            top = 20.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(WedoAppleDimensions.sectionGap)) {
        item { Text("设置", color = colors.onSurface, style = WedoAppleType.largeTitle()) }
        item {
            WedoSettingsGroup("外观与手感") {
                SettingsItem(Icons.Outlined.Palette, "主题模式与强调色", onOpenAppearance)
                WedoLabeledToggle("灵动效果", display.motion) { update(display.copy(motion = it)) }
                WedoLabeledToggle("轻触震动", display.haptics) { update(display.copy(haptics = it)) }
            }
        }
        item {
            WedoSettingsGroup("课表显示") {
                Text("显示密度", color = colors.onSurface)
                Choices(listOf("compact" to "紧凑", "balanced" to "平衡", "spacious" to "宽松"), display.density) {
                    update(display.copy(density = it))
                }
                Text("显示星期", color = colors.onSurface)
                Choices(listOf("5" to "周一至五", "6" to "周一至六", "7" to "全周"),
                    if (days == (1..days.size).toSet()) days.size.toString() else "custom") {
                    days = (1..it.toInt()).toSet(); AppPrefs.setVisibleDays(context, days)
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.outlineVariant)
                Text("课程信息 · 所有课程共用", color = colors.onSurface)
                listOf("name" to "课程名称", "room" to "教室", "teacher" to "教师",
                    "weeks" to "上课周次", "sections" to "节次", "note" to "备注").forEach { (field, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        val fields = if (field in display.fields) display.fields - field else display.fields + field
                        if (fields.isNotEmpty()) update(display.copy(fields = fields))
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(field in display.fields, onCheckedChange = null)
                        Text(label, color = colors.onSurface)
                    }
                }
                WedoLabeledToggle("淡化显示非本周课程", display.ghostCourses) { update(display.copy(ghostCourses = it)) }
                Text("课程冲突样式", color = colors.onSurface)
                Choices(listOf("stack" to "错位堆叠", "fold" to "折角", "rail" to "侧边轨道"), conflict) {
                    conflict = it; AppPrefs.setConflictStyle(context, it)
                }
                SettingsItem(Icons.Outlined.Tune, "更多课表设置", onOpenGeneral)
            }
        }
        item {
            WedoSettingsGroup("课表与数据") {
                SettingsItem(Icons.Outlined.CalendarMonth, "全部课表与学期", onOpenAllTables)
                SettingsItem(Icons.Outlined.Edit, "创建、导入与管理", onManage)
                SettingsItem(Icons.Outlined.Share, "导出课表", onOpenExport)
            }
        }
        item {
            WedoSettingsGroup("隐私与关于") {
                Text("课表保存在本机，离线也能查看。", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                SettingsItem(Icons.Outlined.Info, "关于 wedo · 隐私与开源许可", onOpenAbout)
            }
        }
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
            .background(SleepyTheme.colors.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            style = WedoAppleType.footnote(),
            color = SleepyTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Choices(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected == key, { onSelect(key) }, label = { Text(label, style = MaterialTheme.typography.labelMedium) })
        }
    }
}

/**
 * 带标签的设置开关行。
 *
 * 注意：这里**不再自己画开关** —— 之前本地实现直接包 M3 `Switch`，与本轮新增的
 * 共享 `WedoToggle`（iOS 比例轨道 + 白色圆滑块）重名且观感不一致。现在只负责
 * 「标签 + 右对齐开关」这一层布局，开关本体一律用共享实现，保证全 app 一个样子。
 */
@Composable
private fun WedoLabeledToggle(label: String, checked: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = SleepyTheme.colors.onSurface)
        WedoToggle(checked = checked, onCheckedChange = set)
    }
}
