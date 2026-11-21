package com.aiengineer.academy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun LearnTypography(ui: FontFamily = FontFamily.SansSerif): Typography {
    fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = ui, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight
    )
    return Typography(
        displayLarge = style(54, 62, FontWeight.Medium),
        displayMedium = style(44, 52, FontWeight.Medium),
        displaySmall = style(36, 44, FontWeight.Medium),
        headlineLarge = style(32, 40, FontWeight.SemiBold),
        headlineMedium = style(28, 36, FontWeight.SemiBold),
        headlineSmall = style(24, 32, FontWeight.SemiBold),
        titleLarge = style(22, 30, FontWeight.SemiBold),
        titleMedium = style(17, 24, FontWeight.SemiBold),
        titleSmall = style(15, 22, FontWeight.SemiBold),
        bodyLarge = style(17, 27, FontWeight.Normal),
        bodyMedium = style(15, 23, FontWeight.Normal),
        bodySmall = style(13, 20, FontWeight.Normal),
        labelLarge = style(15, 20, FontWeight.Medium),
        labelMedium = style(13, 18, FontWeight.Medium),
        labelSmall = style(12, 16, FontWeight.Medium),
    )
}

val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp)
