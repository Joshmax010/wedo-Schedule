// SPDX-License-Identifier: GPL-3.0-only
// Derived from Sleepy/wedo JwNewZfParser, JwCourse and JLJU evidence.
// Original parser design references WakeupSchedule_BUPT (Apache-2.0); see NOTICE.
// Changes: JSON-only API, explicit week sets, strict validation, anonymous partial errors.
package com.wedo.jwimport

import org.json.JSONObject
import org.jsoup.Jsoup

object ZhengfangParser {
    const val MAX_RESPONSE_CHARS = 1_000_000

    fun parseTerms(html: String): TermOptions {
        if (html.length > MAX_RESPONSE_CHARS) throw ImportException(ImportError.INVALID_RESPONSE)
        val doc = Jsoup.parse(html)
        fun choices(id: String): Pair<List<TermChoice>, String> {
            val select = doc.getElementById(id) ?: throw ImportException(ImportError.INVALID_RESPONSE)
            val options = select.select("option").filter { it.attr("value").matches(Regex("[A-Za-z0-9_-]{1,32}")) }
            if (options.isEmpty() || options.size > 200) throw ImportException(ImportError.INVALID_RESPONSE)
            val selected = options.firstOrNull { it.hasAttr("selected") } ?: options.first()
            return options.map { TermChoice(it.attr("value"), it.text().take(100)) } to selected.attr("value")
        }
        val years = choices("xnm")
        val semesters = choices("xqm")
        return TermOptions(years.first, semesters.first, years.second, semesters.second)
    }

    fun parseSchedule(json: String, term: TermSelection): ScheduleResult {
        if (json.length > MAX_RESPONSE_CHARS) throw ImportException(ImportError.INVALID_RESPONSE)
        val root = try { JSONObject(json) } catch (_: Exception) { throw ImportException(ImportError.INVALID_RESPONSE) }
        val rows = root.optJSONArray("kbList") ?: throw ImportException(ImportError.INVALID_RESPONSE)
        if (rows.length() > 5000) throw ImportException(ImportError.INVALID_RESPONSE)
        val courses = mutableListOf<ImportedCourse>()
        val skipped = mutableListOf<SkippedRecord>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index)
            if (row == null) { skipped += SkippedRecord(index, RecordError.INVALID_RECORD); continue }
            try {
                fun text(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
                    val v = row.opt(key)
                    if (v is String || v is Number) v.toString().trim().takeIf(String::isNotEmpty) else null
                } ?: ""
                val name = text("kcmc", "kcm", "courseName")
                if (name.isBlank() || name.length > 512) throw BadRecord(RecordError.MISSING_NAME)
                val day = text("xqj").toIntOrNull() ?: when (text("xqjmc")) {
                    "星期一", "周一" -> 1; "星期二", "周二" -> 2; "星期三", "周三" -> 3
                    "星期四", "周四" -> 4; "星期五", "周五" -> 5; "星期六", "周六" -> 6
                    "星期日", "星期天", "周日", "周天" -> 7; else -> 0
                }
                if (day !in 1..7) throw BadRecord(RecordError.INVALID_DAY)
                val sections = try { parseSections(text("jcs", "jc")) } catch (_: IllegalArgumentException) {
                    throw BadRecord(RecordError.INVALID_SECTIONS)
                }
                val weeks = try { parseWeeks(text("zcd", "kkzc", "zc")) } catch (_: IllegalArgumentException) {
                    throw BadRecord(RecordError.INVALID_WEEKS)
                }
                sections.forEach { (start, end) ->
                    courses += ImportedCourse(name, text("xm", "jsxm", "teacher").take(512),
                        text("cdmc", "classroomName").take(512), day, start, end, weeks,
                        text("xkbz").take(2000))
                }
            } catch (e: BadRecord) { skipped += SkippedRecord(index, e.reason) }
        }
        return ScheduleResult(term, courses.distinct(), rows.length(), skipped, System.currentTimeMillis())
    }

    /** Unknown or missing weeks must never silently become weeks 1..16. */
    fun parseWeeks(value: String): Set<Int> {
        val s = normalize(value).replace("第", "").replace("周", "").replace("{", "").replace("}", "")
        require(s.isNotBlank())
        if (s.length in 10..100 && s.all { it == '0' || it == '1' }) {
            return s.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }.toSet().also { require(it.isNotEmpty()) }
        }
        val out = sortedSetOf<Int>()
        s.split(',').forEach { part ->
            val m = Regex("^(\\d{1,3})(?:-(\\d{1,3}))?(?:\\(([单双])\\)|([单双]))?$").matchEntire(part)
                ?: throw IllegalArgumentException("INVALID_WEEKS")
            val start = m.groupValues[1].toInt()
            val end = m.groupValues[2].ifEmpty { m.groupValues[1] }.toInt()
            require(start in 1..100 && end in start..100)
            val parity = m.groupValues[3].ifEmpty { m.groupValues[4] }
            out += (start..end).filter { parity.isEmpty() || (if (parity == "单") it % 2 == 1 else it % 2 == 0) }
        }
        require(out.isNotEmpty())
        return out
    }

    fun parseSections(value: String): List<Pair<Int, Int>> {
        val s = normalize(value).replace("节", "").removePrefix("(").removeSuffix(")")
        require(s.isNotBlank())
        val result = mutableListOf<Pair<Int, Int>>()
        s.split(',').forEach { part ->
            if (part.matches(Regex("0[0-9]+")) && part.length % 2 == 0) {
                val nodes = part.chunked(2).map(String::toInt)
                require(nodes.size % 2 == 0)
                nodes.chunked(2).forEach { result += it[0] to it[1] }
            } else {
                val m = Regex("^(\\d{1,2})(?:-(\\d{1,2}))?$").matchEntire(part)
                    ?: throw IllegalArgumentException("INVALID_SECTIONS")
                result += m.groupValues[1].toInt() to m.groupValues[2].ifEmpty { m.groupValues[1] }.toInt()
            }
        }
        require(result.all { (a, b) -> a in 1..32 && b in a..32 })
        return result.distinct()
    }

    private fun normalize(s: String): String = s.replace(Regex("\\s+"), "")
        .replace('（', '(').replace('）', ')').replace('，', ',').replace('；', ',').replace(';', ',')
        .replace('－', '-').replace('–', '-').replace('—', '-').replace('~', '-').replace("至", "-")
    private class BadRecord(val reason: RecordError) : IllegalArgumentException(reason.name)
}
