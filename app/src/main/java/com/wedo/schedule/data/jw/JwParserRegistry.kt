package com.wedo.schedule.data.jw

/**
 * 解析分发入口。
 *
 * wedo 只服务吉林建筑大学（新版正方 `jwglxt`），因此这里不做协议族遍历，
 * 也不做置信度裁决 —— 那套多校竞争逻辑已随上游解析器一并移除。
 * 保留 Registry 形态是为了让 [JwImportViewModel] 与诊断层（[JwParseDiagnostics]）
 * 的调用契约不变，将来若要接入第二所学校，在此处扩展即可。
 */
object JwParserRegistry {

    /**
     * 单个 parser 尝试的快照。
     * 诊断层直接复用此 data class。
     */
    data class ParserAttempt(
        val parserName: String,
        val type: String?,
        val courseCount: Int,
        val confidence: Int,
        val matchedFeatures: List<String>,
        val exception: String?,
    )

    /**
     * 单一来源：协议 type → parser 工厂。
     * 目前只有新版正方 `zf_new` 一条路径。
     */
    private val FACTORIES: Map<String, (String) -> JwParser> = linkedMapOf(
        JwProtocol.TYPE_ZF_NEW to ::JwNewZfParser,
    )

    /** 全部候选：目前恒为一条。 */
    fun allCandidates(html: String): List<Pair<String?, JwParser>> =
        FACTORIES.entries.map { (t, factory) -> t to factory(html) }

    /**
     * 显式分发：type 已知时按表派发。
     * 未在表内的 type → 抛 [IllegalArgumentException]，由 [JwImportViewModel] 包装成
     * [JwParseException] 交给诊断层分类。
     */
    fun parserFor(type: String, html: String): JwParser =
        FACTORIES[type]?.invoke(html)
            ?: throw IllegalArgumentException("协议 $type 暂不支持")

    private data class Row(
        val type: String?,
        val attempt: ParserAttempt,
        val result: List<JwCourse>,
    )

    /**
     * 解析并返回 (courses, attempts)。
     * attempts 供诊断层消费，即便只有一条路径也保留结构，避免调用方分叉。
     */
    fun selectBest(
        html: String,
        declaredType: String? = null,
    ): Pair<List<JwCourse>, List<ParserAttempt>> {
        val attempts = mutableListOf<ParserAttempt>()
        val candidates = allCandidates(html)

        val results: List<Row> = candidates.map { (type, parser) ->
            val conf = try { parser.confidence() } catch (e: Exception) { 0 }
            val matched = try { parser.matchedFeatures() } catch (e: Exception) { emptyList() }
            val (count, result, exMsg) = try {
                val r = parser.generateCourseList()
                Triple(r.size, r, null)
            } catch (e: JwParseException) {
                val marker = e.attempts.firstOrNull()?.exception
                Triple(0, emptyList<JwCourse>(), marker ?: "${e::class.simpleName}: ${e.message?.take(60)}")
            } catch (e: Exception) {
                Triple(0, emptyList<JwCourse>(), "${e::class.simpleName}: ${e.message?.take(60)}")
            }
            val attempt = ParserAttempt(
                parserName = try { parser.nameForDiag() } catch (e: Exception) { "JwParser" },
                type = type,
                courseCount = count,
                confidence = conf,
                matchedFeatures = matched,
                exception = exMsg,
            )
            attempts += attempt
            Row(type, attempt, result)
        }

        // 单路径，无竞争可裁决：取第一个非空结果即可。
        val bestResult: List<JwCourse> = results.firstOrNull { it.result.isNotEmpty() }?.result.orEmpty()

        return bestResult to attempts
    }
}

/**
 * 诊断用异常，带每个 parser 的尝试快照。
 * 调用方的 `catch (e: JwParseException)` 依赖此类型，签名保持不变。
 */
class JwParseException(
    message: String,
    val attempts: List<JwParserRegistry.ParserAttempt> = emptyList(),
) : RuntimeException(message)

/** 诊断展示名。目前只有一条路径，恒等于类名。 */
internal fun JwParser.nameForDiag(): String = this::class.simpleName ?: "JwParser"
