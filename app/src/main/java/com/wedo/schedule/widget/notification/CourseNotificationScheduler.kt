package com.wedo.schedule.widget.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wedo.schedule.MainActivity
import com.wedo.schedule.R
import com.wedo.schedule.WedoApp
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.data.entity.TimeTableEntity
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课程通知调度器 — V3 重设计（REQ-P5-03/04）：只保留**每节课前提醒**。
 *
 * 每日摘要提醒（旧 [DailyNotifyReceiver]）与流体云/超级岛（旧 `FluidCloudService`）
 * 已随 REQ-P5 删除；课前提醒：每天凌晨调度当天每节课前 N 分钟的通知。
 */
class CourseNotificationScheduler(private val context: Context) {

    companion object {
        const val CHANNEL_BEFORE_CLASS = "wedo_before_class"

        /**
         * 「横幅提醒」关闭时使用的低干扰渠道：同内容但不触发悬浮横幅（heads-up）。
         * Android 8+（本 app minSdk=26）横幅由渠道重要度决定，故用双渠道表达开关语义。
         */
        const val CHANNEL_BEFORE_CLASS_SILENT = "wedo_before_class_silent"

        // Request codes for PendingIntent discrimination
        private const val RC_BEFORE_CLASS_SCHEDULER = 2
        private const val RC_BEFORE_CLASS_BASE = 100 // + courseId offset

        // Notification IDs
        const val NOTIFY_BEFORE_CLASS_BASE = 2000 // + courseId offset
    }

