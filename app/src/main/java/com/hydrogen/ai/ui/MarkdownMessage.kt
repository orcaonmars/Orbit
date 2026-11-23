package com.hydrogen.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hydrogen.ai.model.CodeHighlighter
import com.hydrogen.ai.model.CodeTokenKind
import com.hydrogen.ai.model.MdAlign
import com.hydrogen.ai.model.MarkdownParser
import com.hydrogen.ai.model.MdBlock
import com.hydrogen.ai.model.MdSpan
import com.hydrogen.ai.model.TableCsv
import com.hydrogen.ai.model.CodeFileName
import com.hydrogen.ai.ui.theme.OrbitTheme
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Renders a model reply as Markdown: headings, lists, quotes, links, tables and copyable code blocks, spaced and
 * colored as docs/ORBIT_DESIGN.md ("Markdown") describes.
 */
@Composable
fun MarkdownMessage(content: String, modifier: Modifier = Modifier) {
    val blocks = remember(content) { MarkdownParser.parse(content) }
    SelectionContainer(modifier = modifier) {
        Column {
            blocks.forEachIndexed { i, block ->
                if (i > 0) Spacer(Modifier.height(markdownBlockGap(blocks[i - 1], block).dp))
                MarkdownBlock(block)
            }
        }
    }
}

/**
 * Space in dp above [next] when it follows [previous]. Items of one list sit 6 apart, a heading gets 16 so it groups
 * with what follows it, a rule gets 24 on each side, and everything else is 12 (the paragraph spacing).
 */
internal fun markdownBlockGap(previous: MdBlock, next: MdBlock): Int = when {
    previous is MdBlock.Rule || next is MdBlock.Rule -> 24
    next is MdBlock.Heading -> 16
    previous is MdBlock.ListItem && next is MdBlock.ListItem -> 6
    else -> 12
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    val body = MaterialTheme.typography.bodyLarge
    val orbit = OrbitTheme.colors
    when (block) {
        is MdBlock.Paragraph -> Text(text = rememberAnnotated(block.spans), style = body)
        is MdBlock.Heading -> Text(
            text = rememberAnnotated(block.spans),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.SemiBold
        )
        is MdBlock.ListItem -> Row(modifier = Modifier.padding(start = (block.indent * 16).dp)) {
            Text(
                text = block.marker,
                style = body,
                color = orbit.muted,
                modifier = Modifier.width(if (block.marker.length > 2) 28.dp else 18.dp)
            )
            Text(text = rememberAnnotated(block.spans), style = body)
        }
        is MdBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(orbit.line)
            )
            Spacer(Modifier.width(12.dp))
            Text(text = rememberAnnotated(block.spans), style = body, color = orbit.muted)
        }
        is MdBlock.CodeBlock -> CodeBlockView(block)
        MdBlock.Rule -> HorizontalDivider(thickness = 1.dp, color = orbit.line)
        is MdBlock.Table -> TableView(block)
    }
}

/**
 * Horizontal rules only, a `surface-2` header row and 10 x 12 cell padding. Scrolls sideways inside the reply when
 * wider than it; each column fits its widest cell (measured, capped).
 */
