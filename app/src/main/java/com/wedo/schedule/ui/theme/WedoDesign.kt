package com.wedo.schedule.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.wedo.schedule.ui.settings.WedoSettingsStore
import com.wedo.schedule.util.SystemAccessibility

/** Display preferences are independent of course and authentication data. */
data class WedoDisplay(
    val density: String = "balanced",
    val quality: String = "balanced",
    val fields: Set<String> = setOf("name", "room", "teacher"),
    val motion: Boolean = true,
    val haptics: Boolean = true,
    val ghostCourses: Boolean = false,
)

object WedoPreferences {
    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("wedo_display", Context.MODE_PRIVATE)

    /**
     * 只读取不以材质开关为语义的字段。
     *
     * 三个材质开关的读写统一走 [com.wedo.schedule.ui.settings.WedoSettingsStore]，
     * 这里不重复读取，避免两个状态源漂移。
     */
    fun readBase(p: SharedPreferences) = WedoDisplay(
        density = p.getString("density", "balanced") ?: "balanced",
        fields = p.getStringSet("fields", setOf("name", "room", "teacher"))!!.toSet(),
        ghostCourses = p.getBoolean("ghost", false),
    )

    /** 兼容旧调用；生产侧的三个材质开关不再经这里读写。 */
    fun read(p: SharedPreferences) = readBase(p).copy(
        quality = p.getString("quality", "balanced") ?: "balanced",
        motion = p.getBoolean("motion", true),
        haptics = p.getBoolean("haptics", true),
    )

    fun write(context: Context, value: WedoDisplay) {
        prefs(context).edit().putString("density", value.density)
            .putString("quality", value.quality).putStringSet("fields", value.fields)
            .putBoolean("motion", value.motion).putBoolean("haptics", value.haptics)
            .putBoolean("ghost", value.ghostCourses).apply()
    }
}

val LocalWedoDisplay = staticCompositionLocalOf { WedoDisplay() }

/**
 * 顶栏/底栏是否处于「收起」态。
 *
 * V3 重设计 P0（ADR-2）：原「下滑收起 Dock」联动已删除，本值在 [com.wedo.schedule.MainActivity]
 * 中**恒提供 `false`**；`WedoWeekHeader` 暂零改动消费该值。P3 由课表页顶栏的
 * 大标题滚动收起（标准行为）接手，届时本 CompositionLocal 可移除。
 */
val LocalWedoCollapsed = staticCompositionLocalOf { false }

@Composable
fun WedoDisplayProvider(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 提前初始化单例，保证首帧就拿到持久化偏好；两个入口均幂等。
    remember(context) {
        WedoSettingsStore.init(context)
        SystemAccessibility.start(context)
    }
    val settings by WedoSettingsStore.state.collectAsState()
    val accessibility by SystemAccessibility.state.collectAsState()
    val prefs = remember(context) { WedoPreferences.prefs(context) }
    var display by remember(prefs) { mutableStateOf(WedoPreferences.readBase(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            display = WedoPreferences.readBase(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // 三个开关只由 WedoSettingsStore 提供；动效额外受系统动画倍率约束（M-36）。
    val resolved = display.copy(
        quality = settings.quality,
        motion = settings.motion && accessibility.animationsEnabled,
        haptics = settings.haptics,
    )
    CompositionLocalProvider(LocalWedoDisplay provides resolved, content = content)
}

/** Keep user accent selection, while giving every page the same blue-white/navy base. */
fun wedoColors(base: WakeUpColorScheme, dark: Boolean, blue: Boolean): WakeUpColorScheme =
    if (dark) base.copy(
        primary = if (blue) Color(0xFF80B5FF) else base.primary,
        primaryContainer = if (blue) Color(0xFF183C68) else base.primaryContainer,
        onPrimaryContainer = Color(0xFFE1EFFF),
        background = Color(0xFF08172C), onBackground = Color(0xFFF4F7FF),
        surface = Color(0xFF10243E), onSurface = Color(0xFFF4F7FF),
        surfaceVariant = Color(0xFF213956), onSurfaceVariant = Color(0xFFB4C8E1),
        surfaceContainerLowest = Color(0xFF08172C), surfaceContainerLow = Color(0xFF132C49),
        surfaceContainer = Color(0xFF16314F), surfaceContainerHigh = Color(0xFF1B3655),
        surfaceContainerHighest = Color(0xFF254363), outlineVariant = Color(0xFF355675),
    ) else base.copy(
        primary = if (blue) Color(0xFF1764D9) else base.primary,
        primaryContainer = if (blue) Color(0xFFD5E8FF) else base.primaryContainer,
        onPrimaryContainer = Color(0xFF103D77),
        background = Color(0xFFF4F8FF), onBackground = Color(0xFF14213D),
        surface = Color(0xFFF4F8FF), onSurface = Color(0xFF14213D),
        surfaceVariant = Color(0xFFDDE9F6), onSurfaceVariant = Color(0xFF4C6280),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F7FF),
        surfaceContainer = Color(0xFFE7F0FC), surfaceContainerHigh = Color(0xFFDFEBFA),
        surfaceContainerHighest = Color(0xFFD6E5F7), outlineVariant = Color(0xFFB6CCE6),
    )
