package com.wedo.schedule.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.wedo.schedule.R
import com.wedo.schedule.WedoApp
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.CourseColorUtil
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils
import java.time.LocalDate

/**
 * 小组件字阶 —— 直接引用 [WedoAppleType]，避免桌面端与 App 内各写一套字号。
 *
 * Canvas 只认「数值 sp」，所以这里只取 TextStyle 的 fontSize，字重另由 Typeface 设。
 * 小组件一列常常只有 40dp 宽，HIG 最小档 Caption 2（11pt）在这么窄的列里也会溢出，
 * 因此另有 [columnTinySp] / [columnMicroSp] 两个**刻意低于 HIG 最小档**的档位 ——
 * 它们是例外，不是漏改。
 */
internal object WidgetType {
    /** 13 · Footnote —— 小组件标题行 */
    val titleSp: Float = WedoAppleType.footnote().fontSize.value

    /** 12 · Caption 1 —— 日期、列头 */
    val caption1Sp: Float = WedoAppleType.caption1().fontSize.value

    /** 11 · Caption 2 —— 次要信息、状态提示 */
    val caption2Sp: Float = WedoAppleType.caption2().fontSize.value

    /** 15 · Subheadline —— 空态/状态主文案 */
    val bodySp: Float = WedoAppleType.subheadline().fontSize.value

    /** 16 · Callout —— 今日页「无课」这类强调文案 */
    val calloutSp: Float = WedoAppleType.callout().fontSize.value

    /** 10 —— 窄列档，低于 HIG 最小档（见本对象说明） */
    const val columnTinySp: Float = 10f

    /** 9 —— 窄列 mini-list 档，低于 HIG 最小档（见本对象说明） */
    const val columnMicroSp: Float = 9f
}

/**
 * Canvas bitmap 渲染器 — 各 Receiver.loadDataSync 拉数据后由本对象渲染，
 * 输出 PNG bitmap 推给 RemoteViews（生产桌面渲染 + WidgetRenderActivity 调试预览共用）。
 *
 * 2 种 widget（今日课程 / 本周课表·网格）复用同一份 scheme，色彩与 app 主题一致。
 */
object WidgetBitmapRenderers {

    // ── Apple 尺寸令牌（dp）──────────────────────────────────────────────
    // Canvas 只认像素，取值处一律 × density。改观感请改 WedoAppleDimensions，不要改这里。
    private val CONTAINER_CORNER_DP = WedoAppleDimensions.widgetCorner.value
    private val CELL_CORNER_DP = WedoAppleDimensions.widgetCellCorner.value
    private val COURSE_CORNER_DP = WedoAppleDimensions.courseCorner.value

    // ── Scheme 颜色（与 WidgetContent.resolveSchemePublic 一致） ──
    // 死代码清理: cPrimary…cPractice 9 个课程色字段与 surface 字段赋值后从未被渲染消费
    // (课程底色走 CourseColorUtil, 背景实际用 bg/surfaceContainer), 已删。
    data class Scheme(
        val bg: Int,
        val primary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val surfaceContainer: Int,
        val surfaceVariant: Int,
        /**
         * 分隔线色（Apple separator）。**不要再用「黑色 + alpha」硬凑**：
         * 深色模式下黑色线压在纯黑底上等于没画，而 separator 是成对标定的。
         */
        val separator: Int,
        val isDark: Boolean
    )

    /**
     * 主题色 — 走 resolveSchemePublic (WidgetContent.kt, 全部 widget 渲染共用)
     * 之前硬编码 Default 紫色 → 不跟随 app 主题 → 移植到 RemoteViews 后仍是错的。
     * 现在完全对齐 App 内 WedoThemeProvider 的取色（强调色名 → appleScheme）。
     */
    private fun scheme(context: Context, themeKey: String, isDark: Boolean): Scheme {
        val s = resolveSchemePublic(context, themeKey, isDark)
        fun androidx.compose.ui.graphics.Color.toIntArgb(): Int =
            (0xFF shl 24) or ((this.red * 255).toInt() shl 16) or
                ((this.green * 255).toInt() shl 8) or (this.blue * 255).toInt()
        return Scheme(
            bg = s.bg.toIntArgb(),
            primary = s.primary.toIntArgb(),
            primaryContainer = s.primaryContainer.toIntArgb(),
            onPrimaryContainer = s.onPrimaryContainer.toIntArgb(),
            onSurface = s.onSurface.toIntArgb(),
            onSurfaceVariant = s.onSurfaceVariant.toIntArgb(),
            surfaceContainer = s.surfaceContainer.toIntArgb(),
            surfaceVariant = s.surfaceVariant.toIntArgb(),
            separator = s.separator.toIntArgb(),
            isDark = isDark
        )
    }

