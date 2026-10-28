package com.wedo.schedule.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * 材质风格表（方案 B · 干净克制）。
 *
 * 三档透明度的分配（用户定稿）：
 *  - **底栏**：通透 —— 底下的内容看得见，但被降饱和，不会像脏点
 *  - **G1 面板**：半透 —— 透出轮廓但不透出细节
 *  - **对话框**：不透 —— 已是实心卡片，靠 scrim 与圆角表达浮起
 *
 * **vibrancy 是什么**：iOS 的半透明栏会让身后的内容自动降饱和（材质层面的
 * vibrancy），所以图标透过去是灰蓝而不是刺眼的纯蓝。本项目用
 * `ColorFilter.colorMatrix` 做同一件事，**不需要 AGSL**。
 */
internal data class GlassStyle(
    val tintColor: Color,
    val tintAlpha: Float,
    val blurRadius: Float,
    /** 采样内容饱和度：1 = 原色，0 = 全灰。 */
    val vibrancy: Float,
    val rimAlpha: Float,
    val hairlineColor: Color,
    val hairlineAlpha: Float,
)

internal object GlassStyles {
    /** 深色档的玻璃面用卡片色，不用纯黑：纯黑会与 OLED 背景糊在一起。 */
    private val DARK_TINT = Color(0xFF1C1C1E)

    /**
     * 底栏：通透 + 强降饱和 + 细暗描边。
     *
     * `blurRadius` 实际被 Android 夹在 25（单次 `RenderEffect.createBlurEffect`
     * 的半径上限），链式多遍也不叠加 —— 实测 26 与 40 观感几乎相同。
     * 因此「不刺眼」这件事只能靠 **tint 提高 + vibrancy 压低** 来做：
     * 底下的彩色图标透过来是灰调、边缘被白色面层吃掉，读起来才是玻璃而不是污渍。
     */
    fun floatingBar(dark: Boolean) = GlassStyle(
        tintColor = if (dark) DARK_TINT else Color.White,
        tintAlpha = if (dark) 0.70f else 0.52f,
        blurRadius = 25f,
        vibrancy = 0.18f,
        rimAlpha = 0f,
        hairlineColor = if (dark) Color.White else Color.Black,
        hairlineAlpha = if (dark) 0.16f else 0.09f,
    )

    /** G1 面板：半透。透出轮廓但不透出细节。 */
    fun panel(dark: Boolean) = GlassStyle(
        tintColor = if (dark) DARK_TINT else Color.White,
        tintAlpha = if (dark) 0.82f else 0.78f,
        blurRadius = 25f,
        vibrancy = 0.40f,
        rimAlpha = 0f,
        hairlineColor = if (dark) Color.White else Color.Black,
        hairlineAlpha = if (dark) 0.14f else 0.08f,
    )
}

/**
 * 采样内容的 vibrancy 滤镜。
 *
 * 只作用于**采样层**（面板背后那段内容），不影响面板自己的图标与文字 ——
 * 这正是 iOS 的行为：内容变灰，控件本身仍是原色。
 */
internal object GlassVibrancy {
    private val cache = HashMap<Float, ColorFilter>()

    fun filter(saturation: Float): ColorFilter? {
        val s = saturation.coerceIn(0f, 1f)
        if (s >= 0.999f) return null
        return cache.getOrPut(s) {
            ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(s) },
            )
        }
    }
}
