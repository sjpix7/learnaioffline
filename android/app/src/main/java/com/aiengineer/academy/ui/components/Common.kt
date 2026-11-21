package com.aiengineer.academy.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aiengineer.academy.ui.theme.topicTone
import com.aiengineer.academy.ui.theme.topicIconRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.data.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** True when the bottom navigation bar is visible below the current screen. */
val LocalHasBottomBar = staticCompositionLocalOf { false }

/**
 * Loads data off the main thread and reloads it whenever progress changes or any key changes.
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, initial: T, loader: () -> T): T {
    val version by AppGraph.progress.version.collectAsState()
    val state = produceState(initial, version, *keys) {
        value = withContext(Dispatchers.IO) { loader() }
    }
    return state.value
}

@Composable
fun AppScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val hasBottomNav = LocalHasBottomBar.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (titleContent != null) titleContent() else Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = bottomBar,
        contentWindowInsets = if (hasBottomNav) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        content = content,
    )
}

@Composable
fun IconAction(icon: Int, description: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (tint == Color.Unspecified) Icon(painterResource(icon), contentDescription = description)
        else Icon(painterResource(icon), contentDescription = description, tint = tint)
    }
}

/** Topic tile from the design kit: the family's tonal container with the topic's outline pictogram. */
@Composable
fun TopicBadge(topic: Topic, size: Dp = 44.dp) {
    val tone = topicTone(topic.id)
    val icon = topicIconRes(topic.id)
    Box(
        modifier = Modifier
            .size(size)
            .background(tone.container, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = tone.onContainer, modifier = Modifier.size(size * 0.55f))
        else Text(topic.emoji, fontSize = (size.value * 0.48f).sp)
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    size: Dp = 56.dp,
    stroke: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    label: String? = null,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = stroke,
            trackColor = color.copy(alpha = 0.15f),
        )
        Text(
            label ?: "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun LinearProgress(progress: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp),
        color = color,
        trackColor = color.copy(alpha = 0.15f),
    )
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, icon: Int? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                }
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📭", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

fun formatMinutes(seconds: Int): String {
    val m = seconds / 60
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}

fun kindLabel(kind: String): String = when (kind) {
    "concept" -> "Concept"
    "true-false" -> "True / False"
    "scenario" -> "Scenario"
    "code-output" -> "Code output"
    "code-reading" -> "Read the code"
    "fill-code" -> "Fill the code"
    "matching" -> "Matching"
    "category" -> "Classify"
    "ordering" -> "Ordering"
    "calculation" -> "Calculate"
    "api-behaviour" -> "API behaviour"
    else -> kind.replaceFirstChar { it.uppercase() }
}


/** Brand lockup from the design kit: the mark plus "LearnAI" semibold and "offline" regular on one baseline. */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "LearnAIoffline" }, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_logo_mark), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(width = 26.dp, height = 22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("LearnAI") }
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append("offline") }
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
