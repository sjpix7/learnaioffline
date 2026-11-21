package com.aiengineer.academy.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Both entry points write to the same existing preference (1 light, 2 dark). */
@Composable
fun DayNightSwitch(dark: Boolean, onModeChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val phase by animateFloatAsState(if (dark) 1f else 0f,
        animationSpec = tween(Motion.themeMs, easing = Motion.standard), label = "Day or night")
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier.size(48.dp).semantics { contentDescription = "Dark mode" }
        .toggleable(value = dark, role = Role.Switch, onValueChange = { onModeChange(if (it) 2 else 1) })) {
        val u = size.minDimension / 48f
        drawCircle(surface, 13 * u)
        drawCircle(ink, (5 + 3 * phase) * u, style = Stroke(1.7f * u))
        repeat(8) { i ->
            val angle = i * Math.PI / 4
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(ink, center + direction * (8 * u), center + direction * (11 * u),
                strokeWidth = 1.6f * u, alpha = 1f - phase)
        }
        drawCircle(ink, 8 * u, alpha = phase)
        drawCircle(surface, 6.7f * u, center + Offset(3f * u, -3f * u), alpha = phase)
    }
}
