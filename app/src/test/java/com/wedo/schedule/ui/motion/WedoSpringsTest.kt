package com.wedo.schedule.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3 弹簧硬规则（M-R2）：跟手与淡变禁 bounce；形变弹簧只允许低阻尼。
 */
class WedoSpringsTest {

    @Test
    fun `track is critical damping so gestures cannot overshoot`() {
        val spec = WedoSprings.track<Float>()
        assertEquals(Spring.DampingRatioNoBouncy, spec.dampingRatio, 0.0001f)
        assertEquals(Spring.StiffnessHigh.toFloat(), spec.stiffness, 0.0001f)
    }

    @Test
    fun `press is the single L1 scale spring`() {
        val spec = WedoSprings.press<Float>()
        assertEquals(0.72f, spec.dampingRatio, 0.0001f)
        assertEquals(520f, spec.stiffness, 0.0001f)
    }

    @Test
    fun `jelly keeps visible overshoot for scale only`() {
        val spec = WedoSprings.jelly<Float>()
        assertTrue("形变必须欠阻尼", spec.dampingRatio < 0.6f)
        assertTrue("形变收敛应在 1s 内", spec.stiffness >= 120f)
    }
}
