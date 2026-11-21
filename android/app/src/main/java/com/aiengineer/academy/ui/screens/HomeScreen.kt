package com.aiengineer.academy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.data.DayStat
import com.aiengineer.academy.data.TopicSummary
import com.aiengineer.academy.ui.Navigator
import com.aiengineer.academy.ui.Screen
import com.aiengineer.academy.ui.components.AppScaffold
import com.aiengineer.academy.ui.components.BrandWordmark
import com.aiengineer.academy.ui.components.CompactTopicCard
import com.aiengineer.academy.ui.components.ContinueHeroCard
import com.aiengineer.academy.ui.components.DailyGoal
import com.aiengineer.academy.ui.components.IconAction
import com.aiengineer.academy.ui.components.MonoMeta
import com.aiengineer.academy.ui.components.Overline
import com.aiengineer.academy.ui.components.StudyActivityStrip
import com.aiengineer.academy.ui.components.rememberLoad
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class HomeData(
    val summaries: Map<String, TopicSummary> = emptyMap(),
    val today: DayStat = DayStat(""),
    val streak: Int = 0,
    val last: Pair<String, Int>? = null,
    val days: List<DayStat> = emptyList(),
    val loaded: Boolean = false,
)

/** Suggested first topics for new learners (also fills "Your topics" until four have progress). */
private val STARTER_TOPICS = listOf("python", "fastapi", "llm_models", "embeddings")

private val DATE_OVERLINE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

@Composable
fun HomeScreen(nav: Navigator) {
    val content = AppGraph.content
    val topics = content.topics()
    val goal by AppGraph.settings.dailyGoal.collectAsState()
    val data = rememberLoad(initial = HomeData()) {
        val p = AppGraph.progress
        HomeData(p.topicSummaries(), p.todayStats(), p.streak(), p.lastOpenedLesson(), p.dayStats(28), loaded = true)
    }
    val returning = data.last != null

    // "Your topics": the last opened topic first, then others with progress, then the starter path.
    val yourTopics = buildList {
        data.last?.first?.let { add(it) }
        data.summaries.filter { (_, s) -> s.lessonsDone > 0 || s.answered > 0 }.keys.forEach { if (it !in this) add(it) }
        STARTER_TOPICS.forEach { if (it !in this) add(it) }
    }.mapNotNull { content.topic(it) }.take(4)

    AppScaffold(
        title = "LearnAIoffline",
        titleContent = { BrandWordmark() },
        actions = {
            AppDayNightSwitch()
            IconAction(R.drawable.ic_settings, "Settings") { nav.push(Screen.Settings) }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Overline(LocalDate.now().format(DATE_OVERLINE))
                Spacer(Modifier.height(8.dp))
                Text(if (returning) "Welcome back." else "Welcome.", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_flame), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            data.streak > 0 -> "${data.streak}-day study streak"
                            returning -> "Study today to start a new streak"
                            else -> "Your first lesson starts your streak"
                        },
                        style = MonoMeta,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                val last = data.last
                val t = last?.let { content.topic(it.first) }
                if (t != null && last.second in t.lessons.indices) {
                    val l = last.second
                    ContinueHeroCard("Continue learning", t.title, "Lesson ${l + 1} of ${t.lessons.size} · ${t.lessons[l].minutes} min")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { nav.push(Screen.LessonView(t.id, l)) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                        Text("Resume lesson →", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    val first = content.topic(STARTER_TOPICS.first()) ?: topics.first()
                    ContinueHeroCard("Start learning", first.title, "Lesson 1 of ${first.lessons.size} · ${first.lessons.firstOrNull()?.minutes ?: 3} min")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { nav.push(Screen.LessonView(first.id, 0)) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                        Text("Start learning →", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            item {
                DailyGoal(data.today.studySeconds, goal)
                Spacer(Modifier.height(28.dp))
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your topics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.tab(Screen.Learn) }) { Text("View all →") }
                }
                Spacer(Modifier.height(4.dp))
            }
            yourTopics.chunked(2).forEach { pair ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        pair.forEach { t ->
                            CompactTopicCard(t, data.summaries[t.id] ?: TopicSummary(), Modifier.weight(1f)) { nav.push(Screen.TopicDetail(t.id)) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Study activity", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("Last 4 weeks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                StudyActivityStrip(data.days)
                if (data.loaded && data.days.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Each day you study fills in a square.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
