package com.wedo.jwimport

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private val term = TermSelection("2026", "3")
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.bufferedReader().use { it.readText() }
    private fun row(week: String = "1-16周", sections: String = "1-2", day: String = "1") =
        JSONObject().put("kcmc", "测试课程").put("zcd", week).put("jcs", sections).put("xqj", day)
    private fun json(vararg rows: JSONObject) = JSONObject().put("kbList", org.json.JSONArray(rows.toList())).toString()

    @Test fun verifiedNormal() {
        val result = ZhengfangParser.parseSchedule(fixture("schedule_response.json"), term)
        assertEquals(6, result.sourceCount)
        assertEquals(6, result.courses.size)
        assertTrue(result.skipped.isEmpty())
        assertEquals(setOf(5,7,8,9,10,11,12,13), result.courses.single { it.day == 1 }.weeks)
    }
    @Test fun verifiedEmpty() {
        val result = ZhengfangParser.parseSchedule(fixture("empty_schedule_response.json"), term)
        assertTrue(result.emptySemester); assertTrue(result.courses.isEmpty())
    }
    @Test fun verifiedTerms() {
        val options = ZhengfangParser.parseTerms(fixture("terms_response.html"))
        assertTrue(options.years.isNotEmpty())
        assertTrue(options.semesters.map { it.value }.containsAll(listOf("3", "12", "16")))
    }
    @Test fun weekForms() {
        assertEquals((1..16).toSet(), ZhengfangParser.parseWeeks("1-16周"))
        assertEquals((1..15 step 2).toSet(), ZhengfangParser.parseWeeks("1-15周(单)"))
        assertEquals((2..16 step 2).toSet(), ZhengfangParser.parseWeeks("2－16 周（双周）"))
        assertEquals(setOf(1,3,5,7,9), ZhengfangParser.parseWeeks("1，3,5；7;9周"))
        assertEquals(((1..8)+(10..16)).toSet(), ZhengfangParser.parseWeeks("1-8,10-16"))
        assertEquals(setOf(1,3,5), ZhengfangParser.parseWeeks("1010100000"))
    }
    @Test fun sectionForms() {
        assertEquals(listOf(1 to 2), ZhengfangParser.parseSections("0102"))
        assertEquals(listOf(3 to 4,6 to 7), ZhengfangParser.parseSections("3-4，6-7节"))
        assertEquals(listOf(5 to 5), ZhengfangParser.parseSections("5"))
    }
    @Test fun partialDamage() {
        val result = ZhengfangParser.parseSchedule(json(row(), row("未知"), row(day="8")), term)
        assertEquals(1, result.courses.size); assertEquals(2, result.skipped.size)
        assertFalse(result.emptySemester)
    }
    @Test fun allBadIsNotEmptySemester() {
        val result = ZhengfangParser.parseSchedule(json(row("未知")), term)
        assertFalse(result.emptySemester); assertEquals(1, result.skipped.size)
    }
    @Test fun duplicatesAndMultipleTimes() {
        val result = ZhengfangParser.parseSchedule(json(row(sections="1-2,6-7"), row(sections="1-2,6-7")), term)
        assertEquals(2, result.courses.size)
    }
    @Test fun unknownStructureFails() {
        listOf("", "{}", "<html>维护</html>", "{\"kbList\":null}", "{\"kbList\":{}}", " ".repeat(1_000_001)).forEach { s ->
            try { ZhengfangParser.parseSchedule(s, term); fail("Must reject invalid structure") }
            catch (e: ImportException) { assertEquals(ImportError.INVALID_RESPONSE, e.code) }
        }
    }
    @Test fun invalidWeeksNeverDefault() {
        listOf("", "未知", "0-16", "16-1", "101-102", "6-6(单)").forEach { s ->
            try { ZhengfangParser.parseWeeks(s); fail("Must reject invalid weeks") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun invalidSections() {
        listOf("", "未知", "2-1", "0", "33", "010").forEach { s ->
            try { ZhengfangParser.parseSections(s); fail("Must reject invalid sections") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun exactHttpsAllowlist() {
        val school = SchoolDefinition.JLJU
        assertTrue(school.isAllowedNavigation(school.loginUrl))
        assertTrue(school.isAllowedNavigation("https://cas.jlju.edu.cn/cas/login"))
        listOf("http://jwxt.jlju.edu.cn/", "https://jwxt.jlju.edu.cn.evil.example/", "https://portal.jlju.edu.cn/",
            "https://jwxt.jlju.edu.cn:444/", "https://user@jwxt.jlju.edu.cn/", "file:///tmp/a", "javascript:alert(1)").forEach {
            assertFalse(school.isAllowedNavigation(it))
        }
        assertFalse(school.isEducationPage("https://cas.jlju.edu.cn/cas/login"))
    }
    @Test fun untrustedConfigurationRejected() {
        try { SchoolDefinition("test", "测试", "http://test.example/", "test.example", emptySet()); fail() }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun scriptsNeverInstallNativeBridge() {
        val script = FetchScripts.schedule(SchoolDefinition.JLJU, term)
        assertTrue(script.contains("credentials: 'same-origin'"))
        assertTrue(script.contains("redirect: 'error'"))
        assertFalse(script.contains("addJavascriptInterface"))
        assertTrue(script.contains("Array.isArray(data.kbList)"))
    }
}
