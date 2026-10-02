package com.wedo.schedule.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.theme.AppleNeutralDark
import com.wedo.schedule.ui.theme.AppleNeutralLight
import com.wedo.schedule.ui.theme.WedoSystemColor
import com.wedo.schedule.ui.theme.appleScheme
import com.wedo.schedule.util.DateUtils
import java.time.LocalDate

/**
 * 小组件数据模型 + 配色派生 — 生产 RemoteViews 渲染链路
 * (WidgetBitmapRenderers / WeekGridWidgetProvider / WidgetRenderActivity) 共用。
 *
 * Glance composable 层(WidgetContent/WeekListContent/TwoDayContent/WeekGridContent)
 * 已删除(决策 D5-11): 5 个生产入口全走 RemoteViews + Canvas bitmap,
 * Glance 层生产不可达, 其旧关键词配色路径(CourseColorRules)一并移除。
 * 文件名保留 WidgetContent.kt 以减小 diff; 如需可后续重命名为 WidgetModels.kt。
 */

/**
 * 小组件渲染数据 — 让渲染端单纯绘制，不读 DB。
 * Receiver.loadDataSync 在后台线程拉数据，组装成这个 model 喂给 renderer。
 */
data class WidgetData(
    /** 今日日期 */
    val date: LocalDate,
    /** 今日课程（已按当前周次过滤 + 排序） */
    val courses: List<CourseEntity>,
    /** timeJson（用于查开始/结束时间） */
    val timeJson: String,
    /** 是否有课表 */
    val hasTable: Boolean,
    /** 跟 app 主题保持一致：true=深色小组件 */
    val isDark: Boolean = false,
    /** 强调色名（[WedoSystemColor] 的 enum name，如 "Blue"）。历史上叫 themeKey。 */
    val themeKey: String = WedoSystemColor.DEFAULT_NAME,
    /** 学期状态（v1.0.37）: 学期外时 Today 渲染状态文案不渲染课程 */
    val semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE
) {
    val dayName: String get() = DateUtils.localizedDay(date.dayOfWeek.value, com.wedo.schedule.WedoApp.get())
    val dateLabel: String get() = "${date.monthValue}/${date.dayOfMonth}"
}

/**
 * 小组件配色 —— **Apple 语义**，与 App 内 `WedoThemeProvider` 同一套取值。
 *
 * 字段名沿用了旧 M3 槽位（`bg` / `primary` / `onSurface` …）以免动 4 个渲染器的读取点，
 * 但语义已按 [appleScheme] 重写：
 *  - `bg`              分组列表底（浅 #F2F2F7 / 深 #000000）
 *  - `surface`         卡片底（浅 #FFFFFF / 深 #1C1C1E）
 *  - `primary`         用户强调色（默认系统蓝 #007AFF / #0A84FF）
 *  - `onSurface`       主文字；`onSurfaceVariant` 次文字
 *  - `surfaceContainer` 列/卡片底；`surfaceVariant` 填充色（chip 底）
 *  - `separator`       0.5–1dp 分隔线（**深色模式必须用这个，不能拿黑色加透明度硬凑**）
 */
data class WidgetScheme(
    val bg: Color = AppleNeutralLight.groupedBackground,
    val surface: Color = AppleNeutralLight.cardBackground,
    val primary: Color = WedoSystemColor.Default.light,
    val primaryContainer: Color = Color(0xFFD9EAFF),
    val onPrimaryContainer: Color = WedoSystemColor.Default.light,
    val onSurface: Color = AppleNeutralLight.label,
    val onSurfaceVariant: Color = AppleNeutralLight.secondaryLabel,
    val surfaceContainer: Color = AppleNeutralLight.cardBackground,
    val surfaceVariant: Color = AppleNeutralLight.systemFill,
    val separator: Color = AppleNeutralLight.separator,
    val isDark: Boolean = false
)

/**
 * 按强调色名 + 深浅模式派生小组件配色。
 *
 * **2026-09-28 修正**：此前这里走 `ThemePresets.byKey(themeKey)`，而 App 侧
 * `AppPrefs.getThemeKey` 早已改为存 [WedoSystemColor] 的 enum name（"Blue" …）。
 * 老 key（"default"/"ocean"…）在新表里查不到 → 回落预设默认色（M3 紫），
 * 表现为「App 是蓝的、桌面小组件是紫的」。`themeKey == "system"` 时还会去取
 * Material You 壁纸色 —— 而 App 已删除动态取色，于是变成「小组件跟壁纸、
 * App 不跟」的第二层错位。
 *
 * 现在与 `WedoThemeProvider` 完全同路：强调色名 → [WedoSystemColor.byName] →
 * [appleScheme]。未知/历史值一律回落系统蓝，不会崩也不会再变紫。
 *
 * @param context 保留入参以兼容现有 4 处调用点（取色已不再需要 Context）
 */
internal fun resolveSchemePublic(context: Context, themeKey: String, isDark: Boolean): WidgetScheme {
    val accent = WedoSystemColor.byName(themeKey).color(isDark)
    val apple = appleScheme(accent, isDark)
    return WidgetScheme(
        bg = apple.background,
        surface = apple.surface,
        primary = apple.primary,
        primaryContainer = apple.primaryContainer,
        onPrimaryContainer = apple.onPrimaryContainer,
        onSurface = apple.onSurface,
        onSurfaceVariant = apple.onSurfaceVariant,
        surfaceContainer = apple.surfaceContainer,
        surfaceVariant = apple.surfaceVariant,
        separator = if (isDark) AppleNeutralDark.separator else AppleNeutralLight.separator,
        isDark = isDark
    )
}

// ═══════════════════════════════════════════════════════
// Multi-day widget data
// ═══════════════════════════════════════════════════════

/** 单天数据 */
data class DayData(
    val date: LocalDate,
    val dayOfWeek: Int,
    val courses: List<CourseEntity>,
    val timeJson: String
) {
    val dayLabel: String get() = DateUtils.shortDate(date)
    val dayName: String get() = DateUtils.localizedDay(dayOfWeek, com.wedo.schedule.WedoApp.get())
    val isToday: Boolean get() = date == LocalDate.now()
    val isTomorrow: Boolean get() = date == LocalDate.now().plusDays(1)
}

/** 周视图数据 */
data class WeekData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = WedoSystemColor.DEFAULT_NAME,
    // displayMode 死字段已删（renderer 各自直读 AppPrefs.getDisplayMode, 传入字段从未被消费）
    val showDate: Boolean = false,
    val visibleDays: Set<Int> = (1..7).toSet(),
    /** 学期状态（v1.0.37）: 学期外时列头加状态行 */
    val semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE
)
