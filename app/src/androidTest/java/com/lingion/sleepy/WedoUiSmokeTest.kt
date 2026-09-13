package com.lingion.sleepy

import android.content.pm.ActivityInfo
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.*
import com.lingion.sleepy.ui.theme.*
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** Opt-in only: adds one isolated fixture table and restores the existing selection/settings. */
class WedoUiSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun weeklyHomeImportsDockAndDarkTheme() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("wedoUiQa") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.get(context)
        val previousDefault = runBlocking { db.timeTableDao().getDefault()?.id }
        val mode = AppPrefs.getThemeMode(context)
        val theme = AppPrefs.getThemeKey(context)
        val days = AppPrefs.getVisibleDays(context)
        val display = WedoPreferences.read(WedoPreferences.prefs(context))
        val privacy = context.getSharedPreferences("wedo_privacy", Context.MODE_PRIVATE)
        val accepted = privacy.getBoolean("accepted_v1", false)
        val acceptedPresent = privacy.contains("accepted_v1")
        val monday = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val id = runBlocking {
            val tableId = db.timeTableDao().insert(TimeTableEntity(name = "UI 验证 · 模拟课程", startDate = monday.toString(), nodesPerDay = 12))
            val samples = listOf(
                Triple("高等数学", 1, 1), Triple("大学英语", 2, 1), Triple("计算机基础", 3, 3),
                Triple("大学物理", 4, 1), Triple("材料成型", 5, 3), Triple("体育", 6, 1),
                Triple("线性代数", 2, 5), Triple("无机化学", 4, 5), Triple("程序设计", 6, 5),
                Triple("创新创业", 6, 5), Triple("数据结构", 1, 7), Triple("概率统计", 5, 7))
            db.courseDao().insertAll(samples.mapIndexed { index, (name, day, start) ->
                CourseEntity(groupId = "ui-qa-$tableId-$index", tableId = tableId, courseName = name,
                    teacher = "测试教师", room = "公教B206", day = day, startNode = start, step = 2,
                    startWeek = 1, endWeek = 16, color = "#FF6750A4")
            })
            db.timeTableDao().setDefault(tableId)
            tableId
        }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            privacy.edit().putBoolean("accepted_v1", true).commit()
            WedoPreferences.write(context, WedoDisplay(quality = "fine"))
            AppPrefs.setThemeKey(context, ThemePresets.KEY_OCEAN)
            AppPrefs.setThemeMode(context, AppPrefs.THEME_MODE_LIGHT)
            AppPrefs.setVisibleDays(context, (1..7).toSet())
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("高等数学").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("第 2 周").assertIsDisplayed()
            compose.onNodeWithTag("wedo-dock").assertIsDisplayed()
            capture(context, "home-light")
            compose.onNodeWithContentDescription("下一周").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("第 3 周").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("上一周").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("第 2 周").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            compose.onNodeWithTag("week-grid-scroll-2").performTouchInput { swipeUp() }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("wedo-dock").fetchSemanticsNodes().isEmpty() }
            capture(context, "dock-hidden")
            compose.onNodeWithTag("week-grid-scroll-2").performTouchInput { swipeDown() }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("wedo-dock").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("高等数学") and hasAnyAncestor(hasTestTag("week-grid-scroll-2"))).performClick()
            compose.onNodeWithText("编辑这节课").performScrollTo().assertIsDisplayed()
            compose.waitForIdle()
            capture(context, "course-detail")
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription("添加课表").performClick()
            compose.onNodeWithText("从 WakeUp 迁移").assertIsDisplayed()
            compose.onNodeWithText("先在 WakeUp 选择“导出为日历文件”").assertIsDisplayed()
            compose.onAllNodesWithText("WakeUp JSON 文件").assertCountEquals(0)
            capture(context, "add-schedule")
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("外观与手感").assertIsDisplayed()
            capture(context, "settings")
            compose.onNodeWithText("课表").performClick()
            AppPrefs.setThemeMode(context, AppPrefs.THEME_MODE_DARK)
            scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("高等数学").fetchSemanticsNodes().isNotEmpty() }
            capture(context, "home-dark")
        } finally {
            scenario?.close()
            AppPrefs.setThemeMode(context, mode)
            AppPrefs.setThemeKey(context, theme)
            AppPrefs.setVisibleDays(context, days)
            WedoPreferences.write(context, display)
            if (acceptedPresent) privacy.edit().putBoolean("accepted_v1", accepted).commit()
            else privacy.edit().remove("accepted_v1").commit()
            runBlocking {
                db.timeTableDao().deleteById(id)
                previousDefault?.let { db.timeTableDao().setDefault(it) }
            }
        }
    }

    private fun capture(context: Context, name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = context.getExternalFilesDir("ui-qa")!!
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
