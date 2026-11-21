package com.aiengineer.academy.ui.screens

import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.ui.Navigator
import com.aiengineer.academy.ui.Screen
import com.aiengineer.academy.ui.components.AppScaffold
import com.aiengineer.academy.ui.components.CodeBlock
import com.aiengineer.academy.ui.theme.Accent
import com.aiengineer.academy.ui.theme.CodeTheme
import com.aiengineer.academy.ui.theme.DayNightSwitch
import com.aiengineer.academy.ui.theme.LocalDarkTheme
import com.aiengineer.academy.ui.theme.staticColors

private val THEME_MODES = listOf("Follow system", "Light", "Dark")
private val DYNAMIC_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// ============================================================================ Shared rows

/** A titled group of settings rows on a rounded card, as in the design kit. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.large),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, onClick = { onChange(!checked) }, enabled = enabled) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun ThemeModeChips() {
    val settings = AppGraph.settings
    val themeMode by settings.themeMode.collectAsState()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        THEME_MODES.forEachIndexed { i, label ->
            FilterChip(selected = themeMode == i, onClick = { settings.setThemeMode(i) }, label = { Text(label) })
        }
    }
}

/** The shared day/night control: tapping writes the opposite explicit mode (1 light, 2 dark). */
@Composable
fun AppDayNightSwitch(modifier: Modifier = Modifier) {
    DayNightSwitch(dark = LocalDarkTheme.current, onModeChange = { AppGraph.settings.setThemeMode(it) }, modifier = modifier)
}

/** A small preview of an accent: its container colour with a primary dot, and a check when selected. */
@Composable
private fun AccentDot(accent: Accent, selected: Boolean, size: Int = 28) {
    val scheme = staticColors(accent, LocalDarkTheme.current)
    Box(
        Modifier
            .size(size.dp)
            .background(scheme.primaryContainer, CircleShape)
            .border(2.dp, scheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = scheme.onPrimaryContainer, modifier = Modifier.size((size * 0.6f).dp))
        else Box(Modifier.size((size * 0.36f).dp).background(scheme.primary, CircleShape))
    }
}

// ============================================================================ Settings

