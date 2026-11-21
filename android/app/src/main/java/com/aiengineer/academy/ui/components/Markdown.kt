package com.aiengineer.academy.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aiengineer.academy.R
import com.aiengineer.academy.data.AppGraph
import com.aiengineer.academy.data.Block
import com.aiengineer.academy.ui.theme.CodeTextStyle
import com.aiengineer.academy.ui.theme.ExtraColors
import com.aiengineer.academy.ui.theme.LocalExtraColors

/** Parses **bold**, *italic* and `inline code` into an AnnotatedString. */
fun inlineMarkdown(text: String, codeBg: Color, codeFg: Color): AnnotatedString = buildAnnotatedString {
    var bold = false
    var italic = false
    var i = 0
    val n = text.length
    fun style(): SpanStyle = SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
    )
    while (i < n) {
        val ch = text[i]
        if (ch == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeFg, fontSize = 0.92.em))
                append(text.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }
        if (ch == '*' && i + 1 < n && text[i + 1] == '*') {
            bold = !bold
            i += 2
            continue
        }
        if (ch == '*') {
            val next = if (i + 1 < n) text[i + 1] else ' '
            val prev = if (i > 0) text[i - 1] else ' '
            val opens = !italic && !next.isWhitespace() && (prev.isWhitespace() || prev == '(')
            val closes = italic && !prev.isWhitespace()
            if (opens || closes) {
                italic = !italic
                i++
                continue
            }
        }
        val start = i
        while (i < n && text[i] != '`' && text[i] != '*') i++
        if (i == start) i++ // lone special character
        pushStyle(style())
        append(text.substring(start, i))
        pop()
    }
}

