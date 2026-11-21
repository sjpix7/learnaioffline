package com.aiengineer.academy.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class CodeTheme { FollowApp, Light, Dark }

@Composable
fun isAppInDarkTheme(themeMode: Int) = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun LearnAIofflineTheme(
    themeMode: Int, dynamicColor: Boolean, accent: Accent = Accent.Forest,
    amoled: Boolean = false, codeTheme: CodeTheme = CodeTheme.FollowApp,
    typography: Typography = LearnTypography(), content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(themeMode)
    val context = LocalContext.current
    val base = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else staticColors(accent, dark)
    val scheme = if (dark && amoled) base.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black
    ) else base
    val semantic = if (dark) DarkExtra else LightExtra
    val syntax = if (codeTheme == CodeTheme.Dark || (codeTheme == CodeTheme.FollowApp && dark)) DarkExtra else LightExtra
    val extra = syntax.copy(success = semantic.success, successContainer = semantic.successContainer,
        onSuccessContainer = semantic.onSuccessContainer, danger = semantic.danger,
        dangerContainer = semantic.dangerContainer, onDangerContainer = semantic.onDangerContainer)
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = LearnShapes, content = content)
    }
}

// Keeps the uploaded MainActivity working during migration.
@Composable
fun AcademyTheme(themeMode: Int, dynamicColor: Boolean, content: @Composable () -> Unit) =
    LearnAIofflineTheme(themeMode, dynamicColor, content = content)
