package com.aiengineer.academy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

val LearnShapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp))
object Space {
    val micro = 4.dp
    val small = 8.dp
    val compact = 12.dp
    val card = 16.dp
    val gutter = 20.dp
    val section = 24.dp
    val large = 32.dp
    val touch = 48.dp
}
object Elevation { val flat = 0.dp; val card = 1.dp; val sheet = 3.dp; val dialog = 6.dp }
object Motion {
    const val feedbackMs = 120
    const val themeMs = 220
    const val sheetMs = 300
    val standard = CubicBezierEasing(.2f, 0f, 0f, 1f)
}
