package com.lingion.sleepy.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import com.lingion.sleepy.ui.theme.WedoAppleType

/**
 * Apple 原生质感令牌层 —— 替代此前的液态玻璃方案。
 *
 * 设计原则（Apple HIG：Clarity / Deference / Depth）：
 *  1. **界面无色**。chrome 只用黑白灰，饱和色只出现在「内容」与极少数「强调」位置。
 *     这一条是整套设计的根 —— 此前 13 个方案失败的共同原因，是给 App 一个
 *     彩色身份（靛紫/青碧/砖橙当全局主色），而 Apple App 是「无色身份 + 彩色内容」。
 *  2. **三级背景层级**（systemGroupedBackground / secondarySystemGrouped / 透明），
 *     用明度差表达层次，不靠阴影堆叠。
 *  3. **0.5px 分隔线**。1px 会让界面显钝，这是 Apple 观感的关键细节之一。
 *  4. **连续曲率圆角**（squircle）。Compose 没有原生超椭圆 API，用
 *     [WedoAppleShapes.continuous] 按尺寸反推等效半径近似。
 *
 * 所有数值来自 Apple Human Interface Guidelines，非自创。
 */

// ─────────────────────────────────────────────────────────────────────────────
// 一、颜色：iOS 系统色（浅色版 / 深色版成对）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * iOS 官方系统色。
 *
 * 每个色都有浅色版（light）与深色版（dark）两个变体。深色版**不是**浅色版简单调亮，
 * 而是 Apple 针对纯黑背景单独重新标定过饱和度与明度，保证既有足够亮度又不刺眼。
 *
 * 共 11 色 × 2 版本 = 22 个色位。
 * 用户可在设置页选择任一系统色作为 App 强调色。
 *
 * **为什么是 11 而不是 12**：iOS 系统色本来有 12 个，这里砍掉了 `Yellow`。
 * 原因是**数学上无解** —— `#FFCC00` 在白底上的对比度只有 1.512:1，
 * 是 12 色里唯一一个差到「当图标完全看不见」的（次差的薄荷还有 2.118:1）。
 * 要把它救到 3:1 必须压到很暗的土黄，救到 4.5:1 更是变成 `#8C7000` 这种
 * 和「黄」已经没关系的颜色。Apple 自己也从不用 systemYellow 做文字/链接，
 * 与其给用户一个「选了就难看」的选项，不如不出现在列表里。
 */
