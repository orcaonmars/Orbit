package com.hydrogen.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI

/** Reads, decodes and re-encodes attachment images on the phone. Originals are only ever read. */
internal object AndroidImageCodec {

    sealed interface Read {
        class Bytes(val data: ByteArray, val mimeType: String) : Read
        class Failed(val reason: String) : Read
    }

    /** The file's bytes, from Orbit's own attachment folder (file:) or a content: address. */
    fun read(context: Context, localUri: String, expectedMimeType: String, maxBytes: Long): Read {
        val parsed = URI.create(localUri)
        val (stream, mime) = if (parsed.scheme == "file") {
            val file = File(parsed)
            if (!file.exists() || !file.isFile) return Read.Failed("Persisted attachment file missing: ${file.name}")
            file.inputStream() to expectedMimeType.takeIf { it.startsWith("image/") }
        } else {
            val uri = Uri.parse(localUri)
            val type = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
                ?: expectedMimeType.takeIf { it.startsWith("image/") }
            (context.contentResolver.openInputStream(uri) ?: return Read.Failed("Cannot open attachment stream.")) to type
        }
        stream.use { input ->
            if (mime == null) return Read.Failed("Unsupported or unknown image MIME type.")
            val bytes = readLimited(input, maxBytes)
                ?: return Read.Failed("Image exceeds max allowed size (${maxBytes / (1024 * 1024)} MB).")
            if (bytes.isEmpty()) return Read.Failed("Attachment file is empty.")
            return Read.Bytes(bytes, mime)
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray? {
        val buffer = ByteArray(16 * 1024)
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) throw java.io.IOException("Attachment stream returned no data.")
            total += n
            if (total > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    class Bounds(val width: Int, val height: Int, val orientation: Int) {
        val rotatedOrFlipped: Boolean
            get() = orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED
    }

    fun bounds(bytes: ByteArray): Bounds {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return Bounds(opts.outWidth, opts.outHeight, orientation)
    }

    /** Decodes upright and no larger than [maxEdge] on its longest side; null when the image can't be decoded. */
    fun decodeUpright(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val b = bounds(bytes)
        if (b.width <= 0 || b.height <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(b.width, b.height, maxEdge, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val matrix = orientationMatrix(b.orientation)
        val (w, _) = ImageSendSizing.fitWithin(decoded.width, decoded.height, maxEdge)
        if (w != decoded.width) {
            val scale = w.toFloat() / decoded.width
            matrix.postScale(scale, scale)
        }
        if (matrix.isIdentity) return decoded
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (out !== decoded) decoded.recycle()
        return out
    }

    private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }

    /** JPEG bytes, with any transparent areas drawn on white (JPEG has no transparency). */
    fun encodeJpeg(bitmap: Bitmap, quality: Int = ImageSendSizing.JPEG_QUALITY): ByteArray {
        val opaque = if (bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flat ->
                Canvas(flat).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        opaque.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (opaque !== bitmap) opaque.recycle()
        return out.toByteArray()
    }
}
