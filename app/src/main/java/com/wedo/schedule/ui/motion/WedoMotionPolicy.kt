package com.wedo.schedule.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.wedo.schedule.ui.theme.LocalWedoDisplay

/**
 * 减弱动效门控（M-32..M-35）。
 *
 * `LocalWedoDisplay.motion` 已在 `WedoDisplayProvider` 中完成双源与门：
 *  - 用户开关（WedoSettingsStore.motion）
 *  - 系统动画倍率（SystemAccessibility，ContentObserver 实时监听）
 * 这里只负责把生效档换成正确语义，不再重复读系统设置。
 */
object WedoMotionPolicy {
    /** S1：位移 / 缩放 / 形变，减动效直接跳终态。 */
    fun <T> resolve(enabled: Boolean, base: AnimationSpec<T>): AnimationSpec<T> =
        if (enabled) base else snap()

    /** S3：出现类，关闭动效只保留淡变，不做位移。 */
    fun enter(enabled: Boolean, base: EnterTransition): EnterTransition =
        if (enabled) base else fadeIn(snap())

    /** S3：消失类，关闭动效只保留淡变，不做位移。 */
    fun exit(enabled: Boolean, base: ExitTransition): ExitTransition =
        if (enabled) base else fadeOut(snap())

    /**
     * AnimatedContent 的 S3 语义：前后两态只淡变，不做方向位移。
     */
    fun <T> animatedContent(
        enabled: Boolean,
        base: AnimatedContentTransitionScope<T>.() -> ContentTransform,
    ): AnimatedContentTransitionScope<T>.() -> ContentTransform =
        if (enabled) base else {
            { fadeIn(snap()) togetherWith fadeOut(snap()) }
        }
}

/**
 * Composable 便捷入口：从当前显示偏好取 S1 spec。
 */
@androidx.compose.runtime.Composable
fun <T> motionSpec(base: AnimationSpec<T>): AnimationSpec<T> =
    WedoMotionPolicy.resolve(LocalWedoDisplay.current.motion, base)

/**
 * Composable 便捷入口：从当前显示偏好取 S3 enter/exit。
 */
@androidx.compose.runtime.Composable
fun rememberMotionTransition(
    baseEnter: EnterTransition,
    baseExit: ExitTransition,
): Pair<EnterTransition, ExitTransition> {
    val enabled = LocalWedoDisplay.current.motion
    return WedoMotionPolicy.enter(enabled, baseEnter) to
        WedoMotionPolicy.exit(enabled, baseExit)
}
