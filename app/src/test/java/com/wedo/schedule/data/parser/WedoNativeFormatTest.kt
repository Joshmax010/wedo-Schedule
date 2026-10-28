package com.wedo.schedule.data.parser

import org.junit.Assert.*
import org.junit.Test
import com.wedo.schedule.data.parser.WedoNativeFormat.WeekSpec

/**
 * wedo 原生格式 — 纯函数层测试：
 * magic 识别 / 转义往返 / 调色板 / lenient 时钟·日期·周次·节次 / Nd 预设 / crc32。
 */
class WedoNativeFormatTest {

    // ---- magic 识别 ----

    @Test fun magic_plain() {
        assertEquals(1, WedoNativeFormat.detectVersion("#wedo-v1\nC高数|1|1-2"))
    }

    @Test fun magic_legacySleepyStillAccepted() {
        // 向后兼容：老版本导出的 #sleepy-v1 文件必须仍能导入
        assertEquals(1, WedoNativeFormat.detectVersion("#sleepy-v1\nC高数|1|1-2"))
        assertEquals(1, WedoNativeFormat.detectVersion("#SLEEPY-V1"))
        assertEquals(1, WedoNativeFormat.detectVersion("##sleepy_v1"))
    }

    @Test fun magic_exportConstantIsWedoV1() {
        assertEquals("#wedo-v1", WedoNativeFormat.MAGIC)
        assertEquals("#sleepy-v1", WedoNativeFormat.LEGACY_MAGIC)
    }

    @Test fun magic_caseInsensitive_fullPattern() {
        // 整模式大小写不敏感 — 品牌名与 v 均不限大小写
        assertEquals(1, WedoNativeFormat.detectVersion("#WEDO-V1"))
        assertEquals(1, WedoNativeFormat.detectVersion("#Wedo-V1"))
    }

    @Test fun magic_variants_hit() {
        // 双井号/空格/无横线/下划线/全角井号/尾标点 全命中
        assertEquals(1, WedoNativeFormat.detectVersion("##wedo-v1"))
        assertEquals(1, WedoNativeFormat.detectVersion("# wedo-v1"))
        assertEquals(1, WedoNativeFormat.detectVersion("#wedo v1"))
        assertEquals(1, WedoNativeFormat.detectVersion("#wedo_v1"))
        assertEquals(1, WedoNativeFormat.detectVersion("＃wedo-v1"))
        assertEquals(1, WedoNativeFormat.detectVersion("#wedo-v1。"))
        assertEquals(1, WedoNativeFormat.detectVersion("#wedo－v1"))
    }

    @Test fun magic_quotePrefix_and_latePosition() {
        // 微信引用前缀 + 20 行客套后 magic → 32 非空行窗内命中
        assertEquals(1, WedoNativeFormat.detectVersion("> > #wedo-v1"))
        val chatter = (1..20).joinToString("\n") { "转发语第 $it 行" }
        assertEquals(1, WedoNativeFormat.detectVersion("$chatter\n#wedo-v1\nC高数|1|1-2"))
    }

    @Test fun magic_outsideWindow_miss() {
        val chatter = (1..40).joinToString("\n") { "第 $it 行" }
        assertEquals(-1, WedoNativeFormat.detectVersion("$chatter\n#wedo-v1"))
    }

    @Test fun magic_futureVersion_detected() {
        assertEquals(2, WedoNativeFormat.detectVersion("#wedo-v2"))
    }

    @Test fun magic_miss_onOtherFormats() {
        // 反向矩阵: 五路判别子串不命中
        assertEquals(-1, WedoNativeFormat.detectVersion("""{"name":"x","courses":[]}"""))
        assertEquals(-1, WedoNativeFormat.detectVersion("BEGIN:VCALENDAR"))
        assertEquals(-1, WedoNativeFormat.detectVersion("<html><body></body></html>"))
        assertEquals(-1, WedoNativeFormat.detectVersion("课程,教师,星期\n高数,张三,1"))
        assertEquals(-1, WedoNativeFormat.detectVersion("高数 张三 周一 1-2 1-16 3"))
        assertEquals(-1, WedoNativeFormat.detectVersion(""))
    }

    // ---- 转义往返 (规范 §3.3, §1.3-4) ----

    @Test fun escape_unescape_roundTrip_allReserved() {
        val cases = listOf(
            "A|B候选", "C:\\fs\\A101", "带\"引号", "多行\n备注", "制表\t符",
            "<frameset", "{花括号", "(圆括号", "全角｜不必转", "纯中文", "English Name", "データベース", "Física"
        )
        for (s in cases) {
            assertEquals("roundtrip: $s", s, WedoNativeFormat.unescape(WedoNativeFormat.escape(s)))
        }
    }

