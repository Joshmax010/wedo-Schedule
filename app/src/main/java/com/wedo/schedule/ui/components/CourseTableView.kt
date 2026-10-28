package com.wedo.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.wedo.schedule.ui.theme.LocalWedoDisplay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.wedo.schedule.R
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.theme.WedoTextStyle
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleDimensions
import com.wedo.schedule.ui.theme.WedoAppleShapes
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoCourseBlockColors
import com.wedo.schedule.ui.theme.noRippleClickable
import com.wedo.schedule.ui.theme.wedoCourseBlockColors
import com.wedo.schedule.util.AppPrefs
import com.wedo.schedule.util.ConflictLayoutEngine
import com.wedo.schedule.util.CourseColorUtil
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils
import kotlinx.coroutines.flow.filter
import java.time.LocalTime

/**
 * 时段定义 — 5 个时段（对应 HTML 里的 5 个 slot-row）
 * 与 WakeUp 默认 12 节对应: 1-2 / 3-5 / 6-7 / 8-10 / 11-13
 */
data class TimeSlot(
    val label: String,         // "1-2节"
    val start: LocalTime,
    val end: LocalTime,
    val displayStart: String,  // "08:00"
    val displayEnd: String,    // "09:35"
    val nodeStart: Int,
    val nodeEnd: Int
) {
    // nodeString 死属性已删（恒返回 "N-N" 且全库零调用; 界面用的是 CourseEntity.nodeString 本地化版本）
    val timeString: String get() = "$displayStart-$displayEnd"
}

/**
 * Cards 网格视图
 *
 * 架构：
 *   BoxWithConstraints(fillMaxSize) → 算出 colW (dp)
 *     Column(fillMaxWidth, verticalScroll)
 *       Row(表头)            — Compose 自然排版
 *       Box(固定高度 = maxNode * rowH) — 时间栏 + 课程卡片全用 Modifier.offset 绝对定位
 *
 * 关键点：
 * - Column 用 fillMaxWidth（不是 fillMaxSize），内容高度 = 表头 + 固定 gridH，超出视口 → 可滚动
 * - 时间栏 / 卡片都在同一个 Box 内，Modifier.offset 定位 → 滚动完全同步
 * - 全用 dp 算 offset，不碰 px，不碰 Layout measure/place
 */
