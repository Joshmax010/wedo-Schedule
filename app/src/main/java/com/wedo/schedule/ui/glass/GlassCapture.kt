package com.wedo.schedule.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.WedoGlassQuotaGrade
import com.wedo.schedule.ui.theme.WedoGlassSurfaceClass

/** 默认 null：不在 Host 内的面板自动降 G0，不崩、不白屏。 */
val LocalGlassStage = staticCompositionLocalOf<GlassStage?> { null }

/** 默认 0：Host 自身在提供 Local 时 +1。 */
val LocalGlassDepth = compositionLocalOf { 0 }

/**
 * 嵌套深度闸门（O-14 / Q-24）。
 *
 * 深度 ≥ 2 时**静默降 G0**，不抛异常：多包一层 Host 只应导致「没有采样」，
 * 不应让页面白屏或崩溃。
 */
internal object GlassDepth {
    const val MAX_DEPTH = 2

    fun shouldBuildStage(currentDepth: Int): Boolean = currentDepth < MAX_DEPTH

    fun nextDepth(currentDepth: Int): Int = currentDepth + 1
}

/**
 * 采样舞台。
 *
 * 静止时 Bake 一次；滚动 / 内容失效时切 LiveStrip。G1 与 G2 共用同一层，
 * 但 G1 只在内容失效时重录，G2 在滚动期间每帧重录。
 */
class GlassStage internal constructor(
    internal val layer: GraphicsLayer,
) {
    internal var hostSize: IntSize by mutableStateOf(IntSize.Zero)
    internal var hostTopInRoot by mutableStateOf(0f)
    internal var hasSampleConsumer by mutableStateOf(false)
    internal var isScrolling by mutableStateOf(false)
    internal var isBakeDirty by mutableStateOf(true)

    /** 已在场的采样消费者：档位 + 包围盒面积（px²）。 */
    private val activeConsumers = mutableStateListOf<Pair<WedoGlassQuotaGrade, Long>>()

    internal val activeG1: Int get() = activeConsumers.count { it.first == WedoGlassQuotaGrade.G1 }
    internal val activeG2: Int get() = activeConsumers.count { it.first == WedoGlassQuotaGrade.G2 }

    /** 已在场消费者中最小的包围盒面积；面积倒序仲裁用。 */
    private val smallestActiveArea: Long
        get() = activeConsumers.minOfOrNull { it.second } ?: Long.MAX_VALUE

    fun requestSample(
        surface: WedoGlassSurfaceClass,
        requested: WedoGlassQuotaGrade,
        areaPx: Long = 0L,
    ): WedoGlassQuotaGrade {
        val effective = GlassQuota.resolve(
            surface = surface,
            requested = requested,
            activeG1 = activeG1,
            activeG2 = activeG2,
            areaPx = areaPx,
            screenAreaPx = screenAreaPx(),
            smallestActiveAreaPx = smallestActiveArea,
        )
        if (effective != WedoGlassQuotaGrade.G0) {
            activeConsumers += effective to areaPx
        }
        hasSampleConsumer = activeConsumers.isNotEmpty()
        isBakeDirty = true
        return effective
    }

    fun releaseSample(effective: WedoGlassQuotaGrade) {
        if (effective != WedoGlassQuotaGrade.G0) {
            val idx = activeConsumers.indexOfLast { it.first == effective }
            if (idx >= 0) activeConsumers.removeAt(idx)
        }
        hasSampleConsumer = activeConsumers.isNotEmpty()
        isBakeDirty = true
    }

    /** 屏面积（px²）；Host 尺寸即屏幕代理。 */
    fun screenAreaPx(): Long =
        if (hostSize == IntSize.Zero) 0L
        else hostSize.width.toLong() * hostSize.height.toLong()

    fun markContentDirty() {
        isBakeDirty = true
    }

    fun markScrolling(value: Boolean) {
        isScrolling = value
    }
}

/**
 * 内容宿主。只采样 [content]，不采样 [overlay]；overlay 可以消费舞台层。
 */
@Composable
fun WedoGlassHost(
    modifier: Modifier = Modifier,
    contentKey: Any? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val depth = LocalGlassDepth.current
    if (!GlassDepth.shouldBuildStage(depth)) {
        // 静默降 G0，避免页面多包一层 Host 直接崩。
        Box(modifier = modifier) {
            content()
            overlay()
        }
        return
    }

    val layer = rememberGraphicsLayer()
    val stage = remember(layer) { GlassStage(layer) }

    LaunchedEffect(contentKey) {
        stage.markContentDirty()
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalGlassStage provides stage,
        LocalGlassDepth provides GlassDepth.nextDepth(depth),
    ) {
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onSizeChanged { stage.hostSize = it }
                    .onGloballyPositioned { stage.hostTopInRoot = it.positionInRoot().y }
                    .drawWithContent {
                        if (stage.hasSampleConsumer && (stage.isScrolling || stage.isBakeDirty)) {
                            layer.record { this@drawWithContent.drawContent() }
                            stage.isBakeDirty = false
                            GlassDebug.onCapture()
                        }
                        if (stage.hasSampleConsumer) {
                            drawLayer(layer)
                            GlassDebug.onDraw()
                        } else {
                            drawContent()
                        }
                    },
            ) {
                content()
            }
            overlay()
        }
    }
}

