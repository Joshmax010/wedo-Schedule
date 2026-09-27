package com.wedo.schedule.ui.component

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
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.theme.*
import com.wedo.schedule.util.CourseColorUtil

val LocalWedoCourseLongClick = staticCompositionLocalOf<(CourseEntity) -> Unit> { {} }

/** 课程块最多绘制的文字行数（课程名 + 若干副信息行） */
private const val MAX_COURSE_LINES = 4

/**
 * 课程基色。
 *
 * 保留原有的「有自定义色用自定义色，否则按课程名稳定散列出色相」逻辑 ——
 * 这是辨识度的根，不动。变的是**如何用它**：以前直接铺满整块（饱和色块），
 * 现在只用作色条与文字色相，块底走 12% 淡底（见 [WedoCourseCard]）。
 */
fun wedoCourseColor(course: CourseEntity, dark: Boolean): Color {
    // Use course identity independent of random parser group IDs and database row ordering.
    if (CourseColorUtil.hasCustomColor(course)) {
        runCatching { return Color(android.graphics.Color.parseColor(course.color)) }
    }
    val hue = CourseColorUtil.stableHue(course.courseName.trim())
    return CourseColorUtil.hslToColor(hue, if (dark) .53f else .73f, if (dark) .26f else .82f)
}

/**
 * 课程块上的文字色。
 *
 * Apple 化后**不再需要**「按底色亮度在白/深蓝之间二选一」 ——
 * 那时是因为底色是饱和色块，只能二选一。现在底色是固定的淡底，
 * 文字色由 [wedoCourseBlockColors] 按同色系推导，对比度有单测保证。
 * 保留此函数仅供少数仍要「实色底 + 反白字」的场景（如色条标签）。
 */
fun wedoCourseTextColor(background: Color): Color {
    val navy = Color(0xFF10213A)
    val l = background.luminance()
    val whiteContrast = 1.05f / (l + .05f)
    val navyContrast = (l + .05f) / (navy.luminance() + .05f)
    return if (whiteContrast >= navyContrast) Color.White else navy
}

/**
 * 课程表上的课程块。
 *
 * **Apple 化重写**，与原实现的三处关键差别：
 *
 *  1. **实色块 → 淡底块**。原来整块铺饱和色（`l=0.82` 的彩底）+ 白色描边 +
 *     顶部白色高光渐变，视觉很重、很「贴纸」。Apple 的课程类界面（如日历）
 *     用**很淡的色底 + 同色系的深色文字**，块与块之间靠留白和左侧色条区分。
 *  2. **白字 → 同色系深字**。白字在淡底上对比度只有约 1.2:1（不可读），
 *     这是退化后被明确否掉的方案；同色系深字可达 6.5:1 以上。
 *  3. **左侧 4px 实色条**。课程辨识从「整块颜色」转移到这条细色条 ——
 *     色相信息一点没丢，但界面轻了一个量级。
 *
 * 冲突态的红色描边保留（这是功能性提示，不能省），但改为更细的 1.5pt 且
 * 用 iOS systemRed，不再用刺眼的 #FF627C。
 */
@Composable
fun WedoCourseCard(
    course: CourseEntity,
    modifier: Modifier = Modifier,
    conflict: Boolean = false,
    shape: Shape = RoundedCornerShape(8.dp),
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dark = WedoApple.isDark
    val surface = WedoTheme.colors.surfaceContainerLow
    val colorless = com.wedo.schedule.util.AppPrefs.isCourseColorless(LocalContext.current)

    // 无色模式：不做色相区分，整块走中性淡底
    val block = if (colorless && !CourseColorUtil.hasCustomColor(course)) {
        WedoCourseBlockColors(
            tint = WedoTheme.colors.surfaceContainerHigh,
            bar = WedoTheme.colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive),
            title = WedoTheme.colors.onSurface,
            subtitle = WedoTheme.colors.onSurfaceVariant
        )
    } else {
        WedoApple.courseBlock(base = wedoCourseColor(course, dark), surface = surface)
    }

    val fields = LocalWedoDisplay.current.fields
    val context = LocalContext.current
    val content = listOf(
        "name" to course.courseName,
        "room" to course.room,
        "teacher" to course.teacher,
        "weeks" to "${course.startWeek}–${course.endWeek}周" + when (course.type) { 1 -> "(单)"; 2 -> "(双)"; else -> "" },
        "sections" to course.shortNodeString(context),
        "note" to course.note
    ).filter { it.first in fields && it.second.isNotBlank() }

    val conflictColor = if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)

    Box(
        modifier.padding(1.dp)
            .clip(shape)
            .background(block.tint)
            .border(
                width = if (conflict) 1.5.dp else 0.dp,
                color = if (conflict) conflictColor else Color.Transparent,
                shape = shape
            )
            .semantics {
                contentDescription = (if (conflict) "课程时间冲突，" else "") + course.courseName + "，" + course.room
            }
            .wedoPress(onLongClick = onLongClick, onClick = onClick)
    ) {
        // 左侧 4px 实色条 —— 课程辨识的载体
        Box(
            Modifier.align(Alignment.CenterStart)
                .width(WedoAppleDimensions.courseBarWidth)
                .fillMaxHeight()
                .background(block.bar)
        )

        Column(
            Modifier.fillMaxSize().padding(
                start = WedoAppleDimensions.courseBarWidth + 5.dp,
                top = 4.dp,
                end = 4.dp,
                bottom = 4.dp
            ),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // 最多 4 行：课程名 + 教室 / 教师 / 周次 / 节次 / 备注。
            // 真实可容纳行数由父级（课表网格单元格）的高度约束决定，
            // 超出部分会被裁掉 —— 这里不做动态测量，保持绘制廉价（一屏可能有 60+ 块）。
            content.take(MAX_COURSE_LINES).forEachIndexed { index, (field, text) ->
                Text(
                    text,
                    color = if (field == "name") block.title else block.subtitle,
                    style = if (field == "name") {
                        WedoAppleType.caption1().copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp)
                    } else {
                        WedoAppleType.caption2().copy(fontSize = 10.sp, lineHeight = 13.sp)
                    },
                    maxLines = if (field == "name") 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (conflict) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(13.dp)
                    .background(conflictColor, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
