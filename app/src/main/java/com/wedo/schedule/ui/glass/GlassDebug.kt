package com.wedo.schedule.ui.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 采样观测。只在 debug 构建写入，release 只保留零开销字段读取。
 *
 * `toImageBitmapCount` 是硬闸门：交互路径禁止离屏转位图。
 */
internal object GlassDebug {
    val enabled: Boolean = com.wedo.schedule.BuildConfig.DEBUG

    var captureCount by mutableIntStateOf(0)
        private set

    var drawCount by mutableIntStateOf(0)
        private set

    var toImageBitmapCount by mutableIntStateOf(0)
        private set

    var lastCaptureAtNanos by mutableLongStateOf(0L)
        private set

    var lastDrawAtNanos by mutableLongStateOf(0L)
        private set

    var lastFailure: String? by mutableStateOf(null)
        private set

    fun onCapture() {
        if (!enabled) return
        captureCount++
        lastCaptureAtNanos = System.nanoTime()
    }

    fun onDraw() {
        if (!enabled) return
        drawCount++
        lastDrawAtNanos = System.nanoTime()
    }

    fun onToImageBitmap() {
        if (!enabled) return
        toImageBitmapCount++
    }

    fun onFailure(message: String) {
        if (!enabled) return
        lastFailure = message
    }

    fun reset() {
        if (!enabled) return
        captureCount = 0
        drawCount = 0
        toImageBitmapCount = 0
        lastCaptureAtNanos = 0L
        lastDrawAtNanos = 0L
        lastFailure = null
    }
}
