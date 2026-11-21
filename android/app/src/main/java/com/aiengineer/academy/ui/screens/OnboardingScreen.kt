package com.aiengineer.academy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.ui.components.BrandWordmark
import com.aiengineer.academy.ui.components.KitProgressBar
import com.aiengineer.academy.ui.components.LatticePanel
import com.aiengineer.academy.ui.components.Overline
import com.aiengineer.academy.ui.theme.Accent
import com.aiengineer.academy.ui.theme.LocalDarkTheme
import com.aiengineer.academy.ui.theme.staticColors

private const val PAGES = 4
private val GOALS = listOf(5, 10, 15, 30)

/** First-run welcome tour from the design kit: offline intro, practice, daily goal and theme. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            BrandWordmark()
            Spacer(Modifier.height(24.dp))
            Overline("Getting started ${page + 1} / $PAGES")
            Spacer(Modifier.height(10.dp))
            KitProgressBar((page + 1f) / PAGES)
            Spacer(Modifier.height(16.dp))
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding",
            modifier = Modifier.weight(1f),
        ) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                when (p) {
                    0 -> IntroPage(
                        "Your offline field guide.",
                        "Learn the AI engineering stack anywhere. Lessons, code and concepts travel with you. No account, no ads, no internet needed.",
                    )
                    1 -> IntroPage(
                        "Practice with a purpose.",
                        "Answer questions, reveal hints and revisit the lesson behind every answer. ${"%,d".format(AppGraph.content.totalQuestions)} questions across ${AppGraph.content.topics().size} topics.",
                    )
                    2 -> GoalPage()
                    else -> LookPage()
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.medium) {
                Text("Skip", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = { if (page < PAGES - 1) page++ else onFinish() },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (page < PAGES - 1) "Continue" else "Start learning", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun IntroPage(title: String, body: String) {
    LatticePanel()
    Spacer(Modifier.height(28.dp))
    Text(title, style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GoalPage() {
    val settings = AppGraph.settings
    val goal by settings.dailyGoal.collectAsState()
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(168.dp).semantics(mergeDescendants = true) { contentDescription = "Daily goal $goal minutes" }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { goal / 30f },
                modifier = Modifier.size(168.dp),
                strokeWidth = 14.dp,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text("$goal min", style = MaterialTheme.typography.headlineMedium)
        }
    }
    Spacer(Modifier.height(28.dp))
    Text("Make time for learning.", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    Text(
        "Choose a daily study goal. Home shows how close you are each day. You can change it any time in Settings.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GOALS.forEach { g -> FilterChip(selected = goal == g, onClick = { settings.setDailyGoal(g) }, label = { Text("$g min") }) }
    }
}

@Composable
private fun LookPage() {
    val settings = AppGraph.settings
    val themeMode by settings.themeMode.collectAsState()
    val accent by settings.accent.collectAsState()
    val dynamic by settings.dynamicColor.collectAsState()
    LatticePanel()
    Spacer(Modifier.height(28.dp))
    Text("Choose your look.", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    Text(
        "Pick light or dark and a colour theme. You can change both any time in Settings.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("System", "Light", "Dark").forEachIndexed { i, label ->
            FilterChip(selected = themeMode == i, onClick = { settings.setThemeMode(i) }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Accent.entries.forEach { a ->
            val scheme = staticColors(a, LocalDarkTheme.current)
            val selected = a == accent && !dynamic
            Box(
                Modifier
                    .size(44.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                        settings.setAccent(a)
                        if (dynamic) settings.setDynamicColor(false)
                    })
                    .semantics { contentDescription = "${a.label} colour theme" }
                    .background(scheme.primaryContainer, CircleShape)
                    .border(if (selected) 3.dp else 1.5.dp, scheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                else Box(Modifier.size(16.dp).background(scheme.primary, CircleShape))
            }
        }
    }
}
