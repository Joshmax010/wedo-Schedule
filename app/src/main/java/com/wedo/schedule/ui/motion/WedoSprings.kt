package com.wedo.schedule.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 运动弹簧族表（M-01..M-08）。
 *
 * 只允许通过这里拿弹簧；散落的 `spring(ζ, k)` 会让同一种交互在不同页面手感漂移。
 *
 * 2026-10-28 初版定参：
 *  - `track` 保持历史实测的 ζ1.0 / k10000：跟手必须临界阻尼，任何 bounce 都会追不上手指。
 *  - `press` 保留 ζ0.72 / k520：按压是即时反馈，回落要轻，不需要长尾。
 *  - `settle` 起点取 ζ0.55 / k420：落位类需要一次可见过冲，但要约束在 400ms 左右。
 *  - `jelly` ζ0.40 / k180 仅限 scaleX/scaleY；更低阻尼会把位移拖到 1s 级。
 *  - `travel` ζ0.70 / k300：换周 / 切 Tab 是长距离移动，比 `lift` 更慢、略带到位感。
 *  - `fade` ζ1.0 / k1500：淡变不应过冲。
 */
object WedoSprings {
    /** M-01 跟手位移。禁止用于松手后的自主运动（M-R1）。 */
    fun <T> track(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    /** M-02 按压缩放 L1（0.97）与按钮回落。 */
    fun <T> press(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 520f,
    )

    /** M-03 松手归位 / Chip 选中；有一次可见过冲。 */
    fun <T> settle(): SpringSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = 420f,
    )

    /** M-04 L2 形变；只允许驱动 scaleX / scaleY（M-R3）。 */
    fun <T> jelly(): SpringSpec<T> = spring(
        dampingRatio = 0.40f,
        stiffness = 180f,
    )

    /** M-05 抬起 / 高亮 / 顶部内高光增强。 */
    fun <T> lift(): SpringSpec<T> = spring(
        dampingRatio = 0.80f,
        stiffness = 700f,
    )

    /** M-06 长距离移动：换周、切 Tab。 */
    fun <T> travel(): SpringSpec<T> = spring(
        dampingRatio = 0.70f,
        stiffness = 300f,
    )

    /** M-07 淡入淡出；只允许驱动 alpha（M-R1）。 */
    fun <T> fade(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1500f,
    )

    /** M-08 甩动后落位；过冲最明显的一根，只用于落点。 */
    fun <T> flingSettle(): SpringSpec<T> = spring(
        dampingRatio = 0.50f,
        stiffness = 260f,
    )
}

/**
 * 历史别名：原 `WedoThumbSpring` 的语义就是跟手段坐标（M-01）。
 * 保留名字避免 SegmentSwitcher 一批调用被打断，但值与规则统一由族表管理。
 */
val WedoThumbSpring: SpringSpec<Float> = WedoSprings.track()
