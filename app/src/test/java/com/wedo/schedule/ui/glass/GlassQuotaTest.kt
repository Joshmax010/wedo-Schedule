package com.wedo.schedule.ui.glass

import com.wedo.schedule.ui.theme.WedoGlassQuotaGrade
import com.wedo.schedule.ui.theme.WedoGlassSurfaceClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P2 收尾契约：配额仲裁是纯函数，超配额必须静默降 G0。
 */
class GlassQuotaTest {

    @Test
    fun `content and base always resolve to g0`() {
        assertEquals(
            WedoGlassQuotaGrade.G0,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Content,
                requested = WedoGlassQuotaGrade.G2,
                activeG1 = 0,
                activeG2 = 0,
            ),
        )
        assertEquals(
            WedoGlassQuotaGrade.G0,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Base,
                requested = WedoGlassQuotaGrade.G1,
                activeG1 = 0,
                activeG2 = 0,
            ),
        )
    }

    @Test
    fun `float accepts one g2 and one g1 within quota`() {
        assertEquals(
            WedoGlassQuotaGrade.G2,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Float,
                requested = WedoGlassQuotaGrade.G2,
                activeG1 = 0,
                activeG2 = GlassQuota.MAX_G2 - 1,
            ),
        )
        assertEquals(
            WedoGlassQuotaGrade.G1,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Float,
                requested = WedoGlassQuotaGrade.G1,
                activeG1 = GlassQuota.MAX_G1 - 1,
                activeG2 = 0,
            ),
        )
    }

    @Test
    fun `float over quota silently falls back to g0`() {
        assertEquals(
            WedoGlassQuotaGrade.G0,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Float,
                requested = WedoGlassQuotaGrade.G2,
                activeG1 = 0,
                activeG2 = GlassQuota.MAX_G2,
            ),
        )
        assertEquals(
            WedoGlassQuotaGrade.G0,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Float,
                requested = WedoGlassQuotaGrade.G1,
                activeG1 = GlassQuota.MAX_G1,
                activeG2 = 0,
            ),
        )
    }
}
