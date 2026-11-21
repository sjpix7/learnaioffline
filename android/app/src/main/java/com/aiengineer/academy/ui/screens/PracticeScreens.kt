package com.aiengineer.academy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.data.QState
import com.aiengineer.academy.data.Question
import com.aiengineer.academy.data.QuizFactory
import com.aiengineer.academy.data.QuizMode
import com.aiengineer.academy.data.QuizSession
import com.aiengineer.academy.data.Topic
import com.aiengineer.academy.ui.Navigator
import com.aiengineer.academy.ui.Screen
import com.aiengineer.academy.ui.components.*
import com.aiengineer.academy.ui.theme.LocalExtraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Practice modes shown on the Practice tab and in Quiz setup (icon, one-line explanation). */
private data class ModeInfo(val mode: QuizMode, val title: String, val description: String, val icon: Int)

private val MODES = listOf(
    ModeInfo(QuizMode.CONTINUE, "Continue in order", "Resume your question sequence", R.drawable.ic_arrow_forward),
    ModeInfo(QuizMode.RANDOM, "Random set", "Shuffle questions within a topic", R.drawable.ic_shuffle),
    ModeInfo(QuizMode.LESSON, "By lesson", "Focus on one lesson", R.drawable.ic_book),
    ModeInfo(QuizMode.MISTAKES, "Review mistakes", "Revisit incorrect answers", R.drawable.ic_replay),
    ModeInfo(QuizMode.UNANSWERED, "Unanswered", "Try something new", R.drawable.ic_quiz),
    ModeInfo(QuizMode.BOOKMARKED, "Bookmarked", "Use your saved questions", R.drawable.ic_bookmark_border),
    ModeInfo(QuizMode.FROM_NUMBER, "Start from a number", "Jump to any question number", R.drawable.ic_list),
)

private fun defaultTopicId(): String =
    AppGraph.progress.lastPracticedTopic() ?: AppGraph.progress.lastOpenedLesson()?.first ?: AppGraph.content.topics().first().id

// ============================================================================ Practice tab

