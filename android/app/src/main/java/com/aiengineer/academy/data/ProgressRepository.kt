package com.aiengineer.academy.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Local SQLite database holding all learner progress. Nothing leaves the device. */
class ProgressDb(context: Context) : SQLiteOpenHelper(context, "progress.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE lesson_progress (topic TEXT NOT NULL, lesson INTEGER NOT NULL, " +
                "completed_at INTEGER NOT NULL DEFAULT 0, last_opened INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(topic, lesson))"
        )
        db.execSQL(
            "CREATE TABLE question_state (topic TEXT NOT NULL, qn INTEGER NOT NULL, correct INTEGER NOT NULL DEFAULT 0, " +
                "attempts INTEGER NOT NULL DEFAULT 0, last_choice INTEGER NOT NULL DEFAULT -1, updated_at INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(topic, qn))"
        )
        db.execSQL(
            "CREATE TABLE bookmarks (kind TEXT NOT NULL, topic TEXT NOT NULL, ref INTEGER NOT NULL, created_at INTEGER NOT NULL, " +
                "PRIMARY KEY(kind, topic, ref))"
        )
        db.execSQL(
            "CREATE TABLE activity (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, ts INTEGER NOT NULL, " +
                "type TEXT NOT NULL, topic TEXT NOT NULL, ref INTEGER NOT NULL DEFAULT 0, value INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_activity_day ON activity(day)")
        db.execSQL("CREATE INDEX idx_question_topic ON question_state(topic)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First schema version; future migrations go here.
    }
}

/**
 * Reads and writes progress. Writes run on [scope] (an IO scope) and bump [version],
 * which screens observe to refresh their data.
 *
 * Activity types: lesson_open, lesson_done, answer (value 1 = correct), study (value = seconds).
 */
class ProgressRepository(private val helper: ProgressDb, private val scope: CoroutineScope) {

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun bump() {
        _version.value = _version.value + 1
    }

    private fun today(): String = LocalDate.now().toString()

