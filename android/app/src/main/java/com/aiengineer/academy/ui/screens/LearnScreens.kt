package com.aiengineer.academy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import com.aiengineer.academy.ui.components.MdText
import com.aiengineer.academy.ui.components.ChoiceSheet
import com.aiengineer.academy.ui.components.ScreenHeader
import com.aiengineer.academy.ui.components.KitDivider
import com.aiengineer.academy.ui.components.KitProgressBar
import com.aiengineer.academy.ui.components.MonoMeta
import com.aiengineer.academy.ui.components.ContinueHeroCard
import com.aiengineer.academy.ui.components.KitEmptyState
import androidx.compose.foundation.lazy.items
import com.aiengineer.academy.data.Topic
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import com.aiengineer.academy.ui.components.Overline
import com.aiengineer.academy.ui.components.TonalIconTile
import com.aiengineer.academy.ui.components.LibraryRow
import com.aiengineer.academy.ui.theme.topicTone
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.data.Block
import com.aiengineer.academy.data.Lesson
import com.aiengineer.academy.data.QuizFactory
import com.aiengineer.academy.data.QuizMode
import com.aiengineer.academy.data.TopicSummary
import com.aiengineer.academy.ui.Navigator
import com.aiengineer.academy.ui.Screen
import com.aiengineer.academy.ui.components.AppScaffold
import com.aiengineer.academy.ui.components.BlockView
import com.aiengineer.academy.ui.components.CodeBlock
import com.aiengineer.academy.ui.components.EmptyState
import com.aiengineer.academy.ui.components.IconAction
import com.aiengineer.academy.ui.components.LinearProgress
import com.aiengineer.academy.ui.components.LoadingBox
import com.aiengineer.academy.ui.components.ProgressRing
import com.aiengineer.academy.ui.components.SectionTitle
import com.aiengineer.academy.ui.components.TopicBadge
import com.aiengineer.academy.ui.components.rememberLoad
import com.aiengineer.academy.ui.theme.LocalExtraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================ Learn tab: full table of contents

private enum class LibraryFilter(val label: String) { All("All topics"), InProgress("In progress"), Completed("Completed") }