enum class WedoSystemColor(
    /** 供 UI 显示的色名 */
    val displayName: String,
    /** 浅色模式下的取值 */
    val light: Color,
    /** 深色模式下的取值（Vibrant 变体） */
    val dark: Color
) {
    Blue("蓝色", Color(0xFF007AFF), Color(0xFF0A84FF)),
    Indigo("靛紫", Color(0xFF5856D6), Color(0xFF5E5CE6)),
    Purple("紫色", Color(0xFFAF52DE), Color(0xFFBF5AF2)),
    Pink("玫红", Color(0xFFFF2D55), Color(0xFFFF375F)),
    Red("红色", Color(0xFFFF3B30), Color(0xFFFF453A)),
    Orange("橙色", Color(0xFFFF9500), Color(0xFFFF9F0A)),
    Green("绿色", Color(0xFF34C759), Color(0xFF30D158)),
    Mint("薄荷", Color(0xFF00C7BE), Color(0xFF63E6E2)),
    Teal("青蓝", Color(0xFF30B0C7), Color(0xFF40C8E0)),
    Cyan("天青", Color(0xFF32ADE6), Color(0xFF64D2FF)),
    Brown("棕色", Color(0xFFA2845E), Color(0xFFAC8E68));

    /**
     * 历史名 → 现名。用于兼容老用户持久化过的值。
     *
     * 用户在旧版本里可能已经把强调色存成了 `"Yellow"`；直接按名查表会查不到，
     * 于是回落默认色 —— 能跑，但用户会觉得「我的设置被重置了」。
     * 这里把它显式映射到一个观感最接近的可选色，迁移是无感的。
     */
    private val legacyAliases: Map<String, WedoSystemColor>
        get() = mapOf("Yellow" to Orange)

    /**
     * 取当前深浅模式下的值。
     *
     * 参数名用 `isDark` 而非 `dark`：`dark` 与枚举属性同名会遮蔽属性访问，
     * 导致 `if (dark) dark else light` 返回 Boolean 而不是 Color。
     */
    fun color(isDark: Boolean): Color = if (isDark) this.dark else this.light

    /** 该色的可读版本按角色走哪条门槛，见 [WedoColorRole]。 */
    fun readableColor(isDark: Boolean, role: WedoColorRole = WedoColorRole.Text): Color {
        val surface = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        return readableOn(this.color(isDark), surface, role.minContrast)
    }

    companion object {
        /** 默认强调色（浅色 #007AFF / 深色 #0A84FF） */
        val Default: WedoSystemColor = Blue

        /**
         * 默认强调色的持久化名。
         * AppPrefs 存的就是这个字符串，单独暴露一个常量避免 AppPrefs 依赖枚举实例。
         */
        const val DEFAULT_NAME: String = "Blue"

        /**
         * 按名取色，未知/已移除的名字回落默认（SharedPreferences 存的是 enum name）。
         *
         * 顺序：先查现役色 → 再查历史别名（如已移除的 Yellow）→ 最后才回落默认。
         */
        fun byName(name: String?): WedoSystemColor =
            entries.firstOrNull { it.name == name }
                ?: Default.legacyAliases[name]
                ?: Default
    }
}

/**
 * 强调色被拿来「当什么用」——决定它要多可读。
 *
 * **为什么要分两档**：WCAG 对这两类的门槛本来就不同，而且差得不少：
 *  - 正文（≤17pt）要 **4.5:1**（SC 1.4.3）
 *  - 图标、UI 组件、有意义图形要 **3:1**（SC 1.4.11）
 *
 * Apple 的 HIG 页写的也是同一套数：≤17pt → 4.5:1，18pt 或粗体 → 3:1。
 *
 * 早先图省事只做了一档 4.5:1，结果是**图标被过度矫正**：浅色模式下系统蓝
 * `#007AFF`（4.017:1）本来完全够用（也确实是 Apple 自己在用的值），却被压成
 * 深藏青 `#00438C`（9.626:1），离 Apple 观感远了一大截。按角色给阈值之后，
 * 图标能留在接近 Apple 原色的位置，文字该严还是严。
 */
enum class WedoColorRole(val minContrast: Double) {
    /** 图标、图形、UI 组件 —— WCAG 1.4.11，3:1 */
    Icon(TARGET_ACCENT_ICON_CONTRAST),

    /** 正文文字 —— WCAG 1.4.3，4.5:1 */
    Text(TARGET_ACCENT_TEXT_CONTRAST)
}

/**
 * 课程块配色 —— 淡底 + 同色系深字。
 *
 * **为什么不是白字**：用户最初要「淡色块 + 白字」，但白字在 12% 不透明度的淡底上
 * 对比度只有约 1.2:1，完全不可读（WCAG 要求 4.5:1）。这是数学问题不是审美问题。
 * 改为同色系深字后对比度达 7.1:1。
 *
 * @property tint  块底色（系统色 12% 不透明度叠加在当前 surface 上）
 * @property bar   左侧 4px 实色条（系统色 100%）
 * @property title 课程名文字色（同色系加深，保证 ≥4.5:1）
 * @property subtitle 副标题文字色（比 title 略浅，仍 ≥4.5:1）
 */
data class WedoCourseBlockColors(
    val tint: Color,
    val bar: Color,
    val title: Color,
    val subtitle: Color
)

