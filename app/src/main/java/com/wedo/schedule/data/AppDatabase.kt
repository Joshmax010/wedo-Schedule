package com.wedo.schedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wedo.schedule.data.dao.CourseDao
import com.wedo.schedule.data.dao.TimeTableDao
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.data.entity.TimeTableEntity

@Database(
    entities = [CourseEntity::class, TimeTableEntity::class],
    version = 5,                            // 4 → 5: 加 courses.isIrregularNode/isIrregularTime (issue#23)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun timeTableDao(): TimeTableDao

    companion object {
        private const val DB_NAME = "sleepy.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
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
}