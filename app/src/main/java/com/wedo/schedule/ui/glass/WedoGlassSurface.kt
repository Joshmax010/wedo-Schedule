package com.wedo.schedule.ui.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoGlassLevel
import com.wedo.schedule.ui.theme.WedoGlassQuality
import com.wedo.schedule.ui.theme.WedoGlassQuotaGrade
import com.wedo.schedule.ui.theme.WedoGlassSurfaceClass
import com.wedo.schedule.ui.theme.WedoGlassTokens
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.buildGlassSpec
import com.wedo.schedule.util.CourseColorUtil

/**
 * G0 表面修饰器。
 *
 * 这是 P1 的唯一材质入口：不采样、不做 blur；只画卡片面层、rim、hairline
 * 与顶部内高光。调用方负责提供 shape 与内容，**不负责改 padding**。
 *
 * 目前只用于 CONTENT；FLOAT 的 G1/G2 仍由 P2 的 capture 管线处理。
 */
@Composable
fun Modifier.wedoGlassSurface(
    surface: WedoGlassSurfaceClass,
    level: WedoGlassLevel = WedoGlassLevel.Card,
    quota: WedoGlassQuotaGrade = WedoGlassQuotaGrade.G0,
    shape: Shape = WedoAppleShapes.card,
    fillColor: Color? = null,
    fillAlpha: Float? = null,
    drawShadow: Boolean = true,
    drawInnerHighlight: Boolean = true,
): Modifier {
    val context = LocalContext.current
    val dark = CourseColorUtil.isPaletteDark(WedoTheme.palette)
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }
    val quality = GlassCapability.resolveQualityFor(
        requested = WedoGlassQuality.of(com.wedo.schedule.ui.theme.LocalWedoDisplay.current.quality),
        sdkInt = Build.VERSION.SDK_INT,
        lowRam = lowRam,
    )
    val spec = buildGlassSpec(
        quality = quality,
        dark = dark,
        level = level,
        surface = surface,
        quota = quota,
    )
    if (!spec.materialEnabled) return this

    val fill = (fillColor ?: WedoTheme.colors.surfaceContainerLow).copy(
        alpha = fillAlpha ?: spec.cardFillAlpha,
    )
    val rim = Color.White.copy(alpha = spec.rimAlpha.coerceAtMost(0.72f))
    val hairline = (if (dark) Color.White else Color.Black).copy(alpha = spec.hairlineAlpha)
    val highlight = Color.White.copy(alpha = spec.innerHighlightAlpha.coerceAtMost(0.92f))

    val shadowed = if (drawShadow) shadow(spec.elevation, shape, clip = false) else this
    return shadowed
        .clip(shape)
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }
            drawPath(path, fill, style = Fill)
            drawPath(
                path,
                color = hairline,
                style = Stroke(width = WedoGlassTokens.HAIRLINE_WIDTH.toPx()),
            )
            drawPath(
                path,
                color = rim,
                style = Stroke(width = WedoGlassTokens.RIM_WIDTH.toPx()),
            )
            if (drawInnerHighlight && spec.innerHighlightAlpha > 0f) {
                val inset = WedoGlassTokens.INNER_HIGHLIGHT_INSET.toPx()
                drawLine(
                    color = highlight,
                    start = Offset(inset, 1.dp.toPx()),
                    end = Offset(size.width - inset, 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
}
