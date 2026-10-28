package com.wedo.schedule.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * 宽模糊。
 *
 * `RenderEffect.createBlurEffect` 的单次半径上限是 25（超出被夹断），所以想要
 * 「更柔」不能只把半径调大 —— 实测 26 与 40 的观感几乎一样。
 *
 * 本实现保留链式多遍的能力（各遍独立实例，避免 Skia 把同一实例折叠成一遍），
 * 但**当前设备的实测结论是链式不叠加**；因此风格表里的目标半径一律不超过 25，
 * 「不刺眼」靠 tint 与 vibrancy 解决，而不是靠模糊半径。
 */
internal object GlassBlur {
    /** 单遍半径上限。 */
    private const val MAX_PASS_RADIUS = 25f

    private const val MAX_PASSES = 4

    fun chained(targetRadius: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (targetRadius <= 0f) return null
        val passes = kotlin.math.ceil(targetRadius / MAX_PASS_RADIUS).toInt().coerceIn(1, MAX_PASSES)
        val radius = targetRadius / passes
        // 每遍用**独立实例**串联：传同一个实例两次时 Skia 只生效一遍，
        // 实测「2 遍 20」与「1 遍 20」观感相同，白做。
        var effect: RenderEffect = newBlur(radius)
        for (i in 1 until passes) {
            effect = RenderEffect.createChainEffect(newBlur(radius), effect)
        }
        return effect.asComposeRenderEffect()
    }

    private fun newBlur(radius: Float): RenderEffect =
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
}