    // hslToColorInt / pickCourseColor 本地副本已收敛至 util/CourseColorUtil.kt (决策 D3 单一事实来源)。
    // 之前用 resolveCourseColorKey 关键词分类 → 与首页/WeekGrid 色系不一致, 已废弃。

    private fun drawCourse(
        c: Canvas, p: Paint, course: CourseEntity, timeJson: String, x: Float, y: Float, w: Float, h: Float,
        scheme: Scheme, density: Float, fontSizeSp: Float = 11f, colorless: Boolean = false,
        displayMode: String = "node",
        groupRows: List<CourseEntity> = listOf(course)
    ) {
        // 统一取色入口 (决策 D3) — colorless 灰底传 scheme.surfaceVariant 的 Int 值
        // issue#22: 同名课程多地点 — 用 groupRows 传同 groupId 全行,支持 AUTO/CUSTOM 模式取色
        val bgColor = CourseColorUtil.pickCourseColorIntWithGroupRows(course, groupRows, scheme.isDark, scheme.surfaceVariant, colorless)
        // 文字色亮度自适应 (决策 D5-13) — 深色自定义课色上切白字, 浅色底仍 onSurface
        val textColor = CourseColorUtil.textColorOn(bgColor, scheme.isDark, scheme.onSurface)
        val pad = (3f * density).coerceAtLeast(1f)
        p.color = bgColor
        c.drawRoundRect(RectF(x, y, x + w, y + h),
            COURSE_CORNER_DP * density, COURSE_CORNER_DP * density, p)

        // 时间 + 地点 — 先算 meta 文本 (需要知道是否有第二行才能居中)
        // displayMode (决策 D5-12, 对齐 CourseTableView.LessonRow):
        //   "time" → 具体时间段 "08:00-09:35"; "node"(默认) → 节次 "3-4节"
        val timeStr = if (displayMode == "time" && timeJson.isNotBlank()) {
            TimeTableUtils.courseTimeString(
                courseStartNode = course.startNode,
                courseStep = course.step,
                timeJson = timeJson,
                ownTime = course.ownTime,
                startTime = course.startTime,
                endTime = course.endTime
            ) ?: course.shortNodeString(WedoApp.get())
        } else {
            course.shortNodeString(WedoApp.get())
        }
        val hasMeta = timeStr.isNotBlank() || course.room.isNotBlank()

        // 字号
        val nameSize = fontSizeSp * density
        val metaSize = (fontSizeSp - 2f) * density
        val lineGap = 2f * density

        // meta 行拆分(用户反馈: 宽度不够时时间+地点拼一行必溢出卡片边框)
        // metaSize 字号下行宽可容纳 → 单行(旧行为); 放不下 → 时间一行/地点一行, 每行省略号兜底
        p.textSize = metaSize
        val metaLines = if (hasMeta) {
            courseMetaLines(
                measure = { t -> p.measureText(t) },
                maxWidth = w - pad * 2,
                timeStr = timeStr,
                room = course.room
            )
        } else emptyList()

        // 用 FontMetrics 算真实行高 → 垂直居中两行文字块
        p.textSize = nameSize
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.isAntiAlias = true
        val fmName = p.fontMetrics
        val nameH = fmName.descent - fmName.ascent

        var metaH = 0f
        var fmMeta: Paint.FontMetrics? = null
        if (metaLines.isNotEmpty()) {
            p.textSize = metaSize
            fmMeta = p.fontMetrics
            metaH = fmMeta!!.descent - fmMeta.ascent
        }
        val metaLineCount = metaLines.size
        val metaBlockH = if (metaLineCount > 0) (metaLineCount - 1) * (metaH + lineGap) + metaH else 0f

        val totalH = nameH + (if (metaLineCount > 0) lineGap + metaBlockH else 0f)
        val blockTop = y + (h - totalH) / 2f

        // 课程名 — 亮度自适应文字色 (决策 D5-13)
        p.textSize = nameSize
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.color = textColor
        val name = course.courseName
        val maxWidth = w - pad * 2
        val displayName = if (p.measureText(name) > maxWidth) {
            var n = name
            while (n.isNotEmpty() && p.measureText("$n…") > maxWidth) n = n.dropLast(1)
            "$n…"
        } else name
        c.drawText(displayName, x + pad, blockTop - fmName.ascent, p)

        // 时间/地点 — 拆行后逐行绘制, 每行省略号兜底
        if (metaLines.isNotEmpty()) {
            p.textSize = metaSize
            p.typeface = Typeface.DEFAULT
            p.color = textColor
            var my = blockTop + nameH + lineGap
            for (line in metaLines) {
                c.drawText(ellipsize(p, line, maxWidth), x + pad, my - fmMeta!!.ascent, p)
                my += metaH + lineGap
            }
        }
    }

