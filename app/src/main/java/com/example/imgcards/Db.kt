package com.example.imgcards

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.max
import kotlin.math.roundToInt

data class Card(
    val id: Long, val front: String, val back: String?, val note: String?,
    val due: Long, val ivl: Int, val ease: Double, val reps: Int, val star: Int = 0, val flip: Int = 0, val folder: Long = 0L
)

data class Folder(val id: Long, val name: String)

object Sched {
    // q: 0=دوباره 1=سخت 2=خوب 3=آسان
    fun apply(c: Card, q: Int): Card {
        val now = System.currentTimeMillis()
        if (q == 0) return c.copy(ivl = 0, reps = 0, ease = max(1.3, c.ease - 0.2), due = now + 10 * 60000L)
        val ivl = if (c.ivl == 0) {
            when (q) { 3 -> 4; else -> 1 }
        } else when (q) {
            1 -> max(c.ivl + 1, (c.ivl * 1.2).roundToInt())
            2 -> max(c.ivl + 1, (c.ivl * c.ease).roundToInt())
            else -> max(c.ivl + 2, (c.ivl * c.ease * 1.3).roundToInt())
        }
        val ease = when (q) { 1 -> max(1.3, c.ease - 0.15); 3 -> c.ease + 0.15; else -> c.ease }
        return c.copy(ivl = ivl, ease = ease, reps = c.reps + 1, due = now + ivl * 86400000L)
    }
    fun label(c: Card, q: Int): String = if (q == 0) "۱۰ دقیقه" else "${apply(c, q).ivl} روز"
}

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "cards.db", null, 4) {
    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY AUTOINCREMENT, front TEXT, back TEXT, note TEXT, due INTEGER, ivl INTEGER, ease REAL, reps INTEGER, star INTEGER DEFAULT 0, flip INTEGER DEFAULT 0, folder INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE IF NOT EXISTS folders(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")
    }
    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {
        if (o < 2) d.execSQL("ALTER TABLE cards ADD COLUMN star INTEGER DEFAULT 0")
        if (o < 3) d.execSQL("ALTER TABLE cards ADD COLUMN flip INTEGER DEFAULT 0")
        if (o < 4) {
            d.execSQL("ALTER TABLE cards ADD COLUMN folder INTEGER DEFAULT 0")
            d.execSQL("CREATE TABLE IF NOT EXISTS folders(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")
        }
    }

    fun add(front: String, back: String?, note: String?) {
        val v = ContentValues().apply {
            put("front", front); put("back", back); put("note", note)
            put("due", 0L); put("ivl", 0); put("ease", 2.5); put("reps", 0)
        }
        writableDatabase.insert("cards", null, v)
    }

    private fun query(where: String?, order: String): List<Card> {
        val out = ArrayList<Card>()
        readableDatabase.query("cards", null, where, null, null, null, order).use { c ->
            while (c.moveToNext()) out.add(
                Card(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getLong(4), c.getInt(5), c.getDouble(6), c.getInt(7), c.getInt(8), c.getInt(9), c.getLong(10))
            )
        }
        return out
    }

    fun dueCards(): List<Card> = query("due <= ${System.currentTimeMillis()} AND star = 0", "due ASC, id ASC")
    fun starred(): List<Card> = query("star = 1", "id DESC")
    fun setFlip(id: Long, v: Int) {
        val cv = ContentValues().apply { put("flip", v) }
        writableDatabase.update("cards", cv, "id=?", arrayOf(id.toString()))
    }
    fun setStar(id: Long, v: Int) {
        val cv = ContentValues().apply { put("star", v); if (v == 0) put("folder", 0) }
        writableDatabase.update("cards", cv, "id=?", arrayOf(id.toString()))
    }
    fun all(): List<Card> = query(null, "id DESC")
    fun update(c: Card) {
        val v = ContentValues().apply { put("due", c.due); put("ivl", c.ivl); put("ease", c.ease); put("reps", c.reps) }
        writableDatabase.update("cards", v, "id=?", arrayOf(c.id.toString()))
    }
    fun deleteAll() {
        writableDatabase.delete("cards", null, null)
        writableDatabase.delete("folders", null, null)
    }

    // ---------- پوشه‌ها ----------
    fun addFolder(name: String): Long {
        val cv = ContentValues().apply { put("name", name) }
        return writableDatabase.insert("folders", null, cv)
    }

    fun folders(): List<Folder> {
        val out = ArrayList<Folder>()
        readableDatabase.query("folders", null, null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) out.add(Folder(c.getLong(0), c.getString(1)))
        }
        return out
    }

    /** حذف پوشه؛ تصاویرش حذف نمی‌شوند و به خود پوشه‌ی ستاره‌دارها می‌روند */
    fun deleteFolder(id: Long) {
        val cv = ContentValues().apply { put("folder", 0) }
        writableDatabase.update("cards", cv, "folder=?", arrayOf(id.toString()))
        writableDatabase.delete("folders", "id=?", arrayOf(id.toString()))
    }

    /** انتقال کارت‌ها به پوشه‌ای داخل ستاره‌دارها (folderId=0 یعنی خود ستاره‌دارها) */
    fun setFolder(ids: List<Long>, folderId: Long) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            for (id in ids) {
                val cv = ContentValues().apply { put("star", 1); put("folder", folderId) }
                d.update("cards", cv, "id=?", arrayOf(id.toString()))
            }
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
    }

    fun unstar(ids: List<Long>) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            for (id in ids) {
                val cv = ContentValues().apply { put("star", 0); put("folder", 0) }
                d.update("cards", cv, "id=?", arrayOf(id.toString()))
            }
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
    }

    fun deleteCards(ids: List<Long>) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            for (id in ids) d.delete("cards", "id=?", arrayOf(id.toString()))
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
    }
    fun delete(id: Long) { writableDatabase.delete("cards", "id=?", arrayOf(id.toString())) }
}
