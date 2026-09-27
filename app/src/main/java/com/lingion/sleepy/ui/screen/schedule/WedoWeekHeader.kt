package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.ui.component.*
import com.lingion.sleepy.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 周次栏。
 *
 * Apple 化改造：**去掉玻璃底与容器**。iOS 的导航区不是「浮在内容上的玻璃板」，
 * 而是**与背景同色的空白**，靠留白和字重划出层次 —— 这属于 HIG 的 Deference
 * （界面让位于内容）。所以这里只剩一行：左右箭头 + 居中的「第 N 周」。
 *
 * 中间的「第 N 周 / 日期」整块可点，点开周次选择器；点击区高度守住 44pt。
 */
@Composable
fun WedoWeekHeader(week: Int, maxWeek: Int, select: (Int) -> Unit) {
    val collapsed = LocalWedoCollapsed.current
    val display = LocalWedoDisplay.current
    val headerHeight by animateDpAsState(
        if (collapsed) 44.dp else 60.dp,
        if (display.motion) spring(dampingRatio = .9f) else snap(),
        label = "headerHeight"
    )
    var picker by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Shanghai"))) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            kotlinx.coroutines.delay(60_000)
        }
    }
    val colors = SleepyTheme.colors

    Column(Modifier.fillMaxWidth().padding(horizontal = WedoAppleDimensions.pageMargin)) {
        Row(
            Modifier.fillMaxWidth().height(headerHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeekChevron(Icons.Outlined.ChevronLeft, "上一周", week > 1) { select(week - 1) }

            // 中间信息区：整块可点，唤起周次选择器
            Column(
                Modifier.weight(1f).fillMaxHeight().wedoPress { picker = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "第 $week 周",
                    fontSize = if (collapsed) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)),
                    style = WedoAppleType.footnote(),
                    color = colors.onSurfaceVariant
                )
            }

            WeekChevron(Icons.Outlined.ChevronRight, "下一周", week < maxWeek) { select(week + 1) }
        }
    }

    if (picker) {
        ModalBottomSheet(
            onDismissRequest = { picker = false },
            // 去掉玻璃容器，使用 Apple 的二级分组背景色
            containerColor = colors.surfaceContainerLow
        ) {
            Text(
                "选择周次",
                Modifier.padding(horizontal = WedoAppleDimensions.pageMargin, vertical = 8.dp),
                style = WedoAppleType.title2(),
                color = colors.onSurface
            )
            LazyVerticalGrid(
                GridCells.Adaptive(64.dp),
                Modifier.fillMaxWidth().heightIn(max = 360.dp),
                contentPadding = PaddingValues(WedoAppleDimensions.pageMargin)
            ) {
                items(maxWeek) { index ->
                    val selected = index + 1 == week
                    // 用「淡色底 + 强调色字」表达选中态，而非玻璃高光
                    TextButton(
                        onClick = { select(index + 1); picker = false },
                        modifier = Modifier.padding(4.dp).heightIn(min = WedoAppleDimensions.minTouchTarget),
                        colors = if (selected) {
                            ButtonDefaults.textButtonColors(
                                containerColor = WedoApple.accent.copy(alpha = SleepyTheme.Alpha.tinted),
                                contentColor = WedoApple.accent
                            )
                        } else {
                            ButtonDefaults.textButtonColors(contentColor = colors.onSurface)
                        }
                    ) {
                        Text("第 ${index + 1} 周", style = WedoAppleType.body())
                    }
                }
            }
        }
    }
}

/**
 * 前进/后退周的箭头按钮。
 *
 * 不用 Material 的 IconButton 默认样式（48dp + 涟漪圆底），改为 Apple 的
 * 纯图标 + 44pt 触控区：视觉上只是一个细箭头，没有容器感。
 */
@Composable
private fun WeekChevron(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) {
        WedoApple.accent
    } else {
        SleepyTheme.colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(WedoAppleDimensions.minTouchTarget)
    ) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}