    private fun write(block: (SQLiteDatabase) -> Unit) {
        scope.launch {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                block(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            bump()
        }
    }

    private fun logActivity(db: SQLiteDatabase, type: String, topic: String, ref: Int, value: Int) {
        db.execSQL(
            "INSERT INTO activity(day, ts, type, topic, ref, value) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(today(), System.currentTimeMillis(), type, topic, ref, value)
        )
    }

    // ------------------------------------------------------------------ writes

    fun openLesson(topic: String, lesson: Int) = write { db ->
        val now = System.currentTimeMillis()
        db.execSQL("INSERT OR IGNORE INTO lesson_progress(topic, lesson) VALUES (?, ?)", arrayOf<Any>(topic, lesson))
        db.execSQL("UPDATE lesson_progress SET last_opened = ? WHERE topic = ? AND lesson = ?", arrayOf<Any>(now, topic, lesson))
        logActivity(db, "lesson_open", topic, lesson, 0)
    }

    fun setLessonComplete(topic: String, lesson: Int, done: Boolean) = write { db ->
        db.execSQL("INSERT OR IGNORE INTO lesson_progress(topic, lesson) VALUES (?, ?)", arrayOf<Any>(topic, lesson))
        db.execSQL(
            "UPDATE lesson_progress SET completed_at = ? WHERE topic = ? AND lesson = ?",
            arrayOf<Any>(if (done) System.currentTimeMillis() else 0L, topic, lesson)
        )
        if (done) logActivity(db, "lesson_done", topic, lesson, 0)
    }

    fun recordAnswer(topic: String, number: Int, lesson: Int, correct: Boolean, choice: Int) = write { db ->
        db.execSQL("INSERT OR IGNORE INTO question_state(topic, qn) VALUES (?, ?)", arrayOf<Any>(topic, number))
        db.execSQL(
            "UPDATE question_state SET correct = ?, attempts = attempts + 1, last_choice = ?, updated_at = ? WHERE topic = ? AND qn = ?",
            arrayOf<Any>(if (correct) 1 else 0, choice, System.currentTimeMillis(), topic, number)
        )
        logActivity(db, "answer", topic, lesson, if (correct) 1 else 0)
    }

    fun toggleBookmark(kind: String, topic: String, ref: Int) = write { db ->
        val c = db.rawQuery("SELECT 1 FROM bookmarks WHERE kind = ? AND topic = ? AND ref = ?", arrayOf(kind, topic, ref.toString()))
        val exists = c.use { it.moveToFirst() }
        if (exists) {
            db.execSQL("DELETE FROM bookmarks WHERE kind = ? AND topic = ? AND ref = ?", arrayOf<Any>(kind, topic, ref))
        } else {
            db.execSQL(
                "INSERT INTO bookmarks(kind, topic, ref, created_at) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(kind, topic, ref, System.currentTimeMillis())
            )
        }
    }

    fun addStudySeconds(topic: String, seconds: Int) {
        if (seconds <= 0) return
        write { db -> logActivity(db, "study", topic, 0, seconds.coerceAtMost(3600)) }
    }

    fun resetTopicQuestions(topic: String) = write { db ->
        db.execSQL("DELETE FROM question_state WHERE topic = ?", arrayOf<Any>(topic))
    }

    fun resetAll() = write { db ->
        db.execSQL("DELETE FROM lesson_progress")
        db.execSQL("DELETE FROM question_state")
        db.execSQL("DELETE FROM bookmarks")
        db.execSQL("DELETE FROM activity")
    }

    // ------------------------------------------------------------------ reads (call off the main thread)

    private val rdb: SQLiteDatabase get() = helper.readableDatabase

    fun completedLessons(topic: String): Set<Int> {
        val out = HashSet<Int>()
        rdb.rawQuery("SELECT lesson FROM lesson_progress WHERE topic = ? AND completed_at > 0", arrayOf(topic)).use { c ->
            while (c.moveToNext()) out.add(c.getInt(0))
        }
        return out
    }

    fun allCompletedLessons(): Map<String, Set<Int>> {
        val out = HashMap<String, MutableSet<Int>>()
        rdb.rawQuery("SELECT topic, lesson FROM lesson_progress WHERE completed_at > 0", null).use { c ->
            while (c.moveToNext()) out.getOrPut(c.getString(0)) { HashSet() }.add(c.getInt(1))
        }
        return out
    }

    /** Lessons of a topic that were opened at least once (for "In progress" vs "Unopened"). */
    fun openedLessons(topic: String): Set<Int> {
        val out = HashSet<Int>()
        rdb.rawQuery("SELECT lesson FROM lesson_progress WHERE topic = ? AND last_opened > 0", arrayOf(topic)).use { c ->
            while (c.moveToNext()) out.add(c.getInt(0))
        }
        return out
    }

    /** The topic of the most recently answered question, if any. */
    fun lastPracticedTopic(): String? =
        rdb.rawQuery("SELECT topic FROM question_state WHERE attempts > 0 ORDER BY updated_at DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    // ------------------------------------------------------------------ backup (Settings › Data)

    /** Writes a copy of the progress database to [out]. Call off the main thread. */
    fun exportTo(out: java.io.OutputStream) {
        val db = helper.writableDatabase
        db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        java.io.File(db.path).inputStream().use { it.copyTo(out) }
    }

    /**
     * Replaces progress with a database previously written by [exportTo]. Returns false (and changes nothing)
     * if the file is not a LearnAIoffline progress backup. Call off the main thread.
     */
    fun importFrom(input: java.io.InputStream): Boolean {
        val path = helper.writableDatabase.path
        val dir = java.io.File(path).parentFile ?: return false
        val tmp = java.io.File(dir, "import-check.db")
        try {
            input.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            val header = tmp.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
            if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) return false
            val ok = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { check ->
                val need = setOf("lesson_progress", "question_state", "bookmarks", "activity")
                val have = HashSet<String>()
                check.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c -> while (c.moveToNext()) have.add(c.getString(0)) }
                have.containsAll(need)
            }
            if (!ok) return false
            synchronized(this) {
                helper.close()
                listOf("", "-wal", "-shm", "-journal").forEach { java.io.File(path + it).delete() }
                tmp.copyTo(java.io.File(path), overwrite = true)
            }
            bump()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            tmp.delete()
        }
    }

    fun lastOpenedLesson(): Pair<String, Int>? =
        rdb.rawQuery("SELECT topic, lesson FROM lesson_progress WHERE last_opened > 0 ORDER BY last_opened DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getInt(1) else null
        }

    fun questionStates(topic: String): Map<Int, QState> {
        val out = HashMap<Int, QState>()
        rdb.rawQuery("SELECT qn, correct, attempts, last_choice FROM question_state WHERE topic = ? AND attempts > 0", arrayOf(topic)).use { c ->
            while (c.moveToNext()) out[c.getInt(0)] = QState(c.getInt(1) == 1, c.getInt(2), c.getInt(3))
        }
        return out
    }

