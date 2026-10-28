package com.wedo.schedule.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.components.WedoDialog
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.WedoApp
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.theme.WedoTheme
import kotlinx.coroutines.launch

@Composable
fun WedoCourseActions(course: CourseEntity, onDismiss: () -> Unit, onEdit: () -> Unit, viewModel: ScheduleViewModel) {
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block(); onDismiss() } catch (_: Exception) { error = "保存失败，请重试" }
            finally { busy = false }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Color.Transparent) {
        val glassStyle = com.wedo.schedule.ui.glass.GlassStyles.panel(com.wedo.schedule.ui.theme.WedoApple.isDark)
        com.wedo.schedule.ui.glass.GlassBackdrop(
            modifier = Modifier.fillMaxWidth(),
            surface = com.wedo.schedule.ui.theme.WedoGlassSurfaceClass.Float,
            quota = com.wedo.schedule.ui.theme.WedoGlassQuotaGrade.G1,
            shape = com.wedo.schedule.ui.theme.WedoAppleShapes.continuous(com.wedo.schedule.ui.theme.WedoAppleDimensions.panelCorner),
            tint = glassStyle.tintColor,
            tintAlpha = glassStyle.tintAlpha,
            rimAlpha = glassStyle.rimAlpha,
            hairline = glassStyle.hairlineColor,
            hairlineAlpha = glassStyle.hairlineAlpha,
            hairlineWidth = 0.5.dp,
            blurRadius = glassStyle.blurRadius,
            vibrancy = glassStyle.vibrancy,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(course.courseName, style = WedoAppleType.title3())
            Text("课程颜色 · 同一门课统一使用", color = WedoTheme.colors.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0xFFAED8FF,0xFFC9B8F4,0xFFA9E7D4,0xFFF2B7CC,0xFFF5D493,0xFFADBDF3).forEach { argb ->
                    val color = Color(argb)
                    Box(Modifier.size(42.dp).background(color, CircleShape).clickable(enabled = !busy) {
                        perform {
                            val repo = WedoApp.get().repository
                            val rows = viewModel.state.value.courses.filter { it.groupId == course.groupId }
                            repo.updateCourseGroup(course.tableId, course.groupId, rows.map {
                                it.copy(color = "#%08X".format(color.toArgb()), colorMode = 2)
                            })
                        }
                    })
                }
            }
            TextButton(onClick = onEdit, enabled = !busy) { Text("编辑课程") }
            TextButton(onClick = {
                perform { WedoApp.get().repository.insertCourse(course.copy(id = 0, groupId = java.util.UUID.randomUUID().toString())) }
            }, enabled = !busy) { Text("复制这次课程") }
            TextButton(onClick = { deleting = true }, enabled = !busy) { Text("删除这次课程", color = WedoTheme.colors.error) }
            error?.let { Text(it, color = WedoTheme.colors.error) }
            }
        }
    }
    if (deleting) WedoDialog(onDismissRequest = { deleting = false },
        title = { Text("删除这次课程？") }, text = { Text("同一门课的其他上课记录会保留。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { perform { WedoApp.get().repository.deleteCourse(course.id) } }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
}
