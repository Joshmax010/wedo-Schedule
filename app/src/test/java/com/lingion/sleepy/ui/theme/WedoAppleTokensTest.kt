package com.lingion.sleepy.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Apple 令牌层验证。
 *
 * 重点验证**对比度**：设计上「淡底 + 同色系深字」这个决策，理由是白字不可读。
 * 这里用 WCAG 公式把它变成可断言的数字，而不是靠「看起来够亮」的主观判断。
 */
class WedoAppleTokensTest {

    // ── 系统色 ──────────────────────────────────────────────────────────────

    @Test
    fun `系统色共 12 个`() {
        assertEquals(12, WedoSystemColor.entries.size)
    }

    @Test
    fun `系统色深浅两版必须不同`() {
        // 深色版是单独标定的，不是简单复用浅色值
        WedoSystemColor.entries.forEach { c ->
            assertTrue(
                "${c.displayName} 的深浅两版不应相同",
                c.light != c.dark
            )
        }
    }

    @Test
    fun `byName 未知输入回落默认色`() {
        assertEquals(WedoSystemColor.Default, WedoSystemColor.byName(null))
        assertEquals(WedoSystemColor.Default, WedoSystemColor.byName("NotAColor"))
        assertEquals(WedoSystemColor.Purple, WedoSystemColor.byName("Purple"))
    }

    @Test
    fun `默认强调色是系统蓝`() {
        assertEquals(WedoSystemColor.Blue, WedoSystemColor.Default)
        assertEquals(Color(0xFF007AFF), WedoSystemColor.Default.light)
        assertEquals(Color(0xFF0A84FF), WedoSystemColor.Default.dark)
    }

    @Test
    fun `深色版整体比浅色版亮`() {
        // Apple 的 Vibrant 变体在深色下普遍提亮，保证纯黑底上有足够亮度
        WedoSystemColor.entries.forEach { c ->
            assertTrue(
                "${c.displayName} 深色版应不暗于浅色版",
                relativeLuminance(c.dark) >= relativeLuminance(c.light) - 0.02
            )
        }
    }

    // ── 课程块对比度（核心断言） ────────────────────────────────────────────
    //
    // 门槛是 6.5 而非 WCAG 的 4.5 —— 理由见 WedoAppleTokens.TARGET_TEXT_CONTRAST。
    // 简言之：4.5 只是「合法」，会让蓝色系停在 4.5 而绿色系到 10，
    // 同一张课表两种观感。6.5 是「够清楚」且「还认得出颜色」的交点。

