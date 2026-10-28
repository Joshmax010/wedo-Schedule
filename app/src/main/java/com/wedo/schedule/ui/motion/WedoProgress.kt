package com.wedo.schedule.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.wedo.schedule.ui.theme.LocalWedoDisplay

/**
 * 单进度驱动（M-20..M-23）。
 *
 * 多个视觉量只从同一个 0..1 进度派生，禁止各用一根弹簧。减动效时进度直接跳终值。
 */
class WedoProgress internal constructor(initial: Float) {
    private val animatable = Animatable(initial.coerceIn(0f, 1f))

    val value: Float
        get() = animatable.value

    suspend fun snapTo(value: Float) {
        animatable.snapTo(value.coerceIn(0f, 1f))
    }

    suspend fun animateTo(value: Float) {
        animatable.animateTo(value.coerceIn(0f, 1f), WedoSprings.settle())
    }
}

@Composable
fun rememberWedoProgress(target: Float): Float {
    val motion = LocalWedoDisplay.current.motion
    val progress = remember { WedoProgress(target) }
    LaunchedEffect(target, motion) {
        if (motion) {
            progress.animateTo(target)
        } else {
            progress.snapTo(target)
        }
    }
    return progress.value
}
