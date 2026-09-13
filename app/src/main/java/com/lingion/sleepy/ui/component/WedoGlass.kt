package com.lingion.sleepy.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.*
import com.lingion.sleepy.util.CourseColorUtil

@Composable
fun WedoBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = CourseColorUtil.isPaletteDark(SleepyTheme.palette)
    val base = if (dark) listOf(Color(0xFF061225), Color(0xFF102D50))
        else listOf(Color(0xFFF7FAFF), Color(0xFFE4EFFD))
    Box(modifier.background(Brush.verticalGradient(base)).drawBehind {
        val light = if (dark) Color(0xFF257DDB).copy(alpha = .15f) else Color(0xFF87BCFF).copy(alpha = .19f)
        drawCircle(Brush.radialGradient(listOf(light, Color.Transparent),
            center = Offset(size.width * .86f, size.height * .13f), radius = size.width * .8f),
            radius = size.width * .8f, center = Offset(size.width * .86f, size.height * .13f))
        drawCircle(Brush.radialGradient(listOf(light, Color.Transparent),
            center = Offset(0f, size.height * .91f), radius = size.width * .8f),
            radius = size.width * .8f, center = Offset(0f, size.height * .91f))
    }, content = content)
}

/** Layered glass works from API 26; only decoration is reduced on low-RAM devices. */
@Composable
fun Modifier.wedoGlass(shape: Shape = RoundedCornerShape(28.dp)): Modifier {
    val dark = CourseColorUtil.isPaletteDark(SleepyTheme.palette)
    val display = LocalWedoDisplay.current
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
    }
    val simple = display.quality == "smooth" || lowRam
    val refined = display.quality == "fine" && !simple
    // “精致”档保持真正的通透层次：不再用高不透明白底把每个玻璃容器画成白卡片。
    // Android 26 无系统级实时折射 API，因此这里用低透明渐变、环境光和细边缘高光模拟玻璃，
    // 同时保证老设备与低内存设备仍有稳定、清晰的降级表现。
    val fill = when {
        dark && simple -> listOf(Color(0xFF244D79).copy(alpha = .74f), Color(0xFF0D213D).copy(alpha = .80f))
        dark && refined -> listOf(Color(0xFF61A9F2).copy(alpha = .20f), Color(0xFF0D294B).copy(alpha = .34f))
        dark -> listOf(Color(0xFF315F8E).copy(alpha = .49f), Color(0xFF102A49).copy(alpha = .58f))
        simple -> listOf(Color(0xFFF5FAFF).copy(alpha = .76f), Color(0xFFCFE3FA).copy(alpha = .62f))
        refined -> listOf(Color(0xFFEAF4FF).copy(alpha = .23f), Color(0xFFAFCFF2).copy(alpha = .17f))
        else -> listOf(Color(0xFFF2F8FF).copy(alpha = .48f), Color(0xFFC8DEF6).copy(alpha = .34f))
    }
    val edge = if (dark) {
        Color(0xFF8BC4FF).copy(alpha = if (simple) .30f else if (refined) .62f else .50f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = if (simple) .70f else if (refined) .78f else .88f)
    }
    return this.shadow(if (simple) 0.dp else if (refined) 9.dp else 6.dp, shape,
        ambientColor = Color(0xFF246BBC), spotColor = Color(0xFF246BBC))
        .clip(shape).background(Brush.linearGradient(fill))
        .border(if (refined) 1.3.dp else 1.dp, Brush.linearGradient(listOf(edge, edge.copy(alpha = .15f), edge.copy(alpha = .65f))), shape)
}

@Composable
fun Modifier.wedoPress(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val motion = LocalWedoDisplay.current.motion
    val scale by animateFloatAsState(if (pressed && motion) .96f else 1f,
        if (motion) spring(dampingRatio = .68f, stiffness = 480f) else snap(), label = "wedoPress")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = interactions, indication = null, role = Role.Button,
            onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val action = if (enabled) Modifier.wedoPress(onClick = onClick) else Modifier
    Box(
        modifier = Modifier.size(40.dp).wedoGlass(CircleShape).then(action),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, modifier = Modifier.size(24.dp),
            tint = SleepyTheme.colors.primary.copy(alpha = if (enabled) 1f else .35f))
    }
}

@Composable
fun WedoDock(settings: Boolean, onSchedule: () -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.testTag("wedo-dock").fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp, top = 10.dp)) {
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(.74f).widthIn(max = 300.dp)
            .height(56.dp).wedoGlass(RoundedCornerShape(30.dp)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
            DockItem(Icons.Outlined.CalendarMonth, "课表", !settings, onSchedule)
            Spacer(Modifier.width(52.dp))
            DockItem(Icons.Outlined.Settings, "设置", settings, onSettings)
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-10).dp).size(60.dp)
            .wedoGlass(CircleShape).padding(4.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF62B5FF), Color(0xFF2476F5))), CircleShape)
            .border(1.dp, Color.White.copy(alpha = .65f), CircleShape).wedoPress(onClick = onAdd),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, "添加课表", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, selected: Boolean, action: () -> Unit) {
    val colors = SleepyTheme.colors
    Column(Modifier.width(68.dp).height(48.dp).clip(RoundedCornerShape(22.dp))
        .background(if (selected) colors.primary.copy(alpha = .10f) else Color.Transparent)
        .wedoPress(onClick = action), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (selected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(21.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) colors.primary else colors.onSurfaceVariant)
    }
}
