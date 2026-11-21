package com.aiengineer.academy

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.ui.AppRoot
import com.aiengineer.academy.ui.theme.LearnAIofflineTheme
import com.aiengineer.academy.ui.theme.isAppInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: shows the launch mark, then switches to the app theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = AppGraph.settings
            val themeMode by settings.themeMode.collectAsState()
            val dynamic by settings.dynamicColor.collectAsState()
            val accent by settings.accent.collectAsState()
            val amoled by settings.amoled.collectAsState()
            val codeTheme by settings.codeTheme.collectAsState()
            val dark = isAppInDarkTheme(themeMode)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LearnAIofflineTheme(
                themeMode = themeMode,
                dynamicColor = dynamic,
                accent = accent,
                amoled = amoled,
                codeTheme = codeTheme,
            ) {
                AppRoot()
            }
        }
    }
}