    fun topicSummaries(): Map<String, TopicSummary> {
        val lessons = HashMap<String, Int>()
        rdb.rawQuery("SELECT topic, COUNT(*) FROM lesson_progress WHERE completed_at > 0 GROUP BY topic", null).use { c ->
            while (c.moveToNext()) lessons[c.getString(0)] = c.getInt(1)
        }
        val out = HashMap<String, TopicSummary>()
        rdb.rawQuery("SELECT topic, COUNT(*), SUM(correct) FROM question_state WHERE attempts > 0 GROUP BY topic", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = TopicSummary(lessons[c.getString(0)] ?: 0, c.getInt(1), c.getInt(2))
        }
        for ((t, n) in lessons) if (t !in out) out[t] = TopicSummary(n, 0, 0)
        return out
    }

    fun isBookmarked(kind: String, topic: String, ref: Int): Boolean =
        rdb.rawQuery("SELECT 1 FROM bookmarks WHERE kind = ? AND topic = ? AND ref = ?", arrayOf(kind, topic, ref.toString())).use { it.moveToFirst() }

    fun bookmarkedQuestions(topic: String): Set<Int> {
        val out = HashSet<Int>()
        rdb.rawQuery("SELECT ref FROM bookmarks WHERE kind = 'q' AND topic = ?", arrayOf(topic)).use { c ->
            while (c.moveToNext()) out.add(c.getInt(0))
        }
        return out
    }

    fun bookmarks(): List<Bookmark> {
        val out = ArrayList<Bookmark>()
        rdb.rawQuery("SELECT kind, topic, ref, created_at FROM bookmarks ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) out.add(Bookmark(c.getString(0), c.getString(1), c.getInt(2), c.getLong(3)))
        }
        return out
    }

    fun dayStats(limit: Int = 365): List<DayStat> {
        val out = ArrayList<DayStat>()
        rdb.rawQuery(
            "SELECT day, " +
                "SUM(CASE WHEN type = 'lesson_open' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN type = 'lesson_done' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN type = 'answer' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN type = 'answer' AND value = 1 THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN type = 'study' THEN value ELSE 0 END) " +
                "FROM activity GROUP BY day ORDER BY day DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(DayStat(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5)))
            }
        }
        return out
    }

    fun todayStats(): DayStat = dayStats(1).firstOrNull { it.day == today() } ?: DayStat(today())

    fun dayDetail(day: String): List<DayTopicDetail> {
        data class Acc(
            val opened: MutableSet<Int> = HashSet(),
            val done: MutableSet<Int> = HashSet(),
            var answered: Int = 0,
            var correct: Int = 0,
            var study: Int = 0,
        )
        val map = LinkedHashMap<String, Acc>()
        rdb.rawQuery("SELECT topic, type, ref, value FROM activity WHERE day = ? ORDER BY ts", arrayOf(day)).use { c ->
            while (c.moveToNext()) {
                val acc = map.getOrPut(c.getString(0)) { Acc() }
                when (c.getString(1)) {
                    "lesson_open" -> acc.opened.add(c.getInt(2))
                    "lesson_done" -> acc.done.add(c.getInt(2))
                    "answer" -> {
                        acc.answered++
                        if (c.getInt(3) == 1) acc.correct++
                    }
                    "study" -> acc.study += c.getInt(3)
                }
            }
        }
        return map.map { (t, a) -> DayTopicDetail(t, a.opened, a.done, a.answered, a.correct, a.study) }
    }

    /** Number of consecutive days (ending today, or yesterday if nothing yet today) with any activity. */
    fun streak(): Int {
        val days = HashSet<String>()
        rdb.rawQuery("SELECT DISTINCT day FROM activity ORDER BY day DESC LIMIT 800", null).use { c ->
            while (c.moveToNext()) days.add(c.getString(0))
        }
        var d = LocalDate.now()
        if (d.toString() !in days) d = d.minusDays(1)
        var n = 0
        while (d.toString() in days) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** Set of active days within the last [n] days (for the activity strip). */
    fun activeDays(n: Int): Set<String> {
        val from = LocalDate.now().minusDays(n.toLong()).toString()
        val out = HashSet<String>()
        rdb.rawQuery("SELECT DISTINCT day FROM activity WHERE day >= ?", arrayOf(from)).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }
}
