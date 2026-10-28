package com.wedo.schedule.widget

import android.appwidget.AppWidgetProvider
import com.wedo.schedule.R

/**
 * Metadata describing one widget variant (base or small).
 *
 * The list [ALL_WIDGET_VARIANTS] is the single source of truth for both the
 * refresh broadcast dispatched by [WidgetUpdater] and the widget config UI.
 * Adding a new widget must add exactly one entry here; the refresh broadcast
 * is derived automatically.
 *
 * REQ-P5-01：V3 重设计把小组件从 **5 种（10 变体）收敛为 2 种（4 变体）**：
 *  - 今日课程（[TodayWidgetReceiver]）+ 小（[TodaySmallWidgetReceiver]）
 *  - 本周课表·网格（[WeekGridWidgetProvider]）+ 小（[WeekGridSmallWidgetProvider]）
 * 已删除的 3 种（本周课表·列表 / 周视图 / 最近两天）连同其 6 个 receiver 实现、
 * 渲染函数与资源一并移除。
 */
data class WidgetVariantInfo(
    val receiverClass: Class<out AppWidgetProvider>,
    val displayNameRes: Int
)

val ALL_WIDGET_VARIANTS: List<WidgetVariantInfo> = listOf(
    WidgetVariantInfo(TodayWidgetReceiver::class.java,        R.string.widget_today_label),
    WidgetVariantInfo(TodaySmallWidgetReceiver::class.java,   R.string.widget_today_small_label),
    WidgetVariantInfo(WeekGridWidgetProvider::class.java,      R.string.widget_week_grid_label),
    WidgetVariantInfo(WeekGridSmallWidgetProvider::class.java, R.string.widget_week_grid_small_label)
)
