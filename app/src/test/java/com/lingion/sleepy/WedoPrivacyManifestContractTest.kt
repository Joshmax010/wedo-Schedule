package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WedoPrivacyManifestContractTest {

    private val manifest = TestProjectFiles.read("app/src/main/AndroidManifest.xml")
    private val debugManifest = TestProjectFiles.read("app/src/debug/AndroidManifest.xml")
    private val networkConfig = TestProjectFiles.read("app/src/main/res/xml/network_security_config.xml")
    private val extractionRules = TestProjectFiles.read("app/src/main/res/xml/data_extraction_rules.xml")
    private val appSource = TestProjectFiles.read("app/src/main/java/com/lingion/sleepy/SleepyApp.kt")
    private val holidaySource = TestProjectFiles.read("app/src/main/java/com/lingion/sleepy/util/HolidayManager.kt")

    @Test
    fun `backup and cleartext are disabled`() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(networkConfig.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(networkConfig.contains("certificates src=\"user\""))
    }

    @Test
    fun `cloud backup excludes course and session storage`() {
        assertTrue(extractionRules.contains("<exclude domain=\"database\" path=\".\""))
        assertTrue(extractionRules.contains("<exclude domain=\"sharedpref\" path=\".\""))
    }

    @Test
    fun `v1 only requests internet permission`() {
        val permissions = Regex("<uses-permission[^>]+>").findAll(manifest).map { it.value }.toList()
        assertTrue(permissions.any { it.contains("android.permission.INTERNET") })
        assertFalse(permissions.any { it.contains("POST_NOTIFICATIONS") })
        assertFalse(permissions.any { it.contains("REQUEST_INSTALL_PACKAGES") })
        assertFalse(permissions.any { it.contains("SCHEDULE_EXACT_ALARM") })
    }

    @Test
    fun `debug build exposes no injection components`() {
        assertFalse(debugManifest.contains("android:exported=\"true\""))
        assertFalse(debugManifest.contains("DEBUG_TOOL"))
    }

    @Test
    fun `schedule rendering does not trigger third party holiday requests`() {
        assertFalse(appSource.contains("HolidayManager.preload"))
        val shouldGrey = holidaySource.substringAfter("suspend fun shouldGrey")
            .substringBefore("suspend fun getYearEntries")
        assertFalse(shouldGrey.contains("getYearEntries"))
    }
}
