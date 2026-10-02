package com.wedo.schedule.ui.screen.imports

import android.content.Context
import com.wedo.schedule.R
import com.wedo.schedule.WedoApp
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.data.entity.TimeTableEntity
import com.wedo.schedule.data.parser.ScheduleParser
import com.wedo.schedule.ui.screen.schedule.ScheduleState
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils

/**
 * 导入流程的**非 UI 逻辑层**（从 `ImportSheet` 抽出，供 `ImportWizard` 复用）。
 *
 * 这里只放「格式嗅探 → 解析 → 冲突计算 → 落库」的可复用逻辑，不含任何 Composable：
 *  - [buildImportPreview]：把一段文本解析成 [ImportPreview]（含冲突与告警）
 *  - [applyImportPreview]：把预览按 [ImportApplyMode] 落库（**唯一写库点**）
 *
 * 这样 `ImportSheet`（旧底部面板）与 `ImportWizard`（新全屏向导）共享**同一套**导入语义，
 * 不会出现「两个入口行为不一致」。**本层不改变任何解析/安全内核**（那是教务链路的事）。
 */

/** 导入的落库方式。语义见各分支。 */
internal enum class ImportApplyMode {
    ReplaceCurrent,
    ImportAsNew,
    AppendNonConflict,
    /** 当前课表 + 导入数据合并, 创建新课表保存, 用户命名 */
    AppendAsNew,
    /** 连冲突课一起追加进当前课表(红标: 会形成同格多层) */
    AppendAll
}

/** 一条导入冲突：导入课程 ↔ 目标课表中与其冲突的既有课程。 */
internal data class CourseConflict(
    val incoming: CourseEntity,
    val existing: CourseEntity
)

/**
 * 一次导入预览的结果。
 *
 * @property targetTableId 目标课表 ID；0L 表示「当前没有课表，导入即新建」
 * @property targetTableName 目标课表名（供文案展示）
 * @property parseResult 解析结果（课程/作息/告警）
 * @property existingCourses 目标课表已有课程（0L 时为空）
 * @property conflicts 与既有课程的冲突列表
 * @property multiLocationWarnings 同 groupId 多地点提示（不阻塞导入）
 */
internal data class ImportPreview(
    val targetTableId: Long,
    val targetTableName: String,
    val parseResult: ScheduleParser.ParseResult,
    val existingCourses: List<CourseEntity>,
    val conflicts: List<CourseConflict>,
    val multiLocationWarnings: List<String> = emptyList()
) {
    val incomingCount: Int get() = parseResult.courses.size
    val conflictCount: Int get() = conflicts.size
    val cleanCount: Int get() = incomingCount - conflictCount
}

/**
 * 把一段课表文本解析为 [ImportPreview]；文本为空或解析失败时通过 [onError] 回报并返回 null。
 *
 * `selectedTableId` 缺失时按 `0L` 处理（无课表 → apply 阶段按 [ImportApplyMode.ImportAsNew] 自动建表）。
 */
internal suspend fun buildImportPreview(
    text: String,
    state: ScheduleState,
    context: Context,
    onError: (String) -> Unit
): ImportPreview? {
    if (text.isBlank()) {
        onError(context.getString(R.string.import_content_empty))
        return null
    }
    // selectedTableId 缺失时也能导入 — 没有 tableId 就用 0L，apply 时按 ImportAsNew 自动建表。
    val tableId = state.selectedTableId ?: 0L
    val result = ScheduleParser.parse(text, tableId)
    return result.fold(
        onSuccess = { parseResult ->
            val repo = WedoApp.get().repository
            val existingTable = if (tableId == 0L) null else repo.getTable(tableId)
            val existingCourses = if (tableId == 0L) emptyList() else repo.getCourses(tableId)
            val conflicts = if (tableId == 0L) emptyList() else parseResult.courses.mapNotNull { incoming ->
                existingCourses.firstOrNull { existing -> coursesConflict(incoming, existing) }
                    ?.let { CourseConflict(incoming = incoming, existing = it) }
            }
            // issue#22: 同 groupId 多地点提示文案 — 按 (groupId) 聚合,
            // 有 ≥2 个非空不同地点时给一句提示,导入后会作为独立节次展示
            val multiLocWarnings = mutableListOf<String>()
            parseResult.courses.groupBy { it.groupId }.forEach { (gid, cs) ->
                if (gid.isBlank()) return@forEach
                val distinctRooms = cs.map { it.room.trim() }.distinct().filter { it.isNotEmpty() }
                if (distinctRooms.size >= 2) {
                    multiLocWarnings += context.getString(
                        R.string.import_multi_location_warning_detail,
                        cs.first().courseName,
                        distinctRooms.size
                    )
                }
            }
            ImportPreview(
                targetTableId = tableId,
                targetTableName = existingTable?.name ?: context.getString(R.string.manage_current_table),
                parseResult = parseResult,
                existingCourses = existingCourses,
                conflicts = conflicts,
                multiLocationWarnings = multiLocWarnings
            )
        },
        onFailure = { e ->
            onError(context.getString(R.string.import_failed, e.message))
            null
        }
    )
}

