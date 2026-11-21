package com.aiengineer.academy.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aiengineer.academy.BuildConfig
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.data.Block
import com.aiengineer.academy.ui.Navigator
import com.aiengineer.academy.ui.Screen
import com.aiengineer.academy.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ============================================================================ Search

private sealed interface SearchHit {
    val topicId: String

    data class LessonHit(override val topicId: String, val lesson: Int, val section: Int, val title: String, val snippet: String) : SearchHit
    data class TermHit(override val topicId: String, val term: String, val definition: String, val lesson: Int) : SearchHit
}

private fun runSearch(query: String): List<SearchHit> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    val content = AppGraph.content
    val hits = ArrayList<SearchHit>()
    for (topic in content.topics()) {
        // glossary terms first: they are precise
        for (e in content.glossary(topic.id)) {
            if (e.term.contains(q, true) || e.definition.contains(q, true)) {
                hits.add(SearchHit.TermHit(topic.id, e.term, e.definition, e.lesson))
            }
        }
        content.lessons(topic.id).forEachIndexed { li, lesson ->
            var section = -1
            var matchedLesson = lesson.title.contains(q, true)
            if (matchedLesson) hits.add(SearchHit.LessonHit(topic.id, li, -1, lesson.title, topic.title))
            for (b in lesson.blocks) {
                val text = when (b) {
                    is Block.Heading -> { section++; b.text }
                    is Block.Paragraph -> b.text
                    is Block.Note -> b.text
                    is Block.Bullets -> b.items.joinToString(" ")
                    is Block.Numbered -> b.items.joinToString(" ")
                    is Block.Code -> b.code
                    is Block.Table -> b.rows.joinToString(" ") { it.joinToString(" ") }
                }
                val idx = text.indexOf(q, ignoreCase = true)
                if (idx >= 0 && !matchedLesson) {
                    val start = (idx - 50).coerceAtLeast(0)
                    val end = (idx + q.length + 70).coerceAtMost(text.length)
                    val snippet = (if (start > 0) "…" else "") + text.substring(start, end).replace('\n', ' ') + (if (end < text.length) "…" else "")
                    hits.add(SearchHit.LessonHit(topic.id, li, section, lesson.title, snippet))
                    matchedLesson = true // one hit per lesson keeps results readable
                }
            }
            if (hits.size > 150) return hits
        }
    }
    return hits
}

@Composable
fun SearchScreen(nav: Navigator, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<SearchHit>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        delay(250)
        results = withContext(Dispatchers.IO) { runSearch(query) }
    }
    val content = AppGraph.content
    val list = results.orEmpty()
    val lessons = list.filterIsInstance<SearchHit.LessonHit>()
    val terms = list.filterIsInstance<SearchHit.TermHit>()
    val searched = query.trim().length >= 2 && results != null

    AppScaffold(title = "Search", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                Text("Search", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("e.g. reranking, StateGraph, MCP, RRF") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) ({
                        IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear search") }
                    }) else null,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Lessons", "Concepts").forEachIndexed { i, label ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                    }
                }
                if (!searched) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Search ${content.totalLessons} lessons, their code and ${"%,d".format(content.topics().sumOf { it.glossaryCount })} key concepts. Everything is on your phone, so it works offline.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val showLessons = filter != 2 && lessons.isNotEmpty()
            val showTerms = filter != 1 && terms.isNotEmpty()
            if (searched && !showLessons && !showTerms) item {
                KitEmptyState(R.drawable.ic_search, "No matches found.", "Try a shorter term or clear your filters.", "Clear search") { query = ""; filter = 0 }
            }
            if (showLessons) {
                item { Text("Lessons", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
                items(lessons.take(60)) { hit ->
                    val topic = content.topic(hit.topicId) ?: return@items
                    LibraryRow(hit.title, "${topic.title} · ${hit.snippet}".take(140), leading = { TonalIconTile(R.drawable.ic_book) }) {
                        nav.push(Screen.LessonView(hit.topicId, hit.lesson, hit.section))
                    }
                    KitDivider()
                }
            }
            if (showTerms) {
                item { Text("Concepts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
                items(terms.take(60)) { hit ->
                    val topic = content.topic(hit.topicId) ?: return@items
                    LibraryRow(hit.term, topic.title, leading = { TonalIconTile(R.drawable.ic_copy) }) { nav.push(Screen.Glossary(hit.topicId)) }
                    KitDivider()
                }
            }
        }
    }
}

// ============================================================================ About

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val content = AppGraph.content
    var sheet by remember { mutableStateOf("") }
    AppScaffold(title = "About", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            ContinueHeroCard("Android edition", "LearnAIoffline", "Version ${BuildConfig.VERSION_NAME}")
            Spacer(Modifier.height(24.dp))
            Text("About the app", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "An offline field guide to the modern AI engineering stack: ${content.topics().size} topics, ${content.totalLessons} lessons and " +
                    "${"%,d".format(content.totalQuestions)} practice questions, from Python and FastAPI to RAG, agents, evaluation and the Model Context Protocol. " +
                    "No accounts. No ads.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LibraryRow("Privacy", "How study data is stored", leading = { TonalIconTile(R.drawable.ic_info) }) { sheet = "privacy" }
            KitDivider()
            LibraryRow("How questions are made", "Curated knowledge base and verified code", leading = { TonalIconTile(R.drawable.ic_quiz) }) { sheet = "questions" }
            KitDivider()
            LibraryRow("Open-source licences", "Bundled libraries", leading = { TonalIconTile(R.drawable.ic_list) }) { sheet = "licences" }
            KitDivider()
            Spacer(Modifier.height(20.dp))
            Text(
                "Content reflects these fast-moving libraries as of late 2026; check the official documentation for the version you use. Product and framework names " +
                    "(LangChain, LangGraph, LangSmith, Langfuse, LlamaIndex, FastAPI, CrewAI, RAGAS, OpenAI, Anthropic, Google, Microsoft, Meta and others) are trademarks of " +
                    "their respective owners. This app is an independent learning resource and is not affiliated with or endorsed by them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
    val (title, body) = when (sheet) {
        "privacy" -> "Privacy" to "LearnAIoffline works fully offline and asks for no permissions. It does not collect, transmit or share any personal data. " +
            "Your progress, bookmarks and history are stored on this device, and in your own Android backup if you have it turned on. " +
            "You can export or delete your progress in Settings › Data. Uninstalling the app also deletes it."
        "questions" -> "How questions are made" to "Concept questions are generated from a curated knowledge base written for this course. Code-output, " +
            "API-behaviour and calculation questions were verified by actually running the code (Python, FastAPI, LangChain, LangGraph, LlamaIndex Workflows " +
            "and plain-Python MCP message handling) or by exact arithmetic when the question bank was built."
        "licences" -> "Open-source licences" to "This app is built with open-source software licensed under the Apache License 2.0: Kotlin and kotlinx.coroutines " +
            "(JetBrains), AndroidX Core, Activity, Core SplashScreen and Jetpack Compose including Material 3 (The Android Open Source Project). " +
            "Full licence text: https://www.apache.org/licenses/LICENSE-2.0"
        else -> "" to ""
    }
    if (sheet.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { sheet = "" },
            title = { Text(title) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { sheet = "" }) { Text("Close") } },
        )
    }
}
