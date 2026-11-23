package com.hydrogen.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.DocxTextExtractor
import com.hydrogen.ai.model.FileAttachPolicy
import com.hydrogen.ai.model.PdfTextExtractor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DocumentImportResult {
    /** Text to append to the composer draft. */
    data class Text(val draftText: String, val displayName: String, val truncated: Boolean) : DocumentImportResult
    /** Images to stage (a picked image, or rendered PDF pages). */
    data class Images(val items: List<AttachmentManifestItem>, val totalPages: Int = items.size) : DocumentImportResult
    data class Failure(val reason: String) : DocumentImportResult
}

/** A picked file's words, unformatted, for a project. */
sealed interface TextReadResult {
    data class Ok(val displayName: String, val text: String, val truncated: Boolean) : TextReadResult
    data class Failure(val reason: String) : TextReadResult
}

/** Turns a user-picked document into composer text or staged page images. Nothing is sent automatically. */
class DocumentImporter(private val attachmentStorage: LocalAttachmentStorage) {

    /**
     * Reads a file for a project: text and code files, a PDF's text and a Word file's text. Pictures and scanned
     * PDFs have no words to keep, so they are refused with a reason rather than stored as images.
     */
    suspend fun readText(context: Context, uri: Uri): TextReadResult = withContext(Dispatchers.IO) {
        try {
            var displayName = uri.lastPathSegment ?: "file"
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) displayName = c.getString(0) ?: displayName
            }
            val name = displayName.replace('\n', ' ').take(120)
            when (FileAttachPolicy.classify(context.contentResolver.getType(uri), displayName)) {
                FileAttachPolicy.Kind.TEXT -> {
                    val limit = FileAttachPolicy.MAX_TEXT_BYTES
                    val bytes = readUpTo(context, uri, limit) ?: return@withContext TextReadResult.Failure("Cannot open '$name'.")
                    val truncated = bytes.size > limit
                    val text = FileAttachPolicy.decodeText(if (truncated) bytes.copyOf(limit) else bytes)
                        ?: return@withContext TextReadResult.Failure("'$name' looks like a binary file, not text.")
                    if (text.isBlank()) TextReadResult.Failure("'$name' is empty.")
                    else TextReadResult.Ok(displayName, text, truncated)
                }
                FileAttachPolicy.Kind.PDF -> {
                    val limit = FileAttachPolicy.MAX_PDF_BYTES_FOR_TEXT
                    val bytes = readUpTo(context, uri, limit) ?: return@withContext TextReadResult.Failure("Cannot open '$name'.")
                    val result = PdfTextExtractor.extract(bytes, FileAttachPolicy.MAX_TEXT_CHARS)
                    if (result.reason != null || result.text.isBlank()) {
                        TextReadResult.Failure("'$name' has no text Orbit can read, so it can't go in a project. A scanned PDF can still be attached to a message.")
                    } else {
                        TextReadResult.Ok(displayName, result.text, result.truncated || bytes.size > limit)
                    }
                }
                FileAttachPolicy.Kind.WORD -> {
                    val limit = FileAttachPolicy.MAX_WORD_BYTES
                    val bytes = readUpTo(context, uri, limit) ?: return@withContext TextReadResult.Failure("Cannot open '$name'.")
                    if (bytes.size > limit) return@withContext TextReadResult.Failure("'$name' is too big. Add a Word file under 24 MB.")
                    val result = DocxTextExtractor.extract(bytes, FileAttachPolicy.MAX_TEXT_CHARS)
                    if (result.reason != null) TextReadResult.Failure(FileAttachPolicy.wordFailureMessage(displayName, result.reason))
                    else TextReadResult.Ok(displayName, result.text, result.truncated)
                }
                FileAttachPolicy.Kind.IMAGE, FileAttachPolicy.Kind.UNSUPPORTED -> TextReadResult.Failure(
                    "Projects keep text, code, PDF and Word files. '$name' is not one of those."
                )
            }
        } catch (e: SecurityException) {
            TextReadResult.Failure("Orbit was not given access to that file.")
        } catch (e: Exception) {
            TextReadResult.Failure("Could not read the file: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Up to [limit] + 1 bytes, so the caller can tell the file was longer; null when it can't be opened. */
    private fun readUpTo(context: Context, uri: Uri, limit: Int): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() <= limit) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }

    suspend fun import(context: Context, uri: Uri, maxPdfPages: Int): DocumentImportResult =
        withContext(Dispatchers.IO) {
            try {
                var displayName = uri.lastPathSegment ?: "file"
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) displayName = c.getString(0) ?: displayName
                }
                val mimeType = context.contentResolver.getType(uri)
                when (FileAttachPolicy.classify(mimeType, displayName)) {
                    FileAttachPolicy.Kind.TEXT -> importText(context, uri, displayName)
                    FileAttachPolicy.Kind.PDF -> importPdf(context, uri, displayName, maxPdfPages)
                    FileAttachPolicy.Kind.WORD -> importWord(context, uri, displayName)
                    FileAttachPolicy.Kind.IMAGE -> when (val r = attachmentStorage.importContentUri(context, uri)) {
                        is AttachmentImportResult.Success -> DocumentImportResult.Images(listOf(r.item))
                        is AttachmentImportResult.Failure -> DocumentImportResult.Failure(r.reason)
                    }
                    FileAttachPolicy.Kind.UNSUPPORTED -> DocumentImportResult.Failure(
                        FileAttachPolicy.unsupportedMessage(displayName)
                    )
                }
            } catch (e: SecurityException) {
                DocumentImportResult.Failure("Orbit was not given access to that file.")
            } catch (e: Exception) {
                DocumentImportResult.Failure("Could not read the file: ${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun importText(context: Context, uri: Uri, displayName: String): DocumentImportResult {
        val limit = FileAttachPolicy.MAX_TEXT_BYTES
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (out.size() <= limit) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return DocumentImportResult.Failure("Cannot open '$displayName'.")
        val truncated = bytes.size > limit
        val text = FileAttachPolicy.decodeText(if (truncated) bytes.copyOf(limit) else bytes)
            ?: return DocumentImportResult.Failure("'$displayName' looks like a binary file, not text.")
        if (text.isBlank()) return DocumentImportResult.Failure("'$displayName' is empty.")
        return DocumentImportResult.Text(FileAttachPolicy.formatForDraft(displayName, text, truncated), displayName, truncated)
    }

    /** A Word file's words go into the message box as text, like a PDF with text. Nothing is sent yet. */
    private fun importWord(context: Context, uri: Uri, displayName: String): DocumentImportResult {
        val limit = FileAttachPolicy.MAX_WORD_BYTES
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() <= limit) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return DocumentImportResult.Failure("Cannot open '$displayName'.")
        if (bytes.size > limit) {
            return DocumentImportResult.Failure("'$displayName' is too big. Attach a Word file under 24 MB.")
        }
        val result = DocxTextExtractor.extract(bytes, FileAttachPolicy.MAX_TEXT_CHARS)
        if (result.reason != null) {
            return DocumentImportResult.Failure(FileAttachPolicy.wordFailureMessage(displayName, result.reason))
        }
        return DocumentImportResult.Text(
            FileAttachPolicy.formatForDraft(displayName, result.text, result.truncated), displayName, result.truncated
        )
    }

    /** A PDF with real text goes into the message box as text (works with every model, editable before
     *  sending); scanned or custom-encoded PDFs fall back to page images for vision models. */
    private suspend fun importPdf(context: Context, uri: Uri, displayName: String, maxPdfPages: Int): DocumentImportResult {
        extractPdfText(context, uri, displayName)?.let { return it }
        return importPdfPages(context, uri, displayName, maxPdfPages)
    }

    private fun extractPdfText(context: Context, uri: Uri, displayName: String): DocumentImportResult.Text? {
        val limit = FileAttachPolicy.MAX_PDF_BYTES_FOR_TEXT
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() < limit) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return null
        val result = PdfTextExtractor.extract(bytes, FileAttachPolicy.MAX_TEXT_CHARS)
        if (result.reason != null || result.text.isBlank()) return null
        val truncated = result.truncated || bytes.size >= limit
        return DocumentImportResult.Text(
            FileAttachPolicy.formatForDraft(displayName, result.text, truncated), displayName, truncated
        )
    }

    private suspend fun importPdfPages(context: Context, uri: Uri, displayName: String, maxPdfPages: Int): DocumentImportResult {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return DocumentImportResult.Failure("Cannot open '$displayName'.")
        val baseName = displayName.substringBeforeLast('.').ifBlank { "document" }
        val staged = mutableListOf<AttachmentManifestItem>()
        var totalPages = 0
        try {
            PdfRenderer(pfd).use { renderer ->
                totalPages = renderer.pageCount
                val count = minOf(FileAttachPolicy.pagesToRender(totalPages), maxPdfPages.coerceAtLeast(1))
                for (index in 0 until count) {
                    val jpeg = renderer.openPage(index).use { page ->
                        val (w, h) = FileAttachPolicy.renderSize(page.width, page.height)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE) // PDFs are transparent by default
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    when (val r = attachmentStorage.importFromStream(
                        displayName = "$baseName-p${index + 1}.jpg",
                        mimeType = "image/jpeg",
                        reportedSizeBytes = jpeg.size.toLong(),
                        openInput = { ByteArrayInputStream(jpeg) }
                    )) {
                        is AttachmentImportResult.Success -> staged += r.item
                        is AttachmentImportResult.Failure -> {
                            staged.forEach { attachmentStorage.deleteIfOwned(it.localUri) }
                            return DocumentImportResult.Failure(r.reason)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            staged.forEach { attachmentStorage.deleteIfOwned(it.localUri) }
            return DocumentImportResult.Failure("'$displayName' is password-protected and cannot be opened.")
        } catch (e: java.io.IOException) {
            staged.forEach { attachmentStorage.deleteIfOwned(it.localUri) }
            return DocumentImportResult.Failure("'$displayName' is not a readable PDF.")
        } finally {
            runCatching { pfd.close() }
        }
        if (staged.isEmpty()) return DocumentImportResult.Failure("'$displayName' has no pages.")
        return DocumentImportResult.Images(staged, totalPages)
    }
}