@Composable
fun CardsGridView(
    courses: List<CourseEntity>,
    timeSlots: List<TimeSlot>,
    wedo: Boolean = false,
    ghostCourses: List<CourseEntity> = emptyList(),
    onCourseLongClick: (CourseEntity) -> Unit = {},
    visibleDays: Set<Int> = (1..7).toSet(),
    showDate: Boolean = false,
    startDate: String = "",
    currentWeek: Int = 1,
    today: Int = DateUtils.todayDayOfWeek(),
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier,
    greyDays: Set<Int> = emptySet(),  // 本周应灰显的星期几 (1-7)
    topOverrides: Map<String, Long> = emptyMap(),           // v7.10.5: 会话级置顶 override,上提到 ScheduleScreen(radio 瞬时更新写入同一真相源)
    onSetTopOverride: (String, Long?) -> Unit = { _, _ -> }, // (clusterKey, layerRepId|null)
    // v7.10.16r 轮换态(issue#10, 评审 #1): 与 topOverrides 同级持有(离开课表页重置)。
    // remember 留在页内会在 HorizontalPager 翻页时销毁( beyondViewportPageCount=0 ),
    // 周切换一次轮换态即丢。
    rotationSteps: Map<String, Int> = emptyMap(),
    onRotationStep: (String, Int) -> Unit = { _, _ -> }     // (clusterKey, step)
) {
    val colors = WedoTheme.colors
    val maxNode = timeSlots.maxOfOrNull { it.nodeEnd } ?: 12
    val sortedDays = visibleDays.sorted()
    val dayCount = sortedDays.size

    // issue#23: 边缘节次节点的"行号"按 timeSlots 自然顺序取(已按 node ASC 排序);
    // 前置节点(-1, 0)排到 grid 顶部, 后置节点(N+1, N+2)排到 grid 底部,
    // 视觉上就是"第 0 节在第 1 节之上" / "第 N+1 节在第 N 节之下", 与插入直觉一致。
    //   返回 -1 = 该节点不在 timeSlots(典型场景: 课程来自已删除的旧 timeJson, 数据脏)
    //   调用方需先判 >= 0 再绘;cardY 那侧 .coerceAtLeast(0) 兜底防负坐标。
    fun slotIndexOf(node: Int): Int = timeSlots.indexOfFirst { it.nodeStart == node }

    // 设置页改 scale / cornerRatio 后强制 recompose
    var prefVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppPrefs.changeBus.filter {
            it == AppPrefs.KEY_GRID_SCALE || it == AppPrefs.KEY_GRID_CORNER_RATIO
        }.collect { prefVersion++ }
    }

    // issue#8 网格整体缩放: 0.7~1.3, 字号/行高/间距/圆角/内边距等比联动
    // (12节连堂课表缩到 0.7 可一屏放下; 只影响本 Cards 视图, 小组件与列表视图不受影响)
    val display = LocalWedoDisplay.current
    val scale = if (wedo) 1f else AppPrefs.getGridScale(androidx.compose.ui.platform.LocalContext.current)
    val cornerRatio = AppPrefs.getGridCornerRatio(androidx.compose.ui.platform.LocalContext.current)
    val d = { v: Float -> (v * scale).dp }

    // 布局常量（全 dp, 乘 scale）
    val headH = d(52f)
    val timeW = d(if (wedo) 36f else 68f)
    val slotH = if (wedo) when (display.density) { "compact" -> 48.dp; "spacious" -> 82.dp; else -> 64.dp } else d(52f)
    val gapH = d(4f)
    val gapW = d(if (wedo) 2f else 5f)
    val rowH = slotH + gapH

    val gridBgShape = WedoTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (wedo) Color.Transparent else colors.surfaceContainerHigh, gridBgShape)
            .padding(if (wedo) 5.dp else d(8f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 算出每列宽度 (dp)
            val colW = (maxWidth - timeW - gapW * (dayCount + 1)) / dayCount
            // issue#23: grid 高度按 timeSlots 行数算(maxNode 已不反映边缘节点总数)——
            // edge 节点在 timeSlots 末尾, 视觉上自然排到第 N 节之下。
            val gridH = rowH * (timeSlots.size.coerceAtLeast(1))

            val scrollState = rememberScrollState()

            // Dock 悬浮底栏: 滚动尾部多留 Dock 总高, 最后 ≤maxNode 节的卡片能滚到 Dock 上方
            val navExtra = com.wedo.schedule.ui.components.LocalNavExtraBottomPadding.current

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (wedo) Modifier.fillMaxHeight() else Modifier.verticalScroll(scrollState))
            ) {
                // ---- 表头：自然 Compose Row ----
                Row(
                    modifier = Modifier.fillMaxWidth().height(headH),
                    horizontalArrangement = Arrangement.spacedBy(gapW),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(timeW))
                    for (day in sortedDays) {
                        val dateStr = if (showDate && startDate.isNotBlank()) {
                            try {
                                val ds = DateUtils.dateOfWeek(startDate, currentWeek, day)
                                DateUtils.shortDate(ds)
                            } catch (_: Exception) { null }
                        } else null
                        DayHeadCell(
                            day = day,
                            isToday = day == today,
                            isGrey = day in greyDays,
                            courseCount = courses.count { it.day == day },
                            dateStr = dateStr,
                            scale = scale,
                            cornerRatio = cornerRatio,
                            modifier = Modifier.width(colW).fillMaxHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(gapH))

                Column(Modifier.fillMaxWidth().then(if (wedo) Modifier.weight(1f).testTag("week-grid-scroll-$currentWeek").verticalScroll(scrollState) else Modifier)) {
                // ---- Grid 主体：固定高度 Box，内部全用 Modifier.offset 绝对定位 ----
                // 2026-10-01 真机反馈：横竖格子线**全部移除**——空课表时满屏空格严重影响观感，
                // 课程块自身靠色块界定位置，时间/星期锚点由左侧节次标签与顶部星期行承担。
                Box(modifier = Modifier.fillMaxWidth().height(gridH)) {
                    // 时间栏：每个节次一个 Row，用 offset 定位到正确 y
                    for ((i, slot) in timeSlots.withIndex()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(slotH)
                                .offset(y = rowH * i),
                            horizontalArrangement = Arrangement.spacedBy(gapW),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SingleTimeHeadCell(
                                slot = slot,
                                wedo = wedo,
                                scale = scale,
                                modifier = Modifier.width(timeW).fillMaxHeight(),
                                cornerRatio = cornerRatio
                            )
                            // 透明占位：保证行宽和表头一致
                            for (day in sortedDays) {
                                Spacer(modifier = Modifier.width(colW).fillMaxHeight())
                            }
                        }
                    }

                    // Ghost records are painted below active cards and never join conflict detection.
                    if (wedo) for (course in ghostCourses) {
                        val index = slotIndexOf(course.startNode)
                        val dayIndex = sortedDays.indexOf(course.day)
                        if (index < 0 || dayIndex < 0) continue
                        Box(Modifier.offset(x = timeW + gapW + (colW + gapW) * dayIndex, y = rowH * index)
                            .width(colW).height(rowH * course.step.coerceIn(1, timeSlots.size - index) - gapH)
                            .padding(2.dp).clip(WedoAppleShapes.continuous(WedoAppleDimensions.courseCorner))
                            // 非本周课程：Apple 化后统一走「无彩淡底」——
                            // 它是背景信息，不该用课程色争夺注意力（原来的 surfaceVariant 35% 也接近，这里明确其语义）
                            .background(colors.surfaceContainerHigh.copy(alpha = 0.6f))) {
                            Text("非本周\n" + course.courseName, Modifier.padding(4.dp),
                                color = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.highContent),
                                fontSize = 10.sp, lineHeight = 13.sp, maxLines = 3)
                        }
                    }
                    // 课程卡片：用 offset 绝对定位 — 冲突簇整簇走 ConflictClusterCard,
                    // 非簇课保持原 CourseOverlayCard 单卡路径(回归保护)
                    val context = LocalContext.current
                    val conflictStyle = AppPrefs.getConflictStyle(context)
                    // v7.10.16r 轮换态(issue#10): 簇键 → 轮换步数 — 与 topOverrides 同级
                    // 持有(ScheduleScreen),翻页/周切换不丢(评审 #1);纯会话级不落盘,
                    // 离开课表页即重置。默认序按用户置顶偏好走,轮换只改当次视图。
                    // v7.10.5: 交换置顶状态上提到 ScheduleScreen — 详情弹窗 radio 点击
                    // 与网格 onPickTop 写同一真相源,radio 勾选瞬间网格同帧换层。
                    // (此前 radio 只写持久化 defaultTopMap,被会话级 topOverrides 遮蔽 → 看似不生效)
                    fun setTopOverride(key: String, courseId: Long?) = onSetTopOverride(key, courseId)

                    // v7.9 默认置顶:跨会话持久化偏好,由详情弹窗 radio 写入。
                    // 与 topOverrides 同簇键,作 topOverrideId fallback — 当次切换仍由 topOverrides 主导。
                    val defaultTopMap by AppPrefs.conflictDefaultTopFlow(context).collectAsState(
                        initial = AppPrefs.getConflictDefaultTop(context)
                    )

                    // 引擎聚簇: 同天区间相交(含链式)课程归簇,簇键=主课判定序首课三元组
                    // (override 不改变首课——findClusters 输出固定,键稳定)
                    val clusters = ConflictLayoutEngine.findClusters(courses)
                    val clusteredIds = clusters.flatMap { c -> c.courses.map { it.id } }.toSet()

                    for (cluster in clusters) {
                        // 簇内课若因 visibleDays 过滤或节点不在 timeJson 则整簇跳过
                        if (cluster.day !in visibleDays) continue
                        // issue#23: 边缘节点同样允许 — 用 slotIndexOf 兜底,数据脏返回 -1 也直接过滤掉
                        val inGrid = cluster.courses.filter {
                            slotIndexOf(it.startNode) >= 0
                        }
                        if (inGrid.isEmpty()) continue
                        val anchor = cluster.courses.first() // 主课判定序首位,决定簇基点
                        val dayIdx = sortedDays.indexOf(cluster.day)
                        val cardX = timeW + gapW + (colW + gapW) * dayIdx
                        val cardY = rowH * slotIndexOf(anchor.startNode).coerceAtLeast(0)
                        val clusterKey = ConflictLayoutEngine.conflictClusterKey(cluster)

                        ConflictClusterCard(
                            cluster = cluster,
                            style = conflictStyle,
                            topOverrideId = topOverrides[clusterKey] ?: defaultTopMap[clusterKey],
                            onPickTop = { id -> setTopOverride(clusterKey, id) },
                            // v7.10.16r 轮换(issue#10): 步数与回调都按簇键隔离,纯会话态不落盘
                            rotationStep = rotationSteps[clusterKey],
                            onRotate = {
                                onRotationStep(clusterKey, (rotationSteps[clusterKey] ?: 0) + 1)
                            },
                            onPickFromBadge = { layerPos ->
                                // 气泡选课 = 换来看: 步数使目标层转到基准序首位(会话态)
                                onRotationStep(clusterKey, layerPos)
                            },
                            onCourseClick = onCourseClick,
                            colW = colW,
                            rowH = rowH,
                            maxNode = maxNode,
                            timeSlots = timeSlots,
                            timeW = timeW,
                            gapW = gapW,
                            gapH = gapH,
                            isGrey = cluster.day in greyDays,
                            modifier = Modifier.offset(x = cardX, y = cardY)
                        )
                    }

                    for (course in courses) {
                        if (course.day !in visibleDays) continue
                        // issue#23: 边缘节点允许 — slotIndexOf = -1 表示该课 startNode 不在当前 timeJson,
                        // 通常是数据脏(老 timeJson 残留了已删节点), 静默跳过不渲染避免越界
                        val nodeIdx = slotIndexOf(course.startNode)
                        if (nodeIdx < 0) continue
                        if (course.id in clusteredIds) continue // 簇内课已由 ConflictClusterCard 绘制
                        val dayIdx = sortedDays.indexOf(course.day)
                        // 步长上限按剩余行数算(边缘节点也按 timeSlots 总行数取模)
                        val steps = course.step.coerceAtLeast(1)
                            .coerceAtMost(timeSlots.size - nodeIdx)
                        val cardX = timeW + gapW + (colW + gapW) * dayIdx
                        // issue#23 §5: 非常规时间(ownTime)课按真实分钟比例定位(1.0 = 一整行);
                        // 时间映射失败退回整格吸附(normalizeNode 已反算 nodeIdx/steps)
                        val frac = if (course.ownTime) TimeTableUtils.timeToFractionalRows(
                            course.startTime, course.endTime, timeSlots
                        ) else null
                        val cardY = frac?.let { rowH * it.first } ?: rowH * nodeIdx
                        val cardH = if (frac != null) {
                            // 按比例, 但保底 0.3 行避免过短课胶囊塌缩到不可点
                            (rowH * (frac.second - frac.first) - gapH).coerceAtLeast(rowH * 0.3f)
                        } else {
                            rowH * steps - gapH
                        }

                        CourseOverlayCard(
                            course = course,
                            onClick = { onCourseClick(course) },
                            modifier = Modifier
                                .offset(x = cardX, y = cardY)
                                .width(colW)
                                .height(cardH),
                            isGrey = course.day in greyDays,
                            scale = scale,
                            cornerRatio = cornerRatio,
                            wedo = wedo,
                            onLongClick = { onCourseLongClick(course) },
                            groupRows = courses.filter { it.groupId == course.groupId }
                        )
                    }
                }
                if (navExtra > 0.dp) Spacer(modifier = Modifier.height(navExtra))
                }
            }
        }
    }
}

