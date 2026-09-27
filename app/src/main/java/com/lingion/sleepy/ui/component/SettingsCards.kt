package com.lingion.sleepy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.WedoApple
import com.lingion.sleepy.ui.theme.WedoAppleShapes
import com.lingion.sleepy.ui.theme.WedoAppleType
import com.lingion.sleepy.ui.theme.noRippleClickable

/**
 * 设置页公共卡片组件 — 自 AppearanceScreen 抽出(外观/通用两页共用):
 * SectionHeader 分组标题 / SettingsCard 折叠卡 / DisplayModeOption 单选项 / SettingToggleRow 开关行。
 * SettingsFlatCard: 不折叠的平铺卡 — 内容只是简单选择或单个开关的设置项专用(用户 2026-09-03 指令:
 * 双选/三选纯选择、单开关项禁折叠, 直接露出)。
 */

/**
 * iOS 风格开关。
 *
 * 与 M3 `Switch` 的差别：轨道更宽扁（51×31 是 iOS 的实机比例）、滑块是纯白圆且带
 * 轻微投影、关闭态轨道是中性灰（不是 low-contrast 的 surfaceVariant 系带灰）。
 * M3 的 Switch 滑块偏大、关闭态轨道带紫灰色，一眼能看出不是 iOS。
 */
@Composable
fun WedoToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = SleepyTheme.colors
    val trackOn = WedoApple.accent
    // iOS 关闭态轨道: 浅色 #E9E9EA / 深色 #39393D
    val trackOff = if (WedoApple.isDark) Color(0xFF39393D) else Color(0xFFE9E9EA)
    val track by animateColorAsState(if (checked) trackOn else trackOff, label = "wedoToggleTrack")
    val knob by animateDpAsState(if (checked) 24.dp else 2.dp, label = "wedoToggleKnob")

    Box(
        modifier = Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(WedoAppleShapes.capsule)
            .background(track)
            .noRippleClickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = knob)
                .size(27.dp)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(1.dp, CircleShape)
        )
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    val colors = SleepyTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = WedoAppleType.caption1(), color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsCard(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val colors = SleepyTheme.colors
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "settings-arrow"
    )
    Column(
        modifier = Modifier.fillMaxWidth().clip(WedoAppleShapes.card).background(colors.surfaceContainer).noRippleClickable(onToggle).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = WedoAppleType.body(), color = colors.onSurface, modifier = Modifier.weight(1f))
            // 箭头随展开旋转, 与内容动画同拍
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation)
            )
        }
        // 展开动画: 高度+淡入同拍, 替代此前 if(expanded) 瞬间弹出
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                content()
            }
        }
    }
}

/**
 * 平铺设置卡(不折叠) — 标题行右侧内嵌课表页同款 SegmentedSwitcher(用户 2026-09-03 指令:
 * 整块圆角矩形 + 色块在元素上滑动, 和标题同一行非必要不换行)。
 * 用于内容为纯选项选择或单个开关的设置项; 有滑杆/多段逻辑的仍用 SettingsCard 折叠。
 * options 为空 = 单开关卡, 由 content 自行露出开关。
 */
@Composable
fun SettingsFlatCard(
    title: String,
    subtitle: String? = null,
    options: List<String> = emptyList(),
    selectedKey: Int = 0,
    onSelect: (Int) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(WedoAppleShapes.card).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题 weight(1f) 吃满剩余宽 → tab 永远贴本行最右(与开关行贴右同一逻辑)
            Text(
                text = title,
                style = WedoAppleType.body(),
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (options.isNotEmpty()) {
                // tab 宽 = n × 最宽段文字 + 段内边距(16dp×2) + 容器内边距(4dp×2)。
                // 禁用 IntrinsicSize.Min: CJK 的 minIntrinsicWidth 是单字宽, 配 weight(1f) 会把
                // 每段压到一个汉字宽导致全部换行 —— 必须用 TextMeasurer 实测宽度。
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val labelStyle = WedoAppleType.caption1()
                val maxLabelPx = options.maxOf { textMeasurer.measure(AnnotatedString(it), labelStyle).size.width }
                val tabWidth = with(density) {
                    ((maxLabelPx + 32.dp.toPx()) * options.size + 8.dp.toPx()).toDp()
                }
                SegmentedSwitcher(
                    options = options.mapIndexed { i, label -> i to label },
                    selected = selectedKey,
                    onSelect = onSelect,
                    // 高度 36dp: 介于开关本体(32)与组件默认(42)之间 — 轨道不挤, 行高仍与开关行一致
                    modifier = Modifier.width(tabWidth).height(36.dp),
                    containerColor = colors.surfaceContainerHighest
                )
            }
        }
        if (subtitle != null) {
            Text(text = subtitle, style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
        }
        content()
    }
}

@Composable
fun DisplayModeOption(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Row(modifier = Modifier.fillMaxWidth().noRippleClickable(onClick).padding(vertical = 10.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            // iOS 选中态不染文字色, 只在右侧打勾 —— 文字变色会让整列文字跳色
            Text(text = label, style = WedoAppleType.body(), color = colors.onSurface)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Outlined.Check, null, tint = WedoApple.accentText, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null) {
    val colors = SleepyTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = WedoAppleType.body(), color = colors.onSurface)
            if (subtitle != null) {
                Text(text = subtitle, style = WedoAppleType.footnote(), color = colors.onSurfaceVariant)
            }
        }
        WedoToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}
