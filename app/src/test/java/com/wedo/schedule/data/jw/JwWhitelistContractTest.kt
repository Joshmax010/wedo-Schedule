package com.wedo.schedule.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务白名单契约测试（REQ-P2-04 / REQ-P2-05）。
 *
 * 白名单已从 UI 层**下沉数据层**（ADR-4 / §4.3）：真值源 = [JwImportViewModel.defaultWedoSchools]。
 * 本测试是该下沉后的**唯一**域名真值守卫 —— 原 UI 侧的三条域名字面量断言已迁移至此。
 *
 * 核心红线：
 *   - 每所学校**必须**声明非空的 `authHosts` 与 `jsBridgeHosts`（新增学校漏声明 → 测试失败）；
 *   - 主机名一律**精确**，**严禁通配**（`*` / `*.`）；
 *   - 吉林建筑大学的三域名**逐字**不变。
 */
class JwWhitelistContractTest {

    private val schools = JwImportViewModel.defaultWedoSchools()

    /**
     * 白名单合法性规则 —— 与下方遍历断言**同源**。抽成纯函数是为了能对
     * 「未声明 host 的学校」做规则自测（证明规则不是恒 true/false）。
     */
    private fun hostsAreValid(school: JwSchoolInfo): Boolean {
        if (school.authHosts.isEmpty() || school.jsBridgeHosts.isEmpty()) return false
        return (school.authHosts + school.jsBridgeHosts).all { h ->
            h.isNotBlank() &&
                h == h.trim() &&
                h == h.lowercase() &&
                !h.contains("*") &&
                !h.startsWith("*.") &&
                !h.contains("/") &&
                !h.contains("://")
        }
    }

    @Test
    fun everySchool_declaresNonEmptyHosts() {
        assertTrue("学校清单不得为空", schools.isNotEmpty())
        schools.forEach { s ->
            assertTrue("${s.name} 缺少 authHosts（新增学校必须声明）", s.authHosts.isNotEmpty())
            assertTrue("${s.name} 缺少 jsBridgeHosts（新增学校必须声明）", s.jsBridgeHosts.isNotEmpty())
        }
    }

    @Test
    fun noHost_isWildcard_orBlank_orHostless() {
        schools.forEach { s ->
            (s.authHosts + s.jsBridgeHosts).forEach { h ->
                assertFalse("[$h] 不得含通配符 *", h.contains("*"))
                assertFalse("[$h] 不得以 *. 开头", h.startsWith("*."))
                assertTrue("[$h] 不得为空白", h.isNotBlank())
                assertEquals("[$h] 不得含首尾空白", h.trim(), h)
                assertFalse("[$h] 不得含 scheme 或路径", h.contains("/") || h.contains("://"))
            }
        }
    }

    @Test
    fun authHosts_and_jsBridgeHosts_areDisjoint() {
        schools.forEach { s ->
            assertTrue(
                "${s.name} 的 authHosts 与 jsBridgeHosts 不得重叠（登录页不得装桥）",
                s.authHosts.intersect(s.jsBridgeHosts).isEmpty()
            )
        }
    }

    @Test
    fun jlju_declaresExactlyThreeVerifiedHosts() {
        val jlju = schools.single { it.name == "吉林建筑大学" }
        assertEquals(
            "认证主机必须逐字为 lxr / cas",
            setOf("lxr.jlju.edu.cn", "cas.jlju.edu.cn"),
            jlju.authHosts
        )
        assertEquals(
            "桥主机必须逐字为 jwxt",
            setOf("jwxt.jlju.edu.cn"),
            jlju.jsBridgeHosts
        )
    }

    @Test
    fun rule_newSchoolWithoutHosts_isRejected() {
        // 规则自测：新增一所未声明 host 的学校 → 同一校验判为不合法（→ 遍历断言会失败）。
        val rogue = JwSchoolInfo(sortKey = "X", name = "未声明学校", url = "https://example.edu.cn")
        assertFalse("未声明 host 的学校必须判为不合法", hostsAreValid(rogue))

        // 通配学校同样必须被拒（严禁为"将来多校"提前放宽）。
        val wildcard = JwSchoolInfo(
            sortKey = "Y",
            name = "通配学校",
            url = "https://y.edu.cn",
            authHosts = setOf("*.y.edu.cn"),
            jsBridgeHosts = setOf("jw.y.edu.cn")
        )
        assertFalse("含通配符的学校必须判为不合法", hostsAreValid(wildcard))

        // 对照：真实学校必须通过 —— 证明校验逻辑不是恒 false。
        assertTrue(
            "吉林建筑大学必须通过校验",
            hostsAreValid(schools.single { it.name == "吉林建筑大学" })
        )
    }
}
