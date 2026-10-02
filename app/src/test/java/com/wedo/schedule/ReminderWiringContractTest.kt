package com.wedo.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5「上课前提醒」接线契约（REQ-P5-03 / REQ-P5-04 / REQ-P4-04）。
 *
 * 无真机 / 模拟器 / Robolectric：提醒的启用、权限申请、通知投递、以及对已删
 * 「每日提醒 / 流体云」的清理，只能靠「读源码 + 断言」守住。
 *
 * 断言前**剥掉注释** —— 源码 KDoc 里保留了大量 `FluidCloudService` /
 * `DailyNotifyReceiver` 的「已删除」说明文字，不剥注释会命中说明文字而误判。
 */
class ReminderWiringContractTest {

    private val manifest = TestProjectFiles.read("app/src/main/AndroidManifest.xml")
    private val schedulerRaw = TestProjectFiles.read(SCHEDULER)
    private val prefsSrc = TestProjectFiles.read(PREFS)
    private val reminderSrc = TestProjectFiles.read(REMINDER_SCREEN)
    private val settingsSrc = TestProjectFiles.read(SETTINGS_SCREEN)

    /** 剥掉整行注释 + 行尾注释，避免断言命中说明文字。 */
    private fun code(src: String): String = src.lineSequence()
        .map { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) ""
            else line.substringBefore("//")
        }
        .joinToString("\n")

    private val scheduler = code(schedulerRaw)

    @Test
    fun `manifest declares notification and boot permissions`() {
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
    }

    @Test
    fun `before-class receivers are declared and enabled`() {
        listOf(
            ".widget.notification.BootReceiver",
            ".widget.notification.BeforeClassScheduleReceiver",
            ".widget.notification.BeforeClassNotifyReceiver"
        ).forEach { name ->
            val tag = Regex("<receiver[^>]*android:name=\"${Regex.escape(name)}\"[^>]*>")
                .find(manifest)?.value
            assertTrue("Manifest 缺少 $name", tag != null)
            assertTrue("$name 必须 enabled=\"true\"", tag!!.contains("android:enabled=\"true\""))
        }
    }

    @Test
    fun `before-class notify receiver actually posts the notification`() {
        assertTrue(
            "课前通知须真正投递（notifyManager.notify）",
            scheduler.contains("NotificationManagerCompat.from(context).notify(")
        )
        assertTrue("通知 id 须按课程稳定唯一", scheduler.contains("NOTIFY_BEFORE_CLASS_BASE"))
        assertTrue("须传入预授权渠道 CHANNEL_BEFORE_CLASS", scheduler.contains("CHANNEL_BEFORE_CLASS"))
    }

    @Test
    fun `scheduler has no fluid or daily reminder leftovers`() {
        listOf("CHANNEL_FLUID", "CHANNEL_DAILY", "FluidCloudService", "DailyNotifyReceiver", "ensureActiveFluidCloud")
            .forEach { token ->
                assertFalse("调度器不得残留 $token", scheduler.contains(token))
            }
    }

    @Test
    fun `reminder screen dropped the fluid UI`() {
        val src = code(reminderSrc)
        listOf("流体云", "isBeforeClassFluidEnabled", "reminder_fluid", "FluidCloudService")
            .forEach { token ->
                assertFalse("提醒页不得残留流体云 UI: $token", src.contains(token))
            }
    }

    @Test
    fun `notification permission requested on android 13 plus`() {
        // 提醒页 or 设置页至少一处申请 POST_NOTIFICATIONS（Android 13+）。
        val combined = code(reminderSrc) + code(settingsSrc)
        assertTrue(combined.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(combined.contains("TIRAMISU"))
    }

    @Test
    fun `appprefs keeps before-class controls and drops daily fluid keys`() {
        assertTrue(prefsSrc.contains("fun isBeforeClassEnabled("))
        assertTrue(prefsSrc.contains("fun setBeforeClassEnabled("))
        assertTrue(prefsSrc.contains("fun getBeforeClassMinutes("))
        assertTrue(prefsSrc.contains("fun isBeforeClassBannerEnabled("))
        assertFalse("每日提醒键应删除", prefsSrc.contains("isDailyReminderEnabled"))
        assertFalse("流体云键应删除", prefsSrc.contains("isBeforeClassFluidEnabled"))
        assertFalse("流体云字段键应删除", prefsSrc.contains("getBeforeClassFluidFields"))
    }

    private companion object {
        const val SCHEDULER =
            "app/src/main/java/com/wedo/schedule/widget/notification/CourseNotificationScheduler.kt"
        const val PREFS = "app/src/main/java/com/wedo/schedule/util/AppPrefs.kt"
        const val REMINDER_SCREEN =
            "app/src/main/java/com/wedo/schedule/ui/screen/mine/ReminderScreen.kt"
        const val SETTINGS_SCREEN =
            "app/src/main/java/com/wedo/schedule/ui/screen/mine/WedoSettingsScreen.kt"
    }
}