@Composable
fun MdText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeFg = MaterialTheme.colorScheme.primary
    val annotated = remember(text, codeBg, codeFg) { inlineMarkdown(text, codeBg, codeFg) }
    Text(annotated, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Kit code block: language tag, copy action, syntax-highlighted monospace that scrolls horizontally. */
@Composable
fun CodeBlock(code: String, lang: String = "python", modifier: Modifier = Modifier, showCopy: Boolean = true) {
    val extra = LocalExtraColors.current
    val scale by AppGraph.settings.codeFontScale.collectAsState()
    val highlighted = remember(code, lang, extra) { highlight(code, lang, extra) }
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = extra.codeBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lang.uppercase(),
                    color = extra.codeText,
                    style = MonoMeta.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
                    modifier = Modifier.weight(1f),
                )
                if (showCopy) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("code", code))
                            // Android 13+ shows its own clipboard confirmation.
                            if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = null, tint = extra.codeComment, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelMedium, color = extra.codeComment)
                    }
                } else {
                    Spacer(Modifier.height(40.dp))
                }
            }
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                SelectionContainer {
                    Text(
                        highlighted,
                        fontFamily = FontFamily.Monospace,
                        style = CodeTextStyle.copy(fontSize = (14 * scale).sp, lineHeight = (22 * scale).sp),
                        color = extra.codeText,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Kit lesson table: header row and aligned cells with hairline dividers; scrolls horizontally when wide. */
@Composable
fun TableBlock(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    val cols = rows.maxOf { it.size }
    val widths = remember(rows) {
        (0 until cols).map { c ->
            val maxLen = rows.maxOf { r -> r.getOrNull(c)?.length ?: 0 }
            (maxLen * 7 + 28).coerceIn(96, 260).dp
        }
    }
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column {
            rows.forEachIndexed { r, row ->
                Row(Modifier.semantics { if (r == 0) heading() }) {
                    for (c in 0 until cols) {
                        MdText(
                            row.getOrNull(c) ?: "",
                            modifier = Modifier.width(widths[c]).padding(end = 12.dp, top = 10.dp, bottom = 10.dp),
                            style = if (r == 0) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Box(Modifier.width(widths.fold(0.dp) { acc, w -> acc + w }).height(1.dp).background(line))
            }
        }
    }
}

/** Prose style from the reader settings (text size and line spacing, in sp so system font scale still applies). */
@Composable
fun readerTextStyle(): androidx.compose.ui.text.TextStyle {
    val size by AppGraph.settings.readerTextSize.collectAsState()
    val spacing by AppGraph.settings.readerLineSpacing.collectAsState()
    return MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp, lineHeight = (size * spacing).sp)
}

/** Renders one lesson block in the kit's handbook style. */
@Composable
fun BlockView(block: Block) {
    val prose = readerTextStyle()
    when (block) {
        is Block.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 6.dp).semantics { heading() },
        )
        is Block.Paragraph -> MdText(block.text, modifier = Modifier.padding(vertical = 6.dp), style = prose, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is Block.Code -> CodeBlock(block.code, block.lang, modifier = Modifier.padding(vertical = 8.dp))
        is Block.Bullets -> Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEach { item ->
                val special = item.startsWith("✅") || item.startsWith("❌")
                Row {
                    if (!special) {
                        Text("•", style = prose, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                    }
                    MdText(item, modifier = Modifier.weight(1f), style = prose, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        is Block.Numbered -> Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { idx, item ->
                Row {
                    Text("${idx + 1}.", style = prose, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                    MdText(item, modifier = Modifier.weight(1f), style = prose, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        is Block.Note -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Overline("Field note")
                Spacer(Modifier.height(8.dp))
                MdText(block.text, style = prose, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        is Block.Table -> Box(Modifier.padding(vertical = 8.dp)) { TableBlock(block.rows) }
    }
}

// ---------------------------------------------------------------------- syntax highlighting

private val KEYWORDS = setOf(
    // python
    "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "is", "import", "from", "as",
    "with", "try", "except", "finally", "raise", "lambda", "yield", "async", "await", "pass", "break", "continue", "None",
    "True", "False", "global", "nonlocal", "match", "case", "type", "assert", "del",
    // js/kotlin
    "const", "let", "var", "function", "new", "export", "val", "fun", "null", "true", "false",
    // sql / cypher
    "SELECT", "FROM", "WHERE", "ORDER", "BY", "LIMIT", "GROUP", "INSERT", "INTO", "VALUES", "CREATE", "TABLE", "INDEX", "ON",
    "USING", "UNION", "ALL", "WITH", "AS", "SUM", "DESC", "ASC", "MATCH", "MERGE", "RETURN", "SET", "DELETE", "OPTIONAL",
    "DISTINCT", "CALL", "YIELD", "EXTENSION", "JOIN", "COUNT", "AND", "OR", "NOT",
    // bash
    "export", "pip", "uv", "docker", "curl",
)

private fun commentPrefix(lang: String): String = when (lang.lowercase()) {
    "python", "bash", "sh", "shell", "yaml", "toml", "text", "dockerfile" -> "#"
    "sql" -> "--"
    "json" -> "\u0000"
    else -> "//"
}

fun highlight(code: String, lang: String, c: ExtraColors): AnnotatedString = buildAnnotatedString {
    val comment = commentPrefix(lang)
    val alsoHash = lang.lowercase() in setOf("cypher", "text")
    var i = 0
    val n = code.length
    while (i < n) {
        val ch = code[i]
        // comments to end of line
        if (code.startsWith(comment, i) || (alsoHash && ch == '#') || (lang.lowercase() == "python" && ch == '#')) {
            val end = code.indexOf('\n', i).let { if (it < 0) n else it }
            pushStyle(SpanStyle(color = c.codeComment, fontStyle = FontStyle.Italic))
            append(code.substring(i, end)); pop()
            i = end
            continue
        }
        // strings
        if (ch == '"' || ch == '\'') {
            var j = i + 1
            while (j < n && code[j] != ch && code[j] != '\n') {
                if (code[j] == '\\' && j + 1 < n) j++
                j++
            }
            val end = if (j < n && code[j] == ch) j + 1 else j
            pushStyle(SpanStyle(color = c.codeString))
            append(code.substring(i, end)); pop()
            i = end
            continue
        }
        // decorators
        if (ch == '@' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '_')) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
            pushStyle(SpanStyle(color = c.codeDecorator))
            append(code.substring(i, j)); pop()
            i = j
            continue
        }
        // identifiers / keywords / function calls
        if (ch.isLetter() || ch == '_') {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            val word = code.substring(i, j)
            when {
                word in KEYWORDS -> {
                    pushStyle(SpanStyle(color = c.codeKeyword, fontWeight = FontWeight.SemiBold)); append(word); pop()
                }
                j < n && code[j] == '(' -> {
                    pushStyle(SpanStyle(color = c.codeFunction)); append(word); pop()
                }
                else -> append(word)
            }
            i = j
            continue
        }
        // numbers
        if (ch.isDigit()) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
            pushStyle(SpanStyle(color = c.codeNumber)); append(code.substring(i, j)); pop()
            i = j
            continue
        }
        append(ch)
        i++
    }
}
