package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun WedoWeekHeader(week: Int, maxWeek: Int, select: (Int) -> Unit) {
    val collapsed = LocalWedoCollapsed.current
    val display = LocalWedoDisplay.current
    val height by animateDpAsState(if (collapsed) 48.dp else 64.dp,
        if (display.motion) spring(dampingRatio = .9f) else snap(), label = "headerHeight")
    var picker by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Shanghai"))) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(ZoneId.of("Asia/Shanghai")); kotlinx.coroutines.delay(60_000) } }
    val colors = SleepyTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 6.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(height).wedoGlass(RoundedCornerShape(24.dp)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Outlined.ChevronLeft, "上一周", week > 1) { select(week - 1) }
            Column(Modifier.weight(1f).fillMaxHeight().wedoPress { picker = true },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("第 $week 周", fontSize = if (collapsed) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            GlassIconButton(Icons.Outlined.ChevronRight, "下一周", week < maxWeek) { select(week + 1) }
        }
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = colors.surface) {
        Text("选择周次", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.fillMaxWidth().heightIn(max = 360.dp),
            contentPadding = PaddingValues(16.dp)) {
            items(maxWeek) { index ->
                FilterChip(index + 1 == week, { select(index + 1); picker = false },
                    label = { Text("第 ${index + 1} 周") }, modifier = Modifier.padding(4.dp))
            }
        }
    }
}
