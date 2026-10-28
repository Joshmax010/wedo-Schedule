package com.wedo.schedule.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wedo.schedule.util.CourseColorUtil

/**
 * 液态玻璃 · Design Token
 *
 * 对齐《液态玻璃 Liquid Glass 效果规范》的五层配方。规范中的 "BACKGROUND_BLUR"（① 层）
 * 在本项目无法字面实现 —— Compose 的 Modifier.blur 只能模糊自身子树，不能模糊其背后的
 * 兄弟节点，且 RenderEffect 需 API 31+。因此玻璃质感的重心落在 ③ 顶部内高光与 ④ 边缘光圈：
 * 规范原文即写明「这一层（内高光）才是玻璃的关键，它模拟厚度边缘的受光」。
 *
 * 规范明确列出、且本实现**不承诺**的能力（需 GLASS kernel，当前引擎没有）：
 *   - 边缘折射位移（放大镜畸变）
 *   - 动态流动光斑
 *   - 色散光边
 */

/**
 * 组件层级语义 — 决定适用圆角与调用点，不决定采样方式。
 *
 * 采样方式由 [WedoGlassSurfaceClass] 决定：BASE 不渲染材质；CONTENT 恒 G0；
 * 只有 FLOAT 允许升到 G1/G2。课表格子、列表行可以是 CONTENT G0，但不得采样。
 */
enum class WedoGlassLevel {
    /** 导航类：周顶栏、底部 Dock、Dock 中心按钮、图标按钮 */
    Nav,

    /** 抽屉/弹层类：底部详情弹层、ModalBottomSheet */
    Sheet,

    /** 内容卡片类：设置分组卡、表单分组 */
    Card,

    /** 对话框类 */
    Dialog,
}

/**
 * 表面语义轴：决定「这块面是什么角色」，而不是决定画质。
 *
 * BASE 不渲染材质；CONTENT 恒 G0；只有 FLOAT 允许升到 G1/G2。
 */
enum class WedoGlassSurfaceClass {
    Base,
    Content,
    Float,
}

/**
 * 采样方式轴：G0 不采样、G1 静态 Bake、G2 实时采样。
 *
 * 与 SurfaceClass 正交；CONTENT 永远只能是 G0。
 */
enum class WedoGlassQuotaGrade {
    G0,
    G1,
    G2,
}

/**
 * 三档画质 — 收敛原先散落的裸字符串 "smooth"/"balanced"/"fine"。
 *
 * 存储值保持不变（仍写 SharedPreferences 的 "wedo_display"/"quality"），
 * 本枚举只在渲染期解析，因此**不影响** [WedoPreferences] 的读写契约与既有设置项 UI。
 */
enum class WedoGlassQuality {
    /** 流畅 — 单层半透明，无投影、无内高光；低端机强制落此档 */
    Smooth,

    /** 平衡（默认）— 双层渐变 + 内高光 + 轻投影 */
    Balanced,

    /** 精致 — 三层（含独立高光带）+ 加强描边 + 强化投影 */
    Fine;

    companion object {
        fun of(raw: String): WedoGlassQuality = when (raw) {
            "smooth" -> Smooth
            "fine" -> Fine
            else -> Balanced
        }
    }
}

/**
 * 玻璃配方常量 — 数值直接取自《液态玻璃效果规范》。
 *
 * 深色档为**重标定值**，非规范原值：规范给的「白 58% → 22%」是浅色环境下的填充，
 * 蓝色半透明在深色海军蓝底上会中和成灰、失去通透感。规范自己在边缘描边上给了
 * 「深色环境降至 38%」的降档先例，此处按同一逻辑把填充压到 20% → 6%。
 */
object WedoGlassTokens {

    // ---- ② 半透明体：规范原文「白 58% → 22%」 ----
    const val FILL_TOP_LIGHT = 0.58f
    const val FILL_BOTTOM_LIGHT = 0.22f

