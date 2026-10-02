package com.wedo.schedule.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 每 15 分钟触发一次，刷新所有已放置的 wedo 小组件。
 * 由于 Android 系统限制 updatePeriodMillis 最低 30 分钟，
 * 我们用 WorkManager 突破这个限制。
 */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        WidgetUpdater.notifyDataChanged(applicationContext)
        // 流体云兜底（ensureActiveFluidCloud）已随 REQ-P4-04 删除；课前提醒由
        // BeforeClassScheduleReceiver（每日凌晨 00:05）+ BootReceiver（开机/更新）负责调度。
        return Result.success()
    }
}
