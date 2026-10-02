package com.wedo.schedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wedo.schedule.Tab
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme

/**
 * 底部常驻 tab bar 的尺寸契约（单一事实来源）。
 *
 * 为什么把高度做成常量：内容区需要为「覆盖在内容之上的 tab bar」预留底部留白
 * （见 [LocalNavExtraBottomPadding]），两侧必须读同一个值，否则最后一行会被遮住。
 * 各页**不得**再写死 52 之类的魔数。
 */
object WedoTabBarDefaults {
    /**
     * tab bar 本体高度（**不含**系统导航栏安全区）。
     *
     * 52pt 与改造前的底部标签栏保持一致；安全区由 [WedoTabBar] 内部的
     * `navigationBarsPadding()` 单独吸收，故内容区留白需自行加上安全区高度
     * （见 `MainActivity` 的 `LocalNavExtraBottomPadding` 提供点）。
     */
    val contentHeight = 52.dp
}

/**
 * 底部常驻导航标签栏（3 项：今天 / 课表 / 设置）。
 *
 * 设计依据 `DESIGN_SPEC_V2 §5.1`：
 *  - **只放导航、不放动作**（HIG: *Use a tab bar to support navigation*）——
 *    「＋ 添加课程」已移出底栏，改由课表页顶部发出。
 *  - **常驻不隐藏**（HIG: *Don't disable or hide tab bar buttons*）——
 *    移除旧的「下滑收起 Dock」联动后，本组件不接收任何隐藏/折叠信号。
 *  - 图标用**填充态**；选中态用强调色（`accentIcon` 上色图标、`accentText` 上色文字）。
 *  - 触控目标 ≥ 44×44pt（[WedoAppleDimensions.minTouchTarget]）。
 *  - 材质只在**控件层**：实色 `surfaceContainerLow` + 0.5pt 顶部分隔线
 *    （与既有底部标签栏观感一致；内容卡片一律实色，不用玻璃）。
 *
 * @param current 当前选中的 Tab
 * @param onSelect 点击某项时回调（由调用方切换 Tab，组件自身无状态）
 * @param modifier 由调用方传入对齐等修饰（通常在底部 overlay 中 `align(BottomCenter)`）
 */
@Composable
fun WedoTabBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WedoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow)
            .navigationBarsPadding()
    ) {
        // 0.5pt 顶部分隔线 —— Apple 标签栏的分隔特征，用令牌而非写死。
        HorizontalDivider(
            thickness = WedoAppleDimensions.hairline,
            color = colors.outlineVariant
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(WedoTabBarDefaults.contentHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                TabBarItem(
                    tab = tab,
                    selected = tab == current,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(tab) }
                )
            }
        }
    }
}

/**
 * 单个 tab 项：上下排列的「图标 + 标签」。
 *
 * 选中态用强调色、未选中用中性次级色；触控高度守住 44pt，
 * 宽度由父 Row 的 `weight(1f)` 平分（3 项在手机上均 ≥ 44pt）。
 * 语义上标记为 `Role.Tab` 并暴露 `selected`，保证 TalkBack 可朗读选中态。
 */
@Composable
private fun TabBarItem(
    tab: Tab,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = WedoTheme.colors
    val label = stringResource(tab.labelRes)
    val iconTint = if (selected) WedoApple.accentIcon else colors.onSurfaceVariant
    val labelTint = if (selected) WedoApple.accentText else colors.onSurfaceVariant
    Column(
        modifier
            .heightIn(min = WedoAppleDimensions.minTouchTarget)
            .wedoPress(onClick = onClick)
            .semantics {
                role = Role.Tab
                contentDescription = label
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = WedoAppleType.caption2(),
            color = labelTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
