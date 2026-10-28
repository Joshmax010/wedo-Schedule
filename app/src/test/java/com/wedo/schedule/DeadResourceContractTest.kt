package com.wedo.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 死资源契约测试（REQ-P4-03）：
 *
 *  - `import_qr` / `schedule_share_table` 为**零引用**字符串，必须在 6 个语言目录同步删除，
 *    且全仓源码不得残留 `R.string.<该键>` 引用。
 *  - `tab_today` **不删**（V3 重设计后「今天」页复活，转为存活资源），6 个语言目录必须齐备。
 *
 * 仓库无 Robolectric，靠「读资源文件 + 遍历源码」守住契约。
 */
class DeadResourceContractTest {

    private val locales = listOf(
        "values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW"
    )

    private val deadKeys = listOf("import_qr", "schedule_share_table")

    private fun stringsXml(locale: String): String =
        TestProjectFiles.read("app/src/main/res/$locale/strings.xml")

    /** 递归收集 app/src 下全部 .kt 源码文本。 */
    private fun allKotlinSources(): List<Pair<String, String>> {
        val root = System.getProperty("wedo.project.root")
            ?: error("Gradle did not provide wedo.project.root")
        val srcRoot = File(root, "app/src")
        return srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.path to it.readText() }
            .toList()
    }

    @Test
    fun `dead string keys are removed from every locale`() {
        locales.forEach { loc ->
            val xml = stringsXml(loc)
            deadKeys.forEach { key ->
                assertFalse("$loc 不得残留死资源 $key", xml.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun `no source references the dead string keys`() {
        val sources = allKotlinSources()
        deadKeys.forEach { key ->
            val ref = "R.string.$key"
            sources.forEach { (path, text) ->
                assertFalse("$path 仍引用 $ref", text.contains(ref))
            }
        }
    }

    @Test
    fun `tab_today is kept in every locale`() {
        locales.forEach { loc ->
            assertTrue("$loc 必须保留 tab_today", stringsXml(loc).contains("name=\"tab_today\""))
        }
    }

    @Test
    fun `deleted widget variant labels are removed from every locale`() {
        // REQ-P5-01：WeekList / WeekView / TwoDay 三种小组件裁撤后，其标签键必须一并删除。
        val widgetLabels = listOf(
            "widget_week_list_label", "widget_week_list_small_label",
            "widget_twoday_label", "widget_twoday_small_label",
            "widget_week_view_label", "widget_week_view_small_label"
        )
        locales.forEach { loc ->
            val xml = stringsXml(loc)
            widgetLabels.forEach { key ->
                assertFalse("$loc 不得残留已裁撤小组件标签 $key", xml.contains("name=\"$key\""))
            }
        }
    }
}