    fun scheduleAll() {
        createChannels()
        // 整段放入 IO 协程：cancelAll 现为 suspend，需在协程内先取消再重排，
        //   保证「先取消后重排」的顺序不被打散（避免取消与重排的竞态），
        //   同时把查库挪出主线程，消除 runBlocking 导致的 ANR 风险。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            cancelAll()

            val prefs = context.applicationContext
            if (!AppPrefs.isReminderEnabled(prefs)) return@launch

            if (AppPrefs.isBeforeClassEnabled(prefs)) {
                scheduleBeforeClassDaily()
            }
        }
    }

    suspend fun cancelAll() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel before-class scheduler
        alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java))

        // 课前提醒的 request code 用 RC_BEFORE_CLASS_BASE + course.id（稳定唯一）。
        // 取消时遍历数据库里所有课程 id，逐个 cancel，不再依赖写死的 50 上限。
        // 改为 suspend + withContext(IO) 查库，不再在主线程 runBlocking 阻塞导致 ANR。
        val courseIds = withContext(Dispatchers.IO) {
            runCatching {
                WedoApp.get().repository.let { repo ->
                    repo.getAllTables().flatMap { repo.getCourses(it.id) }
                }.map { it.id.toInt() }
            }.getOrDefault(emptyList())
        }
        cancelCourseAlarmIds(alarmManager, courseIds)
    }

    /**
     * 取消指定课程 id 的课前闹钟（PendingIntent 语义：extras 不参与匹配）。
     * 调用方：ScheduleRepository.deleteTable —— 删表靠外键 CASCADE 级联删课程，
     * 删除后这些课程 id 已查不到，cancelAll 的"现存课程"枚举覆盖不到，
     * 故删除前捕获 id 列表、删除后调这里显式清理孤儿闹钟。
     */
    fun cancelCourseAlarms(courseIds: List<Long>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelCourseAlarmIds(alarmManager, courseIds.map { it.toInt() })
    }

    private fun cancelCourseAlarmIds(alarmManager: AlarmManager, courseIds: List<Int>) {
        for (cid in courseIds) {
            try {
                alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_BASE + cid, BeforeClassNotifyReceiver::class.java))
            } catch (_: Exception) {}
        }
    }

    // ==================== Before-class scheduler ====================

    private fun scheduleBeforeClassDaily() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java)

        // Schedule at 00:05 every day
        val target = LocalTime.of(0, 5)
        var next = LocalDate.now().atTime(target)
        if (LocalTime.now().isAfter(target)) next = next.plusDays(1)
        val epoch = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        setRepeatingAlarm(alarmManager, epoch, AlarmManager.INTERVAL_DAY, pending)

        // Also immediately schedule for today (in case app was opened after midnight)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            scheduleTodayBeforeClassAlarms()
        }
    }

    /**
     * Queries today's courses and schedules individual before-class alarms.
     * Called by [BeforeClassScheduleReceiver] at midnight and by [scheduleBeforeClassDaily].
     */
    suspend fun scheduleTodayBeforeClassAlarms() {
        val app = context.applicationContext
        android.util.Log.d("CourseScheduler", "scheduleToday start enabled=${AppPrefs.isBeforeClassEnabled(app)} minutes=${AppPrefs.getBeforeClassMinutes(app)}")
        if (!AppPrefs.isBeforeClassEnabled(app)) return
        val minutes = AppPrefs.getBeforeClassMinutes(app)
        val today = LocalDate.now()
        val dow = DateUtils.todayDayOfWeek(today)

        val table = resolveCurrentTable()
        android.util.Log.d("CourseScheduler", "table=${table?.id}:${table?.name} start=${table?.startDate} today=$today dow=$dow")
        if (table == null) return
        val week = DateUtils.currentWeek(table.startDate, today)
        val allCourses = WedoApp.get().repository.getCoursesByDayOnce(table.id, dow)
        // 防呆: 学期范围外不上课前闹钟(钳制周数会误匹配第 1 周的课)
        if (DateUtils.semesterStatus(table.startDate, table.maxWeek, today) != DateUtils.SemesterStatus.IN_RANGE) return
        val courses = allCourses.filter { it.inWeek(week) }
        android.util.Log.d("CourseScheduler", "week=$week coursesAll=${allCourses.size} coursesInWeek=${courses.size}")

        // Parse time nodes
        val nodes = TimeTableUtils.parseNodes(table.timeJson)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        courses.forEachIndexed { index, course ->
            // Get course start time
            android.util.Log.d("CourseScheduler", "course index=$index id=${course.id} name=${course.courseName} ownTime=${course.ownTime} start=${course.startTime} node=${course.startNode}")
            val startTimeStr = if (course.ownTime && course.startTime.isNotBlank()) {
                course.startTime
            } else {
                nodes.find { it.node == course.startNode }?.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
            } ?: run {
                android.util.Log.w("CourseScheduler", "skip no start time course=${course.id}")
                return@forEachIndexed
            }
            val parts = startTimeStr.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()
            val m = parts.getOrNull(1)?.toIntOrNull()
            // 钳制：ownTime/startTime 可能是破损值（h≥24/m≥60），非法则跳过本节
            if (h == null || m == null || h !in 0..23 || m !in 0..59) {
                android.util.Log.w("CourseScheduler", "skip invalid time course=${course.id} time=$startTimeStr")
                return@forEachIndexed
            }

            val classStart = today.atTime(h, m)
            val notifyTime = classStart.minusMinutes(minutes.toLong())
            val epoch = notifyTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            android.util.Log.d("CourseScheduler", "course=${course.id} start=$classStart notify=$notifyTime epoch=$epoch now=$now")
            if (epoch <= now) {
                android.util.Log.d("CourseScheduler", "skip past alarm course=${course.id}")
                return@forEachIndexed
            }

            val intent = Intent(context, BeforeClassNotifyReceiver::class.java).apply {
                putExtra("courseName", course.courseName)
                putExtra("room", course.room)
                putExtra("teacher", course.teacher)
                putExtra("startTime", String.format("%02d:%02d", h, m))
                putExtra("notifyEpoch", epoch)
                putExtra("classEpoch", classStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                // 通知 id 按课程稳定唯一，重复投递会覆盖同一通知而非堆叠
                putExtra("notifyId", NOTIFY_BEFORE_CLASS_BASE + course.id.toInt())
            }
            val pending = PendingIntent.getBroadcast(
                context, RC_BEFORE_CLASS_BASE + course.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Use exact alarm for precision, fall back to inexact on Android 12+ without grant
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, epoch, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, pending)
            }
        }
    }

    // ==================== Helpers ====================

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_BEFORE_CLASS,
            context.getString(R.string.notif_channel_before_class),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notif_channel_before_class_desc) })
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_BEFORE_CLASS_SILENT,
            context.getString(R.string.notif_channel_before_class_silent),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_before_class_silent_desc) })
    }

    // buildPendingIntInfo 死函数已删（实际全部走下方 buildPendingIntent）

    @Suppress("UNCHECKED_CAST")
    private fun buildPendingIntent(rc: Int, cls: Class<out BroadcastReceiver>): PendingIntent =
        PendingIntent.getBroadcast(
            context, rc, Intent(context, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setRepeatingAlarm(am: AlarmManager, epoch: Long, interval: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, epoch, interval, pi)
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, epoch, interval, pi)
        }
    }

    private suspend fun resolveCurrentTable(): TimeTableEntity? {
        return com.wedo.schedule.widget.WidgetTableResolver.resolveCurrentTable()
    }
}

