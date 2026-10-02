package com.wedo.schedule.widget

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manifest 契约测试（REQ-P5-02 / REQ-P5-03）：
 *
 *  - 存活的 4 个小组件 receiver 必须 `android:enabled="true"`（否则系统小组件列表里不可见）；
 *  - 课前提醒 3 个 receiver（Boot / BeforeClassSchedule / BeforeClassNotify）必须启用；
 *  - `WidgetConfigureActivity` 必须启用（4 个 `*_widget_info.xml` 的 `android:configure` 目标）；
 *  - 已删除的 3 种小组件 receiver（WeekList / WeekView / TwoDay × base/small）、
 *    `DailyNotifyReceiver` 与两个 service（FluidCloudService / ScrollStripService）不得残留声明。
 *
 * 仓库无 Robolectric，组件可达性靠「读 Manifest 源码 + 断言」守住。
 */
class WidgetEnabledManifestContractTest {

    private val manifest = TestProjectFiles.read("app/src/main/AndroidManifest.xml")

    /** 返回 `<receiver ... android:name="name" ...>` 开标签文本（跨行属性一并纳入）。 */
    private fun receiverOpenTag(name: String): String? =
        Regex("<receiver[^>]*android:name=\"${Regex.escape(name)}\"[^>]*>")
            .find(manifest)?.value

    private fun assertReceiverEnabled(name: String) {
        val tag = receiverOpenTag(name)
        assertTrue("Manifest 缺少 receiver 声明: $name", tag != null)
        assertTrue("receiver $name 必须 enabled=\"true\"", tag!!.contains("android:enabled=\"true\""))
    }

    @Test
    fun `surviving widget receivers are enabled`() {
        assertReceiverEnabled(".widget.TodayWidgetReceiver")
        assertReceiverEnabled(".widget.TodaySmallWidgetReceiver")
        assertReceiverEnabled(".widget.WeekGridWidgetProvider")
        assertReceiverEnabled(".widget.WeekGridSmallWidgetProvider")
    }

    @Test
    fun `before-class reminder receivers are enabled`() {
        assertReceiverEnabled(".widget.notification.BootReceiver")
        assertReceiverEnabled(".widget.notification.BeforeClassScheduleReceiver")
        assertReceiverEnabled(".widget.notification.BeforeClassNotifyReceiver")
    }

    @Test
    fun `widget configure activity is enabled`() {
        val tag = Regex("<activity[^>]*android:name=\"\\.widget\\.WidgetConfigureActivity\"[^>]*>")
            .find(manifest)?.value
        assertTrue("Manifest 缺少 WidgetConfigureActivity", tag != null)
        assertTrue("WidgetConfigureActivity 必须 enabled=\"true\"", tag!!.contains("android:enabled=\"true\""))
    }

    @Test
    fun `deleted receivers and services are absent`() {
        listOf(
            ".widget.WeekListWidgetReceiver",
            ".widget.WeekListSmallWidgetReceiver",
            ".widget.WeekViewWidgetReceiver",
            ".widget.WeekViewSmallWidgetReceiver",
            ".widget.TwoDayWidgetReceiver",
            ".widget.TwoDaySmallWidgetReceiver",
            ".widget.notification.DailyNotifyReceiver"
        ).forEach { name ->
            assertFalse("Manifest 不得残留已删 receiver: $name", manifest.contains(name))
        }

        assertFalse("FluidCloudService 声明应删除", manifest.contains("FluidCloudService"))
        assertFalse("ScrollStripService 声明应删除", manifest.contains("ScrollStripService"))
    }
}