    /** 深色重标定：见 object KDoc */
    const val FILL_TOP_DARK = 0.20f
    const val FILL_BOTTOM_DARK = 0.06f

    // ---- ① 模糊半径：仅作为环境取色范围参考，不做真实 blur ----
    val BLUR_NAV = 30.dp
    val BLUR_CARD = 20.dp

    // ---- ④ 边缘光圈 ----
    const val EDGE_LIGHT = 0.60f    // 白 60%
    const val EDGE_DARK = 0.38f     // 深色环境降至 38%（规范原值）

    // ---- ⑤ 抬升投影：规范 y+10 / radius28 / spread-4 / alpha16% ----
    val SHADOW_ELEVATION_NAV = 10.dp
    val SHADOW_ELEVATION_CARD = 6.dp
    const val SHADOW_ALPHA = 0.16f

    // ---- ③ 顶部内高光：规范 白60% · offset y+1 · radius2 ----
    const val INNER_HIGHLIGHT = 0.60f

    /** 内高光覆盖的高度比例（仅顶部这一段渐隐到透明） */
    const val INNER_HIGHLIGHT_EXTENT = 0.34f

    // ---- G0 卡片面层：A · Quiet / B · Lifted / Smooth ----
    // 注意：FILL_TOP/FILL_BOTTOM 是旧渲染的渐变覆盖层，不直接等于卡片填充 alpha。
    const val CARD_FILL_SMOOTH = 0.97f
    const val CARD_FILL_BALANCED_LIGHT = 0.94f
    const val CARD_FILL_BALANCED_DARK = 0.96f
    const val CARD_FILL_FINE_LIGHT = 0.80f
    const val CARD_FILL_FINE_DARK = 0.82f

    // ---- G0 外圈 hairline：不是 rim；rim 仍是 EDGE_* ----
    const val HAIRLINE_SMOOTH_LIGHT = 0.08f
    const val HAIRLINE_SMOOTH_DARK = 0f
    const val HAIRLINE_BALANCED_LIGHT = 0.065f
    const val HAIRLINE_BALANCED_DARK = 0f
    const val HAIRLINE_FINE_LIGHT = 0.12f
    const val HAIRLINE_FINE_DARK = 0f

    // ---- G0 顶部内高光强度（0 表示不画） ----
    const val INNER_HIGHLIGHT_BALANCED = 0.72f
    const val INNER_HIGHLIGHT_FINE = 0.92f

    // ---- G0 rim：与旧 edgeAlpha 分开；深色下必须压住白边，否则一圈发亮 ----
    const val RIM_SMOOTH_LIGHT = 0.18f
    const val RIM_SMOOTH_DARK = 0.08f
    const val RIM_BALANCED_LIGHT = 0.30f
    const val RIM_BALANCED_DARK = 0.12f
    const val RIM_FINE_LIGHT = 0.48f
    const val RIM_FINE_DARK = 0.20f

    // ---- 圆角语义 ----
    val RADIUS_NAV = 30.dp
    val RADIUS_SHEET = 28.dp
    val RADIUS_CARD = 20.dp

    /** 描边宽度：规范写 1px；高密度屏上严格 1 physical px 会过细，统一取 1dp */
    val BORDER_WIDTH = 1.dp
    val BORDER_WIDTH_FINE = 1.2f.dp

    /** G0 的 rim 与 hairline 分开绘制：rim 加色，hairline 压暗。 */
    val RIM_WIDTH = 1.dp
    val HAIRLINE_WIDTH = 0.67f.dp
    val INNER_HIGHLIGHT_INSET = 10.dp
}

/**
 * 单次玻璃渲染的完整参数集 —— [wedoGlassSpec] 的唯一产物。
 *
 * 所有渲染侧分支都必须读这里，不要在 `wedoGlass()` 内再散落 when。
 */