/**
 * 按 [mode] 把 [preview] 落库 —— **导入路径唯一写库点**。
 *
 * 整个导入是一个动作：批内只保首快照, 撤回一次回退到导入前（`UndoManager` 批边界）。
 */
internal suspend fun applyImportPreview(
    preview: ImportPreview,
    mode: ImportApplyMode,
    confirmedStartDateRaw: String,
    confirmedTableName: String,
    confirmedTimeJson: String,
    context: Context,
    onImported: () -> Unit,
    onError: (String) -> Unit
) {
    val repo = WedoApp.get().repository
    // v7.10.16 撤回: 整个导入是一个动作 — 批内只保首快照, 撤回一次回退到导入前。
    // try/finally 收口: 分支里的 early return 也要退出批边界。
    com.wedo.schedule.data.undo.UndoManager.beginBatch()
    try {
        // 应用约定 startDate=周一；用户在确认框可能手填非周一日期，落库前归一（issue #5）
        val confirmedStartDate = DateUtils.normalizeStartDate(confirmedStartDateRaw)
        when (mode) {
            ImportApplyMode.ReplaceCurrent -> {
                val existing = repo.getTable(preview.targetTableId)
                if (existing != null) {
                    repo.updateTable(
                        existing.copy(
                            name = confirmedTableName.trim().ifBlank { preview.parseResult.tableName },
                            startDate = confirmedStartDate,
                            timeJson = confirmedTimeJson,
                            nodesPerDay = if (preview.parseResult.nodesPerDay > 0) preview.parseResult.nodesPerDay else existing.nodesPerDay
                        )
                    )
                }
                // wedo-v1 (§3.4 契约一): 解析端权威 groupId → 绕过 assignGroupIds 再分配
                if (preview.parseResult.groupIdsAuthoritative) {
                    repo.replaceCoursesKeepingGroups(preview.targetTableId, preview.parseResult.courses)
                } else {
                    repo.replaceCourses(preview.targetTableId, preview.parseResult.courses)
                }
                val badDays = com.wedo.schedule.util.ConflictLayoutEngine
                    .daysExceedingTwoLanes(preview.parseResult.courses)
                if (badDays.isNotEmpty()) {
                    onError(context.getString(R.string.import_three_layers_kept, dayNames(badDays, context)))
                }
                onImported()
            }
            ImportApplyMode.ImportAsNew -> {
                val base = repo.getTable(preview.targetTableId)
                val newTableId = repo.insertTable(
                    TimeTableEntity(
                        name = uniqueImportedTableName(confirmedTableName, repo.getAllTables().map { it.name }, context),
                        startDate = confirmedStartDate,
                        maxWeek = if (preview.parseResult.maxWeek > 0) preview.parseResult.maxWeek else base?.maxWeek ?: 20,
                        timeJson = confirmedTimeJson,
                        color = base?.color ?: "#FF6750A4",
                        isDefault = false
                    )
                )
                if (preview.parseResult.groupIdsAuthoritative) {
                    repo.insertCoursesKeepingGroups(preview.parseResult.courses.map { it.copy(id = 0, tableId = newTableId) })
                } else {
                    repo.insertCourses(preview.parseResult.courses.map { it.copy(id = 0, tableId = newTableId) })
                }
                repo.setDefault(newTableId)
                val badDaysNew = com.wedo.schedule.util.ConflictLayoutEngine
                    .daysExceedingTwoLanes(preview.parseResult.courses)
                if (badDaysNew.isNotEmpty()) {
                    onError(context.getString(R.string.import_three_layers_kept, dayNames(badDaysNew, context)))
                }
                onImported()
            }
            ImportApplyMode.AppendNonConflict -> {
                val cleanCourses = preview.parseResult.courses.filterNot { incoming ->
                    preview.existingCourses.any { existing -> coursesConflict(incoming, existing) }
                }
                if (cleanCourses.isEmpty()) {
                    onError(context.getString(R.string.import_all_conflict))
                    return
                }
                val survivors = dropThreeLayerCourses(preview.existingCourses, cleanCourses)
                if (survivors.isEmpty()) {
                    onError(context.getString(R.string.import_all_conflict))
                    return
                }
                if (survivors.size < cleanCourses.size) {
                    val droppedDays = conflictDaysBetween(cleanCourses, survivors)
                    onError(context.getString(R.string.import_three_layers_dropped, dayNames(droppedDays, context)))
                }
                if (preview.parseResult.groupIdsAuthoritative) {
                    repo.insertCoursesKeepingGroups(survivors.map { it.copy(id = 0, tableId = preview.targetTableId) })
                } else {
                    repo.insertCourses(survivors.map { it.copy(id = 0, tableId = preview.targetTableId) })
                }
                // v7.10.16k 无损延伸: 老表作息∪导入作息, 并拓到导入课程实际到达的最大节。
                val existingTable = repo.getTable(preview.targetTableId)
                if (existingTable != null) {
                    val extended = TimeTableUtils.mergeMostComplete(
                        currentJson = existingTable.timeJson,
                        incomingJson = preview.parseResult.timeJson,
                        requiredNodeCount = preview.parseResult.nodesPerDay
                    )
                    if (extended != existingTable.timeJson) {
                        val newMaxNode = TimeTableUtils.parseTimeSlotRows(extended).maxOfOrNull { it.node } ?: existingTable.nodesPerDay
                        repo.updateTable(existingTable.copy(timeJson = extended, nodesPerDay = newMaxNode))
                    }
                }
                onImported()
            }
            ImportApplyMode.AppendAsNew -> {
                val base = repo.getTable(preview.targetTableId)
                val incoming = preview.parseResult
                val mergedTimeJson = confirmedTimeJson.ifBlank {
                    TimeTableUtils.mergeMostComplete(
                        currentJson = base?.timeJson ?: "",
                        incomingJson = incoming.timeJson,
                        requiredNodeCount = incoming.nodesPerDay
                    )
                }
                val mergedRows = TimeTableUtils.parseTimeSlotRows(mergedTimeJson)
                val newTableId = repo.insertTable(
                    TimeTableEntity(
                        name = uniqueImportedTableName(confirmedTableName, repo.getAllTables().map { it.name }, context),
                        startDate = confirmedStartDate,
                        maxWeek = if (incoming.maxWeek > 0) incoming.maxWeek else base?.maxWeek ?: 20,
                        nodesPerDay = if (mergedRows.isNotEmpty()) mergedRows.size else base?.nodesPerDay ?: 12,
                        timeJson = mergedTimeJson,
                        color = base?.color ?: "#FF6750A4",
                        isDefault = false
                    )
                )
                val cleanIncoming = incoming.courses.filterNot { inc ->
                    preview.existingCourses.any { existing -> coursesConflict(inc, existing) }
                }
                val oldCourses = base?.let { repo.getCourses(it.id) } ?: emptyList()
                val beforeDays = com.wedo.schedule.util.ConflictLayoutEngine.daysExceedingTwoLanes(oldCourses)
                val afterDays = com.wedo.schedule.util.ConflictLayoutEngine
                    .daysExceedingTwoLanes(oldCourses + cleanIncoming)
                if (afterDays != beforeDays) {
                    val droppedDays = afterDays - beforeDays
                    onError(context.getString(R.string.import_three_layers_kept, dayNames(droppedDays, context)))
                }
                if (preview.parseResult.groupIdsAuthoritative) {
                    val keptOld = oldCourses.map { it.copy(id = 0, tableId = newTableId) }
                    val keptIncoming = cleanIncoming.map { it.copy(id = 0, tableId = newTableId) }
                    repo.insertCoursesKeepingGroups(keptOld + keptIncoming)
                } else {
                    repo.insertCourses((oldCourses + cleanIncoming).map { it.copy(id = 0, tableId = newTableId) })
                }
                repo.setDefault(newTableId)
                onImported()
            }
            ImportApplyMode.AppendAll -> {
                val cleanCourses = preview.parseResult.courses
                if (cleanCourses.isEmpty()) {
                    onError(context.getString(R.string.import_content_empty))
                    return
                }
                val badDays = com.wedo.schedule.util.ConflictLayoutEngine
                    .daysExceedingTwoLanes(preview.existingCourses + cleanCourses)
                if (badDays.isNotEmpty()) {
                    onError(context.getString(R.string.import_three_layers_kept, dayNames(badDays, context)))
                }
                if (preview.parseResult.groupIdsAuthoritative) {
                    repo.insertCoursesKeepingGroups(cleanCourses.map { it.copy(id = 0, tableId = preview.targetTableId) })
                } else {
                    repo.insertCourses(cleanCourses.map { it.copy(id = 0, tableId = preview.targetTableId) })
                }
                val existingTable = repo.getTable(preview.targetTableId)
                if (existingTable != null) {
                    val extended = TimeTableUtils.mergeMostComplete(
                        currentJson = existingTable.timeJson,
                        incomingJson = preview.parseResult.timeJson,
                        requiredNodeCount = preview.parseResult.nodesPerDay
                    )
                    if (extended != existingTable.timeJson) {
                        val newMaxNode = TimeTableUtils.parseTimeSlotRows(extended).maxOfOrNull { it.node } ?: existingTable.nodesPerDay
                        repo.updateTable(existingTable.copy(timeJson = extended, nodesPerDay = newMaxNode))
                    }
                }
                onImported()
            }
        }
    } finally {
        // 批边界收口 — 无论哪个分支 return, 撤回批都到此结束
        com.wedo.schedule.data.undo.UndoManager.endBatch()
    }
}

