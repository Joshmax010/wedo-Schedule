package com.wedo.schedule.ui.motion

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.wedo.schedule.ui.theme.LocalWedoDisplay
import kotlinx.coroutines.delay

/** M-43：按压最小驻留；防止快速连点把 scale 打成来回抖动。 */
const val PRESS_MIN_DWELL_MS = 80L

/**
 * Apple 式按压缩放（L1）。
 *
 * - 纯点击只做同轴缩放，不做 L2（M-R3 / §3.2.2）
 * - 终值恒 1.0，最小驻留只延缓回落，不改变视觉落点（M-44）
 * - 减动效直接 snap 到终态（S1）
 */
@Composable
fun Modifier.wedoPress(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val motion = LocalWedoDisplay.current.motion
    var dwellPressed by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (pressed) {
            dwellPressed = true
        } else {
            delay(PRESS_MIN_DWELL_MS)
            dwellPressed = false
        }
    }

    val animatedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dwellPressed && motion) 0.97f else 1f,
        animationSpec = WedoMotionPolicy.resolve(motion, WedoSprings.press()),
        label = "wedoPress",
    )
    return this.graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
        .combinedClickable(
            interactionSource = interactions,
            indication = null,
            role = Role.Button,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}
