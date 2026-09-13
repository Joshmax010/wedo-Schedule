package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WedoSchoolCatalogueTest {

    @Test
    fun `v1 exposes only JLJU with verified endpoint`() {
        val schools = JwImportViewModel.defaultWedoSchools()

        assertEquals(1, schools.size)
        with(schools.single()) {
            assertEquals("吉林建筑大学", name)
            assertEquals(JwSchoolInfo.STATUS_SUPPORTED, status)
            assertEquals("https://jwxt.jlju.edu.cn/sso/hnyyxyiotlogin", url)
            assertEquals(JwProtocol.TYPE_ZF_NEW, type)
            assertTrue(hasUrl)
            assertTrue(enableFetch)
        }
    }
}
