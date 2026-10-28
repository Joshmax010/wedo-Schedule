package com.wedo.schedule.util

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统无障碍偏好。
 *
 * 动效门控是「App 用户开关 && 系统动画倍率 > 0」，不是取其一。
 * 高对比度先在这里收口，供后续材质配额与 rim 强化使用。
 */
data class SystemAccessibilityState(
    val animationsEnabled: Boolean = true,
    val highTextContrast: Boolean = false,
)

object SystemAccessibility {
    /**
     * Android 设置里「高对比度文字」的键。部分 ROM 不公开常量，故用稳定的
     * Settings.Secure 字面键，避免依赖厂商 Framework 实现。
     */
    internal const val KEY_HIGH_TEXT_CONTRAST = "high_text_contrast_enabled"

    private val _state = MutableStateFlow(SystemAccessibilityState())
    val state: StateFlow<SystemAccessibilityState> = _state.asStateFlow()

    @Volatile
    private var observer: ContentObserver? = null

    /** 幂等启动 ContentObserver；进程存活期间只需注册一次。 */
    @Synchronized
    fun start(context: Context) {
        if (observer != null) return
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        _state.value = read(appContext)

        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                _state.value = read(appContext)
            }
        }
        observer = contentObserver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            contentObserver,
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(KEY_HIGH_TEXT_CONTRAST),
            false,
            contentObserver,
        )
    }

    internal fun read(context: Context): SystemAccessibilityState {
        val resolver = context.contentResolver
        val scale = runCatching {
            Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(0f)
        val highContrast = runCatching {
            Settings.Secure.getInt(resolver, KEY_HIGH_TEXT_CONTRAST, 0)
        }.getOrDefault(0)
        return SystemAccessibilityState(
            animationsEnabled = animatorScaleAllowsMotion(scale),
            highTextContrast = highContrastEnabled(highContrast),
        )
    }

    internal fun animatorScaleAllowsMotion(scale: Float?): Boolean =
        scale != null && scale > 0f

    internal fun highContrastEnabled(value: Int): Boolean = value == 1
}
