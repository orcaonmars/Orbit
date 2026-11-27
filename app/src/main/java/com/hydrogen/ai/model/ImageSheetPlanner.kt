package com.hydrogen.ai.model

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Fits more photos than a provider allows into one request. The first photos go as they are; the rest are combined
 * into numbered grid images (sheets), so every photo still reaches the model. Smaller grids come first: a 2 by 2
 * sheet keeps each photo readable, and only a very large batch uses 3 by 3.
 */
object ImageSheetPlanner {

    /** The longest edge, in pixels, of a sheet and of any photo Orbit sends. */
    const val MAX_EDGE_PX = 1568

    /** Tiles per sheet, tried in order. */
    private val TILE_STEPS = intArrayOf(4, 6, 9)

    data class Plan<T>(
        /** Photos sent as they are, in their original order. */
        val singles: List<T>,
        /** Photos combined into one sheet each, in their original order. */
        val sheets: List<List<T>>,
        /** Photos that still don't fit, even at 9 per sheet. */
        val dropped: List<T>
    ) {
        val sentCount: Int get() = singles.size + sheets.size
    }

    fun <T> plan(images: List<T>, slots: Int): Plan<T> {
        if (slots <= 0) return Plan(emptyList(), emptyList(), images)
        if (images.size <= slots) return Plan(images, emptyList(), emptyList())
        val n = images.size
        val extra = n - slots
        // Each sheet with T tiles fits T - 1 more photos than a single slot does.
        var tiles = TILE_STEPS.last()
        var sheetCount = slots
        for (step in TILE_STEPS) {
            val needed = ceilDiv(extra, step - 1)
            if (needed <= slots) {
                tiles = step
                sheetCount = needed
                break
            }
        }
        val singleCount = slots - sheetCount
        val singles = images.take(singleCount)
        val rest = images.drop(singleCount)
        val fitting = rest.take(sheetCount * tiles)
        val dropped = rest.drop(sheetCount * tiles)
        val sheets = ArrayList<List<T>>(sheetCount)
        val base = fitting.size / sheetCount
        val remainder = fitting.size % sheetCount
        var start = 0
        for (i in 0 until sheetCount) {
            val size = base + if (i < remainder) 1 else 0
            if (size == 0) continue
            sheets += fitting.subList(start, start + size)
            start += size
        }
        return Plan(singles, sheets, dropped)
    }

    data class Cell(val x: Int, val y: Int, val width: Int, val height: Int)

    data class Layout(val width: Int, val height: Int, val columns: Int, val rows: Int, val cells: List<Cell>)

    /** Square tiles in a near-square grid that fits within [maxEdge], with [gap] pixels between and around them. */
    fun layout(count: Int, maxEdge: Int = MAX_EDGE_PX, gap: Int = 8): Layout {
        val n = count.coerceAtLeast(1)
        val columns = ceil(sqrt(n.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceilDiv(n, columns)
        val longest = maxOf(columns, rows)
        val tile = ((maxEdge - gap * (longest + 1)) / longest).coerceAtLeast(16)
        val width = columns * tile + gap * (columns + 1)
        val height = rows * tile + gap * (rows + 1)
        val cells = (0 until n).map { i ->
            val col = i % columns
            val row = i / columns
            Cell(gap + col * (tile + gap), gap + row * (tile + gap), tile, tile)
        }
        return Layout(width, height, columns, rows, cells)
    }

    /** Where an image of [srcW] by [srcH] sits, scaled to fit and centred, inside [cell]. */
    fun fitInside(srcW: Int, srcH: Int, cell: Cell): Cell {
        if (srcW <= 0 || srcH <= 0) return cell
        val scale = minOf(cell.width.toDouble() / srcW, cell.height.toDouble() / srcH)
        val w = (srcW * scale).toInt().coerceIn(1, cell.width)
        val h = (srcH * scale).toInt().coerceIn(1, cell.height)
        return Cell(cell.x + (cell.width - w) / 2, cell.y + (cell.height - h) / 2, w, h)
    }

    /**
     * The note added to the copy of the message that is sent, so the model knows which grid holds which photo.
     * [sheetSizes] is the tile count of each sheet, in order after the [singles].
     */
    fun note(totalPhotos: Int, singles: Int, sheetSizes: List<Int>, droppedCount: Int): String {
        if (sheetSizes.isEmpty() && droppedCount == 0) return ""
        val parts = mutableListOf<String>()
        if (sheetSizes.isNotEmpty()) {
            var next = singles + 1
            val ranges = sheetSizes.map { size ->
                val range = if (size == 1) "photo $next" else "photos $next to ${next + size - 1}"
                next += size
                range
            }
            val lead = when (singles) {
                0 -> ""
                1 -> "Photo 1 is attached as it is. "
                else -> "Photos 1 to $singles are attached as they are. "
            }
            parts += lead + "The other photos are combined into numbered grid images, to stay within this " +
                "service's image limit: " + ranges.joinToString("; ") + ". Each grid tile shows its photo number " +
                "in the corner."
        }
        if (droppedCount > 0) {
            parts += "$droppedCount more of the $totalPhotos photos could not be included."
        }
        return "[Orbit note: " + parts.joinToString(" ") + "]"
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
