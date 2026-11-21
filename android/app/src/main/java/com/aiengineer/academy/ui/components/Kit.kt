package com.aiengineer.academy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.data.DayStat
import com.aiengineer.academy.data.Topic
import com.aiengineer.academy.data.TopicSummary
import com.aiengineer.academy.ui.theme.Accent
import com.aiengineer.academy.ui.theme.LocalDarkTheme
import java.time.LocalDate

// ============================================================================ Type helpers

/** Monospace metadata style from the kit ("Lesson 4 of 9 · 3 min", "10 lessons"). */
val MonoMeta = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp)

/** Uppercase monospace overline ("CONTINUE LEARNING", "SATURDAY, 21 NOVEMBER"). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MonoMeta.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
        color = color,
    )
}

// ============================================================================ Hero colours and lattice art

/**
 * The deep "pine" hero surface from the kit, derived from the active scheme so it follows every accent:
 * light mode swaps the container pair (dark onPrimaryContainer as background), dark mode uses it as-is.
 */
data class HeroColors(val container: Color, val content: Color, val muted: Color, val highlight: Color)

@Composable
fun heroColors(): HeroColors {
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val accent by AppGraph.settings.accent.collectAsState()
    val dynamic by AppGraph.settings.dynamicColor.collectAsState()
    val bg = if (dark) cs.primaryContainer else cs.onPrimaryContainer
    val fg = if (dark) cs.onPrimaryContainer else cs.primaryContainer
    // The kit's lime query path belongs to Forest; other themes use their tertiary tone for the same contrast.
    val highlight = if (accent == Accent.Forest && !dynamic) Color(0xFFD9F774) else cs.tertiaryFixedDim
    return HeroColors(bg, fg, fg.copy(alpha = 0.75f), highlight)
}

/** The kit's technical illustration: embeddings as points on a lattice, with a query path picked out. */
@Composable
fun LatticeArt(modifier: Modifier = Modifier, dots: Color, highlight: Color, rings: Int = 6) {
    Canvas(modifier.clearAndSetSemantics { }) { // decorative
        val n = rings
        val cx = size.width / 2f
        val cy = size.height / 2f
        val dx = size.width / (2f * n + 1f)
        val dy = size.height / (2f * n + 1f)
        fun p(i: Int, j: Int) = Offset(cx + (i - j) * dx, cy + (i + j - n) * dy)
        val line = dots.copy(alpha = dots.alpha * 0.35f)
        val stroke = (size.minDimension / 260f).coerceAtLeast(1f)
        for (i in 0..n) for (j in 0..n) {
            if (i < n) drawLine(line, p(i, j), p(i + 1, j), stroke)
            if (j < n) drawLine(line, p(i, j), p(i, j + 1), stroke)
        }
        for (i in 0..n) for (j in 0..n) {
            val big = (i * 7 + j * 3) % 5 == 0
            drawCircle(dots.copy(alpha = dots.alpha * if (big) 0.9f else 0.6f), radius = if (big) dx * 0.16f else dx * 0.09f, center = p(i, j))
        }
        val path = listOf(p(1, n - 1), p(n / 2 + 1, n / 2), p(n - 1, n / 2 + 1))
        for (k in 0 until path.lastIndex) {
            drawLine(highlight, path[k], path[k + 1], stroke * 3.2f, cap = StrokeCap.Round)
        }
        path.forEach { drawCircle(highlight, radius = dx * 0.24f, center = it) }
    }
}

/** Continue card: course context, lesson title, minutes and the lattice beside the text. */
@Composable
fun ContinueHeroCard(overline: String, title: String, meta: String, modifier: Modifier = Modifier) {
    val hero = heroColors()
    Surface(shape = RoundedCornerShape(22.dp), color = hero.container, modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.heightIn(min = 156.dp)) {
            val showArt = maxWidth > 300.dp
            if (showArt) {
                LatticeArt(
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(width = 132.dp, height = 96.dp),
                    dots = hero.content, highlight = hero.highlight, rings = 6,
                )
            }
            Column(Modifier.padding(20.dp).padding(end = if (showArt) 132.dp else 0.dp)) {
                Overline(overline, color = hero.muted)
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, color = hero.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(meta, style = MonoMeta, color = hero.muted)
            }
        }
    }
}

/** A large lattice panel for onboarding and other major learning moments. */
@Composable
fun LatticePanel(modifier: Modifier = Modifier) {
    val hero = heroColors()
    Surface(shape = RoundedCornerShape(22.dp), color = hero.container, modifier = modifier.fillMaxWidth()) {
        LatticeArt(Modifier.fillMaxWidth().height(200.dp).padding(16.dp), dots = hero.content, highlight = hero.highlight, rings = 7)
    }
}