    /**
     * Today widget 渲染 — 今日课程列表
     * SMALL 变体 + 容器 <150dp → 走紧凑档(纯文本); REGULAR 或容器被拖大 ≥150dp → 全量排版
     * (默认参数 REGULAR → 全部现有调用点零改动; 大档路径 renderTodayRegular 函数体逐字节不变)
     */
    fun renderToday(
        context: Context, data: WidgetData, wDp: Float, hDp: Float,
        variant: WidgetVariant = WidgetVariant.REGULAR
    ): Bitmap {
        return renderTodayRegular(context, data, wDp, hDp)
    }

    /**
     * 小档纯文本行(渲染与单测共用单一事实来源)。空课表/学期外也各有对应一行。
     * resolver 抽象掉 Context 资源访问 → 核心选取逻辑可在纯 JVM 单测断言(仓库无 Robolectric)。
     */
    fun todayCompactTexts(context: Context, data: WidgetData): List<String> =
        todayCompactTexts({ resId -> context.getString(resId) }, data)

    /** 同上 — resolver 注入版(纯 JVM 单测入口) */
    fun todayCompactTexts(resolve: (Int) -> String, data: WidgetData): List<String> {
        if (!data.hasTable) return listOf(resolve(R.string.widget_create_schedule))
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            return listOf(resolve(statusRes))
        }
        if (data.courses.isEmpty()) return listOf(resolve(R.string.today_no_course))
        return data.courses.map { it.courseName }
    }

    /**
     * Today 紧凑档 — 日期小字(顶) + 状态/首课程名(居中), 纯文本无课程胶囊。
     * 布局常量: compact 档不参与 todayContentHeightDp 滚动条带估算(固定 size 变体), 无需镜像。
     */
    private fun renderTodayCompact(context: Context, data: WidgetData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val ctx = WedoApp.get()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景圆角
        p.color = s.bg
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
            CONTAINER_CORNER_DP * density, CONTAINER_CORNER_DP * density, p)

        val pad = 10f * density
        val lines = todayCompactTexts(ctx, data)

        // 日期行(顶部小字)
        p.color = s.onSurfaceVariant
        p.textSize = WidgetType.caption2Sp * density
        p.typeface = Typeface.DEFAULT
        val dateStr = "${data.date.monthValue}/${data.date.dayOfMonth}"
        canvas.drawText(dateStr, pad, pad + 11f * density, p)

        // 状态/首课程名 — 居中大字
        p.color = s.onSurface
        p.textSize = WidgetType.bodySp * density
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        var y = h / 2f
        for (line in lines.take(2)) {
            canvas.drawText(ellipsize(p, line, w - pad * 2), pad, y, p)
            y += 20f * density
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /** 按可用宽度截断文本(字符级贪心, 与 [[text-overflow-fix]] 同思路) */
    private fun ellipsize(p: Paint, text: String, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /**
     * Today 全量排版 — 原 renderToday 函数体原样改名迁入(REGULAR 档逐字节不变保证)
     */
    private fun renderTodayRegular(context: Context, data: WidgetData, wDp: Float, hDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val s = scheme(context, data.themeKey, data.isDark)
        val colorless = AppPrefs.isWidgetColorless(context)
        // 用户显示设置 (决策 D5-12, 读法对齐 WeekGridWidgetProvider.loadWeekData L660-662)
        val displayMode = AppPrefs.getDisplayMode(context)
        val showDate = AppPrefs.isShowDate(context)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景圆角
        p.color = s.bg
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
            CONTAINER_CORNER_DP * density, CONTAINER_CORNER_DP * density, p)

        val pad = 14f * density
        var y = pad

        // 标题行：今天 · 周X  +  日期 (showDate=false 时隐藏右侧日期, 对齐课表页设置)
        val ctx = WedoApp.get()
        p.color = s.primary
        p.textSize = WidgetType.titleSp * density
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val titleStr = "${ctx.getString(R.string.today_today)} · ${DateUtils.localizedDay(data.date.dayOfWeek.value, ctx)}"
        canvas.drawText(titleStr, pad, y + 13f * density, p)

        if (showDate) {
            p.color = s.onSurfaceVariant
            p.textSize = WidgetType.caption1Sp * density
            p.typeface = Typeface.DEFAULT
            val dateStr = "${data.date.monthValue}/${data.date.dayOfMonth}"
            val dateWidth = p.measureText(dateStr)
            canvas.drawText(dateStr, w - pad - dateWidth, y + 13f * density, p)
        }

        y += 24f * density

        if (!data.hasTable) {
            p.color = s.onSurface
            p.textSize = WidgetType.bodySp * density
            canvas.drawText(ctx.getString(R.string.widget_create_schedule), pad, y + 15f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        // 学期外: 状态标题 + 提示行, 不画课程 (loadDataSync 已清空 courses, 此处为标题语义)
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            p.color = s.onSurface
            p.textSize = WidgetType.bodySp * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(ctx.getString(statusRes), pad, y + 15f * density, p)
            y += 22f * density
            p.color = s.onSurfaceVariant
            p.textSize = WidgetType.caption2Sp * density
            p.typeface = Typeface.DEFAULT
            canvas.drawText(ctx.getString(R.string.today_semester_out_hint), pad, y + 11f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        if (data.courses.isEmpty()) {
            p.color = s.onSurface
            p.textSize = WidgetType.calloutSp * density
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(ctx.getString(R.string.today_no_course), pad, y + 16f * density, p)
            y += 22f * density
            p.color = s.onSurfaceVariant
            p.textSize = WidgetType.caption1Sp * density
            p.typeface = Typeface.DEFAULT
            canvas.drawText(ctx.getString(R.string.today_rest), pad, y + 12f * density, p)
            return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
        }

        // 课程列表（全部渲染，不再截断）
        // v7.10.11: 冲突分栏 — 与 App 今日页/周视图同一引擎(weekLaneRows),
        // 冲突区域一行内并排(栏间浅细竖线), 同栏课纵向堆叠, 无冲突课整宽。
        // 栏内多课时该行实际高度由最高栏决定(各栏 y 游标独立推进后再取 max 对齐)。
        val rowH = 38f * density
        val rowGap = 10f * density  // 课程胶囊间距放大(用户反馈太紧凑)
        val rowW = w - pad * 2

        val laneRows = com.wedo.schedule.util.ConflictLayoutEngine.weekLaneRows(data.courses)
        // 分栏竖线 —— 用 Apple separator。原实现是「黑色 30%」，深色模式下
        // 黑线压在纯黑底上等于没画，分栏边界整条消失。
        val sepColor = s.separator
        laneRows.forEach { row ->
            if (row.laneCount == 1) {
                drawCourse(canvas, p, row.courses[0], data.timeJson, pad, y, rowW, rowH, s, density,
                    fontSizeSp = WidgetType.caption1Sp, colorless = colorless, displayMode = displayMode,
                    groupRows = data.courses.filter { it.groupId == row.courses[0].groupId })
                y += rowH + rowGap
            } else {
                val laneGap = 5f * density
                val laneW = (rowW - laneGap * (row.laneCount - 1)) / row.laneCount
                val stackGap = 3f * density
                // 行高 = 最高栏(栏内课数最多)的总高 — 各栏共享行起点,行尾对齐
                val maxStack = row.courses.groupBy { row.laneOf[it.id] }.values
                    .maxOf { it.size }.coerceAtLeast(1)
                val laneRowTotalH = maxStack * rowH + (maxStack - 1) * stackGap
                repeat(row.laneCount) { li ->
                    val laneX = pad + li * (laneW + laneGap)
                    // 栏间浅细竖线(与 App 分栏同语义)
                    if (li > 0) {
                        val sepX = laneX - laneGap / 2f
                        val keepColor = p.color
                        p.color = sepColor
                        canvas.drawRect(sepX - 0.5f * density, y, sepX + 0.5f * density,
                            y + laneRowTotalH, p)
                        p.color = keepColor
                    }
                    val laneCourses = row.courses.filter { row.laneOf[it.id] == li }
                    var ly = y
                    laneCourses.forEachIndexed { ci, laneCourse ->
                        drawCourse(canvas, p, laneCourse, data.timeJson, laneX, ly, laneW, rowH, s, density,
                            fontSizeSp = WidgetType.columnTinySp, colorless = colorless, displayMode = displayMode,
                            groupRows = data.courses.filter { it.groupId == laneCourse.groupId })
                        ly += rowH
                        if (ci < laneCourses.size - 1) ly += stackGap
                    }
                }
                y += laneRowTotalH + rowGap
            }
        }

        return bmp.apply { eraseColor(Color.TRANSPARENT); Canvas(this).drawBitmap(c, 0f, 0f, null) }
    }

    /**
     * Today 内容全展开高度(dp) — 可滚动条带渲染用。
     * 纯计算零绘制; 布局常量逐一镜像 renderToday (改那边必须同步这边)。
     * v7.10.11: 冲突分栏行高按最高栏堆叠数算(与 renderToday 分栏镜像)。
     */
    fun todayContentHeightDp(data: WidgetData): Float {
        // 标题区: pad(14) + 标题行(24) — 与 renderToday: y=pad; y+=24
        var h = 14f + 24f
        if (!data.hasTable) return h + 20f          // "去创建课表" 一行
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) return h + 22f + 14f  // 学期状态 + 提示行
        if (data.courses.isEmpty()) return h + 22f + 14f  // 无课标题 + 休息副行
        val rowH = 38f
        val rowGap = 10f
        val stackGap = 3f
        val laneRows = com.wedo.schedule.util.ConflictLayoutEngine.weekLaneRows(data.courses)
        for (row in laneRows) {
            if (row.laneCount == 1) {
                h += rowH + rowGap
            } else {
                val maxStack = row.courses.groupBy { row.laneOf[it.id] }.values
                    .maxOf { it.size }.coerceAtLeast(1)
                h += maxStack * rowH + (maxStack - 1) * stackGap + rowGap
            }
        }
        h += 14f                                    // 底部 pad
        return h
    }

    /**
     * WeekGrid 最小档数据映射 — WeekData → 今日 WidgetData。
     * 最小档(1×1 列)不再"折叠成单列的网格脸", 直接复用今日课程·小的渲染器:
     * 宿主只换数据, 渲染走 renderToday(SMALL) → 与今日课程·小像素同源同一张脸。
     * 纯函数零 LocalDate.now() — today 由调用方注入。
     */
    fun weekGridMinimumTodayData(data: WeekData, today: LocalDate): WidgetData {
        val timeJson = data.days.firstOrNull()?.timeJson ?: ""
        val todayDay = data.days.firstOrNull { it.dayOfWeek == today.dayOfWeek.value }
        return WidgetData(
            date = today,
            courses = todayDay?.courses ?: emptyList(),
            timeJson = timeJson,
            hasTable = data.hasTable,
            isDark = data.isDark,
            themeKey = data.themeKey,
            semesterStatus = data.semesterStatus
        )
    }

    /**
     * drawCourse meta 行拆分 — 拼行("时间 · 地点")放不下时拆两行(时间一行/地点一行)。
     * 文本宽度可加(拼行宽恒 ≥ 两行之和), 拆行永不更差 → 无需收益判定;
     * 拆开后单行仍超宽的极端场景由渲染端逐行省略号兜底。纯函数 — measure 由调用方注入。
     */
    fun courseMetaLines(
        measure: (String) -> Float,
        maxWidth: Float,
        timeStr: String,
        room: String
    ): List<String> {
        if (room.isBlank()) return listOf(timeStr)
        val combined = "$timeStr · $room"
        return if (measure(combined) <= maxWidth) listOf(combined) else listOf(timeStr, room)
    }

}
