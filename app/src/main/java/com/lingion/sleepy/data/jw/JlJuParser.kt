package com.lingion.sleepy.data.jw

/**
 * 吉林建筑大学课表的纯函数式离线解析入口。
 *
 * 2026-09-11 的本地脱敏取证确认 JLJU 使用新版正方 `jwglxt`，个人课表接口
 * 返回包含 `kbList` 的 JSON。因此复用基座已经稳定的 [JwNewZfParser]，不建立
 * 第二套课程模型或解析架构。
 */
object JlJuParser {
    fun parse(response: String): List<JwCourse> =
        JwNewZfParser(response).generateCourseList()
}
