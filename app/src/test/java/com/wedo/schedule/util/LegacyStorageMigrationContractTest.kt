package com.wedo.schedule.util

import com.wedo.schedule.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 历史存储键改名（`sleepy.*` → `wedo.*`）的**源码契约测试**。
 *
 * 为什么是契约测试而不是行为测试：搬迁逻辑跑在真机的 `Context` 上
 * （`getSharedPreferences` / `getDatabasePath`），本机无 Robolectric 依赖、
 * 无真机、无模拟器，无法在单测里造出真实的 prefs 文件与数据库目录。
 *
 * 所以这里守住的是"改名后**没有把旧名删干净**、**没有把新名写错**"——
 * 这两个错误一旦发生，真机上的表现是"用户设置全复位 / 课表空了"，
 * 而 JVM 单测完全看不出来。真正的搬迁路径只能靠代码审查 + 真机验收。
 */
class LegacyStorageMigrationContractTest {

    private val appPrefsSrc: String by lazy {
        read("app/src/main/java/com/wedo/schedule/util/AppPrefs.kt")
    }
    private val appDatabaseSrc: String by lazy {
        read("app/src/main/java/com/wedo/schedule/data/AppDatabase.kt")
    }
    private val holidayManagerSrc: String by lazy {
        read("app/src/main/java/com/wedo/schedule/util/HolidayManager.kt")
    }

    private fun read(relative: String): String {
        // 单测工作目录是 app/，模块根在上一级
        val candidates = listOf(File(relative), File("../$relative"))
        val hit = candidates.firstOrNull { it.isFile }
            ?: error("找不到源文件: $relative (cwd=${File(".").absolutePath})")
        return hit.readText()
    }

    // ── AppPrefs ──────────────────────────────────────────────

    @Test
    fun prefs_new_file_name_is_wedo_prefs() {
        assertTrue(
            "现行偏好文件名必须是 wedo_prefs",
            appPrefsSrc.contains("""private const val FILE = "wedo_prefs"""")
        )
    }

    @Test
    fun prefs_legacy_name_kept_for_migration() {
        assertTrue(
            "旧名下线前必须保留常量，否则老用户设置无处可搬",
            appPrefsSrc.contains("""private const val LEGACY_FILE = "sleepy_prefs"""")
        )
    }

    @Test
    fun prefs_exposes_single_shared_entry_point() {
        assertTrue(
            "AppPrefs 必须对外暴露 sharedPrefs 入口，供 HolidayManager 等复用同一实例",
            appPrefsSrc.contains("internal fun sharedPrefs(ctx: Context): SharedPreferences")
        )
    }

    @Test
    fun holiday_manager_does_not_open_prefs_by_name() {
        assertFalse(
            "HolidayManager 不得自行 getSharedPreferences —— 会绕过搬迁逻辑读到空文件",
            holidayManagerSrc.contains("getSharedPreferences")
        )
        assertTrue(
            "HolidayManager 必须走 AppPrefs.sharedPrefs",
            holidayManagerSrc.contains("AppPrefs.sharedPrefs(ctx)")
        )
    }

    // ── AppDatabase ───────────────────────────────────────────

    @Test
    fun database_new_name_is_wedo_db() {
        assertTrue(
            "现行库名必须是 wedo.db",
            appDatabaseSrc.contains("""private const val DB_NAME = "wedo.db"""")
        )
    }

    @Test
    fun database_legacy_name_kept_for_migration() {
        assertTrue(
            "旧库名 sleepy.db 必须保留，否则无法定位老用户的库文件",
            appDatabaseSrc.contains("""private const val LEGACY_DB_NAME = "sleepy.db"""")
        )
    }

    @Test
    fun database_migrates_wal_sidecar_files() {
        // WAL 模式下只搬主文件会丢掉还留在 -wal 里的近期事务。
        for (suffix in listOf("\"\"", "\"-wal\"", "\"-shm\"", "\"-journal\"")) {
            assertTrue(
                "伴生文件后缀 $suffix 必须在搬迁清单里",
                appDatabaseSrc.contains(suffix)
            )
        }
    }

    @Test
    fun database_adopts_legacy_file_before_builder() {
        val adoptAt = appDatabaseSrc.indexOf("adoptLegacyDatabaseFile(context.applicationContext)")
        val builderAt = appDatabaseSrc.indexOf("Room.databaseBuilder(")
        assertTrue("搬迁调用必须存在", adoptAt >= 0 && builderAt >= 0)
        assertTrue(
            "搬迁必须在 databaseBuilder 之前 —— 否则新空库先落地，旧数据永远搬不过来",
            adoptAt < builderAt
        )
    }

    @Test
    fun database_never_enables_destructive_fallback() {
        // 注意：源码里在 build() 之前有一条**注释**写着「严禁 fallbackToDestructiveMigration」。
        // 所以不能直接搜整个文件 —— 那样永远命中注释。只搜可执行行。
        val callSites = appDatabaseSrc.lineSequence()
            .filterNot { it.trimStart().startsWith("//") }
            .filterNot { it.trimStart().startsWith("*") }
            .filterNot { it.trimStart().startsWith("/*") }
            .joinToString("\n")
        assertFalse(
            "严禁 fallbackToDestructiveMigration —— 任意版本升会清空用户课表",
            callSites.contains("fallbackToDestructiveMigration")
        )
    }

    @Test
    fun database_version_unchanged_at_5() {
        // 纯改名，schema 未动，不应顺手升版本号（否则要额外写一个空 Migration）
        assertTrue(
            "文件改名不改变 schema，version 应保持 5",
            appDatabaseSrc.contains("version = 5")
        )
    }

    @Test
    fun database_never_registers_migration_for_rename() {
        // 改名不是 schema 迁移。若有人在 Migrations.kt 里为 5→6 写迁移来"配合改名"，
        // 说明理解错了 —— 这条守住认知边界。
        assertFalse(
            "文件改名不需要 Migration 登记",
            appDatabaseSrc.contains("MIGRATION_5_6")
        )
    }

    @Test
    fun database_adopt_is_idempotent_guarded() {
        // 新库已存在必须直接返回，不能覆盖用户当前数据
        assertTrue(
            "搬迁前必须先检查新库是否已存在",
            appDatabaseSrc.contains("File(dbDir, DB_NAME).exists()")
        )
        assertTrue(
            "搬迁前必须确认旧库确实存在",
            appDatabaseSrc.contains("!legacy.exists()")
        )
    }

    @Test
    fun database_adopt_rolls_back_on_failure() {
        assertTrue(
            "rename 失败必须回滚已搬文件，不能留半新半旧的残局",
            appDatabaseSrc.contains("renameTo(src)")
        )
    }

    // ── 常量语义 ──────────────────────────────────────────────

    @Test
    fun storage_names_are_distinct_and_prefixed() {
        assertEquals("wedo_prefs", AppPrefs.resolvePrefsFileNameForTest())
        assertEquals("wedo.db", AppDatabase.resolveDbFileNameForTest())
        assertFalse(
            "新名字不得再含历史品牌词",
            AppPrefs.resolvePrefsFileNameForTest().contains("sleepy") ||
                AppDatabase.resolveDbFileNameForTest().contains("sleepy")
        )
    }
}