    @Test fun escape_exportNeverEmitsDangerousLiteral() {
        // 导出物永不出现"未转义"的 " / <<<WEDO-END>>> 图案 / <frameset(转义符本身合法)
        val nasty = "\"courseDetailJson\" <<<WEDO-END>>> <frameset | { ( \\"
        val escaped = WedoNativeFormat.escape(nasty)
        // 每个危险字符前都紧贴反斜杠
        assertFalse(Regex("(?<!\\\\)\"").containsMatchIn(escaped))
        assertFalse(Regex("(?<!\\\\)<").containsMatchIn(escaped))
        assertFalse(Regex("(?<!\\\\)\\{").containsMatchIn(escaped))
        assertFalse(Regex("(?<!\\\\)\\(").containsMatchIn(escaped))
        assertFalse(Regex("(?<!\\\\)\\|").containsMatchIn(escaped))
        // \n 是两字符
        assertTrue(WedoNativeFormat.escape("a\nb").contains("\\n"))
    }

    @Test fun unescape_nAndT_pairs() {
        assertEquals("a\nb", WedoNativeFormat.unescape("a\\nb"))
        assertEquals("a\tb", WedoNativeFormat.unescape("a\\tb"))
        // 其余 \x → 字面 x
        assertEquals("aXb", WedoNativeFormat.unescape("a\\Xb"))
        assertEquals("a\\b", WedoNativeFormat.unescape("a\\\\b"))
        // 尾部孤立反斜杠 → 字面反斜杠
        assertEquals("a\\", WedoNativeFormat.unescape("a\\"))
    }

    // ---- 调色板 (规范 §3.2) ----

    @Test fun palette_exact8BitForms() {
        assertEquals("#FFEADDFF", WedoNativeFormat.PALETTE[1])
        assertEquals("#FFF2C4DE", WedoNativeFormat.PALETTE[9])
        assertEquals(9, WedoNativeFormat.PALETTE.size)
    }

    @Test fun colorToToken_paletteIndex() {
        assertEquals("1", WedoNativeFormat.colorToToken("#FFEADDFF"))
        // 6 位补 FF 后命中 → 索引
        assertEquals("1", WedoNativeFormat.colorToToken("#EADDFF"))
        // 大小写归一
        assertEquals("9", WedoNativeFormat.colorToToken("#f2c4de"))
    }

    @Test fun colorToToken_sentinel_empty_autoColor() {
        assertEquals("", WedoNativeFormat.colorToToken(WedoNativeFormat.AUTO_COLOR))
        assertEquals("", WedoNativeFormat.colorToToken("#FF6750A4".lowercase()))
    }

    @Test fun colorToToken_literalFallback() {
        // 非 FF alpha → 9 位 AARRGGBB
        assertEquals("#80388E3C", WedoNativeFormat.colorToToken("#80388E3C"))
        // alpha FF 其他色 → 6 位
        assertEquals("#388E3C", WedoNativeFormat.colorToToken("#FF388E3C"))
        // 垃圾值 → 空(自动)
        assertEquals("", WedoNativeFormat.colorToToken("not-a-color"))
    }

    @Test fun colorFromToken_allForms() {
        assertEquals("#FFEADDFF", WedoNativeFormat.colorFromToken("1"))
        assertEquals("#FFEADDFF", WedoNativeFormat.colorFromToken("#EADDFF"))
        assertEquals("#FFEADDFF", WedoNativeFormat.colorFromToken("#FFEADDFF"))
        assertEquals("#80388E3C", WedoNativeFormat.colorFromToken("#80388E3C"))
        // 非法索引/垃圾 → 自动色
        assertEquals(WedoNativeFormat.AUTO_COLOR, WedoNativeFormat.colorFromToken(""))
        assertEquals(WedoNativeFormat.AUTO_COLOR, WedoNativeFormat.colorFromToken("99"))
        assertEquals(WedoNativeFormat.AUTO_COLOR, WedoNativeFormat.colorFromToken("xyz"))
    }

    // ---- lenient 时钟 / 日期 / 节次 / 周次 (规范 §2 文法) ----

    @Test fun parseClock_lenient() {
        assertEquals(java.time.LocalTime.of(8, 0), WedoNativeFormat.parseClock("8:00"))
        assertEquals(java.time.LocalTime.of(8, 0), WedoNativeFormat.parseClock("08:00"))
        assertEquals(java.time.LocalTime.of(8, 0), WedoNativeFormat.parseClock("08：00")) // 全角冒号
        assertEquals(null, WedoNativeFormat.parseClock("25:00"))
        assertEquals(null, WedoNativeFormat.parseClock("abc"))
        assertEquals(null, WedoNativeFormat.parseClock("8:5"))
    }

