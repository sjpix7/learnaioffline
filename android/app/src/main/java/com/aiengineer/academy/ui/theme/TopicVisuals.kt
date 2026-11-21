package com.aiengineer.academy.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aiengineer.academy.R

/** Colours for one topic tile: its accent family's tested container/on-container pair plus the family's primary. */
data class TopicTone(val container: Color, val onContainer: Color, val accent: Color)

/**
 * Topic colours follow the design kit: colour identifies a family (Ocean, Violet, Sunset, Forest) and the
 * pictogram plus the visible name identifies the topic. They stay the same whichever app accent is selected.
 */
@Composable
fun topicTone(topicId: String): TopicTone {
    val scheme = staticColors(topicStyles[topicId]?.accent ?: Accent.Forest, LocalDarkTheme.current)
    return TopicTone(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
}

/** Kit pictogram for a topic, or null for unknown topics (callers fall back to the topic's emoji). */
@DrawableRes
fun topicIconRes(topicId: String): Int? = when (topicStyles[topicId]?.icon) {
    "terminal" -> R.drawable.ic_topic_terminal
    "bolt" -> R.drawable.ic_topic_bolt
    "link" -> R.drawable.ic_topic_link
    "branch" -> R.drawable.ic_topic_branch
    "pulse" -> R.drawable.ic_topic_pulse
    "index" -> R.drawable.ic_topic_index
    "vector" -> R.drawable.ic_topic_vector
    "network" -> R.drawable.ic_topic_network
    "merge" -> R.drawable.ic_topic_merge
    "layers" -> R.drawable.ic_topic_layers
    "robot" -> R.drawable.ic_topic_robot
    "dots" -> R.drawable.ic_topic_dots
    "chip" -> R.drawable.ic_topic_chip
    "check" -> R.drawable.ic_topic_check
    "grid" -> R.drawable.ic_topic_grid
    "plug" -> R.drawable.ic_topic_plug
    else -> null
}
