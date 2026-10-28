package com.wedo.schedule.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P4 契约：uniform 顺序唯一源；AGSL 不支持 discard；运行时 shader 不进入滚动路径。
 */
class GlassShaderUniformContractTest {

    private val agsl = File("src/main/res/raw/liquid_glass.agsl").readText()
    private val uniforms = GlassUniforms.entries

    @Test
    fun `agsl declares the exact uniform order`() {
        val declared = Regex("""uniform\s+(\w+)\s+(\w+);""")
            .findAll(agsl)
            .map { it.groupValues[2] to it.groupValues[1] }
            .toList()
            .filterNot { it.first == GlassUniforms.INPUT_CONTENT }

        assertEquals(uniforms, declared)
    }

    @Test
    fun `agsl header documents the uniform order`() {
        val headerComments = agsl.lineSequence()
            .filter { it.trimStart().startsWith("//") }
            .map { it.trim() }
            .toList()
        uniforms.forEachIndexed { index, (name, type) ->
            val line = headerComments.firstOrNull { it.contains("$index: $name") }
            assertTrue("uniform 注释缺少 $index: $name", line != null && line.contains(type))
        }
    }

    @Test
    fun `agsl uses eval and avoids discard`() {
        assertTrue("AGSL 必须用 uContent.eval", agsl.contains("uContent.eval("))
        assertTrue("AGSL 不得使用 discard", !agsl.contains("discard"))
    }

    @Test
    fun `runtime shader uses compose render effect without bitmap conversion`() {
        val src = File("src/main/java/com/wedo/schedule/ui/glass/GlassShader.android.kt").readText()
        assertTrue("必须用 createRuntimeShaderEffect", src.contains("createRuntimeShaderEffect"))
        assertTrue("输入名必须走契约", src.contains(GlassUniforms.INPUT_CONTENT))
        assertTrue("禁止离屏转位图", !src.contains("toImageBitmap("))
    }
}
