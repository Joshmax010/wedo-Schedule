package com.wedo.schedule.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2 收尾契约：观测只在 debug 写入，交互路径不得使用 toImageBitmap。
 */
class GlassDebugTest {

    @Test
    fun `debug counters track capture draw and forbidden bitmap calls`() {
        val enabledBefore = GlassDebug.enabled
        GlassDebug.reset()
        try {
            GlassDebug.onCapture()
            GlassDebug.onDraw()
            GlassDebug.onToImageBitmap()

            if (enabledBefore) {
                assertEquals(1, GlassDebug.captureCount)
                assertEquals(1, GlassDebug.drawCount)
                assertEquals(1, GlassDebug.toImageBitmapCount)
                assertTrue(GlassDebug.lastCaptureAtNanos > 0)
                assertTrue(GlassDebug.lastDrawAtNanos > 0)
                assertNull(GlassDebug.lastFailure)
            }
        } finally {
            GlassDebug.reset()
        }
    }
}