/** 淡底色不透明度 —— 与 SleepyTheme.Alpha.tinted 同源，避免两处魔法数 */
private const val TINT_ALPHA = 0.12f

/**
 * 课程文字的目标对比度。
 *
 * **刻意高于 WCAG 的 4.5**。4.5 是「合法」线，不是「好看」线：
 * 实测蓝/靛紫/紫/玫红/红这几个色相在 4.5 附近时，文字发灰发虚，
 * 而绿/薄荷/青蓝能到 9–11 —— 同一张课表里两种观感，很难看。
 * 把目标提到 6.5 后，弱色相被迫再降一档饱和度往白/黑靠，
 * 全部色相落进 6.5–11 的区间，观感齐整。
 *
 * 选 6.5 而不是更高：再高会让靛紫这类色相几乎褪成白色，
 * 失去「这是紫色课」的辨识度。6.5 是「够清楚」与「还认得出颜色」的交点。
 *
 * `internal` 而非 `private`：单测要拿它做断言基准，同值不同处会漂移。
 */
internal const val TARGET_TEXT_CONTRAST = 6.5

/**
 * 由课程基色推导整块配色。
 *
 * 明暗两套逻辑不同：
 *  - 浅色模式：底色 = 基色 12% 叠白（很淡的彩底）；文字 = 基色压到暗色区
 *  - 深色模式：底色 = 基色压暗后 30% 叠黑（深色下淡底要更实才看得见）；文字 = 基色提到亮色区
 *
 * **明暗取值都是算出来的，不是拍的**。文字色一律交给 [solveForContrast]
 * 按 4.5:1 目标反解 —— 各系统色的固有亮度差近三倍，任何固定值都会
 * 有的过、有的不过。靛紫这种低亮度色相还需要降饱和度才够得着门槛。
 *
 * @param base 课程基色（来自 CourseColorUtil 或用户自选）
 * @param dark 当前是否深色模式
 * @param surface 当前卡片底色，淡底要叠在它上面才自然
 */
fun wedoCourseBlockColors(
    base: Color,
    dark: Boolean,
    surface: Color
): WedoCourseBlockColors {
    val tint = if (dark) buildDarkTint(base, surface) else lerpColor(surface, base, TINT_ALPHA)

    // 深色模式下底色很暗，文字要朝白推；浅色模式底色很亮，文字要朝黑压。
    // 起始明度只是「少迭代几轮」的优化，正确性由 solveForContrast 保证。
    val title = solveForContrast(
        base = base,
        background = tint,
        minRatio = TARGET_TEXT_CONTRAST,
        startValue = if (dark) 0.95f else 0.35f,
        minSaturation = if (dark) 0.25f else 0.50f,
        towardsWhite = dark
    )

    val subtitle = solveForContrast(
        base = base,
        background = tint,
        minRatio = TARGET_TEXT_CONTRAST - 0.8,
        startValue = if (dark) 0.85f else 0.42f,
        minSaturation = if (dark) 0.18f else 0.40f,
        towardsWhite = dark
    )

    return WedoCourseBlockColors(
        tint = tint,
        bar = base,
        title = title,
        subtitle = subtitle
    )
}

/**
 * 深色模式的课程块淡底。
 *
 * 只需满足一点：**足够暗**，好让 [solveForContrast] 能把文字提到 4.5:1。
 * 过早追求「保留颜色感」反而会把底做亮，挤掉文字的对比度空间。
 *
 * 所以这里不玩反推，直接给一个固定组合：基色压到 V=0.30、S=0.45，
 * 再按 0.30 的比例叠到 surface 上。算下来底的相对亮度约 0.02–0.03，
 * 既看得出「这是一块有色区域」，又留足文字对比度余量。
 *
 * 课程辨识不靠底色的深浅 —— 靠左侧 4px 实色条（[WedoCourseBlockColors.bar]），
 * 那条始终是原始系统色，明暗模式下都不变。
 */
