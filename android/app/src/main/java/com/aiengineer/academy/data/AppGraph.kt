package com.aiengineer.academy.data

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aiengineer.academy.ui.Navigator
import com.aiengineer.academy.ui.theme.Accent
import com.aiengineer.academy.ui.theme.CodeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Simple service locator. Lives as long as the process, so navigation and quiz state survive rotation. */
object AppGraph {
    lateinit var content: ContentRepository
        private set
    lateinit var progress: ProgressRepository
        private set
    lateinit var settings: SettingsStore
        private set

    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val navigator = Navigator()
    val quizSessions = HashMap<Long, QuizSession>()

    fun init(app: Application) {
        content = ContentRepository(app)
        progress = ProgressRepository(ProgressDb(app), io)
        settings = SettingsStore(app)
    }
}

/** User preferences stored in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getInt("theme_mode", 0))
    /** 0 = follow system, 1 = light, 2 = dark. */
    val themeMode: StateFlow<Int> = _themeMode

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    private val _quizSize = MutableStateFlow(prefs.getInt("quiz_size", 20))
    val quizSize: StateFlow<Int> = _quizSize

    private val _codeFontScale = MutableStateFlow(prefs.getFloat("code_font", 1f))
    val codeFontScale: StateFlow<Float> = _codeFontScale

    /** Accent colour theme, stored by [Accent.name]. New installs start on Forest (the brand default). */
    private val _accent = MutableStateFlow(Accent.fromName(prefs.getString("accent", null)))
    val accent: StateFlow<Accent> = _accent

    /** True-black backgrounds in dark mode. */
    private val _amoled = MutableStateFlow(prefs.getBoolean("amoled", false))
    val amoled: StateFlow<Boolean> = _amoled

    private val _codeTheme = MutableStateFlow(CodeTheme.fromName(prefs.getString("code_theme", null)))
    val codeTheme: StateFlow<CodeTheme> = _codeTheme

    /** Daily study goal in minutes (onboarding step 3 and Settings › Study). */
    private val _dailyGoal = MutableStateFlow(prefs.getInt("daily_goal_min", 15))
    val dailyGoal: StateFlow<Int> = _dailyGoal

    /** False until the first-run welcome tour is finished or skipped. */
    private val _onboardingDone = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone

    fun setDailyGoal(v: Int) {
        _dailyGoal.value = v
        prefs.edit().putInt("daily_goal_min", v).apply()
    }

    fun setOnboardingDone(v: Boolean) {
        _onboardingDone.value = v
        prefs.edit().putBoolean("onboarding_done", v).apply()
    }

    /** Lesson prose size in sp (kit presets 15 / 17 / 19 / 22). */
    private val _readerTextSize = MutableStateFlow(prefs.getInt("reader_text_sp", 17))
    val readerTextSize: StateFlow<Int> = _readerTextSize

    /** Lesson line height as a multiple of the text size (kit presets 1.4 / 1.6 / 1.8). */
    private val _readerLineSpacing = MutableStateFlow(prefs.getFloat("reader_line_spacing", 1.6f))
    val readerLineSpacing: StateFlow<Float> = _readerLineSpacing

    fun setReaderTextSize(v: Int) {
        _readerTextSize.value = v
        prefs.edit().putInt("reader_text_sp", v).apply()
    }

    fun setReaderLineSpacing(v: Float) {
        _readerLineSpacing.value = v
        prefs.edit().putFloat("reader_line_spacing", v).apply()
    }

    fun setAccent(v: Accent) {
        _accent.value = v
        prefs.edit().putString("accent", v.name).apply()
    }

    fun setAmoled(v: Boolean) {
        _amoled.value = v
        prefs.edit().putBoolean("amoled", v).apply()
    }

    fun setCodeTheme(v: CodeTheme) {
        _codeTheme.value = v
        prefs.edit().putString("code_theme", v.name).apply()
    }

    fun setThemeMode(v: Int) {
        _themeMode.value = v
        prefs.edit().putInt("theme_mode", v).apply()
    }

    fun setDynamicColor(v: Boolean) {
        _dynamicColor.value = v
        prefs.edit().putBoolean("dynamic_color", v).apply()
    }

    fun setQuizSize(v: Int) {
        _quizSize.value = v
        prefs.edit().putInt("quiz_size", v).apply()
    }

    fun setCodeFontScale(v: Float) {
        _codeFontScale.value = v
        prefs.edit().putFloat("code_font", v).apply()
    }
}

