package com.wedo.schedule.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Test

/**
 * 临时诊断：把 Apple 体系的**实际计算结果**打出来看，而不是只看断言是否通过。
 *
 * 教训来自玻璃方案 —— 那次 27 个测试全绿，观感依然被否。绿勾只说明「我没写错」，
 * 不说明「好不好看」。这个诊断专门暴露三类只能靠数字看出的问题：
 *  1. 层级是否够拉开（卡片 vs 背景的明度差）
 *  2. 分割线是否看得见（0.5pt 细线在纯黑上容易消失）
 *  3. 强调色在两种底色上是否都读得清
 */
class AppleSystemDiagnostic {

    private fun f(v: Double) = String.format("%.3f", v)

    @Test
    fun `打印浅色与深色体系的实际数值`() {
        println("\n" + "=".repeat(78))
        println("Apple 体系诊断报告")
        println("=".repeat(78))

        listOf(false to "LIGHT", true to "DARK").forEach { (dark, name) ->
            val s = appleScheme(WedoSystemColor.Default.light, dark)
            println("\n───── $name ─────")
            println("  背景 grouped      = ${hex(s.background)}")
            println("  卡片 card         = ${hex(s.surface)}")
            println("  三级 tertiary     = ${hex(s.surfaceContainerHigh)}")
            println("  主文字 label      = ${hex(s.onSurface)}")
            println("  次文字 secLabel   = ${hex(s.onSurfaceVariant)}")
            println("  分割线 separator  = ${hex(s.outlineVariant)}")

            println("  ── 层级对比度 ──")
            println("    卡片 / 背景            = ${f(contrastRatio(s.surface, s.background))}   (iOS 约 1.1~1.2，靠极细差分层)")
            println("    三级 / 卡片            = ${f(contrastRatio(s.surfaceContainerHigh, s.surface))}")
            println("    主文字 / 卡片          = ${f(contrastRatio(s.onSurface, s.surface))}")
            println("    次文字 / 卡片          = ${f(contrastRatio(s.onSurfaceVariant, s.surface))}")
            println("    分割线 / 卡片          = ${f(contrastRatio(s.outlineVariant, s.surface))}   (0.5pt 细线，只要 >1.05 就可见)")

            println("  ── 强调色 · 填充版（本身当色块底，配白字） ──")
            val fillRatios = WedoSystemColor.entries.map { c ->
                val accent = if (dark) c.dark else c.light
                val r = contrastRatio(accent, s.surface)
                println("    %-10s %s  对比度 = %s".format(c.displayName, hex(accent), f(r)))
                r
            }
            println("    最亮/最暗比值 = ${f(fillRatios.max() / fillRatios.min())}")

            println("  ── 强调色 · 图标版 accentIcon（当图标，按 1.4.11 需 ≥3.0） ──")
            val iconRatios = WedoSystemColor.entries.map { c ->
                val icon = c.readableColor(dark, WedoColorRole.Icon)
                val r = contrastRatio(icon, s.surface)
                val mark = if (r < 3.0) "   ← 未达标!" else ""
                val adjusted = if (icon == c.color(dark)) "" else "  ← 已调整"
                println("    %-10s %s  对比度 = %s  (原 %s)%s%s".format(
                    c.displayName, hex(icon), f(r),
                    hex(if (dark) c.dark else c.light), adjusted, mark
                ))
                r
            }
            println("    最亮/最暗比值 = ${f(iconRatios.max() / iconRatios.min())}")

            println("  ── 强调色 · 文字版 accentText（当正文，需 ≥4.5） ──")
            val textRatios = WedoSystemColor.entries.map { c ->
                val readable = c.readableColor(dark, WedoColorRole.Text)
                val r = contrastRatio(readable, s.surface)
                val mark = if (r < 4.5) "   ← 未达标!" else ""
                println("    %-10s %s  对比度 = %s  (原 %s)%s".format(
                    c.displayName, hex(readable), f(r),
                    hex(if (dark) c.dark else c.light), mark
                ))
                r
            }
            println("    最亮/最暗比值 = ${f(textRatios.max() / textRatios.min())}  (越接近 1 越齐整)")
        }

        println("\n" + "=".repeat(78) + "\n")
    }

    private fun hex(c: Color): String {
        val r = (c.red * 255f).toInt()
        val g = (c.green * 255f).toInt()
        val b = (c.blue * 255f).toInt()
        val a = c.alpha
        return if (a >= 0.999f) "#%02X%02X%02X".format(r, g, b)
        else "#%02X%02X%02X@%.2f".format(r, g, b, a)
    }
}