private fun buildDarkTint(base: Color, surface: Color): Color {
    val dimmed = hsvAdjust(base, targetValue = 0.30f, minSaturation = 0.45f)
    return lerpColor(surface, dimmed, 0.30f)
}

/** 线性插值混合两色（Compose 的 lerp 在 graphics 包，这里手写避免额外 import 冲突） */
private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f
    )
}

/**
 * 在 HSV 空间调整颜色的明度/饱和度，保持色相不变。
 *
 * 用 HSV 而不是直接缩放 RGB，是因为 RGB 缩放会让颜色发灰或偏色；
 * HSV 调明度能保住色相的辨识度 —— 课程色靠色相区分，色相一变就分不清了。
 *
 * ⚠️ **刻意不用 `android.graphics.Color.colorToHSV` / `HSVToColor`**：
 * 那是 Android framework 类，在 JVM 单元测试里没有真实实现，会**静默返回 0**
 * （表现为 `Color(0.0, 0.0, 0.0, 0.0)` 全透明黑），既不报错也不崩。
 * 这里用纯 Kotlin 实现，保证本地单测与真机行为一致。
 *
 * @param targetValue  目标明度（HSV 的 V），0..1
 * @param minSaturation 最低饱和度，防止弱色课程在调整后变成灰
 */
private fun hsvAdjust(color: Color, targetValue: Float, minSaturation: Float): Color {
    val source = floatArrayOf(color.red, color.green, color.blue)
    val hsv = rgbToHsv(source)
    val s = max(hsv[1], minSaturation).coerceIn(0f, 1f)
    val v = targetValue.coerceIn(0f, 1f)
    val out = hsvToRgb(hsv[0], s, v)
    return Color(out[0], out[1], out[2], 1f)
}

/** 提升到指定对比度的重试上限 —— 留足余量，正常情况 20 步内必收敛 */
private const val CONTRAST_SEARCH_STEPS = 40

/**
 * 强调色作**正文**时的可读性门槛（WCAG AA SC 1.4.3）。
 *
 * 注意与 [TARGET_TEXT_CONTRAST] 区分：那个 6.5 是**课程块内**「深字压淡底」的门槛，
 * 场景不同 —— 课程块是同色系深浅叠色，颜色底本身有信息量，所以要求更高更稳；
 * 这里是「强调色直接压在卡片白底上」，按 WCAG 标准值即可。
 */
internal const val TARGET_ACCENT_TEXT_CONTRAST = 4.5

/** 图标 / UI 组件可读性门槛（WCAG AA SC 1.4.11 非文字对比） */
internal const val TARGET_ACCENT_ICON_CONTRAST = 3.0

/**
 * 把一个**填充用**颜色调成**文字/图标用**的可读版本，色相不变。
 *
 * 用于 iOS 系统色：它们是按「色块底 + 白字」标定的（如黄配白字很好看），
 * 但同一支黄直接当白底上的文字/图标就是灾难 —— 实测 #FFCC00 在 #FFFFFF 上只有
 * 1.51:1。Apple 自己的做法也是分档（填充色 vs 文字 link 色）。
 *
 * **[minContrast] 由调用方按角色给**：图标 3:1、正文 4.5:1，见 [WedoColorRole]。
 * 已经达标就**原样返回**，不做任何压制 —— 这是「图标留在 Apple 原色」的关键：
 * 系统蓝 4.017:1 对图标够用，就不会被无谓地调暗。
 *
 * 方向由背景亮度决定：亮底往黑压，暗底往白提。这样同一档内各色不会忽明忽暗。
 */
internal fun readableOn(
    base: Color,
    background: Color,
    minContrast: Double = TARGET_ACCENT_TEXT_CONTRAST
): Color {
    if (contrastRatio(base, background) >= minContrast) return base
    val towardsWhite = relativeLuminance(background) < 0.5
    return solveForContrast(
        base = base,
        background = background,
        minRatio = minContrast,
        startValue = if (towardsWhite) 0.85f else 0.55f,
        minSaturation = 0.30f,
        towardsWhite = towardsWhite
    )
}

