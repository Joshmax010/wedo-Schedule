// SPDX-License-Identifier: GPL-3.0-only
package com.wedo.jwimport

import java.net.URI

/** Evidence-reviewed configuration, not a claim that any configurable school is supported. */
data class SchoolDefinition(
    val id: String,
    val name: String,
    val loginUrl: String,
    val educationHost: String,
    val authenticationHosts: Set<String>,
    val basePath: String = "/jwglxt",
    val functionCode: String = "N2151",
) {
    init {
        require(id.matches(Regex("[a-z0-9_-]+")))
        require(name.isNotBlank())
        val hosts = authenticationHosts + educationHost
        require(hosts.all { it.matches(Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")) && '.' in it })
        require(basePath.isEmpty() || basePath.matches(Regex("/[a-zA-Z0-9/_-]+")))
        require(functionCode.matches(Regex("[A-Za-z0-9_-]{1,32}")))
        val login = URI(loginUrl)
        require(isAllowedNavigation(loginUrl) && login.userInfo == null)
    }

    fun isAllowedNavigation(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", true) && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host?.lowercase() in (authenticationHosts + educationHost)
    }.getOrDefault(false)

    fun isEducationPage(url: String?): Boolean = url != null &&
        isAllowedNavigation(url) && runCatching { URI(url).host.lowercase() == educationHost }.getOrDefault(false)

    companion object {
        val JLJU = SchoolDefinition(
            "jlju", "吉林建筑大学", "https://jwxt.jlju.edu.cn/sso/hnyyxyiotlogin",
            "jwxt.jlju.edu.cn", setOf("lxr.jlju.edu.cn", "cas.jlju.edu.cn"),
        )
    }
}

data class TermChoice(val value: String, val label: String)
data class TermOptions(
    val years: List<TermChoice>, val semesters: List<TermChoice>,
    val defaultYear: String, val defaultSemester: String,
)
data class TermSelection(val year: String, val semester: String) {
    init { require(listOf(year, semester).all { it.matches(Regex("[A-Za-z0-9_-]{1,32}")) }) }
}

/** Transfer object; map to the host's existing Course entity instead of a second permanent model. */
data class ImportedCourse(
    val name: String, val teacher: String, val room: String,
    val day: Int, val startNode: Int, val endNode: Int,
    val weeks: Set<Int>, val note: String,
)
enum class RecordError { MISSING_NAME, INVALID_DAY, INVALID_SECTIONS, INVALID_WEEKS, INVALID_RECORD }
data class SkippedRecord(val index: Int, val reason: RecordError)
data class ScheduleResult(
    val term: TermSelection, val courses: List<ImportedCourse>,
    val sourceCount: Int, val skipped: List<SkippedRecord>, val fetchedAt: Long,
) {
    /** Only an explicit, structurally valid kbList:[] is an empty semester. */
    val emptySemester: Boolean get() = sourceCount == 0
}
enum class ImportError {
    NOT_ON_EDUCATION_PAGE, NETWORK, SERVER, SESSION_EXPIRED,
    INVALID_RESPONSE, TIMEOUT, TLS, BLOCKED_NAVIGATION, CANCELLED,
}
class ImportException(val code: ImportError) : IllegalArgumentException(code.name)

/** Callbacks execute on the WebView/main thread. Never expose cookies, tokens or raw responses. */
interface ImportListener {
    fun onBusyChanged(busy: Boolean) {}
    /** A trusted page is loaded, NOT proof of successful authentication. */
    fun onPageChanged(canRead: Boolean) {}
    fun onTerms(options: TermOptions) {}
    fun onSchedule(result: ScheduleResult) {}
    fun onError(error: ImportError) {}
}