@Composable
fun SettingsScreen(nav: Navigator, onBack: () -> Unit) {
    val settings = AppGraph.settings
    val themeMode by settings.themeMode.collectAsState()
    val dynamic by settings.dynamicColor.collectAsState()
    val accent by settings.accent.collectAsState()
    val amoled by settings.amoled.collectAsState()
    val codeTheme by settings.codeTheme.collectAsState()
    val quizSize by settings.quizSize.collectAsState()
    val codeScale by settings.codeFontScale.collectAsState()
    val dailyGoal by settings.dailyGoal.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    val readerSize by settings.readerTextSize.collectAsState()
    val readerSpacing by settings.readerLineSpacing.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { AppGraph.progress.exportTo(it) } != null }.getOrDefault(false)
            }
            toast(if (ok) "Progress exported" else "Couldn't export progress")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.let { AppGraph.progress.importFrom(it) } ?: false }.getOrDefault(false)
            }
            toast(if (ok) "Progress imported" else "That file isn't a LearnAIoffline progress backup. Nothing was changed.")
        }
    }

    AppScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsGroup("Appearance") {
                SettingRow("Theme mode", THEME_MODES[themeMode.coerceIn(0, 2)])
                ThemeModeChips()
                Spacer(Modifier.height(12.dp))
                RowDivider()
                SettingRow("Day / night", if (LocalDarkTheme.current) "Dark mode is on" else "Light mode is on") { AppDayNightSwitch() }
                RowDivider()
                SettingRow(
                    "Colour theme",
                    if (dynamic && DYNAMIC_SUPPORTED) "Wallpaper colours (${accent.label} saved)" else "${accent.label} · ${Accent.entries.size} themes",
                    onClick = { nav.push(Screen.Appearance) },
                ) { AccentDot(accent, selected = false) }
                if (DYNAMIC_SUPPORTED) {
                    RowDivider()
                    SwitchRow("Dynamic colour", "Use colours from your wallpaper", dynamic) { settings.setDynamicColor(it) }
                }
                RowDivider()
                SwitchRow("AMOLED black", "True black backgrounds in dark mode", amoled) { settings.setAmoled(it) }
            }

            SettingsGroup("Reading") {
                SettingRow("Text size", "$readerSize sp")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 17, 19, 22).forEach { sp ->
                        FilterChip(selected = readerSize == sp, onClick = { settings.setReaderTextSize(sp) }, label = { Text("$sp sp") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                RowDivider()
                SettingRow("Line spacing", "${readerSpacing}×")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.4f, 1.6f, 1.8f).forEach { v ->
                        FilterChip(selected = kotlin.math.abs(readerSpacing - v) < 0.01f, onClick = { settings.setReaderLineSpacing(v) }, label = { Text("${v}×") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                RowDivider()
                val codeSp = (codeScale * 14).let { kotlin.math.round(it).toInt() }
                SettingRow("Code font size", "$codeSp sp")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12, 14, 16, 18).forEach { sp ->
                        FilterChip(selected = codeSp == sp, onClick = { settings.setCodeFontScale(sp / 14f) }, label = { Text("$sp sp") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                RowDivider()
                SettingRow("Code theme", codeTheme.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CodeTheme.entries.forEach { t ->
                        FilterChip(selected = codeTheme == t, onClick = { settings.setCodeTheme(t) }, label = { Text(t.label) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Embeddings map text to vectors so similar meanings land close together.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = readerSize.sp, lineHeight = (readerSize * readerSpacing).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                CodeBlock("def greet(name: str) -> str:\n    return f\"Hello, {name}!\"  # preview", showCopy = false)
                Spacer(Modifier.height(12.dp))
            }

            SettingsGroup("Practice") {
                SettingRow("Default size of random sets", "$quizSize questions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 50, 100).forEach { n ->
                        FilterChip(selected = quizSize == n, onClick = { settings.setQuizSize(n) }, label = { Text("$n") })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SettingsGroup("Study") {
                SettingRow("Daily goal", "$dailyGoal minutes a day")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                        FilterChip(selected = dailyGoal == m, onClick = { settings.setDailyGoal(m) }, label = { Text("$m min") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                RowDivider()
                SettingRow("Welcome tour", "Show the getting-started screens again", onClick = { settings.setOnboardingDone(false) })
            }

            SettingsGroup("Data") {
                Text(
                    "Your progress is stored on this device and included in your own Android backup if you have it turned on. Nothing else is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                RowDivider()
                SettingRow("Export progress", "Save a backup file you can keep or move to another phone", onClick = {
                    exportLauncher.launch("learnaioffline-progress-${java.time.LocalDate.now()}.db")
                })
                RowDivider()
                SettingRow("Import progress", "Replace this phone's progress with a backup file", onClick = { confirmImport = true })
                RowDivider()
                SettingRow("Reset all progress", "Delete lessons, answers, bookmarks and history", onClick = { confirmReset = true })
            }

            SettingsGroup("About") {
                SettingRow("About & privacy", "Version, privacy and credits", onClick = { nav.push(Screen.About) })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import progress?") },
            text = { Text("Your current progress on this phone will be replaced by the backup you choose. Export first if you want to keep it.") },
            confirmButton = {
                TextButton(onClick = { confirmImport = false; importLauncher.launch(arrayOf("*/*")) }) { Text("Choose backup") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset everything?") },
            text = { Text("This permanently deletes lesson progress, answers, bookmarks and history on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    AppGraph.progress.resetAll()
                    confirmReset = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Keep my progress") } },
        )
    }
}

// ============================================================================ Appearance (theme chooser)

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val settings = AppGraph.settings
    val dynamic by settings.dynamicColor.collectAsState()
    val accent by settings.accent.collectAsState()
    val amoled by settings.amoled.collectAsState()
    val dynamicActive = dynamic && DYNAMIC_SUPPORTED

    AppScaffold(title = "Appearance", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Theme mode", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                AppDayNightSwitch()
            }
            Spacer(Modifier.height(8.dp))
            ThemeModeChips()

            Text("Colour theme", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
            Text(
                if (dynamicActive) "Wallpaper colours are on. Picking a theme turns them off." else "Pick the colours used across the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 3,
            ) {
                Accent.entries.forEach { a ->
                    AccentTile(a, selected = a == accent && !dynamicActive, modifier = Modifier.weight(1f)) {
                        settings.setAccent(a)
                        if (dynamicActive) settings.setDynamicColor(false)
                    }
                }
                // keep the last row's tiles the same width as the others
                repeat((3 - Accent.entries.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
            }

            if (DYNAMIC_SUPPORTED) {
                Text("Material You", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
                SwitchRow("Dynamic colour", "Use wallpaper colours. Your colour theme stays saved.", dynamic) { settings.setDynamicColor(it) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SwitchRow("True black in dark mode", "Saves battery on OLED screens", amoled) { settings.setAmoled(it) }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** One accent choice: a mini preview of the theme (surface, container, primary button) plus its name. */
@Composable
private fun AccentTile(accent: Accent, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = staticColors(accent, LocalDarkTheme.current)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) scheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .background(scheme.surfaceContainerLow, shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(44.dp).background(scheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(width = 40.dp, height = 18.dp).background(scheme.primary, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(scheme.secondary, scheme.tertiary).forEach { Box(Modifier.size(10.dp).background(it, CircleShape)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(accent.label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, textAlign = TextAlign.Center)
    }
}
