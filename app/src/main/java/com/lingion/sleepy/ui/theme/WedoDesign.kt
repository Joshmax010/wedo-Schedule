package com.lingion.sleepy.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

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

    fun read(p: SharedPreferences) = WedoDisplay(
        density = p.getString("density", "balanced") ?: "balanced",
        quality = p.getString("quality", "balanced") ?: "balanced",
        fields = p.getStringSet("fields", setOf("name", "room", "teacher"))!!.toSet(),
        motion = p.getBoolean("motion", true),
        haptics = p.getBoolean("haptics", true),
        ghostCourses = p.getBoolean("ghost", false),
    )

    fun write(context: Context, value: WedoDisplay) {
        prefs(context).edit().putString("density", value.density)
            .putString("quality", value.quality).putStringSet("fields", value.fields)
            .putBoolean("motion", value.motion).putBoolean("haptics", value.haptics)
            .putBoolean("ghost", value.ghostCourses).apply()
    }
}

val LocalWedoDisplay = staticCompositionLocalOf { WedoDisplay() }
val LocalWedoCollapsed = staticCompositionLocalOf { false }

@Composable
fun WedoDisplayProvider(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { WedoPreferences.prefs(context) }
    var display by remember(prefs) { mutableStateOf(WedoPreferences.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            display = WedoPreferences.read(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CompositionLocalProvider(LocalWedoDisplay provides display, content = content)
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