// ==================== Receivers ====================

/**
 * Midnight scheduler — sets up individual before-class alarms for the day.
 */
class BeforeClassScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isBeforeClassEnabled(context)) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            CourseNotificationScheduler(context.applicationContext).scheduleTodayBeforeClassAlarms()
        }
    }
}

/**
 * Individual before-class notification — fires N minutes before a class.
 * Content: "下节课{courseName}于{HH}:{MM}在{room}上课"。
 *
 * V3 重设计（REQ-P5-03）：流体云/超级岛路径已删除，仅保留标准高优先级通知。
 * 通知 id 由调度器通过 "notifyId" extra 传入（= NOTIFY_BEFORE_CLASS_BASE + courseId），
 * 同一门课重复投递会覆盖同一条通知而非堆叠。
 */
class BeforeClassNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotifPermission(context)) {
            android.util.Log.w("BeforeClassNotify", "POST_NOTIFICATIONS denied")
            return
        }
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isBeforeClassEnabled(context)) {
            return
        }

        val courseName = intent.getStringExtra("courseName") ?: return
        val room = intent.getStringExtra("room") ?: ""
        val startTime = intent.getStringExtra("startTime") ?: ""
        val roomStr = room.ifBlank { context.getString(R.string.notif_room_unknown) }
        val teacher = intent.getStringExtra("teacher") ?: ""

        val text = if (teacher.isBlank()) {
            context.getString(R.string.notif_before_class_text, courseName, startTime, roomStr)
        } else {
            context.getString(R.string.notif_before_class_text_with_teacher, courseName, startTime, roomStr, teacher)
        }

        // 横幅样式：开启 → 高优先级渠道（可触发 heads-up 横幅）；关闭 → 普通渠道（仅通知栏）。
        val banner = AppPrefs.isBeforeClassBannerEnabled(context)
        val channel = if (banner) {
            CourseNotificationScheduler.CHANNEL_BEFORE_CLASS
        } else {
            CourseNotificationScheduler.CHANNEL_BEFORE_CLASS_SILENT
        }

        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_time)
            .setContentTitle(context.getString(R.string.notif_before_class_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (banner) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        val notifyId = intent.getIntExtra(
            "notifyId", CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE
        )
        try {
            NotificationManagerCompat.from(context).notify(notifyId, notif)
        } catch (_: SecurityException) {
            // 权限在运行时被撤销：静默忽略，不崩溃
        }
    }
}

/**
 * Boot receiver — reschedules everything after reboot or app update.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED
            || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (AppPrefs.isReminderEnabled(context)) {
                WedoApp.get().notificationScheduler.scheduleAll()
            }
        }
    }
}

// ==================== Shared helpers ====================

private fun hasNotifPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
