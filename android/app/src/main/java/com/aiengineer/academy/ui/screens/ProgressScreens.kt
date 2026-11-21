package com.aiengineer.academy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.data.Bookmark
import com.aiengineer.academy.data.DayStat
import com.aiengineer.academy.data.DayTopicDetail
import com.aiengineer.academy.data.QuizFactory
import com.aiengineer.academy.data.QuizSession
import com.aiengineer.academy.data.TopicSummary
import com.aiengineer.academy.ui.Navigator
import com.aiengineer.academy.ui.Screen
import com.aiengineer.academy.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private fun studyTime(seconds: Int): String {
    val m = seconds / 60
    return if (m < 60) "$m min" else "${m / 60}h ${m % 60}m"
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val ROW_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())

fun prettyDay(day: String): String = try {
    when (val d = LocalDate.parse(day)) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> d.format(DAY_FORMAT)
    }
} catch (e: Exception) {
    day
}

// ============================================================================ Progress tab

private data class ProgressData(
    val summaries: Map<String, TopicSummary> = emptyMap(),
    val streak: Int = 0,
    val days: List<DayStat> = emptyList(),
)

@Composable
fun ProgressScreen(nav: Navigator) {
    val content = AppGraph.content
    val data = rememberLoad(initial = ProgressData()) {
        val p = AppGraph.progress
        ProgressData(p.topicSummaries(), p.streak(), p.dayStats(365))
    }
    val answered = data.summaries.values.sumOf { it.answered }
    val correct = data.summaries.values.sumOf { it.correct }
    val lessonsDone = data.summaries.values.sumOf { it.lessonsDone }
    val totalStudy = data.days.sumOf { it.studySeconds }
    val started = content.topics().filter { t -> data.summaries[t.id]?.let { it.lessonsDone > 0 || it.answered > 0 } == true }

    AppScaffold(
        title = "Progress",
        actions = {
            AppDayNightSwitch()
            IconAction(R.drawable.ic_settings, "Settings") { nav.push(Screen.Settings) }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader("Study journal", "Your progress", "$lessonsDone of ${content.totalLessons} lessons · ${"%,d".format(answered)} questions answered")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(if (data.streak == 1) "1 day" else "${data.streak} days", "Streak", Modifier.weight(1f))
                    MetricCard(if (answered == 0) "—" else "${correct * 100 / answered}%", "Accuracy", Modifier.weight(1f))
                    MetricCard(formatMinutes(totalStudy), "Study time", Modifier.weight(1f))
                }
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This week", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("Minutes studied", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                WeekChart(data.days)
                Spacer(Modifier.height(28.dp))
                Text("By topic", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                if (started.isEmpty()) {
                    Text(
                        "Topics appear here once you open a lesson or answer a question.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    KitDivider()
                }
            }
            items(started, key = { it.id }) { t ->
                val s = data.summaries[t.id] ?: TopicSummary()
                LibraryRow(
                    t.title,
                    "${s.lessonsDone} / ${t.lessons.size} lessons" + if (s.answered > 0) " · ${(s.accuracy * 100).toInt()}% accuracy" else "",
                    leading = { TopicBadge(t, 44.dp) },
                ) { nav.push(Screen.TopicProgress(t.id)) }
                KitDivider()
            }
            item {
                LibraryRow("Study history", "Daily activity", leading = { TonalIconTile(R.drawable.ic_history) }) { nav.push(Screen.History) }
                KitDivider()
                LibraryRow("Bookmarks", "Saved lessons and questions", leading = { TonalIconTile(R.drawable.ic_bookmark_border) }) { nav.push(Screen.Bookmarks) }
                KitDivider()
            }
        }
    }
}

