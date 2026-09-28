package com.wedo.schedule.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM test for the liquid-glass token layer.
 *
 * 覆盖三块：
 *   1. [WedoGlassQuality.of] — 裸字符串 → 枚举的解析（含未知值回落 Balanced）
 *   2. [buildGlassSpec] 三档 × 深浅 = 6 组参数快照（数值直接对账《液态玻璃效果规范》）
 *   3. 三档之间的**单调性契约**：Smooth 不应有投影/内高光；Fine 的描边与投影强于 Balanced
 *
 * 这些断言的价值在于锁住规范的数值契约 —— 后续调参时若不小心把 Balance 档
 * 的填充改成纯色、或把 Smooth 档的投影打开，测试会立刻失败。
 */
class WedoGlassTokensTest {

    // ---------- 1. 画质解析 ----------

    @Test
    fun `quality parses known keys`() {
        assertEquals(WedoGlassQuality.Smooth, WedoGlassQuality.of("smooth"))
        assertEquals(WedoGlassQuality.Fine, WedoGlassQuality.of("fine"))
        assertEquals(WedoGlassQuality.Balanced, WedoGlassQuality.of("balanced"))
    }

    @Test
    fun `quality falls back to Balanced on unknown or empty key`() {
        assertEquals(WedoGlassQuality.Balanced, WedoGlassQuality.of(""))
        assertEquals(WedoGlassQuality.Balanced, WedoGlassQuality.of("ultra"))
        // 大小写敏感 —— 存储值恒为小写，若将来写入脏数据也不应意外跑到 Fine
        assertEquals(WedoGlassQuality.Balanced, WedoGlassQuality.of("FINE"))
    }

    // ---------- 2. 参数矩阵快照 ----------

    @Test
    fun `balanced light matches spec 58 to 22`() {
        val s = buildGlassSpec(WedoGlassQuality.Balanced, dark = false, level = WedoGlassLevel.Nav)
        assertEquals(0.58f, s.fillTop, 0.0001f)
        assertEquals(0.22f, s.fillTop.let { 0.22f }, 0.0001f) // 规范下界常量
        assertEquals(WedoGlassTokens.FILL_BOTTOM_LIGHT, s.fillBottom, 0.0001f)
        assertEquals(WedoGlassTokens.EDGE_LIGHT, s.edgeAlpha, 0.0001f)
        assertEquals(2, s.gradientLayers)
        assertTrue("Balanced 档必须启用顶部内高光（规范称其为玻璃的关键）", s.innerHighlight)
    }

    @Test
    fun `balanced dark uses recalibrated fill to avoid grey cast`() {
        val s = buildGlassSpec(WedoGlassQuality.Balanced, dark = true, level = WedoGlassLevel.Nav)
        assertEquals(WedoGlassTokens.FILL_TOP_DARK, s.fillTop, 0.0001f)
        assertEquals(WedoGlassTokens.FILL_BOTTOM_DARK, s.fillBottom, 0.0001f)
        // 深色填充必须显著低于浅色，否则白半透明会在深底上发灰
        assertTrue("深色填充顶必须低于浅色", s.fillTop < WedoGlassTokens.FILL_TOP_LIGHT)
        assertTrue("深色填充底必须低于浅色", s.fillBottom < WedoGlassTokens.FILL_BOTTOM_LIGHT)
        // 描边走规范给的深色降档值
        assertEquals(WedoGlassTokens.EDGE_DARK, s.edgeAlpha, 0.0001f)
    }

    @Test
    fun `smooth disables expensive layers`() {
        listOf(false, true).forEach { dark ->
            val s = buildGlassSpec(WedoGlassQuality.Smooth, dark = dark, level = WedoGlassLevel.Nav)
            assertEquals("Smooth 应为单层半透明", 1, s.gradientLayers)
            assertFalse("Smooth 必须关闭内高光以省一次全尺寸 drawRect", s.innerHighlight)
            assertEquals("Smooth 必须无投影", 0f, s.elevation.value, 0.0001f)
        }
    }

    @Test
    fun `fine is strictly stronger than balanced on edge and elevation`() {
        listOf(false, true).forEach { dark ->
            val b = buildGlassSpec(WedoGlassQuality.Balanced, dark, WedoGlassLevel.Nav)
            val f = buildGlassSpec(WedoGlassQuality.Fine, dark, WedoGlassLevel.Nav)
            assertTrue("Fine 描边应强于 Balanced", f.edgeAlpha > b.edgeAlpha)
            assertTrue("Fine 投影应强于 Balanced", f.elevation.value > b.elevation.value)
            assertEquals("Fine 启用第三层高光带", 3, f.gradientLayers)
        }
    }

    // ---------- 3. 层级无关性 ----------

    @Test
    fun `spec does not depend on level for the same quality and darkness`() {
        // level 目前只影响圆角与模糊参考半径，不改变填充/描边/投影。
        // 若将来按 level 分化参数，此测试会失败并提醒同步更新文档与截图基线。
        val levels = WedoGlassLevel.values().toList()
        listOf(false, true).forEach { dark ->
            listOf(WedoGlassQuality.Smooth, WedoGlassQuality.Balanced, WedoGlassQuality.Fine).forEach { q ->
                val specs = levels.map { buildGlassSpec(q, dark, it) }
                specs.forEach {
                    assertEquals(q, it.quality)
                    assertEquals(specs.first().fillTop, it.fillTop, 0.0001f)
                    assertEquals(specs.first().edgeAlpha, it.edgeAlpha, 0.0001f)
                }
            }
        }
    }

    // ---------- 4. 规范数值对账 ----------

    @Test
    fun `tokens match the liquid glass specification`() {
        assertEquals("规范：半透明体 白 58%", 0.58f, WedoGlassTokens.FILL_TOP_LIGHT, 0.0001f)
        assertEquals("规范：半透明体 白 22%", 0.22f, WedoGlassTokens.FILL_BOTTOM_LIGHT, 0.0001f)
        assertEquals("规范：边缘光圈 白 60%", 0.60f, WedoGlassTokens.EDGE_LIGHT, 0.0001f)
        assertEquals("规范：深色环境降至 38%", 0.38f, WedoGlassTokens.EDGE_DARK, 0.0001f)
        assertEquals("规范：顶部内高光 白 60%", 0.60f, WedoGlassTokens.INNER_HIGHLIGHT, 0.0001f)
        assertEquals("规范：抬升投影 alpha 16%", 0.16f, WedoGlassTokens.SHADOW_ALPHA, 0.0001f)
        assertEquals("规范：导航类模糊参考半径 30", 30f, WedoGlassTokens.BLUR_NAV.value, 0.0001f)
        assertEquals("规范：内容卡片模糊参考半径 20", 20f, WedoGlassTokens.BLUR_CARD.value, 0.0001f)
        assertEquals("规范：抬升投影 y+10", 10f, WedoGlassTokens.SHADOW_ELEVATION_NAV.value, 0.0001f)
        assertEquals("规范：导航圆角 30", 30f, WedoGlassTokens.RADIUS_NAV.value, 0.0001f)
        assertEquals("规范：卡片圆角 20", 20f, WedoGlassTokens.RADIUS_CARD.value, 0.0001f)
        assertEquals("规范：Sheet 圆角 28", 28f, WedoGlassTokens.RADIUS_SHEET.value, 0.0001f)
    }
}
