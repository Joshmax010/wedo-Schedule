package com.wedo.schedule.ui.glass

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.wedo.schedule.R

/**
 * L2 RuntimeShader 封装（P4）。
 *
 * 采用 `createRuntimeShaderEffect`，让 GraphicsLayer 自身作为 `uContent`
 * 输入：RuntimeShaderEffect 直接把 GraphicsLayer 作为 shader 输入，因此可安全用于
 * 底栏静止 Bake，也不会把位图分配引入滚动路径（Q-52）。
 *
 * 加载失败会永久标记 `_loadFailed`：资源缺失 / AGSL 编译失败在同一进程内
 * 不会自愈，重复尝试只会卡 UI（O-58 / O-59）。
 */
internal object GlassShaderRenderer {
    /**
     * L2 折射总开关。
     *
     * **2026-10-28 真机 A/B 后默认关闭。**
     *
     * 同场景 L1（纯模糊）与 L2（模糊 + 折射）并排截图（`evidence/baseline/p6_l1_vs_l2_tabbar.png`）
     * 肉眼几乎看不出差别：底栏只有 52dp 高，边缘带 8px，折射强度 3px，
     * 差异全部落在 8px 边缘带内 —— 这正是方案卷 04 §4.10.4 预判的第三种失败
     * （VR-12「看不出差别」）。
     *
     * 按方案 P4 失败处理第 ③ 条：L2 **默认永久关闭，只留手动开关**。
     * 理由很直接：AGSL 每帧一次 pass 的成本，换不到肉眼可见的收益。
     * 代码、shader、uniform 契约全部保留，改一个常量即可重新开启。
     */
    const val L2_REFRACTION_ENABLED = false

    @Volatile
    private var cachedShader: RuntimeShader? = null

    @Volatile
    private var loadFailed = false

    fun loadShader(context: Context): RuntimeShader? {
        if (loadFailed) return null
        cachedShader?.let { return it }
        return try {
            val source = context.resources
                .openRawResource(R.raw.liquid_glass)
                .use { input -> input.readBytes().decodeToString() }
            RuntimeShader(source).also {
                cachedShader = it
            }
        } catch (t: Throwable) {
            loadFailed = true
            GlassDebug.onFailure("shaderLoadFailed: ${t.message}")
            android.util.Log.w("GlassShader", "AGSL load failed", t)
            null
        }
    }

    fun refractionEffect(
        context: Context,
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        refractionPx: Float = GlassUniforms.DEFAULT_REFRACTION_PX,
        bevelPx: Float = GlassUniforms.DEFAULT_BEVEL_PX,
        dispersionPx: Float = GlassUniforms.DEFAULT_DISPERSION_PX,
        blurRadiusX: Float = 12f,
        blurRadiusY: Float = 12f,
    ): androidx.compose.ui.graphics.RenderEffect? {
        if (!L2_REFRACTION_ENABLED) return null
        val shader = loadShader(context) ?: return null
        return try {
            shader.setFloatUniform("uSize", widthPx, heightPx)
            shader.setFloatUniform("uCornerRadius", cornerRadiusPx)
            shader.setFloatUniform("uRefraction", refractionPx)
            shader.setFloatUniform("uBevel", bevelPx)
            shader.setFloatUniform("uDispersion", dispersionPx)
            shader.setFloatUniform("uTintAlpha", GlassUniforms.DEFAULT_TINT_ALPHA)
            shader.setFloatUniform("uRimAlpha", GlassUniforms.DEFAULT_RIM_ALPHA)
            shader.setFloatUniform("uHairlineAlpha", GlassUniforms.DEFAULT_HAIRLINE_ALPHA)
            val refraction = RenderEffect.createRuntimeShaderEffect(
                shader,
                GlassUniforms.INPUT_CONTENT,
            )
            // L2 = L1 模糊 + L2 折射，**不是替换**。
            // 链式 effect：内层做折射采样，外层再做模糊。
            // 若只保留折射，采样内容是锐利的，高对比图标会被拉成弧线伪影。
            val blurred = RenderEffect.createBlurEffect(
                blurRadiusX,
                blurRadiusY,
                android.graphics.Shader.TileMode.CLAMP,
            )
            RenderEffect
                .createChainEffect(blurred, refraction)
                .asComposeRenderEffect()
                .also {
                    android.util.Log.d(
                        "GlassShader",
                        "L2 effect active size=${widthPx}x${heightPx} corner=$cornerRadiusPx",
                    )
                }
        } catch (t: Throwable) {
            GlassDebug.onFailure("shaderEffectFailed: ${t.message}")
            android.util.Log.w("GlassShader", "AGSL effect failed", t)
            null
        }
    }
}