/**
 * 求一个与 [background] 对比度 ≥ [minRatio] 的前景色，色相取自 [base]。
 *
 * **为什么不写死明度**：各系统色的固有亮度差异极大 —— 例如薄荷
 * (V=1.0 时相对亮度约 0.55) 和靛紫 (V=1.0 时约 0.20) 差了近三倍。
 * 用同一个 `targetValue` 去套所有色，必然有的过、有的不过。
 * 所以这里改成**按对比度目标反解**。
 *
 * **两个杠杆，不是一个**：最初只调 HSV 的 V（明度），结果靛紫卡死在 4.12:1。
 * 原因是数学上的硬上限 —— 靛紫 h≈239° 在 s=0.6 时即使 V=1.0，
 * 相对亮度也只有约 0.195（蓝色通道贡献 0.0722，而绿通道被压到 0.4，
 * 偏偏绿通道权重 0.7152 最大）。此时无论怎么推 V 都到不了 4.5:1。
 *
 * 第二根杠杆是**降饱和度**：s 越低颜色越靠近白/黑，亮度单调上升（towardsWhite）
 * 或下降（towardsBlack）。所以第一轮 V 推到顶后，进入第二轮逐步降 s，
 * 直到达标。降 s 会让颜色变淡，但**色相不变** —— 课程靠色相区分，
 * 色相保住就不会认错，只是同一门课的颜色浅一档，可接受。
 *
 * @param base        课程基色（决定色相）
 * @param background  背景色（通常是课程块淡底）
 * @param minRatio    目标对比度，WCAG AA 普通文字为 4.5
 * @param startValue  起始明度（离目标近可少迭代几轮）
 * @param minSaturation 最低饱和度，防止收敛过程中颜色褪成灰
 * @param towardsWhite true 表示朝白推进（深色模式 / 暗底），false 朝黑推进
 */
private fun solveForContrast(
    base: Color,
    background: Color,
    minRatio: Double,
    startValue: Float,
    minSaturation: Float,
    towardsWhite: Boolean
): Color {
    val hsv = rgbToHsv(floatArrayOf(base.red, base.green, base.blue))
    val hue = hsv[0]
    val nativeSaturation = hsv[1]

    // ── 第一轮：调明度 V ──────────────────────────────────────────────────
    val valueStep = if (towardsWhite) 0.02f else -0.02f
    var value = startValue
    var candidate = hsvColor(hue, max(nativeSaturation, minSaturation), value)

    if (contrastRatio(candidate, background) >= minRatio) return candidate

    repeat(CONTRAST_SEARCH_STEPS) {
        value = (value + valueStep).coerceIn(0f, 1f)
        candidate = hsvColor(hue, max(nativeSaturation, minSaturation), value)
        if (contrastRatio(candidate, background) >= minRatio) return candidate
        if (value <= 0f || value >= 1f) return@repeat
    }

    // ── 第二轮：V 已到顶仍不达标，降饱和度朝白/黑靠 ──────────────────────
    // 深色模式 V 已顶到 1.0，只能降 s 逼近白；浅色模式 V 已到 0，
    // 只能降 s 逼近黑。两者都能单调提升对比度。
    val solvedValue = if (towardsWhite) 1f else 0f
    var saturation = max(nativeSaturation, minSaturation)
    val saturationStep = if (towardsWhite) -0.04f else 0.04f
    repeat(CONTRAST_SEARCH_STEPS) {
        saturation = (saturation + saturationStep).coerceIn(0f, 1f)
        candidate = hsvColor(hue, saturation, solvedValue)
        if (contrastRatio(candidate, background) >= minRatio) return candidate
        if (saturation <= 0f || saturation >= 1f) return candidate
    }
    return candidate
}

