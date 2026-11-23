package com.hydrogen.ai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.hydrogen.ai.model.MarkdownParser
import com.hydrogen.ai.model.MdAlign
import com.hydrogen.ai.model.MdBlock
import com.hydrogen.ai.model.MdSpan
import com.hydrogen.ai.model.PdfMarkdown
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Turns an AI-made document (Markdown from a `<pdf>` reply) into an A4 PDF with the phone's own fonts: a title,
 * headings, paragraphs, lists, quotes, code and real table grids, broken across pages with a footer on each.
 */
object PdfExport {

    data class Preview(val bitmap: Bitmap, val pages: Int)

    /**
     * Writes the PDF into the app's cache (`documents/`, the only folder shared through the file provider) and
     * returns it. The same document is written once; folders older than three days are cleared first.
     */
    fun writeToCache(context: Context, title: String, markdown: String): File {
        val root = File(context.cacheDir, "documents")
        clearOld(root)
        // The layout version is part of the key, so an update that changes how PDFs look never reuses an old file.
        val dir = File(root, digest("$LAYOUT_VERSION\u0000$title\u0000$markdown")).apply { mkdirs() }
        val file = File(dir, PdfMarkdown.fileName(title))
        if (file.isFile && file.length() > 0) return file
        val partial = File(dir, "${file.name}.part")
        partial.outputStream().use { render(title, markdown, it) }
        if (!partial.renameTo(file)) {
            partial.delete()
            error("Could not save the PDF")
        }
        return file
    }