/** Monday-to-Sunday bars of minutes studied this week (kit "This week"). */
@Composable
private fun WeekChart(days: List<DayStat>) {
    val byDay = days.associateBy { it.day }
    val today = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    val week = (0..6).map { monday.plusDays(it.toLong()) }
    val minutes = week.map { (byDay[it.toString()]?.studySeconds ?: 0) / 60 }
    val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(10)
    val summary = week.zip(minutes).joinToString { (d, m) -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $m min" }
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Minutes studied this week: $summary" },
    ) {
        Row(
            Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEachIndexed { i, d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    if (minutes[i] > 0) Text("${minutes[i]}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val frac = minutes[i].toFloat() / max
                    Box(
                        Modifier
                            .width(18.dp)
                            .height((84 * frac).coerceAtLeast(6f).dp)
                            .background(
                                if (minutes[i] > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(5.dp),
                            ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MonoMeta,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ============================================================================ History

@Composable
fun HistoryScreen(nav: Navigator, onBack: () -> Unit) {
    val days = rememberLoad(initial = null as List<DayStat>?) { AppGraph.progress.dayStats(365) }
    AppScaffold(title = "History", onBack = onBack) { padding ->
        val list = days
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader("Study journal", "History")
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                StudyActivityStrip(list ?: emptyList())
                Spacer(Modifier.height(12.dp))
            }
            if (list == null) item { LoadingBox() }
            else if (list.isEmpty()) item {
                KitEmptyState(R.drawable.ic_history, "No study days yet.", "Open a lesson or answer a few questions. Each day you study appears here.", "Start learning") { nav.tab(Screen.Learn) }
            }
            else items(list, key = { it.day }) { d ->
                val label = runCatching { LocalDate.parse(d.day).format(ROW_DAY) }.getOrDefault(d.day)
                LibraryRow(
                    label,
                    listOfNotNull(
                        studyTime(d.studySeconds),
                        if (d.answered > 0) "${d.answered} answer${if (d.answered == 1) "" else "s"}" else null,
                        if (d.lessonsDone > 0) "${d.lessonsDone} lesson${if (d.lessonsDone == 1) "" else "s"} done" else null,
                    ).joinToString(" · "),
                    leading = { TonalIconTile(R.drawable.ic_history) },
                ) { nav.push(Screen.Day(d.day)) }
                KitDivider()
            }
        }
    }
}

// ============================================================================ Day detail

@Composable
fun DayScreen(day: String, nav: Navigator, onBack: () -> Unit) {
    val content = AppGraph.content
    val detail = rememberLoad(day, initial = null as List<DayTopicDetail>?) { AppGraph.progress.dayDetail(day) }
    val date = runCatching { LocalDate.parse(day) }.getOrNull()
    AppScaffold(title = "Day detail", onBack = onBack) { padding ->
        val list = detail ?: emptyList()
        val secs = list.sumOf { it.studySeconds }
        val answered = list.sumOf { it.answered }
        val correct = list.sumOf { it.correct }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader(
                    date?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "Day",
                    date?.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())) ?: day,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(studyTime(secs), "Study time", Modifier.weight(1f))
                    MetricCard("$answered", "Answered", Modifier.weight(1f))
                    MetricCard(if (answered == 0) "—" else "${correct * 100 / answered}%", "Accuracy", Modifier.weight(1f))
                }
                Spacer(Modifier.height(28.dp))
                Text("Study log", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
            }
            if (detail == null) item { LoadingBox() }
            list.forEach { d ->
                val topic = content.topic(d.topicId) ?: return@forEach
                item(key = "t-${d.topicId}") {
                    val parts = listOfNotNull(
                        if (d.lessonsOpened.isNotEmpty()) "${d.lessonsOpened.size} lesson${if (d.lessonsOpened.size == 1) "" else "s"} opened" else null,
                        if (d.answered > 0) "${d.answered} question${if (d.answered == 1) "" else "s"}" else null,
                        if (d.studySeconds >= 60) studyTime(d.studySeconds) else null,
                    )
                    LibraryRow(topic.title, parts.joinToString(" · ").ifBlank { "Studied" }, leading = { TopicBadge(topic, 44.dp) }) {
                        nav.push(Screen.TopicDetail(topic.id))
                    }
                    KitDivider()
                }
                (d.lessonsOpened + d.lessonsDone).sorted().forEach { li ->
                    item(key = "l-${d.topicId}-$li") {
                        LibraryRow(
                            topic.lessons.getOrNull(li)?.title ?: "Lesson ${li + 1}",
                            if (li in d.lessonsDone) "Completed lesson" else "Opened lesson",
                            leading = { TonalIconTile(if (li in d.lessonsDone) R.drawable.ic_check else R.drawable.ic_book) },
                        ) { nav.push(Screen.LessonView(topic.id, li)) }
                        KitDivider()
                    }
                }
            }
        }
    }
}

// ============================================================================ Bookmarks

private data class BookmarkItem(val bookmark: Bookmark, val title: String, val subtitle: String, val questionIndex: Int = -1)

@Composable
fun BookmarksScreen(nav: Navigator, onBack: () -> Unit) {
    val content = AppGraph.content
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val items = rememberLoad(initial = null as List<BookmarkItem>?) {
        val all = AppGraph.progress.bookmarks()
        val questionsByTopic = all.filter { it.kind == "q" }.map { it.topicId }.distinct().associateWith { content.questions(it) }
        all.mapNotNull { b ->
            val topic = content.topic(b.topicId) ?: return@mapNotNull null
            if (b.kind == "l") {
                BookmarkItem(b, topic.lessons.getOrNull(b.ref)?.title ?: "Lesson ${b.ref + 1}", "Lesson · ${topic.title}")
            } else {
                val qs = questionsByTopic[b.topicId].orEmpty()
                val idx = qs.indexOfFirst { it.number == b.ref }
                val text = qs.getOrNull(idx)?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(100) ?: "Question ${b.ref}"
                BookmarkItem(b, text, "Question ${b.ref} · ${topic.title}", idx)
            }
        }
    }
    AppScaffold(title = "Bookmarks", onBack = onBack) { padding ->
        val list = items
        val shown = list?.filter { filter == 0 || (filter == 1 && it.bookmark.kind == "l") || (filter == 2 && it.bookmark.kind == "q") }
        if (list != null && list.isEmpty()) {
            Column(Modifier.padding(padding).padding(horizontal = 20.dp)) {
                KitEmptyState(R.drawable.ic_bookmark_border, "Save something for later.", "Tap the bookmark in a lesson or question to find it here.", "Browse lessons") { nav.tab(Screen.Learn) }
            }
            return@AppScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                ScreenHeader("Your collection", "Bookmarks")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Lessons", "Questions").forEachIndexed { i, label ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (shown == null) item { LoadingBox() }
            else if (shown.isEmpty()) item {
                Text("Nothing saved in this filter yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            }
            else items(shown, key = { "${it.bookmark.kind}-${it.bookmark.topicId}-${it.bookmark.ref}" }) { item ->
                val b = item.bookmark
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        LibraryRow(item.title, item.subtitle, leading = { TonalIconTile(if (b.kind == "l") R.drawable.ic_book else R.drawable.ic_quiz) }) {
                            if (b.kind == "l") nav.push(Screen.LessonView(b.topicId, b.ref))
                            else if (item.questionIndex >= 0) {
                                val s = QuizSession(b.topicId, "${content.topic(b.topicId)?.title ?: ""} · Bookmark", listOf(item.questionIndex))
                                nav.push(Screen.Quiz(QuizFactory.register(s)))
                            }
                        }
                    }
                    IconButton(onClick = { AppGraph.progress.toggleBookmark(b.kind, b.topicId, b.ref) }) {
                        Icon(painterResource(R.drawable.ic_bookmark), contentDescription = "Remove bookmark", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                KitDivider()
            }
            if (list?.any { it.bookmark.kind == "q" } == true) item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { scope.launch { nav.push(Screen.QuizSetup(list.first { it.bookmark.kind == "q" }.bookmark.topicId, com.aiengineer.academy.data.QuizMode.BOOKMARKED)) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium,
                ) { Text("Practise bookmarked questions →") }
            }
        }
    }
}
