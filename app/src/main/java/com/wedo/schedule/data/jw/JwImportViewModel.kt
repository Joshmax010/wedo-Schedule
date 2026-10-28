package com.wedo.schedule.data.jw

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.data.entity.TimeTableEntity
import com.wedo.schedule.data.AppDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wedo.schedule.util.DateUtils
import com.wedo.schedule.util.TimeTableUtils
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/**
 * 教务直连导入 ViewModel。
 *
 * 职责：
 *  1. 提供学校入口（当前只有吉林建筑大学）
 *  2. 用户在 WebView 内登录后，由注入的 fetch 脚本抓回源码
 *  3. 交给 [JwParserRegistry] 解析成 [JwCourse]
 *  4. 转 [CourseEntity] 落库
 *
 * 不做的事：网络请求（在 WebView 内完成）、登录流程（用户手动操作）。
 */
class JwImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _schools = MutableStateFlow<List<JwSchoolInfo>>(emptyList())
    val schools: StateFlow<List<JwSchoolInfo>> = _schools.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    init {
        loadSchools()
    }

    private fun loadSchools() {
        _schools.value = defaultWedoSchools()
    }

    /**
     * 最近一次解析的尝试快照，供 [JwParseDiagnostics.classify] 消费。
     */
    var lastAttempts: List<JwParserRegistry.ParserAttempt> = emptyList()
        private set

    var lastDiagAttempts: List<JwParseDiagnostics.ParserAttempt> = emptyList()
        private set

    /**
     * 解析源码，返回课程列表（不入库）。
     *
     * @param html 登录态下抓到的源码
     * @param protocolType 协议类型；空串表示未识别，此时走 [JwParserRegistry.selectBest]
     */
    suspend fun parseHtml(html: String, protocolType: String): List<JwCourse> = withContext(Dispatchers.IO) {
        val (result, attempts) = if (protocolType.isBlank()) {
            JwParserRegistry.selectBest(html, declaredType = null)
        } else {
            parseHtmlStatic(html, protocolType)
        }
        lastAttempts = attempts
        lastDiagAttempts = attempts.map {
            JwParseDiagnostics.ParserAttempt(it.parserName, it.courseCount, it.exception)
        }
        result
    }

    // ─── 协议判型（URL 优先，页面兜底） ───────────────────────────

    /** URL 判型入口。 */
    fun detectProtocolFromUrl(url: String): String? =
        if (url.isBlank()) null else JwProtocol.detect(url.lowercase())

    /** 页面判型入口。 */
    fun detectProtocolFromHtml(html: String): String? = JwProtocol.detectFromHtml(html)

    /** URL + 页面组合判型：URL 命中即返回，否则看页面。 */
    fun detectProtocol(html: String, url: String? = null): String? {
        url?.takeIf { it.isNotBlank() }?.let { u ->
            JwProtocol.detect(u.lowercase())?.let { return it }
        }
        return JwProtocol.detectFromHtml(html)
    }

    companion object {
        /**
         * 产品内暴露的学校清单。
         *
         * wedo v1 是单校产品：吉林建筑大学在用的新版正方 `jwglxt`。
         * 入口 URL 与协议类型来自实测取证。
         */
        @JvmStatic
        internal fun defaultWedoSchools(): List<JwSchoolInfo> = listOf(
            JwSchoolInfo(
                sortKey = "J",
                sortKeyFull = "jilinjianzhudaxue",
                name = "吉林建筑大学",
                url = "https://jwxt.jlju.edu.cn/sso/hnyyxyiotlogin",
                type = JwProtocol.TYPE_ZF_NEW,
                status = JwSchoolInfo.STATUS_SUPPORTED,
                aliases = listOf("吉建大", "JLJU"),
                enableFetch = true,
                // 白名单下沉数据层（ADR-4 / §4.3）：三域名逐字不变，取自原 UI 层取证常量。
                // 严禁通配 —— 只允许这三个精确主机名。
                authHosts = setOf(
                    "lxr.jlju.edu.cn",
                    "cas.jlju.edu.cn",
                ),
                jsBridgeHosts = setOf(
                    "jwxt.jlju.edu.cn",
                ),
            )
        )

        /**
         * 显式分发的纯函数形式（不依赖 Android Context / Room）。
         */
        internal fun parseHtmlStatic(
            html: String,
            protocolType: String,
        ): Pair<List<JwCourse>, List<JwParserRegistry.ParserAttempt>> {
            val parser = try {
                JwParserRegistry.parserFor(protocolType, html)
            } catch (e: IllegalArgumentException) {
                throw JwParseException(
                    "协议 $protocolType 暂不支持",
                    attempts = listOf(
                        JwParserRegistry.ParserAttempt(
                            parserName = "<none>",
                            type = protocolType,
                            courseCount = 0,
                            confidence = 0,
                            matchedFeatures = emptyList(),
                            exception = e::class.simpleName,
                        )
                    ),
                )
            }
            return try {
                val r = parser.generateCourseList()
                r to listOf(
                    JwParserRegistry.ParserAttempt(
                        parserName = parser.nameForDiag(),
                        type = protocolType,
                        courseCount = r.size,
                        confidence = parser.confidence(),
                        matchedFeatures = parser.matchedFeatures(),
                        exception = null,
                    )
                )
            } catch (e: JwParseException) {
                throw e
            } catch (e: Exception) {
                throw JwParseException(
                    "parser 解析异常: ${e::class.simpleName}: ${e.message?.take(60)}",
                    attempts = listOf(
                        JwParserRegistry.ParserAttempt(
                            parserName = parser.nameForDiag(),
                            type = protocolType,
                            courseCount = 0,
                            confidence = parser.confidence(),
                            matchedFeatures = parser.matchedFeatures(),
                            exception = "${e::class.simpleName}: ${e.message?.take(60)}",
                        )
                    ),
                )
            }
        }

        /** JVM 单测入口：URL 判型。 */
        @JvmStatic
        fun detectProtocolFromUrlForTest(url: String): String? =
            if (url.isBlank()) null else JwProtocol.detect(url.lowercase())

        /** JVM 单测入口：页面判型。 */
        @JvmStatic
        fun detectProtocolFromHtmlForTest(html: String): String? = JwProtocol.detectFromHtml(html)

        /** JVM 单测入口：组合判型。 */
        @JvmStatic
        fun detectProtocolForTest(html: String, url: String?): String? =
            url?.takeIf { it.isNotBlank() }?.let { u -> JwProtocol.detect(u.lowercase())?.let { return it } }
                ?: JwProtocol.detectFromHtml(html)

        /** JVM 单测入口：命中的指纹特征。 */
        @JvmStatic
        fun detectProtocolHitFeaturesForTest(html: String): List<String> = JwProtocol.hitFeatures(html)
    }

    /**
     * 把 [JwCourse] 列表转成落库用的 [CourseEntity] 列表。
     */
    fun toCourseEntities(courses: List<JwCourse>, tableId: Long, defaultColor: String): List<CourseEntity> {
        return courses.map { jw ->
            val step = (jw.endNode - jw.startNode + 1).coerceAtLeast(1)
            CourseEntity(
                id = 0,
                groupId = "",
                tableId = tableId,
                courseName = jw.name.ifBlank { "未命名" },
                teacher = jw.teacher,
                room = jw.room,
                day = jw.day.coerceIn(1, 7),
                startNode = jw.startNode.coerceAtLeast(1),
                step = step,
                startWeek = jw.startWeek.coerceAtLeast(1),
                endWeek = jw.endWeek.coerceAtLeast(jw.startWeek),
                type = jw.type,
                color = defaultColor
            )
        }
    }

    /**
     * 创建新课表并落库，返回新 tableId。
     */
    suspend fun importAsNewTable(
        courses: List<JwCourse>,
        tableName: String,
        startDate: String? = null,
        timeJson: String = "",
        nodesPerDay: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        if (courses.isEmpty()) throw IllegalArgumentException("课程列表为空，请确认已到达课表页面")

        val db = AppDatabase.get(getApplication())
        // 建表 + 落库包在单一事务里：中途失败回滚，避免留下空课表。
        val newId = db.withTransaction {
            val tableDao = db.timeTableDao()
            val courseDao = db.courseDao()

            // autoGenerate (id=0) 交给 Room 分配主键，避免手动 max(id)+1 撞旧 ID。
            val resolvedStartDate = startDate?.takeIf { it.isNotBlank() }
                ?.let { DateUtils.normalizeStartDate(it) }
                ?: computeCurrentSemesterStart()
            val maxNode = if (nodesPerDay > 0) nodesPerDay else courses.maxOf { maxOf(it.startNode, it.endNode) }
            val newTable = TimeTableEntity(
                id = 0,
                name = tableName.ifBlank { "导入的课表" },
                startDate = resolvedStartDate,
                timeJson = timeJson.ifBlank { TimeTableUtils.DEFAULT_TIME_JSON },
                nodesPerDay = maxNode,
                isDefault = true  // 导入的课表设为默认，widget 直接展示
            )
            val generatedId = tableDao.insert(newTable)
            tableDao.setDefault(generatedId)

            val defaultColor = "#FF6750A4"
            // 同名课程归为一组，便于后续批量编辑
            val nameToGroup = mutableMapOf<String, String>()
            val entities = toCourseEntities(courses, generatedId, defaultColor).map { c ->
                val gid = nameToGroup.getOrPut(c.courseName) { java.util.UUID.randomUUID().toString() }
                c.copy(groupId = gid)
            }
            courseDao.insertAll(entities)
            generatedId
        }
        newId
    }

    /**
     * 默认学期开始日期：本学期第一周周一。
     * 寒暑假（2 月 / 8 月）回退到上一学期。
     */
    private fun computeCurrentSemesterStart(): String {
        val today = LocalDate.now()
        val month = today.monthValue
        val semesterStartYear = if (month in 8..12) today.year else today.year - 1
        val semesterStartMonth = if (month in 8..12) 9 else 2
        val firstDay = LocalDate.of(semesterStartYear, semesterStartMonth, 1)
        return firstDay.with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
            .toString()
    }

    sealed class ImportState {
        object Idle : ImportState()
        data class Parsed(val courses: List<JwCourse>) : ImportState()
        data class Imported(val tableId: Long) : ImportState()
        data class Error(val message: String) : ImportState()
    }
}
