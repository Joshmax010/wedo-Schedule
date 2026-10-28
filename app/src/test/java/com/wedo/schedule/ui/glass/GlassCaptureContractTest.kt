package com.wedo.schedule.ui.glass

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P2 收尾契约：玻璃采样路径禁止 `toImageBitmap()`，滚动时才能安全 LiveStrip。
 */
class GlassCaptureContractTest {

    private val glassDir = File("src/main/java/com/wedo/schedule/ui/glass")

    @Test
    fun `glass capture package has no toImageBitmap calls`() {
        val offenders = glassDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("toImageBitmap(") }
            .map { it.name }
            .toList()

        assertTrue("禁用 toImageBitmap，违规文件：$offenders", offenders.isEmpty())
    }
}