data class WedoGlassSpec(
    val quality: WedoGlassQuality,
    /** 旧渐变覆盖层顶/底的白色不透明度（兼容既有断言，不等于卡片 alpha） */
    val fillTop: Float,
    val fillBottom: Float,
    /** 边缘 rim 不透明度 */
    val edgeAlpha: Float,
    /** 投影高度；0 表示不投影 */
    val elevation: Dp,
    /** 是否绘制顶部内高光（③ 层） */
    val innerHighlight: Boolean,
    /** 渐变层数：1=单色半透明 2=双层渐变 3=双层+独立高光带 */
    val gradientLayers: Int,
    /** 表面角色；BASE 不渲染材质 */
    val surface: WedoGlassSurfaceClass = WedoGlassSurfaceClass.Content,
    /** 采样档；P1 只实现 G0 */
    val quota: WedoGlassQuotaGrade = WedoGlassQuotaGrade.G0,
    /** false = 该表面不进入材质渲染（BASE） */
    val materialEnabled: Boolean = true,
    /** G0 卡片面层不透明度；A/B/Smooth 的主差异之一 */
    val cardFillAlpha: Float = fillTop,
    /** 最外圈 hairline 不透明度；与 rim 分开绘制 */
    val hairlineAlpha: Float = 0f,
    /** G0 rim 不透明度；不再复用旧 edgeAlpha */
    val rimAlpha: Float = edgeAlpha,
    /** 顶部内高光强度；0 表示不绘制 */
    val innerHighlightAlpha: Float = if (innerHighlight) WedoGlassTokens.INNER_HIGHLIGHT else 0f,
)

/**
 * 解析当前环境下应使用的玻璃参数。
 *
 * 决策输入只有三个：用户选的画质档、明暗、是否低内存设备。
 * 低内存设备**强制降级到 [WedoGlassQuality.Smooth]** —— 与改造前
 * `WedoGlass.kt` 的 `simple = quality == "smooth" || lowRam` 语义保持一致；
 * 存储值不改，设置页仍显示用户原本选中的档位。
 *
 * 明暗判定**必须**走 [CourseColorUtil.isPaletteDark]：该函数 KDoc 明确警告
 * 不能用 `WakeUpColorScheme.primary`（亮色下 0xFF6750A4 加权亮度 0.38 会被误判为暗色）。
 */
@Composable
fun wedoGlassSpec(
    level: WedoGlassLevel,
    surface: WedoGlassSurfaceClass = WedoGlassSurfaceClass.Content,
    quota: WedoGlassQuotaGrade = WedoGlassQuotaGrade.G0,
): WedoGlassSpec {
    val context = LocalContext.current
    val dark = CourseColorUtil.isPaletteDark(WedoTheme.palette)
    val rawQuality = LocalWedoDisplay.current.quality
    val lowRam = remember(context) {
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
            .isLowRamDevice
    }
    var quality = WedoGlassQuality.of(rawQuality)
    if (lowRam) quality = WedoGlassQuality.Smooth
    return buildGlassSpec(
        quality = quality,
        dark = dark,
        level = level,
        surface = surface,
        quota = quota,
    )
}

/**
 * 纯函数版本 — 供单元测试直接断言参数矩阵，不依赖 Compose 环境。
 *
 * 渲染路径走 [wedoGlassSpec]（带低端机探测），本函数是它的纯逻辑内核。
 */
