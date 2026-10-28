package com.wedo.schedule.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.wedo.schedule.ui.theme.WedoPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户可调的三个材质偏好。
 *
 * 只承载「用户选择」，不叠加系统能力门控；最终动效是否可用由
 * [com.wedo.schedule.util.SystemAccessibility] 与这里做「与」运算。
 */
data class WedoSettingsState(
    val quality: String = QUALITY_BALANCED,
    val motion: Boolean = true,
    val haptics: Boolean = true,
) {
    companion object {
        const val QUALITY_SMOOTH = "smooth"
        const val QUALITY_BALANCED = "balanced"
        const val QUALITY_FINE = "fine"
    }
}

/**
 * 材质偏好的唯一读写入口。
 *
 * 存储仍沿用历史文件 `wedo_display` 与既有键名，避免破坏老用户设置；
 * 只把散落的读写收口，不做数据迁移。
 */
object WedoSettingsStore {
    private const val KEY_QUALITY = "quality"
    private const val KEY_MOTION = "motion"
    private const val KEY_HAPTICS = "haptics"

    private val _state = MutableStateFlow(WedoSettingsState())
    val state: StateFlow<WedoSettingsState> = _state.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { shared, _ ->
            _state.value = read(shared)
        }

    /** 幂等初始化；重复调用不会叠加监听器。 */
    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val shared = prefs(context)
        prefs = shared
        _state.value = read(shared)
        shared.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setQuality(context: Context, quality: String) {
        val safe = normalizeQuality(quality)
        prefs(context).edit().putString(KEY_QUALITY, safe).apply()
        _state.value = _state.value.copy(quality = safe)
    }

    fun setMotion(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MOTION, enabled).apply()
        _state.value = _state.value.copy(motion = enabled)
    }

    fun setHaptics(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _state.value = _state.value.copy(haptics = enabled)
    }

    internal fun read(shared: SharedPreferences): WedoSettingsState =
        WedoSettingsState(
            quality = normalizeQuality(shared.getString(KEY_QUALITY, null).orEmpty()),
            motion = shared.getBoolean(KEY_MOTION, true),
            haptics = shared.getBoolean(KEY_HAPTICS, true),
        )

    internal fun normalizeQuality(raw: String): String = when (raw) {
        WedoSettingsState.QUALITY_SMOOTH -> WedoSettingsState.QUALITY_SMOOTH
        WedoSettingsState.QUALITY_FINE -> WedoSettingsState.QUALITY_FINE
        else -> WedoSettingsState.QUALITY_BALANCED
    }

    private fun prefs(context: Context): SharedPreferences =
        WedoPreferences.prefs(context.applicationContext)
}
