package com.wedo.schedule.ui.motion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3 覆盖契约：生产代码里不允许再散落裸 `spring()` / `tween()`；
 * 每个现存动画入口都必须经过 `WedoMotionPolicy` 或其便捷封装。
 */
class WedoMotionCoverageTest {

    private val root = File("src/main/java/com/wedo/schedule")

    private fun read(rel: String) = File(root, rel).readText()

    private fun source(rel: String): String = read(rel)

    @Test
    fun `raw springs and tweens only live in ui motion`() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.replace('\\', '/').contains("/ui/motion/") }
            .filter { it.readText().containsRegex(Regex("""\b(spring|tween)\(""")) }
            .map { it.path.replace('\\', '/') }
            .toList()

        assertTrue("裸弹簧/裸 tween 必须收敛到 ui/motion，违规：$offenders", offenders.isEmpty())
    }

    @Test
    fun `current motion entries are policy gated`() {
        val entries = listOf(
            "MainActivity.kt",
            "ui/components/WedoTabBar.kt",
            "ui/components/SegmentedSwitcher.kt",
            "ui/components/SettingsCards.kt",
            "ui/screens/schedule/ScheduleScreen.kt",
            "ui/screens/imports/ImportWizard.kt",
        )
        entries.forEach { rel ->
            val src = source(rel)
            assertTrue(
                "$rel 未接入 WedoMotionPolicy",
                src.contains("WedoMotionPolicy") ||
                    src.contains("rememberMotionTransition") ||
                    src.contains("motionSpec"),
            )
        }
    }

    @Test
    fun `press has minimum dwell and always returns to one`() {
        val src = source("ui/motion/WedoPress.kt")
        assertTrue("必须定义 80ms 最小驻留", src.contains("PRESS_MIN_DWELL_MS = 80L"))
        assertTrue("必须等待最小驻留", src.contains("delay(PRESS_MIN_DWELL_MS)"))
        assertTrue("终值必须回到 1f", src.contains("else 1f"))
        assertTrue("必须经过族表", src.contains("WedoSprings.press()"))
    }
}

private fun String.containsRegex(regex: Regex): Boolean = regex.containsMatchIn(this)
