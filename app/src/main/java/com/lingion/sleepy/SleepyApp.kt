package com.lingion.sleepy

import android.app.Application
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.widget.notification.CourseNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 类 — 初始化全局依赖。
 *
 * 没有任何 SDK / 广告 / 拍照搜题，只有：
 * - Room 数据库
 * - 课表仓库
 * wedo v1 intentionally does not schedule reminders or widgets.
 */
class SleepyApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: ScheduleRepository by lazy { ScheduleRepository(database) }
    /** Retained for compiled upstream components; wedo v1 never schedules it at startup. */
    val notificationScheduler: CourseNotificationScheduler by lazy {
        CourseNotificationScheduler(this)
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        // 预热 SharedPreferences: 首次 getSharedPreferences 后台异步加载整文件,
        // 避免冷启动后首个 Compose 屏在主线程同步做磁盘反序列化 (AppPrefs 全部
        // getter 都在调用方线程直读, 严格模式 diskRead / 低端机卡顿来源)。
        // 拿到实例即触发异步 loadFromDisk, 不阻塞本线程。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                getSharedPreferences("sleepy_prefs", android.content.Context.MODE_PRIVATE)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SleepyApp? = null

        fun get(): SleepyApp = instance
            ?: throw IllegalStateException("SleepyApp.onCreate() not called yet")
    }
}