@Composable
private fun SingleTimeHeadCell(slot: TimeSlot, scale: Float = 1f, modifier: Modifier = Modifier, cornerRatio: Float = 1f, wedo: Boolean = false) {
    val colors = WedoTheme.colors
    if (wedo) {
        // Apple 化：时间栏去容器化。原来每格是一张 surfaceContainerLow 圆角卡片，
        // 一列下来是一串「小方块」。iOS 的时间轴只是**一行淡色小字**，
        // 不占视觉重量 —— 它的作用是被扫到，不是被看到。
        Column(
            modifier.padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                slot.label,
                color = colors.onSurfaceVariant,
                style = WedoAppleType.caption1().copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp)
            )
            Text(slot.displayStart, color = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive), fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
            Text(slot.displayEnd, color = colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive), fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
        }
        return
    }
    val sd = { v: Float -> (v * scale).dp }
    val shape = RoundedCornerShape(sd(12f * cornerRatio))
    Box(
        modifier = modifier.padding(sd(2f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(shape)
                .background(colors.surfaceContainerLow)
                .padding(sd(4f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.period_format_node, slot.label),
                    style = WedoTextStyle.smallMeta().copy(fontWeight = FontWeight.SemiBold, fontSize = (10 * scale).sp, lineHeight = (14 * scale).sp),
                    color = colors.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(sd(1f)))
                Text(
                    text = slot.timeString,
                    style = WedoTextStyle.micro().copy(fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// EmptyGridCell 死组件已删（注释自述弃用, 全库零调用——Cards 网格走 SingleTimeHeadCell + CourseOverlayCard）。

@Composable
private fun CourseOverlayCard(
    course: CourseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGrey: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    groupRows: List<CourseEntity> = listOf(course),
    wedo: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    if (wedo) {
        WedoCourseCard(course, modifier, onClick = onClick, onLongClick = onLongClick)
        return
    }
    val palette = WedoTheme.palette
    val colors = WedoTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    // 统一取色入口（决策 D3）— colorless 读取 AppPrefs course_colorless 独立开关
    // issue#22: 同名课程多地点 — 用 groupRows 传同 groupId 全行,支持 AUTO/CUSTOM 模式取色
    val bg = CourseColorUtil.pickCourseColorComposeWithGroupRows(
        row = course,
        groupRows = groupRows,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.surfaceVariant,
        colorless = AppPrefs.isCourseColorless(context)
    )
    // 文字色亮度自适应（决策 D5-13）— 深色自定义课色上切白字，浅色底仍 onSurface
    val fg = CourseColorUtil.textColorOn(bg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    val shape = RoundedCornerShape((12 * scale * cornerRatio).dp)
    val sd = { v: Float -> (v * scale).dp }
    // 副信息（教室/教师/无）— 左栏 SingleTimeHeadCell 已有节次+时间，卡片 y 位置本身编码节次，
    // 故卡内不再显示节次/时间，改由 grid_sub_info 设置决定
    val subInfo = AppPrefs.getGridSubInfo(context)
    val subText = when (subInfo) {
        "room" -> course.room
        "teacher" -> course.teacher
        else -> ""
    }

    // 节假日灰显：色块叠 alpha + 文字应用 strikethrough 样式
    val effectiveBg = if (isGrey) bg.copy(alpha = WedoTheme.Alpha.inactive) else bg
    val effectiveFg = if (isGrey) fg.copy(alpha = WedoTheme.Alpha.inactive) else fg
    val holidayStyle = AppPrefs.getHolidayStyle(context)
    val textDecoration = if (isGrey && holidayStyle == "strikethrough") {
        androidx.compose.ui.text.style.TextDecoration.LineThrough
    } else null

    Box(
        modifier = modifier
            .padding(sd(2f))
            .clip(shape)
            .background(effectiveBg)
            .noRippleClickable(onClick)
            .padding(sd(4f))
    ) {
        if (subText.isBlank()) {
            // 无副信息: 课程名整体居中(原行为)
            Text(
                text = course.courseName,
                style = WedoAppleType.caption2().copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (10 * scale).sp,
                    lineHeight = (13 * scale).sp,
                    textDecoration = textDecoration
                ),
                color = effectiveFg,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // 有副信息: 课程名在剩余空间内居中, 副信息贴卡底 — 主文字不再紧贴副文字
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 用 weight(1f) 占位让课程名在上半区居中, 避免正正好好贴住副文字
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = course.courseName,
                        style = WedoAppleType.caption2().copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (10 * scale).sp,
                            lineHeight = (13 * scale).sp,
                            textDecoration = textDecoration
                        ),
                        color = effectiveFg,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subText,
                    style = WedoTextStyle.micro().copy(
                        fontSize = (9 * scale).sp,
                        lineHeight = (11 * scale).sp,
                        textDecoration = textDecoration
                    ),
                    color = effectiveFg.copy(alpha = WedoTheme.Alpha.highContent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DayHeadCell(day: Int, isToday: Boolean, isGrey: Boolean = false, courseCount: Int, dateStr: String? = null, dayLabel: String = DateUtils.localizedDay(day, androidx.compose.ui.platform.LocalContext.current), modifier: Modifier = Modifier, scale: Float = 1f, cornerRatio: Float = 1f) {
    val colors = WedoTheme.colors
    val sd = { v: Float -> (v * scale).dp }
    // 用作前景文字色，故取 accentText 而非 accent
    val accent = WedoApple.accentText

    // Apple 化：今天不再是整块高亮的色卡，而是**「今天」二字染强调色**（如 iOS 日历）。
    // 整块高亮是 Material 的做法，Apple 用颜色只点一处，其余留白。
    val bg = Color.Transparent
    val fg = when {
        isGrey -> colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive)
        isToday -> accent
        else -> colors.onSurface
    }
    val subFg = when {
        isGrey -> colors.onSurfaceVariant.copy(alpha = WedoTheme.Alpha.inactive)
        isToday -> accent.copy(alpha = WedoTheme.Alpha.highContent)
        else -> colors.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(if (dateStr != null) sd(48f) else sd(44f))
            .padding(vertical = sd(6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sd(1f))
        ) {
            Text(
                text = dayLabel,
                style = WedoAppleType.subheadline().copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (13 * scale).sp,
                    lineHeight = (17 * scale).sp
                ),
                color = fg,
                maxLines = 1
            )
            if (dateStr != null) {
                Text(
                    text = dateStr,
                    style = WedoAppleType.caption1().copy(fontSize = (10 * scale).sp, lineHeight = (13 * scale).sp),
                    color = subFg,
                    maxLines = 1
                )
            } else {
                Text(
                    text = if (courseCount == 0) stringResource(R.string.no_course) else stringResource(R.string.course_count_format, courseCount),
                    style = WedoAppleType.caption2().copy(fontSize = (9 * scale).sp, lineHeight = (12 * scale).sp),
                    color = subFg,
                    maxLines = 1
                )
            }
        }
    }
}


// TimeHeadCell / SpannedTimeHeadCell 死组件已删（SpannedTimeHeadCell 注释自述弃用,
// TimeHeadCell 被 SingleTimeHeadCell 取代, 两者全库零调用; CELL_H 常量随之删除）。

// pickCourseColor / isPaletteDark / hslToColor 三函数已收敛至 util/CourseColorUtil.kt（决策 D3 单一事实来源）。
// 原注释 S/L 值写错（0.48/0.88、0.35/0.26），实际为亮色 S=0.55 L=0.82 / 暗色 S=0.40 L=0.28，正确值见 CourseColorUtil 常量。

// =====================================================================================
// v7.10.4 冲突栏字号/间距压缩 — 用户 2026-09-02: 两栏模式下分栏字全挤到一起,
// 需按 lane 实宽调画面比例。
// =====================================================================================

/** lane 宽压缩基准: 单栏半宽量级(用户实测"比较好"的现状),低于它开始线性缩。 */
internal val WEEK_LANE_SCALE_BASE = 150f

/** 压缩下限 — 再窄也不无限缩,可读性由隐藏侧栏标签/副信息兜底。 */
internal const val WEEK_LANE_SCALE_FLOOR = 0.6f

/**
 * 冲突栏内 LessonRow 的缩放比(纯函数可测):
 *   laneW ≥ 150dp → 1.0(保现状);以下线性压缩;0.6 封底。
 */
internal fun weekLaneFontScale(laneW: Dp): Float =
    if (laneW >= WEEK_LANE_SCALE_BASE.dp) 1f
    else (laneW.value / WEEK_LANE_SCALE_BASE).coerceAtLeast(WEEK_LANE_SCALE_FLOOR)

/** 极窄 lane 判定: 侧栏节次/时间标签(42dp+8dp 间距)挤占正文 → 隐藏它。 */
internal fun weekLaneHideSideLabel(laneW: Dp): Boolean = laneW < 110.dp

// sp 已被上面 textStyle 直接用 inline