@Composable
private fun TableView(table: MdBlock.Table) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val orbit = OrbitTheme.colors
    val cellStyle = MaterialTheme.typography.bodyMedium
    val widths = remember(table, cellStyle, density) {
        val sample = listOf(table.header) + table.rows.take(MEASURED_TABLE_ROWS)
        table.align.indices.map { c ->
            val widest = sample.withIndex().maxOf { (r, row) ->
                val text = row[c].joinToString("") { it.text }
                val style = if (r == 0) cellStyle.copy(fontWeight = FontWeight.SemiBold) else cellStyle
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            }
            with(density) { widest.toDp() }.coerceIn(24.dp, 220.dp) + (TABLE_CELL_PADDING_H * 2).dp
        }
    }
    val tableWidth = widths.fold(0.dp) { a, b -> a + b }
    val context = LocalContext.current
    fun copy(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own clipboard confirmation.
        if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
    Column {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TableRow(table.header, table.align, widths, header = true)
            HorizontalDivider(thickness = 1.dp, color = orbit.line, modifier = Modifier.width(tableWidth))
            for (row in table.rows) {
                TableRow(row, table.align, widths, header = false)
                HorizontalDivider(thickness = 1.dp, color = orbit.line, modifier = Modifier.width(tableWidth))
            }
        }
        // Tab-separated text pastes into separate spreadsheet cells; CSV is for files and other apps.
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { copy("Table", TableCsv.toTsv(table)) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy table", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
                onClick = { copy("CSV", TableCsv.toCsv(table)) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Copy CSV", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private const val MEASURED_TABLE_ROWS = 100
private const val TABLE_CELL_PADDING_H = 12
private const val TABLE_CELL_PADDING_V = 10

@Composable
private fun TableRow(cells: List<List<MdSpan>>, align: List<MdAlign>, widths: List<Dp>, header: Boolean) {
    Row(
        modifier = if (header) Modifier.background(OrbitTheme.colors.surface2) else Modifier
    ) {
        cells.forEachIndexed { c, spans ->
            Text(
                text = rememberAnnotated(spans),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else null,
                textAlign = when (align[c]) {
                    MdAlign.START -> TextAlign.Start
                    MdAlign.CENTER -> TextAlign.Center
                    MdAlign.END -> TextAlign.End
                },
                modifier = Modifier
                    .width(widths[c])
                    .padding(horizontal = TABLE_CELL_PADDING_H.dp, vertical = TABLE_CELL_PADDING_V.dp)
            )
        }
    }
}

/**
 * Token colours for the `code` background (docs/ORBIT_DESIGN.md, "Code colors"). Warm like the rest of the
 * palette; every token meets WCAG AA (4.5:1) on `code` in Night, Paper and Midnight (see ThemeContrastTest).
 */
internal data class CodePalette(val keyword: Color, val string: Color, val comment: Color, val number: Color)

internal val PaperCodePalette = CodePalette(
    keyword = Color(0xFF9A520A),
    string = Color(0xFF44702A),
    comment = Color(0xFF6F675A),
    number = Color(0xFFA8401F)
)

internal val NightCodePalette = CodePalette(
    keyword = Color(0xFFF2B14C),
    string = Color(0xFFA8CF8E),
    comment = Color(0xFF8E8678),
    number = Color(0xFFF29A7E)
)

internal fun codePaletteFor(dark: Boolean): CodePalette = if (dark) NightCodePalette else PaperCodePalette

private fun highlight(code: String, language: String, palette: CodePalette): AnnotatedString {
    val tokens = runCatching { CodeHighlighter.tokenize(code, language) }.getOrDefault(emptyList())
    if (tokens.isEmpty()) return AnnotatedString(code)
    return buildAnnotatedString {
        append(code)
        for (token in tokens) {
            val start = token.start.coerceIn(0, code.length)
            val end = token.end.coerceIn(start, code.length)
            if (start == end) continue
            val style = when (token.kind) {
                CodeTokenKind.KEYWORD -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.Medium)
                CodeTokenKind.STRING -> SpanStyle(color = palette.string)
                CodeTokenKind.COMMENT -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
                CodeTokenKind.NUMBER -> SpanStyle(color = palette.number)
                CodeTokenKind.PLAIN -> null
            }
            if (style != null) addStyle(style, start, end)
        }
    }
}

@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock) {
    val context = LocalContext.current
    val orbit = OrbitTheme.colors
    val background = orbit.code
    val palette = codePaletteFor(orbit.dark)
    val highlighted = remember(block.code, block.language, palette) {
        highlight(block.code, block.language, palette)
    }
    // octet-stream so the file picker keeps the suggested extension (.py, .kt...) instead of appending .txt.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(block.code.toByteArray(Charsets.UTF_8)) }
                ?: error("no stream")
        }.isSuccess
        Toast.makeText(context, if (saved) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
    }
    // "Copy" reads "Copied" for 1.5 s after a tap (docs/ORBIT_DESIGN.md, "Code block").
    var copiedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(copiedAt) {
        if (copiedAt != 0L) {
            delay(COPIED_LABEL_MS)
            copiedAt = 0L
        }
    }
    val copied = copiedAt != 0L
    Surface(
        color = background,
        contentColor = orbit.text,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, orbit.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // A quiet header on the same `code` fill, split from the code by a hairline: the language in the faint
            // code font on the left, Copy and Save on the right in the accent.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp)
                    .padding(start = 16.dp, end = 4.dp)
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = orbit.faint,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Orbit code", block.code))
                        copiedAt = System.nanoTime().coerceAtLeast(1L)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (copied) "Copied" else "Copy", style = MaterialTheme.typography.labelSmall)
                }
                if (block.closed && block.code.isNotBlank()) {
                    TextButton(
                        onClick = { saveLauncher.launch(CodeFileName.suggest(block.language, block.code)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Save", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = orbit.line)
            Text(
                text = highlighted,
                style = CodeTextStyle,
                color = orbit.text,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun rememberAnnotated(spans: List<MdSpan>): AnnotatedString {
    val codeBackground = OrbitTheme.colors.code
    val linkColor = OrbitTheme.colors.accent
    return remember(spans, codeBackground, linkColor) { spansToAnnotated(spans, codeBackground, linkColor) }
}

/** Code text: 14 sp at 1.55 (docs/ORBIT_DESIGN.md, section 4). */
private val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp)

private const val COPIED_LABEL_MS = 1_500L

private fun spansToAnnotated(spans: List<MdSpan>, codeBackground: Color, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        for (span in spans) {
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                // Inline code at 0.92 em on the `code` fill. Spans can't take a radius or padding, so the fill is square.
                fontSize = if (span.code) 0.92.em else TextUnit.Unspecified,
                background = if (span.code) codeBackground else Color.Unspecified
            )
            val url = span.url
            if (url != null) {
                // The spec underlines links on hover only. A phone has no hover, so links keep the underline: color
                // alone would be the only cue that the text is a link.
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    )
                ) {
                    withStyle(style) { append(span.text) }
                }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
