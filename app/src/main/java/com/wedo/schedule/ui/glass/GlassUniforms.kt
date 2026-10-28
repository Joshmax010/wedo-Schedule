package com.wedo.schedule.ui.glass

/**
 * AGSL uniform 契约（O-44..O-47）。
 *
 * 顺序唯一源。`liquid_glass.agsl` 的头部注释必须与此数组逐行一致；
 * `GlassShaderUniformContractTest` 会同时锁 Kotlin 侧与 AGSL 注释块。
 */
internal object GlassUniforms {
    const val INPUT_CONTENT = "uContent"

    val entries: List<Pair<String, String>> = listOf(
        "uSize" to "float2",
        "uCornerRadius" to "float",
        "uRefraction" to "float",
        "uBevel" to "float",
        "uDispersion" to "float",
        "uTintAlpha" to "float",
        "uRimAlpha" to "float",
        "uHairlineAlpha" to "float",
    )

    /**
     * 折射强度（px）。
     *
     * 2026-10-28 真机下调到 3.0：6.0 会把底栏下方的高饱和图标（如隐私蓝色盾牌）
     * 放大成一块明显色斑，读起来像 bug 而不像玻璃。3.0 只让边缘「微微弯」。
     */
    const val DEFAULT_REFRACTION_PX = 3.0f

    /** 边缘带宽度（px）；太宽会把非边缘内容也卷进来。 */
    const val DEFAULT_BEVEL_PX = 8.0f

    /** RGB 色散强度（px）；过大会在边缘产生彩色噪点。 */
    const val DEFAULT_DISPERSION_PX = 0.8f
    const val DEFAULT_TINT_ALPHA = 0f
    const val DEFAULT_RIM_ALPHA = 0.12f
    const val DEFAULT_HAIRLINE_ALPHA = 0.06f
}