/** HSV → Compose Color 的便捷包装（h ∈ [0,360)，s/v ∈ [0,1]） */
private fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
    val out = hsvToRgb(hue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    return Color(out[0], out[1], out[2], 1f)
}

/**
 * RGB(0..1) → HSV。h ∈ [0,360)，s/v ∈ [0,1]。
 * 标准算法，与 Android framework 实现同源。
 */
private fun rgbToHsv(rgb: FloatArray): FloatArray {
    val r = rgb[0]
    val g = rgb[1]
    val b = rgb[2]
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC

    val h = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val s = if (maxC == 0f) 0f else delta / maxC
    return floatArrayOf(h, s, maxC)
}

/**
 * HSV → RGB(0..1)。h ∈ [0,360)，s/v ∈ [0,1]。
 */
private fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
    val c = v * s
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hh % 2f - 1f))
    val m = v - c

    val (r1, g1, b1) = when (hh.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return floatArrayOf(r1 + m, g1 + m, b1 + m)
}

// ─────────────────────────────────────────────────────────────────────────────
// 二、字号：HIG 标准字阶
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Apple 字阶。与 M3 baseline 的差别在于：
 *  - Apple 用**更大的字号 + 更紧的字距**（大标题上尤其明显）
 *  - Body 是 17pt 而非 M3 的 16pt
 *  - 字重档位更少但更明确（Bold / Semibold / Medium / Regular）
 *
 * 做成 object 返回函数而非 val，理由同 [SleepyTextStyle]：避免调用方 `.copy()` 污染共享实例。
 */
object WedoAppleType {
    /** Large Title 34pt Bold —— 页面主标题（课表 / 管理 / 我的） */
    fun largeTitle() = TextStyle(
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.37.sp
    )

