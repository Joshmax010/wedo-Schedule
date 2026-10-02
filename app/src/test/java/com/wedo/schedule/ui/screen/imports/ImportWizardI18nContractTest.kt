package com.wedo.schedule.ui.screen.imports

import com.wedo.schedule.TestProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入向导 i18n 契约（TASK-05 E-债务② / TASK-06 收口）：硬编码中文文案必须**全部**走字符串资源。
 *
 * 2026-10-01 真机反馈：教务直连行改为**完全同款**（无角标、无高亮、不提学校名），
 * 副标题改为中性功能描述 [com.wedo.schedule.R.string.import_wiz_jw_subtitle]；
 * 原 `import_wiz_jw_badge` / `import_wiz_jw_school_hint` 等死键已从 6 语言删除。
 *
 * 无 Robolectric：靠「读源码 + 断言」守住。
 */
class ImportWizardI18nContractTest {

    private val wizardRaw = TestProjectFiles.read(WIZARD)

    /** 剥掉整行注释 + 行尾注释。 */
    private fun code(src: String): String = src.lineSequence()
        .map { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) ""
            else line.substringBefore("//")
        }
        .joinToString("\n")

    private val cjk = Regex("[\\u4e00-\\u9fff]")

    @Test
    fun `wizard executable code has no hardcoded chinese`() {
        val src = code(wizardRaw)
        val remaining = cjk.findAll(src).map { it.value }.toList()
        assertTrue(
            "导入向导可执行代码不得残留硬编码中文（应走 stringResource），命中: $remaining",
            remaining.isEmpty()
        )
    }

    @Test
    fun `jw source row subtitle goes through string resources`() {
        // 2026-10-01 真机反馈：教务直连行副标题改为中性功能描述
        // 「登录学校教务系统抓取课表」（不再提学校名、不再有角标），仍走资源。
        assertTrue("教务直连行副标题须走 stringResource", wizardRaw.contains("R.string.import_wiz_jw_subtitle"))
    }

    @Test
    fun `new wizard i18n keys exist in all six locales`() {
        val locales = listOf(
            "values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW"
        )
        val keys = listOf(
            "import_wiz_title_detail",
            "import_wiz_title_preview",
            "import_wiz_title_done",
            "import_wiz_close",
            "import_wiz_pick_way",
            "import_wiz_source_jw",
            "import_wiz_source_file",
            "import_wiz_source_text",
            "import_wiz_source_manual",
            "import_wiz_file_sub",
            "import_wiz_text_sub",
            "import_wiz_detail_file",
            "import_wiz_detail_text",
            "import_wiz_pick_file",
            "import_wiz_parse_preview",
            "import_wiz_metric_course",
            "import_wiz_metric_conflict",
            "import_wiz_view_schedule",
            "import_wiz_again",
            "import_wiz_jw_subtitle"
        )
        for (loc in locales) {
            val xml = TestProjectFiles.read("app/src/main/res/$loc/strings.xml")
            keys.forEach { key ->
                assertTrue("$loc 缺少 $key", xml.contains("name=\"$key\""))
            }
        }
    }

    private companion object {
        const val WIZARD = "app/src/main/java/com/wedo/schedule/ui/screen/imports/ImportWizard.kt"
    }
}
