package com.hydrogen.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.hydrogen.ai.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import com.hydrogen.ai.model.CodeExportSnapshot
import com.hydrogen.ai.model.ReplyLayoutPolicy
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

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons

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
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        is MdBlock.ListItem -> Row(modifier = Modifier.padding(start = OrbitTheme.space.m * block.indent)) {
            Text(
                text = block.marker,
                style = body,
                color = orbit.muted,
                modifier = Modifier.widthIn(min = OrbitTheme.space.xl).padding(end = OrbitTheme.space.s)
            )
            Text(text = rememberAnnotated(block.spans), style = body, modifier = Modifier.weight(1f))
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
@OptIn(ExperimentalLayoutApi::class)
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
    val naturalWidth = widths.fold(0.dp) { a, b -> a + b }
    val context = LocalContext.current
    fun copy(label: String, text: String) {
        if (!copyOrbitText(context, label, text)) {
            Toast.makeText(context, R.string.markdown_copy_failed, Toast.LENGTH_SHORT).show()
        } else if (android.os.Build.VERSION.SDK_INT < 33) {
            // Android 13+ shows its own clipboard confirmation.
            Toast.makeText(context, R.string.markdown_copied, Toast.LENGTH_SHORT).show()
        }
    }
    val space = OrbitTheme.space
    Column {
        Surface(shape = OrbitTheme.shapes.m, border = BorderStroke(1.dp, orbit.line),
            color = orbit.bg, contentColor = orbit.text, modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints {
                // A narrow table fills the card (columns grow in proportion) so the header shade and rules run edge
                // to edge instead of stopping halfway; a wide one keeps its measured widths and scrolls sideways.
                val stretch = if (naturalWidth < maxWidth && naturalWidth > 0.dp) maxWidth / naturalWidth else 1f
                val shown = if (stretch > 1f) widths.map { it * stretch } else widths
                val tableWidth = if (stretch > 1f) maxWidth else naturalWidth
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TableRow(table.header, table.align, shown, header = true, width = tableWidth)
                    for (row in table.rows) {
                        HorizontalDivider(thickness = 1.dp, color = orbit.line, modifier = Modifier.width(tableWidth))
                        TableRow(row, table.align, shown, header = false, width = tableWidth)
                    }
                }
            }
        }
        // Wrap at large font sizes instead of squeezing or clipping the copy actions.
        FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { copy("Table", TableCsv.toTsv(table)) },
                colors = quietActionColors(),
                contentPadding = PaddingValues(horizontal = space.s)
            ) {
                Icon(OrbitIcons.Copy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reply_table_copy), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = { copy("CSV", TableCsv.toCsv(table)) },
                colors = quietActionColors(),
                contentPadding = PaddingValues(horizontal = space.s)
            ) {
                Text(stringResource(R.string.reply_csv_copy), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private const val MEASURED_TABLE_ROWS = 100
private const val TABLE_CELL_PADDING_H = 12
private const val TABLE_CELL_PADDING_V = 10

@Composable
private fun TableRow(cells: List<List<MdSpan>>, align: List<MdAlign>, widths: List<Dp>, header: Boolean, width: Dp) {
    Row(
        modifier = Modifier.width(width).then(if (header) Modifier.background(OrbitTheme.colors.surface2) else Modifier)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock) {
    val context = LocalContext.current
    val orbit = OrbitTheme.colors
    val background = orbit.code
    val palette = codePaletteFor(orbit.dark)
    val highlighted = remember(block.code, block.language, palette) {
        highlight(block.code, block.language, palette)
    }
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<CodeExportSnapshot?>(null) }
    var saving by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.reply_code_saved)
    val failedMessage = stringResource(R.string.reply_code_save_failed)
    // The picker can outlive a change of message version. Snapshot the exact bytes at the tap,
    // and fail closed if the composable was destroyed instead of exporting the wrong reply.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val snapshot = pendingExport
        pendingExport = null
        if (uri != null) {
            if (snapshot == null) {
                Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
            } else {
                saving = true
                scope.launch {
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openOutputStream(uri, "wt")?.use {
                                    it.write(snapshot.code.toByteArray(Charsets.UTF_8))
                                } ?: error("no stream")
                            }.isSuccess
                        }
                        Toast.makeText(context, if (saved) savedMessage else failedMessage, Toast.LENGTH_SHORT).show()
                    } finally {
                        saving = false
                    }
                }
            }
        }
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
        shape = OrbitTheme.shapes.l,
        border = BorderStroke(1.dp, orbit.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            val space = OrbitTheme.space
            val codeLabel = block.language.ifBlank { stringResource(R.string.reply_code_label) }
            val onCopy: () -> Unit = {
                if (copyOrbitText(context, "Orbit code", block.code)) {
                    copiedAt = System.nanoTime().coerceAtLeast(1L)
                } else {
                    copiedAt = 0L
                    Toast.makeText(context, R.string.markdown_copy_failed, Toast.LENGTH_SHORT).show()
                }
            }
            val onSave: () -> Unit = {
                val snapshot = CodeExportSnapshot.capture(block)
                if (snapshot != null && !saving && pendingExport == null) {
                    pendingExport = snapshot
                    try {
                        saveLauncher.launch(snapshot.fileName)
                    } catch (_: android.content.ActivityNotFoundException) {
                        pendingExport = null
                        Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = space.l, end = space.xs)) {
                val stacked = ReplyLayoutPolicy.stackHeader(maxWidth.value, LocalDensity.current.fontScale)
                val label: @Composable (Modifier) -> Unit = { modifier ->
                    Text(codeLabel, style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace, color = orbit.muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
                }
                val actions: @Composable () -> Unit = {
                    CodeHeaderActions(copied, block.closed && block.code.isNotBlank(),
                        saveEnabled = !saving && pendingExport == null, onCopy = onCopy, onSave = onSave)
                }
                if (stacked) {
                    Column(Modifier.fillMaxWidth().padding(top = space.s)) {
                        label(Modifier.fillMaxWidth())
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        label(Modifier.weight(1f))
                        actions()
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
                    .padding(horizontal = OrbitTheme.space.l, vertical = OrbitTheme.space.m)
            )
        }
    }
}

@Composable
private fun CodeHeaderActions(
    copied: Boolean,
    canSave: Boolean,
    saveEnabled: Boolean,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    val space = OrbitTheme.space
    TextButton(onClick = onCopy, colors = quietActionColors(), contentPadding = PaddingValues(horizontal = space.s),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Icon(if (copied) OrbitIcons.Check else OrbitIcons.Copy,
            contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(space.xs))
        Text(stringResource(if (copied) R.string.reply_code_copied else R.string.reply_code_copy),
            style = MaterialTheme.typography.labelMedium)
    }
    if (canSave) {
        TextButton(onClick = onSave, enabled = saveEnabled, colors = quietActionColors(), contentPadding = PaddingValues(horizontal = space.s)) {
            Text(stringResource(R.string.reply_code_save), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Copy, Save and the table actions sit beside the answer, so they use the muted text colour instead of the accent:
 * the reply stays the loudest thing in the bubble, as in other chat apps. They still read as buttons by their icon,
 * ripple and 48 dp touch target.
 */
@Composable
private fun quietActionColors() = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = OrbitTheme.colors.muted)

@Composable
private fun rememberAnnotated(spans: List<MdSpan>): AnnotatedString {
    val codeBackground = OrbitTheme.colors.code
    val linkColor = OrbitTheme.colors.accent
    return remember(spans, codeBackground, linkColor) { spansToAnnotated(spans, codeBackground, linkColor) }
}

/** Code text: 14 sp at 1.55 (docs/ORBIT_DESIGN.md, section 4). */
private val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp)

internal fun copyOrbitText(context: Context, label: String, text: String): Boolean =
    runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }.isSuccess
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