    /** Title 1 28pt Regular —— 次级页面大标题 */
    fun title1() = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.36.sp
    )

    /** Title 2 22pt Semibold —— 弹层标题、分组标题 */
    fun title2() = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.26).sp
    )

    /** Title 3 20pt Semibold —— 卡片主标题 */
    fun title3() = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.45).sp
    )

    /** Headline 17pt Semibold —— 列表主行、课程名 */
    fun headline() = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.41).sp
    )

    /** Body 17pt Regular —— 正文、表单输入 */
    fun body() = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.41).sp
    )

    /** Callout 16pt Regular —— 次要正文 */
    fun callout() = TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.32).sp
    )

    /** Subheadline 15pt Regular —— 卡片副文字 */
    fun subheadline() = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.24).sp
    )

    /** Footnote 13pt Regular —— 辅助说明 */
    fun footnote() = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.08).sp
    )

    /** Caption 1 12pt Regular —— 时间、地点、元信息 */
    fun caption1() = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )

    /** Caption 2 11pt Regular —— 最小信息 */
    fun caption2() = TextStyle(
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.07.sp
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 三、间距 / 触控 / 圆角
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Apple 间距与尺寸常量。
 *
 * 与 M3 的关键差别：
 *  - 页边距固定 16pt（M3 常用 16/24 混用）
 *  - **最小触控 44×44pt** 是硬约束（M3 是 48dp，Apple 是 44pt）
 *  - 分隔线 0.5pt
 */
object WedoAppleDimensions {
    /** 页边距 —— 全 app 统一 16pt，不许出现 12/20 */
    val pageMargin = 16.dp

    /** 分组之间的垂直间距 */
    val sectionGap = 16.dp

    /** 大分组之间的间距（如「基础设置」「高级」两块之间） */
    val sectionGroupGap = 32.dp

    /** 列表行最小高度 —— HIG 硬要求，防止点击区域过小 */
    val minRowHeight = 44.dp

    /** 最小触控边长 */
    val minTouchTarget = 44.dp

    /** 分隔线厚度 —— 0.5dp 是 Apple 观感的关键 */
    val hairline = 0.5.dp

    /** 卡片圆角 */
    val cardCorner = 10.dp

    /** 大面板圆角（弹层、模态卡） */
    val panelCorner = 22.dp

    /** 课程块圆角 */
    val courseCorner = 8.dp

    /** 课程块左侧色条宽度 */
    val courseBarWidth = 4.dp

    /** 图标按钮惯用边长 */
    val iconButton = 28.dp
}

/**
 * 连续曲率（超椭圆）圆角。
 *
 * iOS 的圆角不是数学圆弧，而是 **squircle**（超椭圆，|x|^n + |y|^n = 1，n≈5）。
 * 视觉上角更「饱满」、过渡更「顺」，这是 Apple 观感最不易察觉但最有效的细节。
 *
 * Compose 没有原生超椭圆 Shape，这里用「半径略大于标注值 + 中等 n 的等价近似」
 * 的工程折中：对 8–24dp 这一档，等效半径约为标注值 × 1.2，观感已非常接近。
 * 真正的超椭圆需要自定义 Shape + Path 采样，此处不值当。
 *
 * @param size 视觉上期望的圆角大小（按普通圆角来想）
 */
object WedoAppleShapes {
    /** 卡片形状（连续曲率近似） */
    val card: CornerBasedShape
        get() = continuous(WedoAppleDimensions.cardCorner)

    /** 大面板形状 */
    val panel: CornerBasedShape
        get() = continuous(WedoAppleDimensions.panelCorner)

    /** 课程块形状 */
    val course: CornerBasedShape
        get() = continuous(WedoAppleDimensions.courseCorner)

    /** 胶囊 */
    val capsule: CornerBasedShape
        get() = RoundedCornerShape(percent = 50)

    /**
     * 由视觉半径推导连续曲率等效半径。
     *
     * 系数 1.2 的来历：超椭圆 n=5 时，同样「看起来圆」的半径比数学圆弧大约 20%。
     * 数值太大时再加系数会让角过于夸张，故只在 ≤28dp 档位生效。
     */
    fun continuous(visualRadius: androidx.compose.ui.unit.Dp): CornerBasedShape {
        val r = visualRadius.value.coerceIn(0f, 28f) * 1.2f
        return RoundedCornerShape(r.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 四、动效
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Apple 动效参数。
 *
 * [standardEasing] 是 Apple 在 UIKit 里广泛使用的曲线 —— 起步快、收尾缓，
 * 摸起来有「惯性」而不是「匀速」。这个曲线是 Apple 观感的重要组成。
 */
object WedoAppleMotion {
    /** 进入动画时长 */
    const val enterDurationMs = 350

    /** 退出动画时长（比进入快，符合「离开要干脆」的直觉） */
    const val exitDurationMs = 250

    /** 轻交互时长（按钮按下的反馈） */
    const val quickDurationMs = 150

    /** 标准缓动曲线参数，对应 cubic-bezier(0.32, 0.72, 0, 1) */
    val standardEasing: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** 弹性缓动（弹层滑入） */
    val springEasing: Easing = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)
}

/**
 * 三次贝塞尔缓动。
 *
 * Compose 自带 [androidx.compose.animation.core.CubicBezierEasing]，这里做一层类型别名式的
 * 封装是为了让令牌层不直接依赖 animation 包的具体实现，便于单测替换。
 */
class CubicBezierEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float
) : Easing {
    /**
     * 贝塞尔求值。用牛顿迭代从 x 反解 t，再求 y。
     *
     * Compose 动画每帧都要调，故迭代收敛条件取 1e-6 且最多 8 次 —— 精度足够，
     * 又不至于在低端机上拖慢帧率。
     */
    override fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f

        var t = fraction
        repeat(8) {
            val x = bezier(t, x1, x2) - fraction
            if (abs(x) < 1e-6f) return bezier(t, y1, y2)
            val dx = bezierDerivative(t, x1, x2)
            if (abs(dx) < 1e-6f) return bezier(t, y1, y2)
            t -= x / dx
        }
        return bezier(t.coerceIn(0f, 1f), y1, y2)
    }

    private fun bezier(t: Float, p1: Float, p2: Float): Float {
        val mt = 1f - t
        // P0=0, P3=1
        return 3f * mt * mt * t * p1 + 3f * mt * t * t * p2 + t * t * t
    }

    private fun bezierDerivative(t: Float, p1: Float, p2: Float): Float {
        val mt = 1f - t
        return 3f * mt * mt * p1 + 6f * mt * t * (p2 - p1) + 3f * t * t * (1f - p2)
    }
}

/** 缓动接口 —— 与 Compose 的 androidx.compose.animation.core.Easing 同签名，便于替换 */
fun interface Easing {
    fun transform(fraction: Float): Float
}

// ─────────────────────────────────────────────────────────────────────────────
// 五、CompositionLocal 入口
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 当前 App 强调色。由 [SleepyThemeProvider] 提供。
 *
 * 强调色**只在三处出现**：选中态、链接、主按钮。
 * 这个克制程度是 Apple 观感的核心 —— 一旦强调色铺满界面，就退回成普通安卓 App 了。
 */
val LocalWedoAccent = staticCompositionLocalOf { WedoSystemColor.Default }

/** 当前是否深色模式（供课程块配色等需要自行推导颜色的场景使用） */
val LocalWedoDark = staticCompositionLocalOf { false }

/** 便捷入口 */
object WedoApple {
    /**
     * 当前强调色的**填充**版本（已按深浅模式解析）。
     *
     * 用它做色块底、按钮底、胶囊底 —— 即「它当背景」的场合。
     * 若要把它当**图标颜色**用，请用 [accentIcon]；当**文字**用，请用 [accentText]。
     */
    val accent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalWedoAccent.current.color(LocalWedoDark.current)

    /**
     * 当前强调色的**图标 / UI 组件**版本（门槛 3:1）。
     *
     * 用于给 `Icon` 上色、描边、进度条、勾选标记这类「非文字」场合。
     * 因为门槛只有 3:1，绝大多数色**原样返回** —— 浅色模式下的系统蓝仍是
     * `#007AFF`，和 Apple 观感一致，不会被压成深藏青。
     */
    val accentIcon: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalWedoAccent.current.readableColor(LocalWedoDark.current, WedoColorRole.Icon)

    /**
     * 当前强调色的**文字**版本（门槛 4.5:1）。
     *
     * 与 [accent] 的区别是必要的：系统色是按填充标定的，浅色底上直接染色当文字
     * 会不可读（橙色在白底仅 2.20:1）。本属性保证在对应模式的卡片底色上 ≥4.5:1，
     * 色相不变，只是同色更深的一档。
     *
     * 判断口诀：**它当背景 → accent；它当图标 → accentIcon；它当文字 → accentText**。
     * 拿不准就当文字用，宁可深一点。
     */
    val accentText: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalWedoAccent.current.readableColor(LocalWedoDark.current, WedoColorRole.Text)

    /** 当前是否深色 */
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalWedoDark.current

    /** 取当前模式下的系统色 */
    @Composable
    @ReadOnlyComposable
    fun systemColor(color: WedoSystemColor): Color = color.color(LocalWedoDark.current)

    /** 课程块配色入口 */
    @Composable
    @ReadOnlyComposable
    fun courseBlock(
        base: Color,
        surface: Color
    ): WedoCourseBlockColors = wedoCourseBlockColors(base, LocalWedoDark.current, surface)
}

// ─────────────────────────────────────────────────────────────────────────────
// 六、对比度工具（供单测断言，避免「看起来够亮」这种主观判断）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * WCAG 相对亮度。
 * 公式见 https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/**
 * WCAG 对比度，返回 1..21。
 * 4.5 是普通文字的 AA 门槛，本项目的课程块文字要求 ≥4.5。
 */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = max(la, lb)
    val darker = min(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}
