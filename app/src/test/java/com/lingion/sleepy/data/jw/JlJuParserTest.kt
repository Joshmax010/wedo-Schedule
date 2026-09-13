package com.lingion.sleepy.data.jw

import com.lingion.sleepy.TestProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JlJuParserTest {

    private val verifiedFixture = TestProjectFiles.read(
        "test/fixtures/jlju/schedule_response.json"
    )

    @Test
    fun `verified JLJU response parses course week day and section relationships`() {
        val courses = JlJuParser.parse(verifiedFixture)

        assertEquals(12, courses.size)
        assertEquals(setOf("测试课程01", "测试课程02"), courses.map { it.name }.toSet())
        assertEquals(setOf(1, 2, 3, 5), courses.map { it.day }.toSet())
        assertEquals(setOf(1 to 2, 3 to 4), courses.map { it.startNode to it.endNode }.toSet())
        assertEquals(6, courses.count { it.type == 1 })
        assertEquals(6, courses.count { it.type == 0 })
        assertTrue(courses.all { it.teacher.startsWith("测试教师") })
        assertTrue(courses.all { it.room.startsWith("测试教室") })
    }

    @Test
    fun `verified JLJU response preserves observed split week ranges`() {
        val monday = JlJuParser.parse(verifiedFixture).filter { it.day == 1 }

        assertEquals(2, monday.size)
        assertTrue(monday.any { it.startWeek == 5 && it.endWeek == 7 && it.type == 1 })
        assertTrue(monday.any { it.startWeek == 8 && it.endWeek == 13 && it.type == 0 })
    }

    @Test
    fun `verified JLJU empty response returns no courses`() {
        val emptyFixture = TestProjectFiles.read(
            "test/fixtures/jlju/empty_schedule_response.json"
        )

        assertTrue(JlJuParser.parse(emptyFixture).isEmpty())
    }
}
