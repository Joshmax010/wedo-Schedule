package com.wedo.schedule.data.jw

/**
 * 解析中间结构：一条课程记录。
 *
 * 与落库结构 [com.wedo.schedule.data.entity.CourseEntity] 的区别在于：
 * 这里用 startNode/endNode 表达节次区间，落库时换算成 startNode + step。
 * 由 [JwImportViewModel.toCourseEntities] 完成转换。
 */
data class JwCourse(
    val name: String,
    val room: String = "",
    val teacher: String = "",
    val day: Int,
    val startNode: Int,
    val endNode: Int,
    val startWeek: Int,
    val endWeek: Int,
    val type: Int = 0  // 0=每周 1=单周 2=双周
)
