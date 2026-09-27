package com.wedo.schedule.data.jw

import com.wedo.schedule.data.entity.CourseEntity

/**
 * 教务来源解析器基类。
 *
 * 约定：
 *   - 输入是登录态下从教务页面抓到的源码（HTML 或内嵌 JSON 字符串），
 *     由 WebView 侧的 fetch 脚本透传，不在此层做网络请求。
 *   - 输出是统一的 [JwCourse] 列表，转换与落库交给 [JwImportViewModel]。
 *   - 不依赖 Android Context —— 颜色、资源等表现层逻辑留在 ViewModel。
 *
 * 用法：
 *   ```
 *   val courses = JwNewZfParser(payload).generateCourseList()
 *   ```
 */
abstract class JwParser(val source: String) {

    /** 解析源码，输出统一结构的课程列表。 */
    abstract fun generateCourseList(): List<JwCourse>

    /**
     * 基于源码结构锚点的命中置信度（0..100）。
     *
     * 只允许检查静态特征，**禁止**在内部调用 [generateCourseList]（避免重复解析）。
     * 兜底返回 0。
     */
    open fun confidence(): Int = 0

    /** 实际命中的结构锚点列表，供诊断输出。默认空，子类按需覆盖。 */
    open fun matchedFeatures(): List<String> = emptyList()
}