    @Test fun parseDate_lenient_normToMonday() {
        // 2026-03-02 是周一
        assertEquals("2026-03-02", WedoNativeFormat.parseDate("2026-03-02"))
        assertEquals("2026-03-02", WedoNativeFormat.parseDate("2026/03/02"))
        assertEquals("2026-03-02", WedoNativeFormat.parseDate("2026.3.2"))
        assertEquals("2026-03-02", WedoNativeFormat.parseDate("20260302"))
        // 非周一 → 归到所在周一(2026-03-04 是周三)
        assertEquals("2026-03-02", WedoNativeFormat.parseDate("2026-03-04"))
        // 非法 → null
        assertEquals(null, WedoNativeFormat.parseDate("abc"))
        assertEquals(null, WedoNativeFormat.parseDate("2026-13-40"))
    }

    @Test fun parseNodeSpan() {
        assertEquals(1 to 1, WedoNativeFormat.parseNodeSpan("1"))
        assertEquals(3 to 4, WedoNativeFormat.parseNodeSpan("3-4"))
        assertEquals(null, WedoNativeFormat.parseNodeSpan("abc"))
        assertEquals(null, WedoNativeFormat.parseNodeSpan("1-2-3"))
    }

    @Test fun parseWeekSpec_fiveShapes() {
        assertEquals(WeekSpec(1, 16, 0), WedoNativeFormat.parseWeekSpec("1-16"))
        assertEquals(WeekSpec(1, 15, 1), WedoNativeFormat.parseWeekSpec("1-15单"))
        assertEquals(WeekSpec(2, 16, 2), WedoNativeFormat.parseWeekSpec("2-16双"))
        assertEquals(WeekSpec(3, 4, 3), WedoNativeFormat.parseWeekSpec("3-4定"))
        assertEquals(WeekSpec(8, 8, 3), WedoNativeFormat.parseWeekSpec("8定"))
        // 导入额外容忍
        assertEquals(WeekSpec(1, 15, 1), WedoNativeFormat.parseWeekSpec("1-15奇"))
        assertEquals(WeekSpec(1, 15, 1), WedoNativeFormat.parseWeekSpec("1-15odd"))
        assertEquals(WeekSpec(2, 16, 2), WedoNativeFormat.parseWeekSpec("2-16even"))
        assertEquals(WeekSpec(2, 16, 2), WedoNativeFormat.parseWeekSpec("2-16e"))
        assertEquals(WeekSpec(3, 4, 3), WedoNativeFormat.parseWeekSpec("3-4散"))
        // 单数字无后缀 = 只上这周 (type 3)
        assertEquals(WeekSpec(8, 8, 3), WedoNativeFormat.parseWeekSpec("8"))
        // 未知后缀 → null(形状非法, 交给调用方上报)
        assertEquals(null, WedoNativeFormat.parseWeekSpec("1-16x"))
        assertEquals(null, WedoNativeFormat.parseWeekSpec("张三"))
        // 反写区间原样返回, 方向钳制在调用方(§3.1: E<S → 交换+上报)
        assertEquals(WeekSpec(16, 1, 0), WedoNativeFormat.parseWeekSpec("16-1"))
    }

    @Test fun parseDay_lenient() {
        assertEquals(1, WedoNativeFormat.parseDay("1"))
        assertEquals(3, WedoNativeFormat.parseDay("周三"))
        assertEquals(7, WedoNativeFormat.parseDay("日"))
        assertEquals(7, WedoNativeFormat.parseDay("天"))
        assertEquals(5, WedoNativeFormat.parseDay("星期5"))
        assertEquals(2, WedoNativeFormat.parseDay("礼拜二"))
        // 非法形状 → null
        assertEquals(null, WedoNativeFormat.parseDay("张三"))
        assertEquals(null, WedoNativeFormat.parseDay("13"))
    }

    // ---- Nd 冻结预设 (规范 §5, = TimeTableUtils.DEFAULT_TIME_JSON) ----

    @Test fun ndPreset_matchesDefaultTimeJson() {
        val defaults = com.wedo.schedule.util.TimeTableUtils.parseNodes(
            com.wedo.schedule.util.TimeTableUtils.DEFAULT_TIME_JSON
        )
        assertEquals(12, WedoNativeFormat.ND_PRESET.size)
        assertEquals(defaults.size, WedoNativeFormat.ND_PRESET.size)
        defaults.forEachIndexed { i, n ->
            assertEquals("node ${n.node} start", n.start, WedoNativeFormat.ND_PRESET[i].first)
            assertEquals("node ${n.node} end", n.end, WedoNativeFormat.ND_PRESET[i].second)
            assertEquals(i + 1, n.node)
        }
    }

    // ---- crc32 ----

    @Test fun crc32_knownVector() {
        // 标准 CRC32 校验向量: "123456789" → 0xCBF43926
        assertEquals("cbf43926", WedoNativeFormat.crc32("123456789".toByteArray(Charsets.UTF_8)))
    }
}