    /** The first page as a bitmap [widthPx] wide, plus the page count. */
    fun preview(file: File, widthPx: Int): Preview =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    Preview(bitmap, renderer.pageCount)
                }
            }
        }

    /** Renders [markdown] under [title] into [out] and returns the number of pages. */
    fun render(title: String, markdown: String, out: OutputStream): Int {
        val document = PdfDocument()
        try {
            val pager = Pager(document, title)
            val blocks = MarkdownParser.parse(markdown)
            val first = blocks.firstOrNull()
            val hasOwnTitle = first is MdBlock.Heading && first.level == 1
            var previous: MdBlock? = null
            if (!hasOwnTitle) {
                pager.heading(MdBlock.Heading(1, listOf(MdSpan(title))))
                previous = MdBlock.Heading(1, emptyList())
            }
            for (block in blocks) {
                if (pager.pageCount > MAX_PAGES) break
                if (previous != null) pager.gap(gap(previous, block))
                pager.block(block)
                previous = block
            }
            pager.finish()
            document.writeTo(out)
            return pager.pageCount
        } finally {
            document.close()
        }
    }

    private fun gap(previous: MdBlock, next: MdBlock): Float = when {
        previous is MdBlock.Rule || next is MdBlock.Rule -> 10f
        next is MdBlock.Heading -> if (next.level <= 2) 18f else 14f
        previous is MdBlock.Heading -> 7f
        previous is MdBlock.ListItem && next is MdBlock.ListItem -> 4f
        else -> 9f
    }

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(10).joinToString("") { "%02x".format(it) }

    private fun clearOld(root: File) {
        val cutoff = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 56f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val MAX_PAGES = 250

    /** Raise when the PDF layout or the Markdown it reads changes, so cached files are made again. */
    private const val LAYOUT_VERSION = 2

    private const val INK = 0xFF1C1C1F.toInt()
    private const val MUTED = 0xFF5D6068.toInt()
    private const val FAINT = 0xFF8D9097.toInt()
    private const val LINE = 0xFFD5D7DC.toInt()
    private const val CODE_BACKGROUND = 0xFFF3F3F1.toInt()
    private const val INLINE_CODE_BACKGROUND = 0xFFECECE9.toInt()
    private const val TABLE_HEADER_BACKGROUND = 0xFFF1F2F4.toInt()
    private const val LINK = 0xFF1F5FBF.toInt()

    private val SANS: Typeface = Typeface.DEFAULT
    private val SANS_BOLD: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val MONO: Typeface = Typeface.MONOSPACE

    private fun paint(size: Float, color: Int = INK, typeface: Typeface = SANS) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            this.typeface = typeface
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Float,
        spacing: Float = 1.32f,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .build()

    private fun styled(spans: List<MdSpan>): CharSequence {
        val text = SpannableStringBuilder()
        for (span in spans) {
            if (span.text.isEmpty()) continue
            val start = text.length
            text.append(span.text)
            val end = text.length
            val style = when {
                span.bold && span.italic -> Typeface.BOLD_ITALIC
                span.bold -> Typeface.BOLD
                span.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) text.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (span.code) {
                text.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(RelativeSizeSpan(0.9f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(BackgroundColorSpan(INLINE_CODE_BACKGROUND), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (span.url != null) {
                text.setSpan(ForegroundColorSpan(LINK), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return text
    }

    /** Lays blocks top to bottom and starts a new page when one is full. */
    private class Pager(private val document: PdfDocument, private val title: String) {
        private val top = MARGIN
        private val bottom = PAGE_HEIGHT - MARGIN
        private val left = MARGIN
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = top
        var pageCount = 0
            private set

        private val body = paint(10.5f)
        private val quote = paint(10.5f, MUTED)
        private val code = paint(8.8f, INK, MONO)
        private val cell = paint(9.5f)
        private val headerCell = paint(9.5f, INK, SANS_BOLD)
        private val bullet = paint(10.5f, MUTED)
        private val footer = paint(8f, FAINT)
        private val rule = stroke(LINE, 0.8f)
        private val grid = stroke(LINE, 0.6f)
        private val codeFill = fill(CODE_BACKGROUND)
        private val headerFill = fill(TABLE_HEADER_BACKGROUND)
        private val bar = fill(LINE)
        private val bodyLine = body.fontSpacing * 1.32f

        init {
            newPage()
        }

        fun gap(points: Float) {
            if (y > top) y += points
        }

        fun block(block: MdBlock) {
            when (block) {
                is MdBlock.Heading -> heading(block)
                is MdBlock.Paragraph -> {
                    val text = layout(styled(block.spans), body, CONTENT_WIDTH)
                    keep(minOf(text.height.toFloat(), bodyLine * 2))
                    flow(text, left)
                }
                is MdBlock.ListItem -> listItem(block)
                is MdBlock.Quote -> {
                    val text = layout(styled(block.spans), quote, CONTENT_WIDTH - 14f)
                    keep(minOf(text.height.toFloat(), bodyLine * 2))
                    flow(text, left + 14f, decorate = { c, from, to -> c.drawRect(left, from, left + 2.5f, to, bar) })
                }
                is MdBlock.CodeBlock -> {
                    val text = layout(block.code.ifEmpty { " " }, code, CONTENT_WIDTH - 20f, spacing = 1.25f)
                    keep(minOf(text.height.toFloat() + 16f, code.fontSpacing * 3 + 16f))
                    flow(text, left + 10f, padTop = 8f, padBottom = 8f, decorate = { c, from, to ->
                        c.drawRoundRect(RectF(left, from, left + CONTENT_WIDTH, to), 4f, 4f, codeFill)
                    })
                }
                MdBlock.Rule -> {
                    keep(12f)
                    y += 6f
                    canvas.drawLine(left, y, left + CONTENT_WIDTH, y, rule)
                    y += 6f
                }
                is MdBlock.Table -> table(block)
            }
        }

        fun heading(block: MdBlock.Heading) {
            val (size, face) = when (block.level) {
                1 -> 20f to SANS_BOLD
                2 -> 15f to SANS_BOLD
                3 -> 12.5f to SANS_BOLD
                else -> 11f to SANS_BOLD
            }
            val text = layout(styled(block.spans), paint(size, INK, face), CONTENT_WIDTH, spacing = 1.2f)
            // A heading stays with at least two lines of what follows it.
            keep(text.height + bodyLine * 2 + 8f)
            flow(text, left)
        }

        private fun listItem(item: MdBlock.ListItem) {
            val indent = item.indent.coerceIn(0, 4) * 16f
            val numbered = item.marker != "•"
            val markerWidth = if (numbered) maxOf(16f, body.measureText(item.marker) + 6f) else 13f
            val x = left + indent + markerWidth
            val text = layout(styled(item.spans), body, CONTENT_WIDTH - indent - markerWidth)
            keep(minOf(text.height.toFloat(), bodyLine * 2))
            val markerPaint = if (numbered) body else bullet
            flow(text, x, firstBaseline = { c, baseline -> c.drawText(item.marker, left + indent, baseline, markerPaint) })
        }

        private fun table(table: MdBlock.Table) {
            val columns = table.align.size
            if (columns == 0) return
            val padX = 6f
            val padY = 4.5f
            val header = table.header.map { styled(it) }
            val rows = table.rows.map { row -> row.map { styled(it) } }
            // Column widths follow the widest cell of each column, capped so one long cell can't crowd the rest,
            // then stretch to the page width.
            val natural = FloatArray(columns) { c ->
                val widest = (listOfNotNull(header.getOrNull(c)) + rows.mapNotNull { it.getOrNull(c) })
                    .maxOfOrNull { Layout.getDesiredWidth(it, headerCell) } ?: 0f
                widest.coerceIn(24f, 220f) + padX * 2
            }
            val scale = CONTENT_WIDTH / natural.sum()
            val widths = natural.map { it * scale }
            val alignments = table.align.map {
                when (it) {
                    MdAlign.START -> Layout.Alignment.ALIGN_NORMAL
                    MdAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    MdAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
                }
            }

            fun rowLayouts(cells: List<CharSequence>, paint: TextPaint) = List(columns) { c ->
                layout(cells.getOrElse(c) { "" }, paint, widths[c] - padX * 2, spacing = 1.2f, align = alignments[c])
            }

            fun height(layouts: List<StaticLayout>) = layouts.maxOf { it.height } + padY * 2

            val headerLayouts = rowLayouts(header, headerCell)
            val headerHeight = height(headerLayouts)

            fun drawRow(layouts: List<StaticLayout>, rowHeight: Float, isHeader: Boolean, firstOnPage: Boolean) {
                val right = left + CONTENT_WIDTH
                if (isHeader) canvas.drawRect(left, y, right, y + rowHeight, headerFill)
                if (firstOnPage) canvas.drawLine(left, y, right, y, grid)
                var x = left
                layouts.forEachIndexed { c, cellLayout ->
                    canvas.save()
                    canvas.clipRect(x, y, x + widths[c], y + rowHeight)
                    canvas.translate(x + padX, y + padY)
                    cellLayout.draw(canvas)
                    canvas.restore()
                    canvas.drawLine(x, y, x, y + rowHeight, grid)
                    x += widths[c]
                }
                canvas.drawLine(right, y, right, y + rowHeight, grid)
                canvas.drawLine(left, y + rowHeight, right, y + rowHeight, grid)
                y += rowHeight
            }

            val firstRow = rows.firstOrNull()?.let { rowLayouts(it, cell) }
            keep(headerHeight + (firstRow?.let { height(it) } ?: 0f))
            drawRow(headerLayouts, headerHeight, isHeader = true, firstOnPage = true)
            rows.forEachIndexed { index, cells ->
                val layouts = if (index == 0 && firstRow != null) firstRow else rowLayouts(cells, cell)
                val rowHeight = height(layouts)
                if (y + rowHeight > bottom && rowHeight + headerHeight <= bottom - top) {
                    // The header repeats at the top of each new page.
                    newPage()
                    drawRow(headerLayouts, headerHeight, isHeader = true, firstOnPage = true)
                }
                drawRow(layouts, rowHeight, isHeader = false, firstOnPage = false)
            }
        }

        /** Starts a new page unless [height] still fits on this one (or the page is empty). */
        private fun keep(height: Float) {
            if (y > top && y + height > bottom) newPage()
        }

        /**
         * Draws [layout] at [x], line by line across as many pages as it needs. [decorate] paints behind each
         * page's slice (a code background, a quote bar); [firstBaseline] gets the first line's baseline (list
         * markers).
         */
        private fun flow(
            layout: StaticLayout,
            x: Float,
            padTop: Float = 0f,
            padBottom: Float = 0f,
            decorate: ((Canvas, Float, Float) -> Unit)? = null,
            firstBaseline: ((Canvas, Float) -> Unit)? = null
        ) {
            val count = layout.lineCount
            var line = 0
            while (line < count) {
                val sliceTop = layout.getLineTop(line)
                val padBefore = if (line == 0) padTop else 0f
                var end = line
                while (end < count) {
                    val padAfter = if (end == count - 1) padBottom else 0f
                    if (y + padBefore + (layout.getLineBottom(end) - sliceTop) + padAfter > bottom) break
                    end++
                }
                if (end == line) {
                    if (y > top) {
                        newPage()
                        continue
                    }
                    end = line + 1 // Taller than a whole page: draw it anyway, cut at the margin.
                }
                val padAfter = if (end == count) padBottom else 0f
                val textHeight = (layout.getLineBottom(end - 1) - sliceTop).toFloat()
                decorate?.invoke(canvas, y, y + padBefore + textHeight + padAfter)
                canvas.save()
                canvas.clipRect(x - 2f, y + padBefore, x + layout.width + 2f, y + padBefore + textHeight)
                canvas.translate(x, y + padBefore - sliceTop)
                layout.draw(canvas)
                canvas.restore()
                if (line == 0) firstBaseline?.invoke(canvas, y + padBefore + layout.getLineBaseline(0))
                y += padBefore + textHeight + padAfter
                line = end
                if (line < count) newPage()
            }
        }

        private fun newPage() {
            finishPage()
            pageCount++
            val next = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create())
            page = next
            canvas = next.canvas
            y = top
        }

        private fun finishPage() {
            val current = page ?: return
            val baseline = PAGE_HEIGHT - MARGIN / 2
            val number = "Page $pageCount"
            val numberWidth = footer.measureText(number)
            canvas.drawText(number, left + CONTENT_WIDTH - numberWidth, baseline, footer)
            val name = TextUtils.ellipsize(title, footer, CONTENT_WIDTH - numberWidth - 24f, TextUtils.TruncateAt.END)
            canvas.drawText(name, 0, name.length, left, baseline, footer)
            document.finishPage(current)
            page = null
        }

        fun finish() = finishPage()
    }
}
