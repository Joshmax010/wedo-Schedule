package com.wedo.schedule.ui.glass

import com.wedo.schedule.ui.theme.WedoGlassQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassCapabilityTest {

    @Test
    fun `glass requires api 33`() {
        assertFalse(GlassCapability.supportsGlass(32))
        assertTrue(GlassCapability.supportsGlass(33))
        assertTrue(GlassCapability.supportsGlass(35))
    }

    @Test
    fun `below api 33 falls back to smooth`() {
        assertEquals(
            WedoGlassQuality.Smooth,
            GlassCapability.resolveQualityFor(WedoGlassQuality.Fine, sdkInt = 32, lowRam = false),
        )
    }

    @Test
    fun `low ram forces smooth but keeps requested value outside`() {
        assertEquals(
            WedoGlassQuality.Smooth,
            GlassCapability.resolveQualityFor(WedoGlassQuality.Fine, sdkInt = 35, lowRam = true),
        )
    }

    @Test
    fun `api 33 non low ram keeps requested quality`() {
        assertEquals(
            WedoGlassQuality.Fine,
            GlassCapability.resolveQualityFor(WedoGlassQuality.Fine, sdkInt = 33, lowRam = false),
        )
        assertEquals(
            WedoGlassQuality.Balanced,
            GlassCapability.resolveQualityFor(WedoGlassQuality.Balanced, sdkInt = 35, lowRam = false),
        )
    }
}