/**
 * G1/G2 背景面板：把 Host 采样层画在自己的包围盒里，再叠 tint / rim / hairline。
 *
 * `quota` 必须显式声明；`GlassQuota` 负责超配额时的静默降 G0。
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    surface: WedoGlassSurfaceClass = WedoGlassSurfaceClass.Float,
    quota: WedoGlassQuotaGrade = WedoGlassQuotaGrade.G1,
    shape: Shape = RectangleShape,
    tint: Color = Color.White,
    tintAlpha: Float = 0f,
    rimAlpha: Float = 0f,
    rimWidth: Dp = 1.dp,
    hairline: Color = Color.White,
    hairlineAlpha: Float = 0f,
    hairlineWidth: Dp = 0.67f.dp,
    blurRadius: Float = 12f,
    cornerRadiusPx: Float? = null,
    scrolling: Boolean = false,
    /**
     * 采样内容的饱和度：1 = 原色，0 = 全灰。
     *
     * 这是 iOS 的 vibrancy：半透明面板身后的内容自动降饱和，透过去是灰蓝而不是
     * 刺眼的纯蓝。2026-10-28 真机反馈的「底栏下方蓝色图标变成刺眼色斑」就是
     * 缺这一环。
     */
    vibrancy: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val stage = LocalGlassStage.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var backdropSize by remember { mutableStateOf(IntSize.Zero) }
    var backdropTopInRoot by remember { mutableStateOf(0f) }
    var effectiveQuota by remember { mutableStateOf(WedoGlassQuotaGrade.G0) }
    var liveSampling by remember { mutableStateOf(false) }
    var l2Effect by remember { mutableStateOf<androidx.compose.ui.graphics.RenderEffect?>(null) }

    // 配额申请与包围盒面积绑定：尺寸首次测出后重新仲裁（面积闸 / 面积倒序）。
    DisposableEffect(stage, surface, quota, backdropSize) {
        val areaPx = if (backdropSize == IntSize.Zero) {
            0L
        } else {
            backdropSize.width.toLong() * backdropSize.height.toLong()
        }
        val granted = stage?.requestSample(surface, quota, areaPx) ?: WedoGlassQuotaGrade.G0
        effectiveQuota = granted
        onDispose {
            stage?.releaseSample(granted)
            if (effectiveQuota == granted) effectiveQuota = WedoGlassQuotaGrade.G0
        }
    }

    LaunchedEffect(effectiveQuota, scrolling) {
        if (effectiveQuota != WedoGlassQuotaGrade.G2) {
            liveSampling = false
            return@LaunchedEffect
        }
        if (scrolling) {
            liveSampling = true
        } else {
            kotlinx.coroutines.delay(120)
            liveSampling = false
        }
    }

    LaunchedEffect(stage, effectiveQuota, liveSampling) {
        if (effectiveQuota == WedoGlassQuotaGrade.G2) {
            stage?.markScrolling(liveSampling)
        }
    }

    // L2 只在静止 Bake 时生效；LiveStrip / shader 失败都自动退回 L1 blur。
    LaunchedEffect(effectiveQuota, liveSampling, backdropSize, cornerRadiusPx) {
        val canUseL2 = effectiveQuota == WedoGlassQuotaGrade.G2 && !liveSampling && backdropSize != IntSize.Zero
        l2Effect = if (!canUseL2) {
            null
        } else {
            val corner = cornerRadiusPx
                ?: (minOf(backdropSize.width, backdropSize.height) / 2f).toFloat()
            GlassShaderRenderer.refractionEffect(
                context = context,
                widthPx = backdropSize.width.toFloat(),
                heightPx = backdropSize.height.toFloat(),
                cornerRadiusPx = corner,
            )
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
    ) {
        if (stage != null && effectiveQuota != WedoGlassQuotaGrade.G0) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onSizeChanged { backdropSize = it }
                    .onGloballyPositioned { backdropTopInRoot = it.positionInRoot().y }
                    .graphicsLayer {
                        renderEffect = l2Effect ?: GlassBlur.chained(blurRadius)
                        // vibrancy 只作用于采样层（面板背后的内容），
                        // 面板自己的图标与文字是兄弟节点，不受影响。
                        colorFilter = GlassVibrancy.filter(vibrancy)
                    }
                    .drawBehind {
                        val offsetY = -(backdropTopInRoot - stage.hostTopInRoot)
                        translate(left = 0f, top = offsetY) {
                            drawLayer(stage.layer)
                        }
                    },
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val path = Path().apply {
                        when (outline) {
                            is Outline.Rectangle -> addRect(outline.rect)
                            is Outline.Rounded -> addRoundRect(outline.roundRect)
                            is Outline.Generic -> addPath(outline.path)
                        }
                    }
                    drawPath(path, tint.copy(alpha = tintAlpha))
                    drawPath(
                        path,
                        color = Color.White.copy(alpha = rimAlpha),
                        style = Stroke(width = rimWidth.toPx()),
                    )
                    drawPath(
                        path,
                        color = hairline.copy(alpha = hairlineAlpha),
                        style = Stroke(width = hairlineWidth.toPx()),
                    )
                },
        )

        content()
    }
}
