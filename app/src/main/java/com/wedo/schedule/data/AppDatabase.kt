package com.wedo.schedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wedo.schedule.data.dao.CourseDao
import com.wedo.schedule.data.dao.TimeTableDao
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.data.entity.TimeTableEntity
import java.io.File

@Database(
    entities = [CourseEntity::class, TimeTableEntity::class],
    version = 5,                            // 4 → 5: 加 courses.isIrregularNode/isIrregularTime (issue#23)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun timeTableDao(): TimeTableDao

    companion object {
        /** 现行库文件名。历史名为 sleepy.db，靠 [adoptLegacyDatabaseFile] 一次性搬过来。 */
        private const val DB_NAME = "wedo.db"

        /** 历史库文件名。仅用于开库前的文件搬迁，不再作为 Room 的库名。 */
        private const val LEGACY_DB_NAME = "sleepy.db"

        /** WAL 模式下 SQLite 会额外产出这些伴随文件，搬迁时必须一并带走。 */
        private val DB_SIDECAR_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: run {
                    // 必须在 databaseBuilder 之前完成：Room 一开库就会按 DB_NAME 创建新文件，
                    // 那时再搬就晚了（新空库已落地，旧数据还在 sleepy.db 里躺着）。
                    adoptLegacyDatabaseFile(context.applicationContext)

                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .addMigrations(*ALL_MIGRATIONS)
                        // 严禁 fallbackToDestructiveMigration — 任意版本升会清空用户课表
                        // 任何 schema 改动必须先在 Migrations.kt 登记, 再升 version
                        .build()
                        .also { instance = it }
                }
            }
        }

        /**
         * 仅供单测断言库名常量，不参与运行时逻辑。
         * 常量本身是 `private`，从 JVM 单测无法直接读。
         */
        internal fun resolveDbFileNameForTest(): String = DB_NAME

        /**
         * 把历史 `sleepy.db` 搬迁为 `wedo.db`。
         *
         * 这是**纯文件改名**，不是数据库结构迁移：schema 与 version(5) 都没变，
         * 因此不需要（也不能）在 [Migrations.kt] 里登记 Migration。
         *
         * 三条安全约束：
         * 1. 新库文件已存在 → 立即返回，绝不覆盖用户当前数据；
         * 2. WAL 伴生文件 `-wal` / `-shm` / `-journal` 必须同批搬迁，
         *    只搬主文件会丢掉还留在 WAL 里的近期事务；
         * 3. 任何一步 rename 失败 → 中止整批，并把已搬的文件回滚，
         *    宁可让用户继续读旧库，也不留半新半旧的残局。
         *    旧文件不删（rename 本身就是移动），失败时旧库仍然完好。
         */
        private fun adoptLegacyDatabaseFile(context: Context) {
            val dbDir = context.getDatabasePath(DB_NAME).parentFile ?: return
            val legacy = File(dbDir, LEGACY_DB_NAME)
            if (File(dbDir, DB_NAME).exists() || !legacy.exists()) return

            val moved = mutableListOf<Pair<File, File>>()
            for (suffix in DB_SIDECAR_SUFFIXES) {
                val from = File(dbDir, LEGACY_DB_NAME + suffix)
                if (!from.exists()) continue
                val to = File(dbDir, DB_NAME + suffix)
                if (from.renameTo(to)) {
                    moved += from to to
                } else {
                    // 回滚：把本批已搬走的文件挪回旧名，保证旧库自洽
                    moved.asReversed().forEach { (src, dst) -> dst.renameTo(src) }
                    return
                }
            }
        }
    }
}