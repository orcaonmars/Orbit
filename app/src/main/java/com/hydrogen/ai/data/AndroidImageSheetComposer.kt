package com.hydrogen.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.HydrogenDefaults
import com.hydrogen.ai.model.ImageSheetPlanner
import com.hydrogen.ai.network.ImageSheetComposer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws photos into one grid image with each tile's photo number in its corner, saved as a temporary JPEG in
 * [outDir] (app cache) and deleted once its request is done. Originals are only read.
 */
class AndroidImageSheetComposer(
    private val context: Context,
    private val outDir: File,
    private val maxBytes: Long = HydrogenDefaults.DEFAULT_MAX_IMAGE_BYTES
) : ImageSheetComposer {

    init {
        // Grids left behind by a crash or a killed process. Recent ones may belong to a send still in progress.
        val cutoff = System.currentTimeMillis() - 30 * 60 * 1000L
        runCatching {
            outDir.listFiles()?.filter { it.name.startsWith(PREFIX) && it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    override suspend fun compose(items: List<AttachmentManifestItem>, firstNumber: Int): AttachmentManifestItem? =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext null
            var sheet: Bitmap? = null
            try {
                val layout = ImageSheetPlanner.layout(items.size)
                sheet = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet)
                canvas.drawColor(BACKGROUND)
                val tilePaint = Paint().apply { color = TILE }
                val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                var anyDrawn = false
                items.forEachIndexed { i, item ->
                    val cell = layout.cells[i]
                    canvas.drawRect(Rect(cell.x, cell.y, cell.x + cell.width, cell.y + cell.height), tilePaint)
                    val photo = (AndroidImageCodec.read(context, item.localUri, item.mimeType, maxBytes)
                        as? AndroidImageCodec.Read.Bytes)?.let { AndroidImageCodec.decodeUpright(it.data, cell.width) }
                    if (photo != null) {
                        val fit = ImageSheetPlanner.fitInside(photo.width, photo.height, cell)
                        canvas.drawBitmap(photo, null, Rect(fit.x, fit.y, fit.x + fit.width, fit.y + fit.height), imagePaint)
                        photo.recycle()
                        anyDrawn = true
                    } else {
                        drawCentred(canvas, "Photo ${firstNumber + i} could not be read", cell, cell.width / 16f)
                    }
                    drawNumber(canvas, (firstNumber + i).toString(), cell)
                }
                if (!anyDrawn) return@withContext null
                outDir.mkdirs()
                val file = File(outDir, "$PREFIX${UUID.randomUUID()}.jpg")
                file.writeBytes(AndroidImageCodec.encodeJpeg(sheet))
                val last = firstNumber + items.size - 1
                AttachmentManifestItem(
                    id = "sheet-" + file.nameWithoutExtension.removePrefix(PREFIX),
                    displayName = if (items.size == 1) "Photo $firstNumber" else "Photos $firstNumber to $last",
                    mimeType = "image/jpeg",
                    sizeBytes = file.length(),
                    localUri = file.toURI().toString()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Out of memory or an unreadable photo: the caller says which photos could not be included.
                null
            } finally {
                sheet?.recycle()
            }
        }

    override fun discard(item: AttachmentManifestItem) {
        val file = runCatching { File(java.net.URI.create(item.localUri)) }.getOrNull() ?: return
        // Only ever delete grids this composer made.
        if (file.parentFile?.canonicalPath == outDir.canonicalPath && file.name.startsWith(PREFIX)) file.delete()
    }

    private fun drawNumber(canvas: Canvas, label: String, cell: ImageSheetPlanner.Cell) {
        val textSize = (cell.width * 0.09f).coerceIn(22f, 72f)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val padX = textSize * 0.45f
        val padY = textSize * 0.25f
        val width = text.measureText(label) + padX * 2
        val height = textSize + padY * 2
        val inset = textSize * 0.3f
        val box = RectF(cell.x + inset, cell.y + inset, cell.x + inset + width, cell.y + inset + height)
        canvas.drawRoundRect(box, textSize * 0.2f, textSize * 0.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BADGE })
        canvas.drawText(label, box.left + padX, box.bottom - padY - text.descent() * 0.6f, text)
    }

    private fun drawCentred(canvas: Canvas, label: String, cell: ImageSheetPlanner.Cell, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = size.coerceAtLeast(14f)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, cell.x + cell.width / 2f, cell.y + cell.height / 2f, paint)
    }

    private companion object {
        const val PREFIX = "orbit_grid_"
        val BACKGROUND = Color.rgb(32, 33, 36)
        val TILE = Color.rgb(48, 49, 52)
        val BADGE = Color.argb(215, 0, 0, 0)
    }
}
