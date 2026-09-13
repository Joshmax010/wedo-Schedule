package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.*
import com.lingion.sleepy.util.CourseColorUtil

val LocalWedoCourseLongClick = staticCompositionLocalOf<(CourseEntity) -> Unit> { {} }

fun wedoCourseColor(course: CourseEntity, dark: Boolean): Color {
    // Use course identity independent of random parser group IDs and database row ordering.
    if (CourseColorUtil.hasCustomColor(course)) {
        runCatching { return Color(android.graphics.Color.parseColor(course.color)) }
    }
    val hue = CourseColorUtil.stableHue(course.courseName.trim())
    return CourseColorUtil.hslToColor(hue, if (dark) .53f else .73f, if (dark) .26f else .82f)
}

/** Choose against actual card luminance, including custom colors. */
fun wedoCourseTextColor(background: Color): Color {
    val navy = Color(0xFF10213A)
    val l = background.luminance()
    val whiteContrast = 1.05f / (l + .05f)
    val navyContrast = (l + .05f) / (navy.luminance() + .05f)
    return if (whiteContrast >= navyContrast) Color.White else navy
}

@Composable
fun WedoCourseCard(course: CourseEntity, modifier: Modifier = Modifier,
    conflict: Boolean = false, shape: Shape = RoundedCornerShape(10.dp),
    onClick: () -> Unit, onLongClick: () -> Unit) {
    val dark = CourseColorUtil.isPaletteDark(SleepyTheme.palette)
    val bg = if (com.lingion.sleepy.util.AppPrefs.isCourseColorless(LocalContext.current) && !CourseColorUtil.hasCustomColor(course))
        SleepyTheme.colors.surfaceVariant else wedoCourseColor(course, dark)
    val fg = wedoCourseTextColor(bg)
    val fields = LocalWedoDisplay.current.fields
    val context = LocalContext.current
    val content = listOf(
        "name" to course.courseName, "room" to course.room, "teacher" to course.teacher,
        "weeks" to "${course.startWeek}–${course.endWeek}周" + when(course.type) { 1 -> "(单)"; 2 -> "(双)"; else -> "" },
        "sections" to course.shortNodeString(context), "note" to course.note
    ).filter { it.first in fields && it.second.isNotBlank() }
    BoxWithConstraints(modifier.padding(1.dp).clip(shape).background(bg)
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .045f), Color.Transparent)))
        .border(if (conflict) 1.6.dp else 1.dp,
            if (conflict) Color(0xFFFF627C) else if (dark) bg.copy(alpha = .9f) else Color.White.copy(alpha = .92f), shape)
        .semantics { contentDescription = (if(conflict) "课程时间冲突，" else "") + course.courseName + "，" + course.room }
        .wedoPress(onLongClick = onLongClick, onClick = onClick).padding(4.dp)) {
        val lineBudget = (maxHeight.value / (14f * androidx.compose.ui.platform.LocalDensity.current.fontScale)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content.forEachIndexed { index, (field, text) ->
                if (index < lineBudget) Text(text, color = fg,
                    fontSize = if (field == "name") 11.sp else 10.sp,
                    lineHeight = if (field == "name") 15.sp else 13.sp,
                    fontWeight = if (field == "name") FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = if (field == "name") (lineBudget - (content.size - 1)).coerceIn(1, 4) else 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        if (conflict) Box(Modifier.align(Alignment.TopEnd).size(13.dp)
            .background(Color(0xFFE94160), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) {
            Text("!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
