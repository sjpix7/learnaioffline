package com.aiengineer.academy.ui

import androidx.compose.runtime.mutableStateListOf
import com.aiengineer.academy.data.QuizMode

/** All destinations in the app. */
sealed interface Screen {
    // bottom-navigation tabs
    data object Home : Screen
    data object Learn : Screen
    data object Practice : Screen
    data object Progress : Screen

    // detail screens
    data class TopicDetail(val topicId: String) : Screen
    data class LessonView(val topicId: String, val index: Int, val section: Int = -1) : Screen
    data class TopicProgress(val topicId: String) : Screen
    /** Key concepts; an empty [topicId] means all topics. */
    data class Glossary(val topicId: String = "") : Screen
    data class Flashcards(val topicId: String = "", val start: Int = 0) : Screen
    /** Build a practice set; empty [topicId] picks the last practised topic. */
    data class QuizSetup(val topicId: String = "", val mode: QuizMode? = null) : Screen
    data class Quiz(val sessionId: Long) : Screen
    data class QuestionBank(val topicId: String) : Screen
    data object History : Screen
    data class Day(val day: String) : Screen
    data object Search : Screen
    data object Bookmarks : Screen
    data object Settings : Screen
    data object Appearance : Screen
    data object About : Screen

    val isTab: Boolean get() = this == Home || this == Learn || this == Practice || this == Progress

    /** Browse screens keep the bottom navigation (design kit); focused reading, quizzes and settings hide it. */
    val showsBottomBar: Boolean
        get() = isTab || this is TopicDetail || this is TopicProgress || this is Glossary || this is QuestionBank ||
            this == History || this is Day || this == Bookmarks || this == Search
}

/** A minimal back-stack navigator backed by snapshot state. */
class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)

    val current: Screen get() = stack.last()

    fun push(screen: Screen) {
        if (stack.lastOrNull() != screen) stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replace(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    /** Switches to a bottom tab, clearing deeper screens. */
    fun tab(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    /** The tab that owns the current back stack. */
    val rootTab: Screen get() = stack.firstOrNull { it.isTab } ?: Screen.Home
}
