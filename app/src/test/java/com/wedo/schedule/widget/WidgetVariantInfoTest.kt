package com.wedo.schedule.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [WidgetVariantInfo] — shared metadata describing the
 * 4 widget variants (2 kinds × base/small). The list is the single source of
 * truth for both the refresh broadcast in [WidgetUpdater] and the widget
 * config UI; this test pins that contract.
 *
 * REQ-P5-01：V3 重设计把小组件从 5 种（10 变体）收敛为 **2 种（4 变体）**：
 * Today / WeekGrid，各含 base + small。
 */
class WidgetVariantInfoTest {

    @Test
    fun `ALL_WIDGET_VARIANTS has exactly 4 entries`() {
        assertEquals(4, ALL_WIDGET_VARIANTS.size)
    }

    @Test
    fun `every variant has a unique receiver class`() {
        val classes = ALL_WIDGET_VARIANTS.map { it.receiverClass }
        assertEquals("duplicate receiver class in metadata", classes.size, classes.toSet().size)
    }

    @Test
    fun `all entries carry a positive name resource id`() {
        ALL_WIDGET_VARIANTS.forEach { v ->
            assertTrue("displayNameRes must be non-zero for ${v.receiverClass.simpleName}", v.displayNameRes != 0)
        }
    }

    @Test
    fun `WidgetUpdater receiver list matches ALL_WIDGET_VARIANTS`() {
        // The refresh broadcast must not silently drop or duplicate a variant;
        // it must mirror the metadata list 1:1.
        val infoClasses = ALL_WIDGET_VARIANTS.map { it.receiverClass }.toSet()
        val updaterClasses = WidgetUpdater.remoteViewsReceiverClasses.toSet()
        assertEquals(infoClasses, updaterClasses)
    }

    @Test
    fun `metadata includes both base and small variant for each kind`() {
        // Two surviving widget "kinds": Today / WeekGrid, each with base + small.
        // WeekGrid uses its own provider class hierarchy (open class + subclass)
        // so we accept either WeekGridWidgetProvider or WeekGridSmallWidgetProvider
        // as the "small" form — both must be present.
        val byKind = ALL_WIDGET_VARIANTS.map { v ->
            v.receiverClass.simpleName.removeSuffix("SmallWidgetProvider")
                .removeSuffix("SmallWidgetReceiver")
                .removeSuffix("WidgetProvider")
                .removeSuffix("WidgetReceiver")
        }
        val counts = byKind.groupingBy { it }.eachCount()
        counts.forEach { (kind, count) ->
            assertEquals(
                "kind=$kind must appear exactly twice (base + small)",
                2, count
            )
        }
        // And specifically: the 2 base + 2 small must be present.
        assertNotEquals(0, byKind.count { it == "Today" })
        assertNotEquals(0, byKind.count { it == "WeekGrid" })
        // The three deleted kinds must be gone.
        assertEquals(0, byKind.count { it == "WeekList" })
        assertEquals(0, byKind.count { it == "WeekView" })
        assertEquals(0, byKind.count { it == "TwoDay" })
    }
}
