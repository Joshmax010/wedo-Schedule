package com.wedo.schedule.ui.glass

import com.wedo.schedule.ui.theme.WedoGlassQuotaGrade
import com.wedo.schedule.ui.theme.WedoGlassSurfaceClass

/**
 * 同屏采样配额（O-15a / O-15b）。
 *
 * 三道闸门：
 *  1. **语义闸**：BASE / CONTENT 恒 G0，内容卡不得采样。
 *  2. **数量闸**：G2 ≤ 3、G1 ≤ 2（总量 ≤ 5，留 1 个余量给开合过渡期）。
 *  3. **面积闸**：单个包围盒 ≤ 屏面积 15%；超出的直接降 G0。
 *
 * 数量闸冲突时按**包围盒面积倒序**保留大面板、小面板降级（O-15b）：
 * 已经在场的面板面积都大于新面板时，新面板拿不到采样额度。
 *
 * 该对象只做纯逻辑仲裁，不持有状态；运行时计数由 [GlassStage] 提供。
 */
internal object GlassQuota {
    const val MAX_G1 = 2
    const val MAX_G2 = 3

    /** 单个采样面板的包围盒面积上限：屏面积的 15%。 */
    const val MAX_PANEL_AREA_RATIO = 0.15f

    /**
     * 解析一个面板的生效档。
     *
     * @param areaPx 面板包围盒面积（像素²）；未知时传 0（视为不触面积闸）
     * @param screenAreaPx 屏幕面积（像素²）；未知时传 0（视为不触面积闸）
     * @param smallestActiveAreaPx 已在场的采样面板中最小包围盒面积；用于面积倒序仲裁
     */
    fun resolve(
        surface: WedoGlassSurfaceClass,
        requested: WedoGlassQuotaGrade,
        activeG1: Int,
        activeG2: Int,
        areaPx: Long = 0L,
        screenAreaPx: Long = 0L,
        smallestActiveAreaPx: Long = Long.MAX_VALUE,
    ): WedoGlassQuotaGrade {
        if (surface != WedoGlassSurfaceClass.Float) return WedoGlassQuotaGrade.G0

        // 面积闸：单个面板超过屏面积 15% 一律不采样。
        if (areaPx > 0L && screenAreaPx > 0L) {
            if (areaPx.toDouble() > screenAreaPx * MAX_PANEL_AREA_RATIO) {
                return WedoGlassQuotaGrade.G0
            }
        }

        return when (requested) {
            WedoGlassQuotaGrade.G2 -> {
                val countOk = activeG2 < MAX_G2
                // 面积倒序：场上已有更小的面板时，新面板让位。
                val areaOk = activeG2 == 0 || areaPx == 0L || areaPx >= smallestActiveAreaPx
                if (countOk && areaOk) WedoGlassQuotaGrade.G2 else WedoGlassQuotaGrade.G0
            }
            WedoGlassQuotaGrade.G1 -> {
                val countOk = activeG1 < MAX_G1
                val areaOk = activeG1 == 0 || areaPx == 0L || areaPx >= smallestActiveAreaPx
                if (countOk && areaOk) WedoGlassQuotaGrade.G1 else WedoGlassQuotaGrade.G0
            }
            WedoGlassQuotaGrade.G0 -> WedoGlassQuotaGrade.G0
        }
    }
}
