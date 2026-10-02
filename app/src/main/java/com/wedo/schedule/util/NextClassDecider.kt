package com.wedo.schedule.util

import com.wedo.schedule.data.entity.CourseEntity
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 「下一节课」判定结果（REQ-P1-02）。
 *
 * @property course 命中的课程节
 * @property startTime 该节开始时刻（来自 timeJson 或课程的 ownTime）
 * @property endTime 该节结束时刻
 * @property minutesUntilStart `now → start` 的分钟数：> 0 尚未开始；≤ 0 表示已开始（绝对值 = 已上分钟）
 * @property minutesUntilEnd `now → end` 的分钟数：> 0 距下课分钟；≤ 0 表示已下课
 * @property inProgress 当前是否**正在上**（start ≤ now ≤ end）
 */
data class NextClass(
    val course: CourseEntity,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val minutesUntilStart: Long,
    val minutesUntilEnd: Long,
    val inProgress: Boolean
)

/**
 * 「下一节课」纯函数判定器（REQ-P1-02）。
 *
 * **为什么抽成纯函数**：倒计时是本页最高频、也最容易出错的逻辑（跨节、课间、正在上课、
 * 当天已无课、跨天……）。把它从 Composable 里剥离出来，就能用**纯 JVM 单测**穷举这些
 * 边界，而不必依赖真机/模拟器（本机两者皆无）。Composable 只负责「每分钟把 now 递进来」。
 *
 * 不做任何 Android / Context 依赖；时间取自 [TimeTableUtils.courseTimeParts]（与课表页同源）。
 */
object NextClassDecider {

    private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 取某节课的 `(开始, 结束)` 时刻；无法确定（无作息且非自定义时间、格式错误）返回 null。
     *
     * 供 UI 判断「进行中」高亮复用，保证与倒计时同一套时间解析。
     */
    fun timeRangeOf(course: CourseEntity, timeJson: String): Pair<LocalTime, LocalTime>? {
        val parts = TimeTableUtils.courseTimeParts(
            courseStartNode = course.startNode,
            courseStep = course.step,
            timeJson = timeJson,
            ownTime = course.ownTime,
            startTime = course.startTime,
            endTime = course.endTime
        ) ?: return null
        val start = parse(parts.first) ?: return null
        val end = parse(parts.second) ?: return null
        return start to end
    }

    /** 某节课此刻是否正在进行（start ≤ now ≤ end）。用于时间轴的「进行中」标识。 */
    fun isInProgress(course: CourseEntity, timeJson: String, now: LocalTime): Boolean {
        val (start, end) = timeRangeOf(course, timeJson) ?: return false
        return !start.isAfter(now) && !end.isBefore(now)
    }

    /**
     * 判定「下一节课」：**正在上的课优先**，否则取当日**尚未开始**中最近的一节；
     * 当天已无课（全部已下课 / 无课）返回 null。
     *
     * @param courses 候选课程（调用方通常已按「今日 + 本周」过滤；本函数再做一次 day 过滤兜底）
     * @param now 当前时刻（含日期与时间，便于跨天/跨周语义）
     * @param timeJson 当前课表作息；用于把 startNode/step 换算成具体时刻
     */
    fun decide(courses: List<CourseEntity>, now: LocalDateTime, timeJson: String): NextClass? {
        val today = now.dayOfWeek.value   // Monday=1 .. Sunday=7，与 CourseEntity.day 同口径
        val nowTime = now.toLocalTime()

        val candidates = courses.asSequence()
            .filter { it.day == today }
            .mapNotNull { c -> timeRangeOf(c, timeJson)?.let { r -> Triple(c, r.first, r.second) } }
            // 尚未下课（含正在上）；已下课的排除
            .filter { (_, _, end) -> !end.isBefore(nowTime) }
            .toList()

        if (candidates.isEmpty()) return null

        val running = candidates.filter { (_, start, end) -> !start.isAfter(nowTime) && !end.isBefore(nowTime) }
        // 正在上课：取最早开始的一节（冲突时也稳定）；否则取最近将来的一节
        val (course, start, end) = running.minByOrNull { it.second }
            ?: candidates.minByOrNull { it.second }
            ?: return null

        return NextClass(
            course = course,
            startTime = start,
            endTime = end,
            minutesUntilStart = Duration.between(nowTime, start).toMinutes(),
            minutesUntilEnd = Duration.between(nowTime, end).toMinutes(),
            inProgress = !start.isAfter(nowTime) && !end.isBefore(nowTime)
        )
    }

    private fun parse(hm: String): LocalTime? = try {
        LocalTime.parse(hm.trim(), FMT)
    } catch (_: Exception) {
        null
    }
}
