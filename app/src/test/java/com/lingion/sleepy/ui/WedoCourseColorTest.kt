package com.lingion.sleepy.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.wedoCourseColor
import com.lingion.sleepy.ui.component.wedoCourseTextColor
import com.lingion.sleepy.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class WedoCourseColorTest {
    private val course = CourseEntity(groupId = "import-a", tableId = 1, courseName = "高等数学",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")

    @Test fun repeatImportsKeepColorDespiteNewDatabaseAndGroupIds() {
        for (dark in listOf(false, true)) assertEquals(wedoCourseColor(course, dark),
            wedoCourseColor(course.copy(id = 37, groupId = "import-b", tableId = 2), dark))
    }

    @Test fun autoTextHasAccessibleContrastAcrossEveryHue() {
        for (dark in listOf(false, true)) for (index in 0..359) {
            val bg = wedoCourseColor(course.copy(courseName = "测试课程$index"), dark)
            val fg = wedoCourseTextColor(bg)
            val ratio = (maxOf(bg.luminance(), fg.luminance()) + .05f) /
                (minOf(bg.luminance(), fg.luminance()) + .05f)
            assertTrue("Contrast $ratio for $index, dark=$dark", ratio >= 4.5f)
        }
    }

    @Test fun customBackgroundExtremesChooseReadableText() {
        assertEquals(Color.White, wedoCourseTextColor(Color.Black))
        assertEquals(Color(0xFF10213A), wedoCourseTextColor(Color.White))
    }

    @Test fun userAccentSurvivesWedoSurfaceConversion() {
        val custom = LightScheme.copy(primary = Color.Magenta)
        assertEquals(Color.Magenta, wedoColors(custom, false, false).primary)
        assertEquals(Color(0xFF08172C), wedoColors(DarkScheme, true, true).background)
    }
}