    @Test
    fun `浅色模式所有课程块文字对比度达标`() {
        val surface = Color(0xFFFFFFFF)
        WedoSystemColor.entries.forEach { c ->
            val block = wedoCourseBlockColors(c.light, dark = false, surface = surface)
            val ratio = contrastRatio(block.title, block.tint)
            assertTrue(
                "${c.displayName} 浅色课程名对比度仅 $ratio，应 ≥$TARGET_TEXT_CONTRAST",
                ratio >= TARGET_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun `深色模式所有课程块文字对比度达标`() {
        val surface = Color(0xFF1C1C1E)
        WedoSystemColor.entries.forEach { c ->
            val block = wedoCourseBlockColors(c.dark, dark = true, surface = surface)
            val ratio = contrastRatio(block.title, block.tint)
            assertTrue(
                "${c.displayName} 深色课程名对比度仅 $ratio，应 ≥$TARGET_TEXT_CONTRAST",
                ratio >= TARGET_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun `副标题对比度也达标`() {
        // 副标题字号更小（Caption 12pt），对比度不达标更致命
        val lightSurface = Color(0xFFFFFFFF)
        val darkSurface = Color(0xFF1C1C1E)
        val floor = TARGET_TEXT_CONTRAST - 0.8
        WedoSystemColor.entries.forEach { c ->
            val lightBlock = wedoCourseBlockColors(c.light, dark = false, surface = lightSurface)
            assertTrue(
                "${c.displayName} 浅色副标题对比度仅 ${contrastRatio(lightBlock.subtitle, lightBlock.tint)}",
                contrastRatio(lightBlock.subtitle, lightBlock.tint) >= floor
            )

            val darkBlock = wedoCourseBlockColors(c.dark, dark = true, surface = darkSurface)
            assertTrue(
                "${c.displayName} 深色副标题对比度仅 ${contrastRatio(darkBlock.subtitle, darkBlock.tint)}",
                contrastRatio(darkBlock.subtitle, darkBlock.tint) >= floor
            )
        }
    }

    @Test
    fun `12 个色相的观感强度是齐整的`() {
        // 这条是给「各色对比度差三倍很难看」这个真实问题兜底。
        // 只要有一个色相掉队（低于目标一半），整张课表就会显得脏。
        val surface = Color(0xFF1C1C1E)
        val ratios = WedoSystemColor.entries.map { c ->
            c.displayName to contrastRatio(
                wedoCourseBlockColors(c.dark, dark = true, surface = surface).let { it.title },
                wedoCourseBlockColors(c.dark, dark = true, surface = surface).tint
            )
        }
        val weakest = ratios.minByOrNull { it.second }!!
        val strongest = ratios.maxByOrNull { it.second }!!
        assertTrue(
            "色相强弱过于悬殊：最弱 ${weakest.first}=${"%.2f".format(weakest.second)}，" +
                "最强 ${strongest.first}=${"%.2f".format(strongest.second)}",
            strongest.second / weakest.second <= 2.0
        )
    }

    @Test
    fun `白字在淡底上确实不可读（记录这个反例）`() {
        // 这条测试是「为什么不用白字」的书面证据，防止后人改回去
        val surface = Color(0xFFFFFFFF)
        val block = wedoCourseBlockColors(WedoSystemColor.Blue.light, dark = false, surface = surface)
        val whiteOnTint = contrastRatio(Color.White, block.tint)
        assertTrue(
            "白字在淡底上对比度 $whiteOnTint，应当明显低于 4.5（这正是弃用白字的原因）",
            whiteOnTint < 3.0
        )
    }

    @Test
    fun `色条保持原始系统色`() {
        val block = wedoCourseBlockColors(WedoSystemColor.Orange.light, dark = false, surface = Color.White)
        assertEquals(WedoSystemColor.Orange.light, block.bar)
    }

    @Test
    fun `淡底色不等于 surface 也不等于基色`() {
        val surface = Color(0xFFFFFFFF)
        val block = wedoCourseBlockColors(WedoSystemColor.Purple.light, dark = false, surface = surface)
        assertTrue("淡底不应等于 pure 基色", block.bar != block.tint)
        assertTrue("淡底在白卡上应可见（不等于白）", block.tint != surface)
    }

    @Test
    fun `同色系深字保持色相`() {
        // 深字必须仍是「这个色」，不能调成灰 —— 否则课程色相辨识失效
        val block = wedoCourseBlockColors(Color(0xFFFF3B30), dark = false, surface = Color.White)
        // 红色系：R 分量应显著高于 B
        assertTrue(
            "红色课程的深字应仍偏红，实际 = ${block.title}",
            block.title.red > block.title.blue
        )
    }

    // ── 对比度工具自检 ──────────────────────────────────────────────────────

    @Test
    fun `对比度工具符合 WCAG 已知值`() {
        // 黑白对比度应为 21:1
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        // 同色对比度应为 1:1
        assertEquals(1.0, contrastRatio(Color.Red, Color.Red), 0.01)
    }

    @Test
    fun `对比度与顺序无关`() {
        val a = contrastRatio(Color.Black, Color.White)
        val b = contrastRatio(Color.White, Color.Black)
        assertEquals(a, b, 0.0001)
    }

    // ── 字体 ────────────────────────────────────────────────────────────────

    @Test
    fun `正文字号不小于 17pt`() {
        // Apple 的 Body 是 17pt，比 M3 的 16pt 大一号
        assertEquals(17f, WedoAppleType.body().fontSize.value, 0.01f)
    }

    @Test
    fun `大标题 34pt 且字距为负或极小`() {
        val t = WedoAppleType.largeTitle()
        assertEquals(34f, t.fontSize.value, 0.01f)
        // Apple 大标题字距接近 0，不收得太紧
        assertTrue("大标题字距应 ≥0", t.letterSpacing.value >= 0f)
    }

    @Test
    fun `字阶单调递减`() {
        val sizes = listOf(
            WedoAppleType.largeTitle(),
            WedoAppleType.title1(),
            WedoAppleType.title2(),
            WedoAppleType.title3(),
            WedoAppleType.headline(),
            WedoAppleType.body(),
            WedoAppleType.callout(),
            WedoAppleType.subheadline(),
            WedoAppleType.footnote(),
            WedoAppleType.caption1(),
            WedoAppleType.caption2()
        ).map { it.fontSize.value }

        sizes.zipWithNext().forEach { (a, b) ->
            assertTrue("字阶应递减，但遇到 $a → $b", a >= b)
        }
    }

    @Test
    fun `每次调用返回独立实例`() {
        // 防退化：若改成共享 val，调用方 .copy() 会污染其他屏
        val a = WedoAppleType.body()
        val b = WedoAppleType.body()
        assertTrue(a !== b)
    }

    // ── 尺寸 ────────────────────────────────────────────────────────────────

    @Test
    fun `页边距是 16pt`() {
        assertEquals(16f, WedoAppleDimensions.pageMargin.value, 0.01f)
    }

    @Test
    fun `最小触控为 44pt`() {
        assertEquals(44f, WedoAppleDimensions.minTouchTarget.value, 0.01f)
    }

    @Test
    fun `分隔线是 0_5pt`() {
        assertEquals(0.5f, WedoAppleDimensions.hairline.value, 0.01f)
    }

    @Test
    fun `连续曲率半径大于视觉半径`() {
        val visual = 10f
        val actual = WedoAppleShapes.continuous(androidx.compose.ui.unit.Dp(visual))
        val size = androidx.compose.ui.geometry.Size(100f, 100f)
        val density = androidx.compose.ui.unit.Density(1f)
        val radius = (actual.topStart as androidx.compose.foundation.shape.CornerSize)
            .toPx(size, density)
        assertTrue("连续曲率等效半径应大于标注值，实际 $radius", radius > visual)
    }

    @Test
    fun `连续曲率系数为 1_2`() {
        // 系数变化会让所有圆角观感偏移，锁死它
        val dp20 = androidx.compose.ui.unit.Dp(20f)
        val shape = WedoAppleShapes.continuous(dp20)
        val size = androidx.compose.ui.geometry.Size(100f, 100f)
        val density = androidx.compose.ui.unit.Density(1f)
        val radius = (shape.topStart as androidx.compose.foundation.shape.CornerSize).toPx(size, density)
        assertEquals(24f, radius, 0.01f)
    }

    // ── 动效曲线 ────────────────────────────────────────────────────────────

    @Test
    fun `缓动曲线端点正确`() {
        val e = WedoAppleMotion.standardEasing
        assertEquals(0f, e.transform(0f), 0.001f)
        assertEquals(1f, e.transform(1f), 0.001f)
    }

    @Test
    fun `缓动曲线中段应在 0 到 1 之间`() {
        val e = WedoAppleMotion.standardEasing
        listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f).forEach { x ->
            val y = e.transform(x)
            assertTrue("transform($x) = $y 越界", y in -0.01f..1.01f)
        }
    }

    @Test
    fun `标准缓动起步快于匀速`() {
        // cubic-bezier(0.32, 0.72, 0, 1) 的特点是前期进度快于线性
        val e = WedoAppleMotion.standardEasing
        assertTrue("标准缓动在 25% 处应快于线性", e.transform(0.25f) > 0.25f)
    }

    @Test
    fun `动效时长在 HIG 区间`() {
        assertTrue(WedoAppleMotion.enterDurationMs in 250..400)
        assertTrue(WedoAppleMotion.exitDurationMs in 200..350)
        assertTrue(WedoAppleMotion.exitDurationMs <= WedoAppleMotion.enterDurationMs)
    }

    // ── 强调色作文字/图标时的可读性 ────────────────────────────────────────
    //
    // 下面三条是**打印真实数值才发现的**问题，不是先写测试再实现：
    // 系统色是按「色块底 + 白字」标定的，直接拿来染图标会不可读 ——
    // 实测浅色模式下 12 个色有 11 个低于 4.5，黄色只有 1.512:1。

    @Test
    fun `浅色模式下 12 个强调色作文字都达标`() {
        val surface = Color(0xFFFFFFFF)
        WedoSystemColor.entries.forEach { c ->
            val readable = c.readableColor(isDark = false)
            val r = contrastRatio(readable, surface)
            assertTrue(
                "${c.displayName} 文字版在浅色卡片上对比度仅 ${"%.3f".format(r)}，未达 4.5",
                r >= 4.5
            )
        }
    }

    @Test
    fun `深色模式下 12 个强调色作文字都达标`() {
        val surface = Color(0xFF1C1C1E)
        WedoSystemColor.entries.forEach { c ->
            val readable = c.readableColor(isDark = true)
            val r = contrastRatio(readable, surface)
            assertTrue(
                "${c.displayName} 文字版在深色卡片上对比度仅 ${"%.3f".format(r)}，未达 4.5",
                r >= 4.5
            )
        }
    }

    @Test
    fun `文字版强调色的强弱是齐整的`() {
        // 深色模式下 12 色本身差异就不小（黄 12.05 vs 靛紫 4.93），
        // 这里只要求不出现「三倍级」的参差，阈值按实测留一点余量
        val surface = Color(0xFF1C1C1E)
        val ratios = WedoSystemColor.entries.map { contrastRatio(it.readableColor(true), surface) }
        assertTrue(
            "深色模式文字版强调色强弱过于悬殊：${"%.2f".format(ratios.max())} / ${"%.2f".format(ratios.min())}",
            ratios.max() / ratios.min() <= 2.6
        )
    }

    @Test
    fun `已达标且色相合适的颜色不被改动`() {
        // readableColor 只处理不达标的情况，达标色必须原样返回，
        // 否则「选了蓝色却显示成深蓝」会让用户觉得颜色没生效
        val c = WedoSystemColor.Indigo
        assertEquals(c.light, c.readableColor(isDark = false))
    }

    // ── 层级分层 ────────────────────────────────────────────────────────────

    @Test
    fun `浅色模式三级容器与卡片必须能分层`() {
        // 曾经两者都是 #FFFFFF，对比度恰好 1.000 —— 任何依赖这一层抬升的组件
        // 都拿不到分层，属于静默失效（编译过、测试过、视觉上就是「没效」）
        val s = appleScheme(WedoSystemColor.Default.light, dark = false)
        val r = contrastRatio(s.surfaceContainerHigh, s.surface)
        assertTrue("三级容器与卡片对比度为 $r，无法分层", r >= 1.03)
    }

    @Test
    fun `卡片与页面背景能分层（深浅两模式）`() {
        listOf(false, true).forEach { dark ->
            val s = appleScheme(WedoSystemColor.Default.light, dark = dark)
            val r = contrastRatio(s.surface, s.background)
            val mode = if (dark) "深色" else "浅色"
            assertTrue("$mode 模式卡片与背景对比度为 $r，无法分层", r >= 1.05)
        }
    }
}
