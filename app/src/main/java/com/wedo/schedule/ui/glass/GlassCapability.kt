package com.wedo.schedule.ui.glass

import com.wedo.schedule.ui.theme.WedoGlassQuality

/**
 * 玻璃能力判定 — 纯函数，不读 Compose Local，供 JVM 测试直接覆盖。
 *
 * `minSdk` 已提升到 33，物料层只需要 API 33+ 的能力；保留纯函数是为了
 * 未来在低内存/运行时 shader 失败时仍能统一降级，而不是散落 `if`。
 */
object GlassCapability {

    const val MIN_GLASS_API = 33

    fun supportsGlass(sdkInt: Int): Boolean = sdkInt >= MIN_GLASS_API

    /**
     * 用户所选画质在设备上的实际落档。
     *
     * - 低于 API 33：不进入材质路径（Smooth）
     * - 低内存设备：强制最轻的 Smooth，但保留用户设置值不写回
     */
    fun resolveQualityFor(
        requested: WedoGlassQuality,
        sdkInt: Int,
        lowRam: Boolean,
    ): WedoGlassQuality = when {
        !supportsGlass(sdkInt) -> WedoGlassQuality.Smooth
        lowRam -> WedoGlassQuality.Smooth
        else -> requested
    }
}
