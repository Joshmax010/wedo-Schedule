package com.wedo.schedule.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.*

/**
 * 应用级外壳组件（原 WedoGlass.kt）。
 *
 * 本文件原为玻璃组件（`wedoGlass()` 等），按「全面取消玻璃质感」的指令重写：
 *  - `WedoBackground` 从「蓝色渐变 + 光斑」改为 **Apple grouped 纯色背景**
 *  - `wedoGlass()` **已删除**（唯一调用点均已改为实色卡片）
 *  - `WedoDock` 从「悬浮玻璃胶囊」改为 **iOS 标准底部标签栏**
 *  - `wedoPress` 保留按压反馈，但改为 Apple 的缩放式（不用涟漪）
 *
 * 另：原 `PillNavigationBar.kt`（含 `NavDockSpec` / `DockNavigationBar` 悬浮玻璃胶囊）
 * 已整文件删除 —— 底栏只有 `WedoDock` 这一种形态，不再提供会渲染玻璃的第二形态。
 */

/**
 * 底栏占位高度 —— 供各页滚动容器在末尾留白，避免最后一项被标签栏遮住。
 *
 * 语义已随 `WedoDock` 变化：旧值 84dp 是为**悬浮胶囊**预留的（胶囊 64dp + 悬空 12dp
 * + 投影余量）。iOS 标准标签栏是**通栏贴底**的，不需要为「悬空」留白，只要覆盖
 * 标签栏本体（52dp）即可；安全区由 `navigationBarsPadding()` 自行吸收，不重复计入。
 */
val LocalNavExtraBottomPadding = staticCompositionLocalOf { 52.dp }

/**
 * Apple 分组背景。
 *
 * 此前这里是「垂直蓝色渐变 + 两个径向光斑」，属于给 App 一个**彩色身份** ——
 * 正是被否定的那类做法。iOS 的底色永远是**中性灰白**（grouped）或**纯黑**
 * （dark），层次靠卡片与背景的明度差表达，不靠渐变和光斑。
 *
 * 浅色 #F2F2F7 / 深色 #000000，与 UIKit 的 systemGroupedBackground 一致。
 */
@Composable
fun WedoBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val base = if (WedoApple.isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    Box(modifier.background(base), content = content)
}

/**
 * Apple 按压反馈。
 *
 * 与 Material 的区别：**不用涟漪**，改为整体轻微缩放 + 透明度微降。
 * iOS 的反馈是「整个元素轻轻陷下去」，涟漪是 Android 的语汇。
 */
@Composable
fun Modifier.wedoPress(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val motion = LocalWedoDisplay.current.motion
    val scale by animateFloatAsState(
        if (pressed && motion) 0.97f else 1f,
        if (motion) spring(dampingRatio = .72f, stiffness = 520f) else snap(),
        label = "wedoPress"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interactions, indication = null, role = Role.Button,
            onLongClick = onLongClick, onClick = onClick
        )
}

/**
 * 纯图标按钮 —— Apple 风格。
 *
 * 视觉上只有一个色化的图标，没有圆形底、没有描边；触控区撑到 44pt。
 * 之前这里是「40dp 玻璃圆底 + 24dp 图标」，容器感是 Material 的做法。
 */
@Composable
fun WedoIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    tint: Color = WedoApple.accentIcon,
    onClick: () -> Unit
) {
    val action = if (enabled) Modifier.wedoPress(onClick = onClick) else Modifier
    Box(
        modifier = Modifier.size(WedoAppleDimensions.minTouchTarget).then(action),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) tint else tint.copy(alpha = WedoTheme.Alpha.inactive)
        )
    }
}

/**
 * iOS 实心强调色按钮 —— 全 app 主 CTA 唯一入口。
 *
 * 与 M3 `Button` 的差别：**完全圆角（capsule）**、高度 50pt、无阴影无描边、
 * 文字 17pt Semibold、按压是整体变淡（不是涟漪+变暗）。iOS 的主按钮就是这个样子。
 *
 * @param destructive 危险操作（删除等）—— 用系统红，不是强调色
 */
@Composable
fun WedoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val colors = WedoTheme.colors
    val bg = if (destructive) Color(0xFFFF3B30) else WedoApple.accent
    val bgResolved = if (enabled) bg else colors.onSurfaceVariant.copy(alpha = 0.32f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(WedoAppleShapes.capsule)
            .background(bgResolved)
            .then(if (enabled) Modifier.wedoPress(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = WedoAppleType.headline(),
            color = Color.White.copy(alpha = if (enabled) 1f else 0.7f)
        )
    }
}

/**
 * iOS 描边按钮（次要动作）—— 用于「取消」这类并列动作。
 * 浅底透明、1pt 强调色描边、强调色文字。
 */
@Composable
fun WedoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = WedoApple.accentText
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(WedoAppleShapes.capsule)
            .border(1.dp, accent.copy(alpha = 0.4f), WedoAppleShapes.capsule)
            .wedoPress(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = WedoAppleType.headline(), color = accent)
    }
}

/**
 * iOS 标准底部标签栏。
 *
 * 与原玻璃悬浮胶囊的三处差别：
 *  1. **通栏**，不是居中悬浮的 74% 宽胶囊
 *  2. **实色 + 0.5pt 顶部细线**，不是半透明玻璃
 *  3. 「添加」不再是凸起的圆形浮动按钮 —— iOS 的标签栏里没有 FAB 这个语汇
 *
 * 三个入口等宽平分（课表 / 添加 / 设置），中间「添加」用强调色图标区分。
 */
@Composable
fun WedoDock(settings: Boolean, onSchedule: () -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val colors = WedoTheme.colors
    Column(
        Modifier.testTag("wedo-dock").fillMaxWidth()
            .background(colors.surfaceContainerLow)
            .navigationBarsPadding()
    ) {
        // 顶部 0.5pt 细线 —— Apple 标签栏的分隔特征
        HorizontalDivider(thickness = WedoAppleDimensions.hairline, color = colors.outlineVariant)
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockItem(Icons.Outlined.CalendarMonth, "课表", !settings, Modifier.weight(1f), onSchedule)
            DockItem(Icons.Outlined.Add, "添加", false, Modifier.weight(1f), onAdd, accentIcon = true)
            DockItem(Icons.Outlined.Settings, "设置", settings, Modifier.weight(1f), onSettings)
        }
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    action: () -> Unit,
    accentIcon: Boolean = false
) {
    val colors = WedoTheme.colors
    val inactive = colors.onSurfaceVariant
    // 图标和标题分两档：22dp 图标按 1.4.11 走 3:1，10dp 标题是正文字号走 4.5:1。
    // 多数色两者取值相同，只有橙色/薄荷这类才拉开 —— 但拉不开的那个位置
    // 本来就是最需要拉开的（小字最怕对比度不够）。
    val iconTint = when {
        accentIcon || selected -> WedoApple.accentIcon
        else -> inactive
    }
    val labelTint = when {
        accentIcon || selected -> WedoApple.accentText
        else -> inactive
    }
    Column(
        modifier.height(WedoAppleDimensions.minTouchTarget).wedoPress(onClick = action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = WedoAppleType.caption2(), color = labelTint)
    }
}
