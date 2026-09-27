package com.lingion.sleepy.data.jw

import com.lingion.sleepy.ui.screen.imports.ZF_NEW_FETCH_JS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新版正方 kbList JSON → 课程的字段映射回归。
 *
 * 这些用例钉的是 JLJU 真实接口返回的字段形态（`kcmc` / `xqj` / `jc` / `zcd` /
 * `cdmc` / `xm`），是导入链路最容易被改坏的一环。纯 JVM，不依赖 Android。
 */
class JwNewZfMappingTest {

    private val sampleKbList = """
        {"kbList":[{"kcmc":"高等数学","xqj":1,"jc":"1-2","zcd":"1-16周","cdmc":"教一101","xm":"张老师"}]}
    """.trimIndent()

    @Test
    fun `kbList 单条映射到完整课程字段`() {
        val courses = JwNewZfParser(sampleKbList).generateCourseList()
        assertEquals("应解出 1 门课", 1, courses.size)
        with(courses.single()) {
            assertEquals("高等数学", name)
            assertEquals(1, day)
            assertEquals(1, startNode)
            assertEquals(2, endNode)
            assertEquals("张老师", teacher)
            assertEquals("教一101", room)
            assertEquals(1, startWeek)
            assertEquals(16, endWeek)
        }
    }

    @Test
    fun `空 kbList 解析为 0 课而不抛异常`() {
        val courses = JwNewZfParser("""{"kbList":[]}""").generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `多段节次 jc 串拆成多条课程`() {
        // "3-4,6-7" 是两段独立节次，各成一条课程（中间 5 节没课）
        val payload = """
            {"kbList":[{"kcmc":"大学物理","xqj":3,"jc":"3-4,6-7","zcd":"1-8周","cdmc":"理教201","xm":"李老师"}]}
        """.trimIndent()
        val courses = JwNewZfParser(payload).generateCourseList()
        assertEquals("两段节次应拆成 2 条", 2, courses.size)
        assertEquals(3, courses[0].startNode)
        assertEquals(4, courses[0].endNode)
        assertEquals(6, courses[1].startNode)
        assertEquals(7, courses[1].endNode)
        assertTrue("两条课名应一致", courses.all { it.name == "大学物理" })
    }

    @Test
    fun `单周 zcd 标记解析为 type=1`() {
        val payload = """
            {"kbList":[{"kcmc":"体育","xqj":5,"jc":"5-6","zcd":"1-16周(单)","cdmc":"体育馆","xm":"王老师"}]}
        """.trimIndent()
        val courses = JwNewZfParser(payload).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("单周应标 type=1", 1, courses[0].type)
    }

    @Test
    fun `zf_new fetch JS 保留关键路径指纹与嗅探锚点`() {
        // 静态契约校验：改 JS 时这些锚点不能丢，否则抓取会静默失败
        assertTrue("/jwglxt/ 路径指纹", ZF_NEW_FETCH_JS.contains("/jwglxt/"))
        assertTrue("/kbcx/ 路径指纹", ZF_NEW_FETCH_JS.contains("/kbcx/"))
        assertTrue("课表接口路径", ZF_NEW_FETCH_JS.contains("xskbcx_cxXsgrkb.html"))
        assertTrue("kbList 嗅探", ZF_NEW_FETCH_JS.contains("kbList"))
        assertTrue("会话过期分类", ZF_NEW_FETCH_JS.contains("SESSION_EXPIRED"))
        assertTrue("未在课表页分类", ZF_NEW_FETCH_JS.contains("NOT_ON_TIMETABLE"))
        assertTrue(
            "apiPath 必须通过 pathPrefix 拼接，不能硬编 /jwglxt/kbcx",
            ZF_NEW_FETCH_JS.contains("pathPrefix + '/kbcx/xskbcx_cxXsgrkb.html")
        )
    }
}