// ============================================================================ Progress pieces

/** Kit progress bar: 8 dp, rounded, primary on the highest container. */
@Composable
fun KitProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
    ) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(8.dp).background(color, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun DailyGoal(studiedSeconds: Int, goalMinutes: Int, modifier: Modifier = Modifier) {
    val minutes = studiedSeconds / 60
    Column(modifier.semantics(mergeDescendants = true) {
        contentDescription = "Daily goal: $minutes of $goalMinutes minutes studied today"
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily goal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("${minutes.coerceAtMost(999)} / $goalMinutes min", style = MonoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        KitProgressBar(if (goalMinutes <= 0) 0f else minutes.toFloat() / goalMinutes)
        if (minutes >= goalMinutes && goalMinutes > 0) {
            Spacer(Modifier.height(6.dp))
            Text("Goal reached today. Nice work!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Last 4 weeks as two rows of 14 days, shaded by how much was studied each day. */
@Composable
fun StudyActivityStrip(days: List<DayStat>, modifier: Modifier = Modifier) {
    val byDay = days.associateBy { it.day }
    val today = LocalDate.now()
    val range = (27 downTo 0).map { today.minusDays(it.toLong()) }
    val active = range.count { (byDay[it.toString()]?.let { d -> d.studySeconds + d.answered + d.lessonsOpened } ?: 0) > 0 }
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    fun level(d: DayStat?): Int {
        if (d == null) return 0
        val score = d.studySeconds / 60 + d.answered / 4 + d.lessonsDone * 5
        return when {
            score <= 0 && d.lessonsOpened == 0 -> 0
            score < 5 -> 1
            score < 15 -> 2
            score < 30 -> 3
            else -> 4
        }
    }
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Studied on $active of the last 28 days" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        range.chunked(14).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { day ->
                    val c = when (level(byDay[day.toString()])) {
                        0 -> empty
                        1 -> primary.copy(alpha = 0.3f)
                        2 -> primary.copy(alpha = 0.55f)
                        3 -> primary.copy(alpha = 0.8f)
                        else -> primary
                    }
                    Box(Modifier.weight(1f).height(16.dp).background(c, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

// ============================================================================ Topic pieces

/** Compact topic card for Home: family tile, name and lesson count (with progress once started). */
@Composable
fun CompactTopicCard(topic: Topic, summary: TopicSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp)) {
            TopicBadge(topic, 40.dp)
            Spacer(Modifier.height(12.dp))
            Text(topic.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val total = topic.lessons.size
            Text(
                if (summary.lessonsDone > 0) "${summary.lessonsDone} of $total lessons" else "$total lessons",
                style = MonoMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.lessonsDone > 0 && total > 0) {
                Spacer(Modifier.height(8.dp))
                KitProgressBar(summary.lessonsDone.toFloat() / total)
            }
        }
    }
}

/** A list row with a tonal leading tile, two lines of text and a chevron (Learn library, shortcuts). */
@Composable
fun LibraryRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(R.drawable.ic_arrow_forward), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
        )
    }
}

/** A tonal square holding an icon, used as the leading element of shortcut rows. */
@Composable
fun TonalIconTile(icon: Int, size: Int = 44) {
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape((size * 0.3f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size((size * 0.5f).dp))
    }
}

// ============================================================================ Page pieces

/** Kit page header: overline, large title and an optional supporting line. */
@Composable
fun ScreenHeader(overline: String, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 12.dp)) {
        Overline(overline)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Metric card: value plus label, 72 dp minimum (kit). */
@Composable
fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Kit empty / error state: symbol tile, heading, context and one recovery action. */
@Composable
fun KitEmptyState(icon: Int, title: String, body: String, action: String? = null, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp)) {
        TonalIconTile(icon, 44)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onAction, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                Text(action, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Selector card used in Quiz setup: leading tile, value, supporting text and a dropdown chevron. */
@Composable
fun SelectorCard(title: String, subtitle: String, leading: @Composable () -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A modal bottom sheet with a title and a list of choices (kit: index, filters, selectors). */
@Composable
fun <T> ChoiceSheet(
    title: String,
    choices: List<T>,
    label: (T) -> String,
    supporting: (T) -> String? = { null },
    leading: (@Composable (T) -> Unit)? = null,
    selected: (T) -> Boolean = { false },
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        androidx.compose.foundation.lazy.LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            items(choices.size) { i ->
                val c = choices[i]
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(c) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) {
                        leading(c)
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(label(c), style = MaterialTheme.typography.titleMedium, color = if (selected(c)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        supporting(c)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (selected(c)) Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun KitDivider() = androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