@Composable
fun LearnScreen(nav: Navigator) {
    val content = AppGraph.content
    val topics = content.topics()
    val summaries = rememberLoad(initial = emptyMap<String, TopicSummary>()) { AppGraph.progress.topicSummaries() }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    fun isComplete(t: Topic) = t.lessons.isNotEmpty() && (summaries[t.id]?.lessonsDone ?: 0) >= t.lessons.size
    fun isStarted(t: Topic) = (summaries[t.id]?.let { it.lessonsDone > 0 || it.answered > 0 } ?: false)
    val shown = when (filter) {
        LibraryFilter.All -> topics
        LibraryFilter.InProgress -> topics.filter { isStarted(it) && !isComplete(it) }
        LibraryFilter.Completed -> topics.filter { isComplete(it) }
    }

    AppScaffold(
        title = "Learn",
        actions = {
            AppDayNightSwitch()
            IconAction(R.drawable.ic_settings, "Settings") { nav.push(Screen.Settings) }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                Overline("Course library")
                Spacer(Modifier.height(8.dp))
                Text("Learn", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${topics.size} topics · ${content.totalLessons} lessons",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LibraryRow("Search lessons and concepts", null, leading = { TonalIconTile(R.drawable.ic_search) }) { nav.push(Screen.Search) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                LibraryRow("Bookmarks", "Saved lessons and questions", leading = { TonalIconTile(R.drawable.ic_bookmark_border) }) { nav.push(Screen.Bookmarks) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (shown.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (filter == LibraryFilter.Completed) "No completed topics yet" else "No topics in progress yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Open any lesson to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { filter = LibraryFilter.All }) { Text("Show all topics") }
                    }
                }
            }
            items(shown, key = { it.id }) { topic ->
                val s = summaries[topic.id] ?: TopicSummary()
                val lessons = topic.lessons.size
                val progressText = when {
                    isComplete(topic) -> "All $lessons lessons complete"
                    s.lessonsDone > 0 -> "${s.lessonsDone} of $lessons lessons complete"
                    else -> "$lessons lessons"
                }
                LibraryRow(
                    topic.title,
                    "$progressText · ${"%,d".format(topic.questionCount)} questions",
                    leading = { TopicBadge(topic, 44.dp) },
                ) { nav.push(Screen.TopicDetail(topic.id)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// ============================================================================ Topic overview

private data class TopicData(val done: Set<Int> = emptySet(), val summary: TopicSummary = TopicSummary())

@Composable
private fun LessonStateTile(done: Boolean, size: Int = 44) {
    val extra = LocalExtraColors.current
    Box(
        Modifier.size(size.dp).background(if (done) extra.successContainer else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape((size * 0.3f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (done) R.drawable.ic_check else R.drawable.ic_book),
            contentDescription = if (done) "Completed" else null,
            tint = if (done) extra.onSuccessContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
fun TopicScreen(topicId: String, nav: Navigator, onBack: () -> Unit) {
    val content = AppGraph.content
    val topic = content.topic(topicId) ?: return KitEmptyState(R.drawable.ic_info, "Topic not found.", "This topic is unavailable.", "Back", Modifier.padding(horizontal = 20.dp), onAction = onBack)
    val topicIndex = content.topics().indexOf(topic)
    val data = rememberLoad(topicId, initial = TopicData()) {
        TopicData(AppGraph.progress.completedLessons(topicId), AppGraph.progress.topicSummaries()[topicId] ?: TopicSummary())
    }
    val nextLesson = (0 until topic.lessons.size).firstOrNull { it !in data.done } ?: 0
    val total = topic.lessons.size

    AppScaffold(title = "Topic overview", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ContinueHeroCard(
                    "Topic %02d / %d".format(topicIndex + 1, content.topics().size),
                    topic.title,
                    "$total lessons · ${"%,d".format(topic.questionCount)} questions",
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { nav.push(Screen.LessonView(topic.id, nextLesson)) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                    Text(
                        when {
                            data.done.isEmpty() -> "Start lesson 1 →"
                            data.done.size >= total -> "Review lesson 1 →"
                            else -> "Continue: lesson ${nextLesson + 1} →"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Course progress", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${data.done.size} / $total complete", style = MonoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                KitProgressBar(if (total == 0) 0f else data.done.size.toFloat() / total)
                Spacer(Modifier.height(16.dp))
                LibraryRow("Practice this topic", "${data.summary.answered} of ${"%,d".format(topic.questionCount)} answered" +
                    if (data.summary.answered > 0) " · ${(data.summary.accuracy * 100).toInt()}% correct" else "",
                    leading = { TonalIconTile(R.drawable.ic_quiz) }) { nav.push(Screen.QuizSetup(topic.id)) }
                KitDivider()
                LibraryRow("Key concepts", "${topic.glossaryCount} terms · list and flashcards", leading = { TonalIconTile(R.drawable.ic_lightbulb) }) { nav.push(Screen.Glossary(topic.id)) }
                KitDivider()
                LibraryRow("Question bank", "Browse all questions with filters", leading = { TonalIconTile(R.drawable.ic_list) }) { nav.push(Screen.QuestionBank(topic.id)) }
                KitDivider()
                LibraryRow("Topic progress", "Lesson-by-lesson breakdown", leading = { TonalIconTile(R.drawable.ic_chart) }) { nav.push(Screen.TopicProgress(topic.id)) }
                KitDivider()
                Spacer(Modifier.height(20.dp))
                Text("Lessons", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(topic.lessons) { li, lesson ->
                val done = li in data.done
                LibraryRow(
                    "${li + 1}. ${lesson.title}",
                    "${lesson.minutes} min · ${lesson.sections.size} sections · ${lesson.questionCount} questions",
                    leading = { LessonStateTile(done) },
                ) { nav.push(Screen.LessonView(topic.id, li)) }
                KitDivider()
            }
        }
    }
}

// ============================================================================ Topic progress

private data class TopicProgressData(
    val done: Set<Int> = emptySet(),
    val opened: Set<Int> = emptySet(),
    val perLesson: Map<Int, Pair<Int, Int>> = emptyMap(), // lesson -> (answered, correct)
)

@Composable
fun TopicProgressScreen(topicId: String, nav: Navigator, onBack: () -> Unit) {
    val topic = AppGraph.content.topic(topicId) ?: return KitEmptyState(R.drawable.ic_info, "Topic not found.", "This topic is unavailable.", "Back", Modifier.padding(horizontal = 20.dp), onAction = onBack)
    val data = rememberLoad(topicId, initial = TopicProgressData()) {
        val qs = AppGraph.content.questions(topicId)
        val states = AppGraph.progress.questionStates(topicId)
        val per = HashMap<Int, Pair<Int, Int>>()
        qs.forEach { q -> states[q.number]?.let { st -> val (a, c) = per[q.lesson] ?: (0 to 0); per[q.lesson] = (a + 1) to (c + if (st.correct) 1 else 0) } }
        TopicProgressData(AppGraph.progress.completedLessons(topicId), AppGraph.progress.openedLessons(topicId), per)
    }
    val total = topic.lessons.size
    AppScaffold(title = "Topic progress", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader("Topic progress", topic.title, "${data.done.size} of $total lessons complete")
                KitProgressBar(if (total == 0) 0f else data.done.size.toFloat() / total)
                Spacer(Modifier.height(24.dp))
                Text("Lesson breakdown", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(topic.lessons) { li, lesson ->
                val (answered, correct) = data.perLesson[li] ?: (0 to 0)
                val state = when {
                    li in data.done -> "Completed"
                    li in data.opened || answered > 0 -> "In progress"
                    else -> "Unopened"
                }
                LibraryRow(
                    lesson.title,
                    state + if (answered > 0) " · $correct/$answered correct" else "",
                    leading = { LessonStateTile(li in data.done) },
                ) { nav.push(Screen.LessonView(topic.id, li)) }
                KitDivider()
            }
        }
    }
}

// ============================================================================ Lesson reader

@Composable
fun LessonScreen(topicId: String, index: Int, section: Int, nav: Navigator, onBack: () -> Unit) {
    val content = AppGraph.content
    val topic = content.topic(topicId) ?: return KitEmptyState(R.drawable.ic_info, "Lesson not found.", "This lesson is unavailable.", "Back", Modifier.padding(horizontal = 20.dp), onAction = onBack)
    val lessons = rememberLoad(topicId, initial = null as List<Lesson>?) { content.lessons(topicId) }
    val done = rememberLoad(topicId, initial = emptySet<Int>()) { AppGraph.progress.completedLessons(topicId) }
    val bookmarked = rememberLoad(topicId, index, initial = false) { AppGraph.progress.isBookmarked("l", topicId, index) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showIndex by remember { mutableStateOf(false) }

    LaunchedEffect(topicId, index) { AppGraph.progress.openLesson(topicId, index) }
    DisposableEffect(topicId, index) {
        val start = System.currentTimeMillis()
        onDispose {
            val secs = ((System.currentTimeMillis() - start) / 1000).toInt()
            if (secs >= 5) AppGraph.progress.addStudySeconds(topicId, secs)
        }
    }

    val lesson = lessons?.getOrNull(index)
    val headingIdx = remember(lesson) { lesson?.blocks?.mapIndexedNotNull { i, b -> if (b is Block.Heading) i else null } ?: emptyList() }
    LaunchedEffect(lesson, section) {
        if (lesson != null && section in headingIdx.indices) listState.scrollToItem(headingIdx[section] + 1)
    }
    val isDone = index in done
    val last = index >= topic.lessons.size - 1

    AppScaffold(
        title = "Lesson reader",
        onBack = onBack,
        actions = {
            IconAction(if (bookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border, if (bookmarked) "Remove bookmark" else "Bookmark lesson") {
                AppGraph.progress.toggleBookmark("l", topicId, index)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { if (index > 0) nav.replace(Screen.LessonView(topicId, index - 1)) },
                        enabled = index > 0, modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("Previous", maxLines = 1) }
                    if (isDone) {
                        FilledTonalButton(
                            onClick = { AppGraph.progress.setLessonComplete(topicId, index, false) },
                            modifier = Modifier.weight(1.2f).height(52.dp), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Completed ✓", maxLines = 1) }
                    } else {
                        Button(
                            onClick = { AppGraph.progress.setLessonComplete(topicId, index, true) },
                            modifier = Modifier.weight(1.2f).height(52.dp), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Mark complete", maxLines = 1) }
                    }
                    OutlinedButton(
                        onClick = { if (last) nav.replace(Screen.TopicDetail(topicId)) else nav.replace(Screen.LessonView(topicId, index + 1)) },
                        modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text(if (last) "Overview" else "Next", maxLines = 1) }
                }
            }
        },
    ) { padding ->
        if (lesson == null) {
            LoadingBox(Modifier.padding(padding))
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 4.dp)) {
                        Overline("${topic.title} / Lesson %02d".format(index + 1))
                        Spacer(Modifier.height(8.dp))
                        Text(lesson.title, style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(6.dp))
                        Text("${lesson.minutes} min read", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (headingIdx.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            LibraryRow("In this lesson", "${headingIdx.size} sections", leading = { TonalIconTile(R.drawable.ic_toc) }) { showIndex = true }
                            KitDivider()
                        }
                    }
                }
                itemsIndexed(lesson.blocks) { _, block -> BlockView(block) }
                item {
                    Column(Modifier.padding(top = 24.dp)) {
                        KitDivider()
                        Spacer(Modifier.height(20.dp))
                        Overline("Check your understanding")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${topic.lessons[index].questionCount} practice questions are linked to this lesson.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val s = withContext(Dispatchers.IO) { QuizFactory.build(topicId, QuizMode.LESSON, lesson = index) }
                                    if (s.order.isNotEmpty()) nav.push(Screen.Quiz(QuizFactory.register(s)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("Practice this lesson →") }
                    }
                }
            }
        }
    }

    if (showIndex && lesson != null) {
        // Lesson index sheet: sections of this lesson, then the other lessons in the topic.
        data class IndexItem(val title: String, val supporting: String, val blockIndex: Int, val lesson: Int)
        val items = headingIdx.map { bi -> IndexItem((lesson.blocks[bi] as Block.Heading).text, "Jump to section", bi, index) } +
            topic.lessons.mapIndexedNotNull { li, l -> if (li == index) null else IndexItem("${li + 1}. ${l.title}", "Open lesson", -1, li) }
        ChoiceSheet(
            title = "In this lesson",
            choices = items,
            label = { it.title },
            supporting = { it.supporting },
            leading = { TonalIconTile(if (it.blockIndex >= 0) R.drawable.ic_toc else R.drawable.ic_book, 40) },
            onDismiss = { showIndex = false },
        ) { item ->
            showIndex = false
            if (item.blockIndex >= 0) scope.launch { listState.animateScrollToItem(item.blockIndex + 1) }
            else nav.replace(Screen.LessonView(topicId, item.lesson))
        }
    }
}

// ============================================================================ Glossary

private data class Concept(val topicId: String, val entry: com.aiengineer.academy.data.GlossaryEntry)

private fun loadConcepts(topicId: String): List<Concept> {
    val content = AppGraph.content
    val topics = if (topicId.isBlank()) content.topics() else listOfNotNull(content.topic(topicId))
    return topics.flatMap { t -> content.glossary(t.id).map { Concept(t.id, it) } }
        .let { list -> if (topicId.isBlank()) list.sortedBy { c -> c.entry.term.lowercase().trimStart { !it.isLetterOrDigit() } } else list }
}

@Composable
fun GlossaryScreen(topicId: String, nav: Navigator, onBack: () -> Unit) {
    val content = AppGraph.content
    var topicFilter by rememberSaveable { mutableStateOf(topicId) }
    val concepts = rememberLoad(topicFilter, initial = null as List<Concept>?) { loadConcepts(topicFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf("") }
    var pickTopic by remember { mutableStateOf(false) }
    val filterTopic = content.topic(topicFilter)

    AppScaffold(title = "Glossary", onBack = onBack) { padding ->
        val list = concepts
        val filtered = list?.filter { query.isBlank() || it.entry.term.contains(query, true) || it.entry.definition.contains(query, true) }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader("Reference", "Glossary", "${"%,d".format(list?.size ?: (filterTopic?.glossaryCount ?: content.topics().sumOf { it.glossaryCount }))} key concepts")
                Button(
                    onClick = { nav.push(Screen.Flashcards(topicFilter)) },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium,
                ) { Text("Study flashcards", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search concepts") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = topicFilter.isBlank(), onClick = { topicFilter = "" }, label = { Text("All topics") })
                    FilterChip(selected = topicFilter.isNotBlank(), onClick = { pickTopic = true }, label = { Text(filterTopic?.title ?: "Choose topic ▾") })
                }
                Spacer(Modifier.height(16.dp))
                Text("Concepts", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
            }
            if (list == null) item { LoadingBox() }
            else if (filtered.isNullOrEmpty()) item {
                KitEmptyState(R.drawable.ic_search, "No matches found.", "Try a shorter term or choose all topics.", "Clear search") { query = ""; topicFilter = "" }
            }
            else items(filtered, key = { "${it.topicId}/${it.entry.term}" }) { c ->
                val e = c.entry
                val key = "${c.topicId}/${e.term}"
                val t = content.topic(c.topicId)
                Column {
                    LibraryRow(e.term, t?.title, leading = { TonalIconTile(R.drawable.ic_copy) }) { expanded = if (expanded == key) "" else key }
                    AnimatedVisibility(expanded == key) {
                        Column(Modifier.padding(start = 58.dp, bottom = 12.dp)) {
                            MdText(e.definition, style = MaterialTheme.typography.bodyLarge)
                            if (e.example.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                MdText(e.example, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (e.code.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                CodeBlock(e.code)
                            }
                            TextButton(onClick = { nav.push(Screen.LessonView(c.topicId, e.lesson)) }, contentPadding = PaddingValues(0.dp)) {
                                Text("Open lesson ${e.lesson + 1}: ${t?.lessons?.getOrNull(e.lesson)?.title ?: ""} →", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    KitDivider()
                }
            }
        }
    }
    if (pickTopic) {
        ChoiceSheet(
            title = "Topic",
            choices = content.topics(),
            label = { it.title },
            supporting = { "${it.glossaryCount} concepts" },
            leading = { TopicBadge(it, 40.dp) },
            selected = { it.id == topicFilter },
            onDismiss = { pickTopic = false },
        ) { topicFilter = it.id; pickTopic = false }
    }
}

// ============================================================================ Flashcards

@Composable
fun FlashcardsScreen(topicId: String, start: Int, onBack: () -> Unit) {
    val content = AppGraph.content
    val concepts = rememberLoad(topicId, initial = null as List<Concept>?) { loadConcepts(topicId) }
    var order by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var pos by rememberSaveable { mutableIntStateOf(start) }
    var revealed by rememberSaveable { mutableStateOf(false) }

    AppScaffold(title = "Flashcards", onBack = onBack) { padding ->
        val list = concepts
        if (list == null) {
            LoadingBox(Modifier.padding(padding)); return@AppScaffold
        }
        if (list.isEmpty()) {
            KitEmptyState(R.drawable.ic_copy, "No cards yet.", "This topic has no key concepts.", "Back", Modifier.padding(padding).padding(horizontal = 20.dp), onBack); return@AppScaffold
        }
        if (order.size != list.size) order = list.indices.toList()
        val i = pos.coerceIn(0, list.size - 1)
        val c = list[order[i]]
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Overline(content.topic(topicId)?.title ?: "All topics")
            Spacer(Modifier.height(8.dp))
            Text("Flashcards", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text("Card ${"%,d".format(i + 1)} of ${"%,d".format(list.size)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            KitProgressBar((i + 1f) / list.size)
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = { revealed = !revealed },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite; stateDescription = if (revealed) "Definition side" else "Concept side" },
            ) {
                AnimatedContent(targetState = revealed, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "card") { back ->
                    Column(Modifier.padding(22.dp)) {
                        Overline(if (back) "Definition" else "Concept", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(20.dp))
                        Icon(
                            painterResource(R.drawable.ic_copy), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp).align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(c.entry.term, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(12.dp))
                        if (back) {
                            MdText(c.entry.definition, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            Text(
                                "From ${content.topic(c.topicId)?.title ?: ""}. Can you explain it before you flip?",
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { revealed = !revealed }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                Text(if (revealed) "Show concept" else "Reveal definition", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { pos = (i + 1) % list.size; revealed = false },
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium,
            ) { Text(if (i == list.size - 1) "Start again ↺" else "Next card →", style = MaterialTheme.typography.titleMedium) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { pos = (i - 1 + list.size) % list.size; revealed = false }) { Text("← Previous") }
                TextButton(onClick = { order = list.indices.shuffled(); pos = 0; revealed = false }) { Text("Shuffle") }
            }
        }
    }
}
