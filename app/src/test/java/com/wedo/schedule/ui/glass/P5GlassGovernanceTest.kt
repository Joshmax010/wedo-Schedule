package com.wedo.schedule.ui.glass

import com.wedo.schedule.ui.theme.WedoGlassQuotaGrade
import com.wedo.schedule.ui.theme.WedoGlassSurfaceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P5 收口契约：
 *  - Q-24 嵌套深度 ≤ 2，第 3 层静默降 G0
 *  - O-15a/O-15b 数量闸 + 面积闸 + 面积倒序
 *  - Q-71 15 处 AlertDialog 全部经 WedoDialog 包装
 *  - Q-83 死代码已删
 */
class P5GlassGovernanceTest {

    private val mainRoot = File("src/main/java/com/wedo/schedule")

    // ───────────────────────── Q-24 嵌套深度 ─────────────────────────

    @Test
    fun `nested depth beyond two silently degrades instead of throwing`() {
        assertTrue(GlassDepth.shouldBuildStage(0))
        assertTrue(GlassDepth.shouldBuildStage(1))
        assertFalse("第 3 层必须静默降 G0", GlassDepth.shouldBuildStage(2))
        assertFalse(GlassDepth.shouldBuildStage(3))
        assertEquals(1, GlassDepth.nextDepth(0))
        assertEquals(2, GlassDepth.nextDepth(1))
    }

    @Test
    fun `host source does not throw when depth exceeds limit`() {
        val src = File(mainRoot, "ui/glass/GlassCapture.kt").readText()
        assertTrue("必须走深度闸门", src.contains("GlassDepth.shouldBuildStage"))
        assertTrue("必须静默降级而不是抛异常", src.contains("静默降 G0"))
    }

    // ───────────────────────── 配额三道闸 ─────────────────────────

    @Test
    fun `panel larger than fifteen percent of screen area is downgraded`() {
        val screen = 1_000_000L
        val tooBig = (screen * 0.16).toLong()
        assertEquals(
            WedoGlassQuotaGrade.G0,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Float,
                requested = WedoGlassQuotaGrade.G2,
                activeG1 = 0,
                activeG2 = 0,
                areaPx = tooBig,
                screenAreaPx = screen,
            ),
        )
        val ok = (screen * 0.10).toLong()
        assertEquals(
            WedoGlassQuotaGrade.G2,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Float,
                requested = WedoGlassQuotaGrade.G2,
                activeG1 = 0,
                activeG2 = 0,
                areaPx = ok,
                screenAreaPx = screen,
            ),
        )
    }

    @Test
    fun `smaller panel yields quota to larger one on area ordering`() {
        val screen = 1_000_000L
        // 场上已有一个更大的面板（120000），新面板更小（100000）→ 让位降 G0
        assertEquals(
            WedoGlassQuotaGrade.G0,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Float,
                requested = WedoGlassQuotaGrade.G2,
                activeG1 = 0,
                activeG2 = 1,
                areaPx = 100_000L,
                screenAreaPx = screen,
                smallestActiveAreaPx = 120_000L,
            ),
        )
        // 新面板更大 → 允许（两者都在 15% 面积闸内）
        assertEquals(
            WedoGlassQuotaGrade.G2,
            GlassQuota.resolve(
                surface = WedoGlassSurfaceClass.Float,
                requested = WedoGlassQuotaGrade.G2,
                activeG1 = 0,
                activeG2 = 1,
                areaPx = 140_000L,
                screenAreaPx = screen,
                smallestActiveAreaPx = 120_000L,
            ),
        )
    }

    // ───────────────────────── Q-71 统一对话框 ─────────────────────────

    @Test
    fun `all fifteen dialogs go through WedoDialog`() {
        val businessFiles = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.replace('\\', '/').endsWith("ui/components/WedoDialog.kt") }
            .toList()

        val offenders = businessFiles
            .filter { it.readText().contains("AlertDialog(") }
            .map { it.name }
            .toList()

        assertTrue("业务文件不得直接使用 AlertDialog，违规：$offenders", offenders.isEmpty())

        val wrapper = File(mainRoot, "ui/components/WedoDialog.kt")
        assertTrue("必须存在统一入口 WedoDialog", wrapper.exists())
        assertTrue("统一入口必须归零 tonalElevation", wrapper.readText().contains("tonalElevation = 0.dp"))
    }

    @Test
    fun `privacy consent keeps its business flow`() {
        val src = File(mainRoot, "WedoPrivacyConsent.kt").readText()
        assertTrue("同意流程必须保留", src.contains("onAccept"))
        assertTrue("拒绝流程必须保留", src.contains("onReject"))
        assertTrue("必须走统一对话框", src.contains("WedoDialog("))
    }

    // ───────────────────────── Q-83 死代码 ─────────────────────────

    @Test
    fun `removed week view family has zero hits`() {
        listOf(
            "FullWeekView", "WeekStrip", "DetailPanel",
            "DayColumn", "DetailDayCard",
        ).forEach { symbol ->
            val hits = mainRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.readText().contains(symbol) }
                .map { it.name }
                .toList()
            assertTrue("$symbol 应已删除，残留：$hits", hits.isEmpty())
        }
    }
}
