package com.wedo.schedule.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [JwSchoolInfo] 的 status 语义与派生属性。
 */
class JwSchoolInfoTest {

    private fun school(status: String, url: String = "https://jw.example.edu.cn") = JwSchoolInfo(
        sortKey = "T", name = "测试大学", url = url, type = JwProtocol.TYPE_ZF_NEW,
        status = status, aliases = emptyList(), sortKeyFull = "ceshidaxue"
    )

    @Test
    fun `supported 状态可点且非待适配`() {
        val s = school(JwSchoolInfo.STATUS_SUPPORTED)
        assertTrue(s.isSupported)
        assertFalse(s.isPending)
        assertTrue(s.hasUrl)
    }

    @Test
    fun `pending 状态不可点且标记待适配`() {
        val s = school(JwSchoolInfo.STATUS_PENDING)
        assertFalse(s.isSupported)
        assertTrue(s.isPending)
    }

    @Test
    fun `URL 为空则不可点但状态仍为 supported`() {
        val s = school(JwSchoolInfo.STATUS_SUPPORTED, url = "")
        assertFalse("URL 为空的学校不可点", s.hasUrl)
        assertTrue(s.isSupported)
    }

    @Test
    fun `status 常量仅两个生命周期取值`() {
        // 单校产品：只有「已支持」和「待适配」两种状态
        assertEquals(
            setOf("supported", "pending"),
            setOf(JwSchoolInfo.STATUS_SUPPORTED, JwSchoolInfo.STATUS_PENDING)
        )
    }

    @Test
    fun `enableFetch 默认 false 且 copy 保留`() {
        val s = JwSchoolInfo("T", "测试", "https://a.edu", JwProtocol.TYPE_ZF_NEW)
        assertFalse("enableFetch 默认 false", s.enableFetch)
        assertTrue(s.copy(enableFetch = true).enableFetch)
    }
}
