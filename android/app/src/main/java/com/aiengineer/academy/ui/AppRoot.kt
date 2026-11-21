package com.aiengineer.academy.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import com.aiengineer.academy.ui.screens.TopicProgressScreen
import com.aiengineer.academy.ui.screens.FlashcardsScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.aiengineer.academy.ui.screens.OnboardingScreen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.ui.components.LocalHasBottomBar
import com.aiengineer.academy.ui.screens.AboutScreen
import com.aiengineer.academy.ui.screens.AppearanceScreen
import com.aiengineer.academy.ui.screens.BookmarksScreen
import com.aiengineer.academy.ui.screens.DayScreen
import com.aiengineer.academy.ui.screens.GlossaryScreen
import com.aiengineer.academy.ui.screens.HistoryScreen
import com.aiengineer.academy.ui.screens.HomeScreen
import com.aiengineer.academy.ui.screens.LearnScreen
import com.aiengineer.academy.ui.screens.LessonScreen
import com.aiengineer.academy.ui.screens.PracticeScreen
import com.aiengineer.academy.ui.screens.ProgressScreen
import com.aiengineer.academy.ui.screens.QuestionBankScreen
import com.aiengineer.academy.ui.screens.QuizScreen
import com.aiengineer.academy.ui.screens.QuizSetupScreen
import com.aiengineer.academy.ui.screens.SearchScreen
import com.aiengineer.academy.ui.screens.SettingsScreen
import com.aiengineer.academy.ui.screens.TopicScreen

private data class TabItem(val screen: Screen, val label: String, val icon: Int)

private val TABS = listOf(
    TabItem(Screen.Home, "Home", R.drawable.ic_home),
    TabItem(Screen.Learn, "Learn", R.drawable.ic_book),
    TabItem(Screen.Practice, "Practice", R.drawable.ic_quiz),
    TabItem(Screen.Progress, "Progress", R.drawable.ic_chart),
)

@Composable
fun AppRoot() {
    // "Developed by Sj.Pixels" credit above every screen. It takes the status-bar inset itself and marks it
    // consumed, so the top app bars below don't add the same space again.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DeveloperCredit()
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)) {
            AppContent()
        }
    }
}

/** Slim credit strip; "Sj.Pixels" opens https://sjpixels.com/ in the browser (no INTERNET permission needed for that). */
@Composable
private fun DeveloperCredit() {
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            buildAnnotatedString {
                append("Developed by ")
                withLink(LinkAnnotation.Url("https://sjpixels.com/", TextLinkStyles(style = linkStyle))) { append("Sj.Pixels") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun AppContent() {
    val onboardingDone by AppGraph.settings.onboardingDone.collectAsState()
    if (!onboardingDone) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            OnboardingScreen(onFinish = { AppGraph.settings.setOnboardingDone(true) })
        }
        return
    }
    val nav = AppGraph.navigator
    val current = nav.current
    val showBottomBar = current.showsBottomBar

    BackHandler(enabled = nav.stack.size > 1 || current != Screen.Home) {
        if (!nav.pop()) nav.tab(Screen.Home)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalHasBottomBar provides showBottomBar) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { screen ->
                        ScreenContent(screen, nav)
                    }
                }
            }
            AnimatedVisibility(visible = showBottomBar, enter = expandVertically(), exit = shrinkVertically()) {
                NavigationBar {
                    val root = nav.rootTab
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = root == tab.screen,
                            onClick = { nav.tab(tab.screen) },
                            icon = { Icon(painterResource(tab.icon), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(screen: Screen, nav: Navigator) {
    val back: () -> Unit = { nav.pop() }
    when (screen) {
        Screen.Home -> HomeScreen(nav)
        Screen.Learn -> LearnScreen(nav)
        Screen.Practice -> PracticeScreen(nav)
        Screen.Progress -> ProgressScreen(nav)
        is Screen.TopicDetail -> TopicScreen(screen.topicId, nav, back)
        is Screen.LessonView -> LessonScreen(screen.topicId, screen.index, screen.section, nav, back)
        is Screen.Glossary -> GlossaryScreen(screen.topicId, nav, back)
        is Screen.Flashcards -> FlashcardsScreen(screen.topicId, screen.start, back)
        is Screen.TopicProgress -> TopicProgressScreen(screen.topicId, nav, back)
        is Screen.QuizSetup -> QuizSetupScreen(screen.topicId, screen.mode, nav, back)
        is Screen.Quiz -> QuizScreen(screen.sessionId, nav, back)
        is Screen.QuestionBank -> QuestionBankScreen(screen.topicId, nav, back)
        Screen.History -> HistoryScreen(nav, back)
        is Screen.Day -> DayScreen(screen.day, nav, back)
        Screen.Search -> SearchScreen(nav, back)
        Screen.Bookmarks -> BookmarksScreen(nav, back)
        Screen.Settings -> SettingsScreen(nav, back)
        Screen.Appearance -> AppearanceScreen(back)
        Screen.About -> AboutScreen(back)
    }
}