/** State of one practice session. [order] holds indices into the topic's question list. */
class QuizSession(
    val topicId: String,
    val title: String,
    val order: List<Int>,
) {
    /** When the session started, for the summary's time metric. */
    val startedAt: Long = System.currentTimeMillis()
    var pos by mutableIntStateOf(0)
    var selected by mutableIntStateOf(-1)
    var revealed by mutableStateOf(false)
    var showHint by mutableStateOf(false)
    var correctCount by mutableIntStateOf(0)
    var answeredCount by mutableIntStateOf(0)
    var finished by mutableStateOf(false)
    /** position -> answered correctly */
    val results = mutableStateMapOf<Int, Boolean>()
    /** position -> chosen option */
    val choices = mutableStateMapOf<Int, Int>()

    fun goTo(newPos: Int) {
        pos = newPos.coerceIn(0, (order.size - 1).coerceAtLeast(0))
        selected = choices[pos] ?: -1
        revealed = results.containsKey(pos)
        showHint = false
    }
}

/** Builds practice sessions from the question bank and saved progress. Call off the main thread. */
object QuizFactory {
    fun build(
        topicId: String,
        mode: QuizMode,
        lesson: Int = -1,
        fromNumber: Int = 1,
        size: Int = 20,
        custom: List<Int> = emptyList(),
    ): QuizSession {
        val content = AppGraph.content
        val progress = AppGraph.progress
        val qs = content.questions(topicId)
        val states = progress.questionStates(topicId)
        val topicTitle = content.topic(topicId)?.title ?: topicId
        val order: List<Int> = when (mode) {
            QuizMode.CONTINUE -> {
                val first = qs.indexOfFirst { it.number !in states }.let { if (it < 0) 0 else it }
                (first until qs.size).toList()
            }
            QuizMode.RANDOM -> qs.indices.shuffled().take(size)
            QuizMode.LESSON -> {
                val idx = qs.indices.filter { qs[it].lesson == lesson }
                val firstNew = idx.indexOfFirst { qs[it].number !in states }.coerceAtLeast(0)
                idx.drop(firstNew) + idx.take(firstNew)
            }
            QuizMode.MISTAKES -> qs.indices.filter { states[qs[it].number]?.correct == false }.shuffled()
            QuizMode.UNANSWERED -> qs.indices.filter { qs[it].number !in states }.shuffled().take(size)
            QuizMode.BOOKMARKED -> {
                val marked = progress.bookmarkedQuestions(topicId)
                qs.indices.filter { qs[it].number in marked }
            }
            QuizMode.FROM_NUMBER -> {
                val start = qs.indexOfFirst { it.number == fromNumber }.coerceAtLeast(0)
                (start until qs.size).toList()
            }
            QuizMode.CUSTOM -> custom
        }
        val title = when (mode) {
            QuizMode.LESSON -> "$topicTitle · Lesson ${lesson + 1}"
            else -> "$topicTitle · ${mode.label}"
        }
        return QuizSession(topicId, title, order)
    }

    fun register(session: QuizSession): Long {
        val id = System.nanoTime()
        AppGraph.quizSessions[id] = session
        // keep memory bounded
        if (AppGraph.quizSessions.size > 12) {
            AppGraph.quizSessions.keys.sorted().take(AppGraph.quizSessions.size - 12).forEach { AppGraph.quizSessions.remove(it) }
        }
        return id
    }
}
