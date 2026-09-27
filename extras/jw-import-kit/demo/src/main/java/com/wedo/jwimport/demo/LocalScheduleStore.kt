// SPDX-License-Identifier: GPL-3.0-only
package com.wedo.jwimport.demo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Demo-only local preview cache. A real host maps ImportedCourse to its own Room entities. */
class LocalScheduleStore(context: Context) : SQLiteOpenHelper(context, "import-preview.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE confirmed_preview (id INTEGER PRIMARY KEY, body TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Add an explicit migration before changing the cache schema")
    }
    fun save(body: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply { put("id", 1); put("body", body) }
            check(db.insertWithOnConflict("confirmed_preview", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun read(): String? = readableDatabase.query("confirmed_preview", arrayOf("body"), "id=?",
        arrayOf("1"), null, null, null).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}
