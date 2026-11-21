package com.aiengineer.academy.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads bundled course content from assets/content. Everything is offline.
 * Parsed data is cached; question banks (about 2 MB each) are kept in a small LRU cache.
 */
class ContentRepository(private val context: Context) {

    private val topicList: List<Topic> by lazy { parseTopics(readAsset("content/topics.json")) }
    private val lessonCache = ConcurrentHashMap<String, List<Lesson>>()
    private val glossaryCache = ConcurrentHashMap<String, List<GlossaryEntry>>()
    private val questionCache = object : LinkedHashMap<String, List<Question>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Question>>?): Boolean = size > 5
    }

    fun topics(): List<Topic> = topicList

    fun topic(id: String): Topic? = topicList.firstOrNull { it.id == id }

    val totalLessons: Int get() = topicList.sumOf { it.lessons.size }

    val totalQuestions: Int get() = topicList.sumOf { it.questionCount }

    fun lessons(topicId: String): List<Lesson> =
        lessonCache.getOrPut(topicId) { parseLessons(readAsset("content/lessons/$topicId.json")) }

    fun glossary(topicId: String): List<GlossaryEntry> =
        glossaryCache.getOrPut(topicId) { parseGlossary(readAsset("content/glossary/$topicId.json")) }

    /** Returns cached questions or null if they have not been loaded yet (safe to call on the main thread). */
    fun cachedQuestions(topicId: String): List<Question>? = synchronized(questionCache) { questionCache[topicId] }

    /** Loads (and caches) the question bank for a topic. Call from a background thread. */
    fun questions(topicId: String): List<Question> {
        synchronized(questionCache) { questionCache[topicId]?.let { return it } }
        val parsed = parseQuestions(readAsset("content/questions/$topicId.json"))
        synchronized(questionCache) { questionCache[topicId] = parsed }
        return parsed
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    // ------------------------------------------------------------------ parsing

    private fun parseColor(hex: String): Long =
        try {
            ("FF" + hex.removePrefix("#").take(6)).toLong(16)
        } catch (e: NumberFormatException) {
            0xFF5B4BDB
        }

    private fun parseTopics(json: String): List<Topic> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val lessonsArr = o.getJSONArray("lessons")
            val lessons = List(lessonsArr.length()) { j ->
                val l = lessonsArr.getJSONObject(j)
                LessonMeta(
                    title = l.getString("t"),
                    minutes = l.optInt("m", 5),
                    sections = l.optJSONArray("s").toStringList(),
                    questionCount = l.optInt("q", 0),
                )
            }
            Topic(
                id = o.getString("id"),
                title = o.getString("title"),
                short = o.optString("short"),
                emoji = o.optString("emoji", "📘"),
                color = parseColor(o.optString("color", "#5B4BDB")),
                lessons = lessons,
                questionCount = o.optInt("qcount", 0),
                glossaryCount = o.optInt("gcount", 0),
            )
        }
    }

    private fun parseLessons(json: String): List<Lesson> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val blocksArr = o.getJSONArray("b")
            val blocks = ArrayList<Block>(blocksArr.length())
            for (j in 0 until blocksArr.length()) {
                parseBlock(blocksArr.getJSONObject(j))?.let { blocks.add(it) }
            }
            Lesson(o.getString("t"), o.optInt("m", 5), blocks)
        }
    }

    private fun parseBlock(b: JSONObject): Block? = when (b.optString("k")) {
        "h" -> Block.Heading(b.getString("t"))
        "p" -> Block.Paragraph(b.getString("t"))
        "c" -> Block.Code(b.optString("l", "text"), b.getString("t"))
        "ul" -> Block.Bullets(b.optJSONArray("i").toStringList())
        "ol" -> Block.Numbered(b.optJSONArray("i").toStringList())
        "n" -> Block.Note(b.getString("t"))
        "tb" -> {
            val rows = b.getJSONArray("r")
            Block.Table(List(rows.length()) { r -> rows.getJSONArray(r).toStringList() })
        }
        else -> null
    }

    private fun parseQuestions(json: String): List<Question> {
        val root = JSONObject(json)
        val snippets = root.getJSONArray("s").toStringList()
        val arr = root.getJSONArray("q")
        fun snip(o: JSONObject, key: String): String? {
            val idx = o.optInt(key, -1)
            return if (idx in snippets.indices) snippets[idx] else null
        }
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Question(
                number = o.getInt("n"),
                lesson = o.optInt("l", 0),
                kind = o.optString("k", "concept"),
                difficulty = o.optInt("d", 1),
                text = o.getString("q"),
                code = snip(o, "c"),
                options = o.getJSONArray("o").toStringList(),
                answer = o.getInt("a"),
                hint = o.optString("h"),
                hintCode = snip(o, "hc"),
                explanation = o.optString("e"),
                explanationCode = snip(o, "ec"),
            )
        }
    }

    private fun parseGlossary(json: String): List<GlossaryEntry> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            GlossaryEntry(
                term = o.getString("t"),
                definition = o.optString("d"),
                lesson = o.optInt("l", 0),
                code = o.optString("c"),
                example = o.optString("x"),
            )
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { optString(it) }
    }
}
