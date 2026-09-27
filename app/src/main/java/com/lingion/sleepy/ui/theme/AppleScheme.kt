package com.lingion.sleepy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Apple 语义配色 —— 替换原来的 M3 紫色底与「蓝白/藏青」底色。
 *
 * 核心原则：**chrome 无色**。界面骨架（背景 / 卡片 / 分隔线 / 标签）全部是中性灰阶，
 * 饱和色只出现在两处：用户选定的强调色，以及课程块本身。
 *
 * 数值全部对齐 UIKit 的语义色，不是自己调的：
 *
 * | 用途 | 浅色 | 深色 | UIKit 对应 |
 * |---|---|---|---|
 * | 页面底（分组列表） | #F2F2F7 | #000000 | systemGroupedBackground |
 * | 卡片/列表行底 | #FFFFFF | #1C1C1E | secondarySystemGroupedBackground |
 * | 三级容器 | #FFFFFF | #2C2C2E | tertiarySystemGroupedBackground |
 * | 主文字 | #000000 | #FFFFFF | label |
 * | 次文字 | #3C3C43α60% | #EBEBF5α60% | secondaryLabel |
 * | 三级文字 | #3C3C43α30% | #EBEBF5α30% | tertiaryLabel |
 * | 分隔线 | #3C3C43α36% | #545458α65% | separator |
 * | 填充/输入框底 | #767680α12% | #767680α32% | systemFill |
 *
 * 深色底用纯黑（用户已确认）：OLED 省电、层次最强 —— 卡片 #1C1C1E 浮在纯黑上，
 * 明度差一眼可辨。
 */

// ── 浅色 ─────────────────────────────────────────────────────────────────────

/** 浅色模式的中性色骨架 */
internal object AppleNeutralLight {
    val groupedBackground = Color(0xFFF2F2F7)   // 页面底
    val cardBackground = Color(0xFFFFFFFF)      // 卡片 / 列表行
    // 三级容器必须**不等于卡片**：原来两者都是 #FFFFFF，实测「三级/卡片」对比度
    // 恰为 1.000 —— 任何依赖这一层做抬升的组件都拿不到任何分层，等于白写。
    // iOS 浅色下 elevated 层用极浅灰，与白卡片形成 1.06 左右的差。
    val tertiaryBackground = Color(0xFFF7F7FA)

    val label = Color(0xFF000000)
    val secondaryLabel = Color(0x993C3C43)      // #3C3C43 @ 60%
    val tertiaryLabel = Color(0x4D3C3C43)       // #3C3C43 @ 30%
    val quaternaryLabel = Color(0x2E3C3C43)     // #3C3C43 @ 18%

    val separator = Color(0x5C3C3C43)           // #3C3C43 @ 36%
    val opaqueSeparator = Color(0xFFC6C6C8)

    val systemFill = Color(0x1F767680)          // #767680 @ 12%
    val secondaryFill = Color(0x2E767680)       // @ 18%
}

// ── 深色 ─────────────────────────────────────────────────────────────────────

/** 深色模式的中性色骨架（纯黑底） */
internal object AppleNeutralDark {
    val groupedBackground = Color(0xFF000000)   // 纯黑 —— OLED
    val cardBackground = Color(0xFF1C1C1E)      // 卡片浮在纯黑上
    val tertiaryBackground = Color(0xFF2C2C2E)

    val label = Color(0xFFFFFFFF)
    val secondaryLabel = Color(0x99EBEBF5)      // #EBEBF5 @ 60%
    val tertiaryLabel = Color(0x4DEBEBF5)
    val quaternaryLabel = Color(0x2EEBEBF5)

    val separator = Color(0xA6545458)           // #545458 @ 65%
    val opaqueSeparator = Color(0xFF38383A)

    val systemFill = Color(0x52767680)          // #767680 @ 32%
    val secondaryFill = Color(0x5C767680)
}

/**
 * 由中性色骨架 + 强调色构造 [WakeUpColorScheme]。
 *
 * 为什么不直接换一套新的 scheme 类型：全库已有大量 `SleepyTheme.colors.xxx` 读取点，
 * 换类型等于全库改名。这里是**保留旧类型、换值** —— 接缝最小。
 *
 * 映射关系（M3 槽位 → Apple 语义）：
 *  - `background` / `surface` → groupedBackground（页面底）
 *  - `surfaceContainerLow` / `surfaceContainer` → cardBackground（卡片）
 *  - `onSurface` → label；`onSurfaceVariant` → secondaryLabel
 *  - `outlineVariant` → separator
 *  - `primary` → 用户选的强调色；`onPrimary` → 白
 *  - secondary/tertiary 一律去饱和为中性灰 —— Apple 的配色体系里没有第二、第三强调色
 */
internal fun appleScheme(accent: Color, dark: Boolean): WakeUpColorScheme {
    val n = if (dark) {
        Triple(AppleNeutralDark.groupedBackground, AppleNeutralDark.cardBackground, AppleNeutralDark.tertiaryBackground)
    } else {
        Triple(AppleNeutralLight.groupedBackground, AppleNeutralLight.cardBackground, AppleNeutralLight.tertiaryBackground)
    }
    val grouped = n.first
    val card = n.second
    val tertiary = n.third

    val label = if (dark) AppleNeutralDark.label else AppleNeutralLight.label
    val secondaryLabel = if (dark) AppleNeutralDark.secondaryLabel else AppleNeutralLight.secondaryLabel
    val tertiaryLabel = if (dark) AppleNeutralDark.tertiaryLabel else AppleNeutralLight.tertiaryLabel
    val separator = if (dark) AppleNeutralDark.separator else AppleNeutralLight.separator
    val fill = if (dark) AppleNeutralDark.systemFill else AppleNeutralLight.systemFill

    // 强调色上的文字：系统色都是中高饱和，白字对比度足够（Blue #0A84FF 上白字 3.5:1，
    // 但 Apple 本身就这么做，且按钮文字是 17pt Semibold，符合大字号的 3:1 门槛）。
    val onAccent = Color.White

    // 次要色一律中性灰 —— 这是「界面无色」原则的直接体现
    val neutralSecondary = if (dark) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    return WakeUpColorScheme(
        primary = accent,
        onPrimary = onAccent,
        // container 系列：强调色的淡底，用于选中态
        primaryContainer = accent.copy(alpha = SleepyTheme.Alpha.tinted).compositeOver(card),
        onPrimaryContainer = accent,

        secondary = neutralSecondary,
        onSecondary = if (dark) Color(0xFF1C1C1E) else Color.White,
        secondaryContainer = fill,
        onSecondaryContainer = secondaryLabel,

        tertiary = neutralSecondary,
        onTertiary = if (dark) Color(0xFF1C1C1E) else Color.White,
        tertiaryContainer = tertiary,
        onTertiaryContainer = label,

        background = grouped,
        onBackground = label,
        surface = card,
        onSurface = label,
        surfaceVariant = fill,
        onSurfaceVariant = secondaryLabel,
        surfaceContainerLowest = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF),
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = tertiary,
        surfaceContainerHighest = if (dark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),

        outline = tertiaryLabel,
        outlineVariant = separator,
        scrim = Color(0xFF000000),

        // 错误色也用 iOS systemRed，不用 M3 的 error 色
        error = if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30),
        onError = Color.White,
        errorContainer = (if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30))
            .copy(alpha = SleepyTheme.Alpha.tinted).compositeOver(card),
        onErrorContainer = if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    )
}
