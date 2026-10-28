package com.wedo.schedule.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ensures data-change broadcasts cover every surviving regular and small widget provider.
 *
 * REQ-P5-01：小组件从 5 种（10 变体）收敛为 **2 种（4 变体）**，广播列表随之收缩。
 */
class WidgetUpdaterWiringTest {
    @Test
    fun `refresh receiver list contains all widget variants`() {
        val receivers = WidgetUpdater.remoteViewsReceiverClasses
        val expected = listOf(
            TodayWidgetReceiver::class.java,
            TodaySmallWidgetReceiver::class.java,
            WeekGridWidgetProvider::class.java,
            WeekGridSmallWidgetProvider::class.java
        )

        assertEquals("4 widget providers are registered", 4, receivers.size)
        expected.forEach { receiver ->
            assertTrue("missing ${receiver.simpleName}", receiver in receivers)
        }
    }
}