fun buildGlassSpec(
    quality: WedoGlassQuality,
    dark: Boolean,
    level: WedoGlassLevel,
    surface: WedoGlassSurfaceClass = WedoGlassSurfaceClass.Content,
    quota: WedoGlassQuotaGrade = WedoGlassQuotaGrade.G0,
): WedoGlassSpec = when (quality) {
    // 流畅：单层半透明，无投影、无内高光 —— 性能优先，观感接近改造前实现
    WedoGlassQuality.Smooth -> WedoGlassSpec(
        quality = quality,
        fillTop = if (dark) 0.78f else 0.76f,
        fillBottom = if (dark) 0.70f else 0.62f,
        edgeAlpha = if (dark) 0.30f else 0.70f,
        elevation = 0.dp,
        innerHighlight = false,
        gradientLayers = 1,
        surface = surface,
        quota = quota,
        materialEnabled = surface != WedoGlassSurfaceClass.Base,
        cardFillAlpha = WedoGlassTokens.CARD_FILL_SMOOTH,
        hairlineAlpha = if (dark) {
            WedoGlassTokens.HAIRLINE_SMOOTH_DARK
        } else {
            WedoGlassTokens.HAIRLINE_SMOOTH_LIGHT
        },
        rimAlpha = if (dark) WedoGlassTokens.RIM_SMOOTH_DARK else WedoGlassTokens.RIM_SMOOTH_LIGHT,
        innerHighlightAlpha = 0f,
    )

    // 平衡：规范标准配方
    WedoGlassQuality.Balanced -> WedoGlassSpec(
        quality = quality,
        fillTop = if (dark) WedoGlassTokens.FILL_TOP_DARK else WedoGlassTokens.FILL_TOP_LIGHT,
        fillBottom = if (dark) WedoGlassTokens.FILL_BOTTOM_DARK else WedoGlassTokens.FILL_BOTTOM_LIGHT,
        edgeAlpha = if (dark) WedoGlassTokens.EDGE_DARK else WedoGlassTokens.EDGE_LIGHT,
        elevation = if (dark) 6.dp else 9.dp,
        innerHighlight = true,
        gradientLayers = 2,
        surface = surface,
        quota = quota,
        materialEnabled = surface != WedoGlassSurfaceClass.Base,
        cardFillAlpha = if (dark) {
            WedoGlassTokens.CARD_FILL_BALANCED_DARK
        } else {
            WedoGlassTokens.CARD_FILL_BALANCED_LIGHT
        },
        hairlineAlpha = if (dark) {
            WedoGlassTokens.HAIRLINE_BALANCED_DARK
        } else {
            WedoGlassTokens.HAIRLINE_BALANCED_LIGHT
        },
        rimAlpha = if (dark) WedoGlassTokens.RIM_BALANCED_DARK else WedoGlassTokens.RIM_BALANCED_LIGHT,
        // 深色下 72% 白内高光会变成一条亮条；只保留 rim，不画顶部线。
        innerHighlightAlpha = if (dark) 0f else WedoGlassTokens.INNER_HIGHLIGHT_BALANCED,
    )

    // 精致：加强描边与投影，并启用独立高光带
    WedoGlassQuality.Fine -> WedoGlassSpec(
        quality = quality,
        fillTop = if (dark) WedoGlassTokens.FILL_TOP_DARK else WedoGlassTokens.FILL_TOP_LIGHT,
        fillBottom = if (dark) WedoGlassTokens.FILL_BOTTOM_DARK else WedoGlassTokens.FILL_BOTTOM_LIGHT,
        edgeAlpha = if (dark) WedoGlassTokens.EDGE_DARK + 0.12f else WedoGlassTokens.EDGE_LIGHT + 0.06f,
        elevation = if (dark) 8.dp else 12.dp,
        innerHighlight = true,
        gradientLayers = 3,
        surface = surface,
        quota = quota,
        materialEnabled = surface != WedoGlassSurfaceClass.Base,
        cardFillAlpha = if (dark) {
            WedoGlassTokens.CARD_FILL_FINE_DARK
        } else {
            WedoGlassTokens.CARD_FILL_FINE_LIGHT
        },
        hairlineAlpha = if (dark) {
            WedoGlassTokens.HAIRLINE_FINE_DARK
        } else {
            WedoGlassTokens.HAIRLINE_FINE_LIGHT
        },
        rimAlpha = if (dark) WedoGlassTokens.RIM_FINE_DARK else WedoGlassTokens.RIM_FINE_LIGHT,
        innerHighlightAlpha = if (dark) 0f else WedoGlassTokens.INNER_HIGHLIGHT_FINE,
    )
}