/**
 * v7.10.12 三层冲突闸门(导入路径) — keepers 保持不变, 候选逐门试探:
 * 加入后若使其所在 day 的 chainGroups 分组数 > 2 则剔除该候选。
 * 策略: 导入数据服从闸门(超层课不入库), 现有课永不动。
 */
internal fun dropThreeLayerCourses(
    keepers: List<CourseEntity>,
    candidates: List<CourseEntity>
): List<CourseEntity> {
    val out = keepers.toMutableList()
    return candidates.filter { cand ->
        val trial = out + cand
        com.wedo.schedule.util.ConflictLayoutEngine.daysExceedingTwoLanes(trial).isEmpty().also { ok ->
            if (ok) out.add(cand)
        }
    }
}

/** 被剔除课所在的 day 集合(用于提示文案)。 */
internal fun conflictDaysBetween(
    before: List<CourseEntity>,
    after: List<CourseEntity>
): Set<Int> = (before.toSet() - after.toSet()).map { it.day }.toSet()

/** day 集合 → 本地化星期文案（"周一 / 周三"）。 */
internal fun dayNames(days: Set<Int>, context: Context): String =
    days.sorted().joinToString(" / ") { DateUtils.localizedDay(it, context) }

/** 两节课是否在**同一周区间 + 同一节次区间**重叠（用于冲突判定）。 */
internal fun coursesConflict(a: CourseEntity, b: CourseEntity): Boolean {
    if (a.day != b.day) return false
    if (a.endWeek < b.startWeek || b.endWeek < a.startWeek) return false
    val aStart = a.startNode
    val aEnd = a.startNode + a.step - 1
    val bStart = b.startNode
    val bEnd = b.startNode + b.step - 1
    return aStart <= bEnd && bStart <= aEnd
}

/** 导入建表时的去重命名（原名 + "2"/"3"…）。 */
internal fun uniqueImportedTableName(base: String, existingNames: List<String>, context: Context): String {
    val default = context.getString(R.string.default_table_name)
    val effective = base.ifBlank { default }
    if (effective !in existingNames) return effective.ifBlank { "${default}1" }
    var index = 2
    while ("${effective}$index" in existingNames || "${effective}($index)" in existingNames) index++
    return "${effective}$index"
}
