package com.aiengineer.academy.data

/** Metadata about one lesson, read from the topic index (topics.json). */
data class LessonMeta(
    val title: String,
    val minutes: Int,
    val sections: List<String>,
    val questionCount: Int,
)

/** A course topic such as "LangChain" or "Vector RAG". */
data class Topic(
    val id: String,
    val title: String,
    val short: String,
    val emoji: String,
    val color: Long,
    val lessons: List<LessonMeta>,
    val questionCount: Int,
    val glossaryCount: Int,
)

/** Building blocks of lesson content. */
sealed interface Block {
    data class Heading(val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
    data class Bullets(val items: List<String>) : Block
    data class Numbered(val items: List<String>) : Block
    data class Note(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
}

data class Lesson(val title: String, val minutes: Int, val blocks: List<Block>)

/** One multiple-choice question. [number] is 1-based and stable; [lesson] is 0-based. */
data class Question(
    val number: Int,
    val lesson: Int,
    val kind: String,
    val difficulty: Int,
    val text: String,
    val code: String?,
    val options: List<String>,
    val answer: Int,
    val hint: String,
    val hintCode: String?,
    val explanation: String,
    val explanationCode: String?,
)

data class GlossaryEntry(
    val term: String,
    val definition: String,
    val lesson: Int,
    val code: String,
    val example: String,
)

/** Saved answer state for a question. */
data class QState(val correct: Boolean, val attempts: Int, val lastChoice: Int)

data class TopicSummary(
    val lessonsDone: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
) {
    val accuracy: Float get() = if (answered == 0) 0f else correct.toFloat() / answered
}

data class DayStat(
    val day: String,
    val lessonsOpened: Int = 0,
    val lessonsDone: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
    val studySeconds: Int = 0,
) {
    val accuracy: Float get() = if (answered == 0) 0f else correct.toFloat() / answered
}

data class DayTopicDetail(
    val topicId: String,
    val lessonsOpened: Set<Int>,
    val lessonsDone: Set<Int>,
    val answered: Int,
    val correct: Int,
    val studySeconds: Int,
)

data class Bookmark(val kind: String, val topicId: String, val ref: Int, val createdAt: Long)

enum class QuizMode(val label: String) {
    CONTINUE("Continue in order"),
    RANDOM("Random set"),
    LESSON("By lesson"),
    MISTAKES("Review mistakes"),
    UNANSWERED("Unanswered only"),
    BOOKMARKED("Bookmarked"),
    FROM_NUMBER("From question #"),
    CUSTOM("Selected questions"),
}