@Composable
fun PracticeScreen(nav: Navigator) {
    val content = AppGraph.content
    val size by AppGraph.settings.quizSize.collectAsState()
    AppScaffold(
        title = "Practice",
        actions = {
            AppDayNightSwitch()
            IconAction(R.drawable.ic_settings, "Settings") { nav.push(Screen.Settings) }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader("Question studio", "Practice", "${"%,d".format(content.totalQuestions)} questions across ${content.topics().size} topics.")
                ContinueHeroCard("Quick set", "${numberWord(size)} questions.", "Practice a mixed set in one topic.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { nav.push(Screen.QuizSetup("", QuizMode.RANDOM)) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                    Text("Set up practice →", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(28.dp))
                Text("Practice modes", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
            }
            items(MODES, key = { it.mode }) { m ->
                LibraryRow(m.title, m.description, leading = { TonalIconTile(m.icon) }) { nav.push(Screen.QuizSetup("", m.mode)) }
                KitDivider()
            }
            item {
                LibraryRow("Question bank", "Browse every question with filters", leading = { TonalIconTile(R.drawable.ic_list) }) {
                    nav.push(Screen.QuestionBank(""))
                }
                KitDivider()
            }
        }
    }
}

private fun numberWord(n: Int): String = when (n) {
    5 -> "Five"; 10 -> "Ten"; 20 -> "Twenty"; 50 -> "Fifty"; 100 -> "A hundred"; else -> "$n"
}

// ============================================================================ Quiz setup

private data class SetupData(val questions: List<Question>, val states: Map<Int, QState>, val bookmarked: Set<Int>)

@Composable
fun QuizSetupScreen(topicArg: String, modeArg: QuizMode?, nav: Navigator, onBack: () -> Unit) {
    val content = AppGraph.content
    var topicId by rememberSaveable { mutableStateOf(topicArg) }
    LaunchedEffect(Unit) { if (topicId.isBlank()) topicId = withContext(Dispatchers.IO) { defaultTopicId() } }
    var mode by rememberSaveable { mutableStateOf(modeArg ?: QuizMode.RANDOM) }
    var lesson by rememberSaveable { mutableIntStateOf(0) }
    var fromText by rememberSaveable { mutableStateOf("") }
    val size by AppGraph.settings.quizSize.collectAsState()
    var sheet by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val topic = content.topic(topicId)
    val data = rememberLoad(topicId, initial = null as SetupData?) {
        if (topicId.isBlank()) null
        else SetupData(content.questions(topicId), AppGraph.progress.questionStates(topicId), AppGraph.progress.bookmarkedQuestions(topicId))
    }
    val info = MODES.first { it.mode == mode }

    // How many questions the chosen mode will contain, so the start button can say it.
    val poolSize: Int? = data?.let { d ->
        when (mode) {
            QuizMode.RANDOM -> minOf(size, d.questions.size)
            QuizMode.UNANSWERED -> minOf(size, d.questions.count { it.number !in d.states })
            QuizMode.MISTAKES -> d.states.values.count { !it.correct }
            QuizMode.BOOKMARKED -> d.bookmarked.size
            QuizMode.LESSON -> d.questions.count { it.lesson == lesson }
            QuizMode.CONTINUE -> d.questions.size - (d.questions.indexOfFirst { it.number !in d.states }.takeIf { it >= 0 } ?: 0)
            QuizMode.FROM_NUMBER -> fromText.toIntOrNull()?.let { n -> d.questions.count { it.number >= n } } ?: d.questions.size
            QuizMode.CUSTOM -> 0
        }
    }

    fun start() {
        val t = topic ?: return
        val from = fromText.toIntOrNull() ?: 1
        if (mode == QuizMode.FROM_NUMBER && (from < 1 || from > t.questionCount)) {
            message = "Enter a question number between 1 and ${t.questionCount}."; return
        }
        scope.launch {
            val s = withContext(Dispatchers.IO) { QuizFactory.build(t.id, mode, lesson, from, size) }
            if (s.order.isEmpty()) message = when (mode) {
                QuizMode.MISTAKES -> "No mistakes to review in ${t.title} yet. Nice!"
                QuizMode.BOOKMARKED -> "You haven't bookmarked any ${t.title} questions yet."
                QuizMode.UNANSWERED -> "You've answered every question in ${t.title}."
                else -> "No questions match this mode yet."
            }
            else nav.push(Screen.Quiz(QuizFactory.register(s)))
        }
    }

    AppScaffold(title = "Quiz setup", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            ScreenHeader("Practice", "Build your set")
            Text("Topic", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            if (topic != null) {
                SelectorCard(topic.title, "${"%,d".format(topic.questionCount)} questions", leading = { TopicBadge(topic, 44.dp) }) { sheet = "topic" }
            }
            Spacer(Modifier.height(20.dp))
            Text("Mode", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            SelectorCard(info.title, info.description, leading = { TonalIconTile(info.icon) }) { sheet = "mode" }
            if (mode == QuizMode.LESSON && topic != null) {
                Spacer(Modifier.height(12.dp))
                SelectorCard(
                    "Lesson ${lesson + 1}", topic.lessons.getOrNull(lesson)?.title ?: "",
                    leading = { TonalIconTile(R.drawable.ic_book) },
                ) { sheet = "lesson" }
            }
            if (mode == QuizMode.FROM_NUMBER) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { v -> fromText = v.filter { it.isDigit() }.take(5) },
                    label = { Text("Start at question #") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (mode == QuizMode.RANDOM || mode == QuizMode.UNANSWERED) {
                Spacer(Modifier.height(20.dp))
                Text("Questions per set", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20, 50, 100).forEach { n ->
                        FilterChip(selected = size == n, onClick = { AppGraph.settings.setQuizSize(n) }, label = { Text("$n") })
                    }
                }
            }
            data?.let { d ->
                val answered = d.states.size
                val wrong = d.states.values.count { !it.correct }
                Spacer(Modifier.height(16.dp))
                Text(
                    "$answered answered · $wrong to review · ${d.bookmarked.size} saved. Hints and explanations are available for every question.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { start() }, enabled = topic != null,
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    when (val n = poolSize) {
                        null -> "Start practice →"
                        0 -> "Nothing to practise here yet"
                        1 -> "Start 1 question →"
                        else -> "Start $n questions →"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (topic != null) {
                TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Reset answers for ${topic.title}", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (sheet) {
        "topic" -> ChoiceSheet(
            "Topic", content.topics(), label = { it.title }, supporting = { "${"%,d".format(it.questionCount)} questions" },
            leading = { TopicBadge(it, 40.dp) }, selected = { it.id == topicId }, onDismiss = { sheet = "" },
        ) { topicId = it.id; lesson = 0; sheet = "" }
        "mode" -> ChoiceSheet(
            "Mode", MODES, label = { it.title }, supporting = { it.description }, leading = { TonalIconTile(it.icon, 40) },
            selected = { it.mode == mode }, onDismiss = { sheet = "" },
        ) { mode = it.mode; sheet = "" }
        "lesson" -> if (topic != null) ChoiceSheet(
            "Lesson", topic.lessons.indices.toList(), label = { "${it + 1}. ${topic.lessons[it].title}" },
            supporting = { "${topic.lessons[it].questionCount} questions" }, selected = { it == lesson }, onDismiss = { sheet = "" },
        ) { lesson = it; sheet = "" }
    }
    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, text = { Text(m) })
    }
    if (confirmReset && topic != null) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset answers?") },
            text = { Text("This clears your saved answers for ${topic.title}. Lesson progress, bookmarks and history are kept.") },
            confirmButton = {
                TextButton(onClick = { AppGraph.progress.resetTopicQuestions(topic.id); confirmReset = false }) { Text("Reset answers", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Keep answers") } },
        )
    }
}

// ============================================================================ Quiz session

@Composable
fun QuizScreen(sessionId: Long, nav: Navigator, onBack: () -> Unit) {
    val session = AppGraph.quizSessions[sessionId]
        ?: return KitEmptyState(R.drawable.ic_quiz, "Session ended.", "Start a new practice set from the Practice tab.", "Back to practice", Modifier.padding(horizontal = 20.dp)) { nav.tab(Screen.Practice) }
    val content = AppGraph.content
    val questions = rememberLoad(session.topicId, initial = content.cachedQuestions(session.topicId)) { content.questions(session.topicId) }
    val topic = content.topic(session.topicId)

    DisposableEffect(sessionId) {
        val start = System.currentTimeMillis()
        onDispose {
            val secs = ((System.currentTimeMillis() - start) / 1000).toInt()
            if (secs >= 5) AppGraph.progress.addStudySeconds(session.topicId, secs)
        }
    }

    if (questions == null || topic == null) {
        LoadingBox(); return
    }
    if (session.finished) {
        QuizSummary(session, questions, nav, onBack); return
    }
    val q = questions[session.order[session.pos]]
    val bookmarked = rememberLoad(session.topicId, q.number, initial = false) { AppGraph.progress.isBookmarked("q", session.topicId, q.number) }
    val listState = rememberLazyListState()
    LaunchedEffect(session.pos) { listState.scrollToItem(0) }
    val extra = LocalExtraColors.current
    val monoOptions = q.kind in setOf("code-output", "api-behaviour", "calculation", "fill-code")
    val total = session.order.size
    val isLast = session.pos >= total - 1

    fun check() {
        val correct = session.selected == q.answer
        session.revealed = true
        if (!session.results.containsKey(session.pos)) {
            session.answeredCount++
            if (correct) session.correctCount++
        }
        session.results[session.pos] = correct
        session.choices[session.pos] = session.selected
        AppGraph.progress.recordAnswer(session.topicId, q.number, q.lesson, correct, session.selected)
    }

    AppScaffold(
        title = "Practice set",
        onBack = onBack,
        actions = {
            IconAction(if (bookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border, if (bookmarked) "Remove bookmark" else "Bookmark question") {
                AppGraph.progress.toggleBookmark("q", session.topicId, q.number)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (session.pos > 0) {
                        OutlinedButton(onClick = { session.goTo(session.pos - 1) }, modifier = Modifier.height(56.dp), shape = MaterialTheme.shapes.medium) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Previous question", modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!session.revealed) {
                        Button(onClick = { check() }, enabled = session.selected >= 0, modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.medium) {
                            Text("Check answer", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Button(
                            onClick = { if (!isLast) session.goTo(session.pos + 1) else session.finished = true },
                            modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.medium,
                        ) { Text(if (isLast) "See results →" else "Next question →", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline(topic.title, Modifier.weight(1f))
                    Overline("%02d / %02d".format(session.pos + 1, total))
                }
                Spacer(Modifier.height(10.dp))
                KitProgressBar((session.pos + 1f) / total)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Q${q.number} · ${kindLabel(q.kind)} · Lesson ${q.lesson + 1}",
                    style = MonoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { MdText(q.text, style = if (q.text.length > 140) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium) }
            q.code?.let { code -> item { CodeBlock(code, guessLang(code)) } }
            item {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    q.options.forEachIndexed { i, option ->
                        QuizOption(
                            letter = "ABCD"[i], text = option, mono = monoOptions,
                            selected = i == session.selected, revealed = session.revealed, isAnswer = i == q.answer,
                        ) { if (!session.revealed) session.selected = i }
                    }
                }
            }
            if (!session.revealed) {
                item {
                    OutlinedButton(
                        onClick = { session.showHint = !session.showHint },
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium,
                    ) { Text(if (session.showHint) "Hide hint" else "View hint", style = MaterialTheme.typography.titleMedium) }
                }
                if (session.showHint) item {
                    FeedbackCard("Hint") {
                        MdText(q.hint, style = MaterialTheme.typography.bodyLarge)
                        q.hintCode?.let { Spacer(Modifier.height(10.dp)); CodeBlock(it, guessLang(it)) }
                    }
                }
            } else {
                item {
                    val ok = session.results[session.pos] == true
                    FeedbackCard(if (ok) "Correct" else "Review answer · correct answer ${"ABCD"[q.answer]}", accent = if (ok) extra.success else extra.danger) {
                        MdText(q.explanation, style = MaterialTheme.typography.bodyLarge)
                        val ec = q.explanationCode ?: q.hintCode
                        if (ec != null && ec != q.code) { Spacer(Modifier.height(10.dp)); CodeBlock(ec, guessLang(ec)) }
                        TextButton(onClick = { nav.push(Screen.LessonView(session.topicId, q.lesson)) }, contentPadding = PaddingValues(0.dp)) {
                            Text("Open related lesson →", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Kit quiz option: letter box, wrapping answer, state icon. Selected = primary outline; submitted = success / danger. */
@Composable
private fun QuizOption(letter: Char, text: String, mono: Boolean, selected: Boolean, revealed: Boolean, isAnswer: Boolean, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    val cs = MaterialTheme.colorScheme
    val (container, border, content) = when {
        revealed && isAnswer -> Triple(extra.successContainer, extra.success, extra.onSuccessContainer)
        revealed && selected -> Triple(extra.dangerContainer, extra.danger, extra.onDangerContainer)
        selected -> Triple(cs.surfaceContainerLowest, cs.primary, cs.onSurface)
        else -> Triple(cs.surfaceContainerLowest, cs.outlineVariant, cs.onSurface)
    }
    val state = when {
        revealed && isAnswer -> "Correct answer"
        revealed && selected -> "Your answer, incorrect"
        selected -> "Selected"
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(if (selected || (revealed && isAnswer)) 2.dp else 1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = !revealed, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { if (state != null) contentDescription = "$letter. $text. $state" },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val filled = selected && !revealed
            Box(
                Modifier
                    .size(30.dp)
                    .then(if (filled) Modifier.background(cs.primary, RoundedCornerShape(8.dp)) else Modifier.border(1.5.dp, content, RoundedCornerShape(8.dp))),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    revealed && isAnswer -> Icon(painterResource(R.drawable.ic_check), null, tint = content, modifier = Modifier.size(18.dp))
                    revealed && selected -> Icon(painterResource(R.drawable.ic_close), null, tint = content, modifier = Modifier.size(18.dp))
                    else -> Text(letter.toString(), fontWeight = FontWeight.Bold, color = if (filled) cs.onPrimary else content)
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, fontFamily = if (mono) FontFamily.Monospace else null, color = content, modifier = Modifier.weight(1f))
        }
    }
}

/** Hint / feedback card: overline label, body and actions on the lowest surface with an outline. */
@Composable
private fun FeedbackCard(label: String, accent: androidx.compose.ui.graphics.Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, accent ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp)) {
            Overline(label, color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Picks a highlighting language from the snippet's content. */
fun guessLang(code: String): String {
    val c = code.trimStart()
    return when {
        c.startsWith("MATCH") || c.startsWith("MERGE") || c.startsWith("CREATE (") || c.startsWith("CALL db.") || c.startsWith("(") && c.contains(")-[") -> "cypher"
        c.startsWith("SELECT") || c.startsWith("CREATE TABLE") || c.startsWith("CREATE EXTENSION") || c.startsWith("WITH ") -> "sql"
        c.startsWith("{") || c.startsWith("[{") -> "json"
        c.startsWith("pip ") || c.startsWith("uv ") || c.startsWith("export ") || c.startsWith("ollama ") || c.startsWith("vllm ") ||
            c.startsWith("docker ") || c.startsWith("graphrag ") || c.startsWith("fastapi ") || c.startsWith("uvicorn ") ||
            c.startsWith("curl ") || c.startsWith("crewai ") || c.startsWith("adk ") || c.startsWith("langgraph ") || c.startsWith("npx ") ||
            c.startsWith("claude ") -> "bash"
        c.startsWith("//") || c.startsWith("import {") || c.startsWith("const ") -> "typescript"
        else -> "python"
    }
}

// ============================================================================ Summary

@Composable
private fun QuizSummary(session: QuizSession, questions: List<Question>, nav: Navigator, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val content = AppGraph.content
    val topic = content.topic(session.topicId)
    val total = session.answeredCount
    val pct = if (total == 0) 0 else session.correctCount * 100 / total
    val wrongPositions = session.results.filterValues { !it }.keys.sorted()
    val elapsed = remember { ((System.currentTimeMillis() - session.startedAt) / 1000).toInt() }

    AppScaffold(title = "Quiz summary", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader("Practice complete", "Set complete.")
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(150.dp).semantics(mergeDescendants = true) { contentDescription = "${session.correctCount} of $total correct" }, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { if (total == 0) 0f else session.correctCount.toFloat() / total },
                            modifier = Modifier.size(150.dp), strokeWidth = 12.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        Text("${session.correctCount} / $total", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(if (total == 0) "—" else "$pct%", "Accuracy", Modifier.weight(1f))
                    MetricCard("%d:%02d".format(elapsed / 60, elapsed % 60), "Time", Modifier.weight(1f))
                    MetricCard("${wrongPositions.size}", "Mistakes", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    when {
                        total == 0 -> "No answers yet. Try a few questions to see your results."
                        pct >= 80 -> "Excellent work. You're mastering this."
                        pct >= 50 -> "Good progress. Review the misses below."
                        else -> "Keep going. Study the linked lessons and try again."
                    },
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (wrongPositions.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("Review mistakes", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(wrongPositions.take(20)) { pos ->
                val q = questions[session.order[pos]]
                LibraryRow(
                    q.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(90) ?: "Question",
                    "${topic?.title ?: ""} · Question ${pos + 1}",
                    leading = {
                        val extra = LocalExtraColors.current
                        Box(Modifier.size(44.dp).background(extra.dangerContainer, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_close), null, tint = extra.onDangerContainer, modifier = Modifier.size(22.dp))
                        }
                    },
                ) { nav.push(Screen.LessonView(session.topicId, q.lesson)) }
                KitDivider()
            }
            item {
                Spacer(Modifier.height(16.dp))
                if (wrongPositions.isNotEmpty()) {
                    Button(onClick = {
                        val s = QuizSession(session.topicId, session.title.substringBefore(" · ") + " · Retry mistakes", wrongPositions.map { session.order[it] })
                        nav.replace(Screen.Quiz(QuizFactory.register(s)))
                    }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                        Text("Review ${wrongPositions.size} mistake${if (wrongPositions.size == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val s = withContext(Dispatchers.IO) { QuizFactory.build(session.topicId, QuizMode.RANDOM, size = AppGraph.settings.quizSize.value) }
                        nav.replace(Screen.Quiz(QuizFactory.register(s)))
                    }
                }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                    Text("New random set", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { nav.tab(Screen.Practice) }, modifier = Modifier.fillMaxWidth()) { Text("Back to practice") }
                Spacer(Modifier.height(8.dp))
                Text("Your answers are saved to your progress and today's history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================================================================ Question bank

private data class BankData(val questions: List<Question>, val states: Map<Int, QState>, val bookmarked: Set<Int>)

private val STATE_FILTERS = listOf("All states", "Unanswered", "Answered", "Correct", "Wrong", "Saved")

@Composable
fun QuestionBankScreen(topicArg: String, nav: Navigator, onBack: () -> Unit) {
    val content = AppGraph.content
    var topicId by rememberSaveable { mutableStateOf(topicArg) }
    LaunchedEffect(Unit) { if (topicId.isBlank()) topicId = withContext(Dispatchers.IO) { defaultTopicId() } }
    val topic = content.topic(topicId)
    val data = rememberLoad(topicId, initial = null as BankData?) {
        if (topicId.isBlank()) null
        else BankData(content.questions(topicId), AppGraph.progress.questionStates(topicId), AppGraph.progress.bookmarkedQuestions(topicId))
    }
    var stateFilter by rememberSaveable { mutableIntStateOf(0) }
    var lessonFilter by rememberSaveable { mutableIntStateOf(-1) }
    var kindFilter by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    AppScaffold(title = "Question bank", onBack = onBack) { padding ->
        val d = data
        val filtered = remember(d, stateFilter, lessonFilter, kindFilter) {
            d?.questions?.indices?.filter { i ->
                val q = d.questions[i]
                val st = d.states[q.number]
                val f = when (stateFilter) {
                    1 -> st == null
                    2 -> st != null
                    3 -> st?.correct == true
                    4 -> st != null && !st.correct
                    5 -> q.number in d.bookmarked
                    else -> true
                }
                f && (lessonFilter < 0 || q.lesson == lessonFilter) && (kindFilter.isBlank() || q.kind == kindFilter)
            } ?: emptyList()
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader("Practice", "Question bank")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = { sheet = "topic" }, label = { Text(topic?.title ?: "Topic") })
                    FilterChip(selected = lessonFilter >= 0, onClick = { sheet = "lesson" }, label = { Text(if (lessonFilter >= 0) "Lesson ${lessonFilter + 1}" else "Lesson") })
                    FilterChip(selected = kindFilter.isNotBlank(), onClick = { sheet = "kind" }, label = { Text(if (kindFilter.isNotBlank()) kindLabel(kindFilter) else "Type") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STATE_FILTERS.forEachIndexed { i, label -> FilterChip(selected = stateFilter == i, onClick = { stateFilter = i }, label = { Text(label) }) }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Questions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${"%,d".format(filtered.size)} shown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (d == null) item { LoadingBox() }
            else if (filtered.isEmpty()) item {
                KitEmptyState(R.drawable.ic_search, "No matches found.", "No questions match these filters.", "Clear filters") {
                    stateFilter = 0; lessonFilter = -1; kindFilter = ""
                }
            }
            else itemsIndexed(filtered, key = { _, i -> d.questions[i].number }) { pos, qi ->
                val q = d.questions[qi]
                val st = d.states[q.number]
                val extra = LocalExtraColors.current
                val status = when {
                    st == null -> ""
                    st.correct -> " · ✓ Correct"
                    else -> " · ✗ Wrong"
                }
                LibraryRow(
                    q.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(100) ?: q.text,
                    "${topic?.title ?: ""} · Q%04d · %s%s%s".format(q.number, kindLabel(q.kind), status, if (q.number in d.bookmarked) " · Saved" else ""),
                    leading = {
                        Box(
                            Modifier.size(44.dp).background(
                                when { st == null -> MaterialTheme.colorScheme.primaryContainer; st.correct -> extra.successContainer; else -> extra.dangerContainer },
                                RoundedCornerShape(13.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(when { st == null -> R.drawable.ic_quiz; st.correct -> R.drawable.ic_check; else -> R.drawable.ic_close }), null,
                                tint = when { st == null -> MaterialTheme.colorScheme.onPrimaryContainer; st.correct -> extra.onSuccessContainer; else -> extra.onDangerContainer },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                ) {
                    val s = QuizSession(topicId, "${topic?.title ?: ""} · Question bank", filtered.drop(pos))
                    nav.push(Screen.Quiz(QuizFactory.register(s)))
                }
                KitDivider()
            }
        }
    }
    when (sheet) {
        "topic" -> ChoiceSheet(
            "Topic", content.topics(), label = { it.title }, supporting = { "${"%,d".format(it.questionCount)} questions" },
            leading = { TopicBadge(it, 40.dp) }, selected = { it.id == topicId }, onDismiss = { sheet = "" },
        ) { topicId = it.id; lessonFilter = -1; kindFilter = ""; sheet = "" }
        "lesson" -> if (topic != null) ChoiceSheet(
            "Lesson", listOf(-1) + topic.lessons.indices.toList(),
            label = { if (it < 0) "All lessons" else "${it + 1}. ${topic.lessons[it].title}" },
            selected = { it == lessonFilter }, onDismiss = { sheet = "" },
        ) { lessonFilter = it; sheet = "" }
        "kind" -> {
            val kinds = listOf("") + (data?.questions?.map { it.kind }?.distinct()?.sorted() ?: emptyList())
            ChoiceSheet("Type", kinds, label = { if (it.isBlank()) "All types" else kindLabel(it) }, selected = { it == kindFilter }, onDismiss = { sheet = "" }) {
                kindFilter = it; sheet = ""
            }
        }
    }
}
