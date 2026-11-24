package com.hydrogen.ai.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hydrogen.ai.model.AttachmentManifestItem
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface AttachmentImportResult {
    data class Success(val item: AttachmentManifestItem) : AttachmentImportResult
    data class Failure(val reason: String) : AttachmentImportResult
}

class LocalAttachmentStorage(
    private val attachmentsDir: File,
    private val maxPersistBytes: Long = DEFAULT_MAX_PERSIST_BYTES
) {
    /**
     * @param mimeTypeHint a concrete type declared by the sender (e.g. a share intent), used only when
     * the content resolver cannot report one. Non-image hints are still rejected below.
     */
    suspend fun importContentUri(context: Context, uri: Uri, mimeTypeHint: String? = null): AttachmentImportResult {
        var displayName = uri.lastPathSegment ?: "attachment"
        var sizeBytes = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
            }
        }
        val mimeType = context.contentResolver.getType(uri) ?: mimeTypeHint ?: "application/octet-stream"
        return importFromStream(
            displayName = displayName,
            mimeType = mimeType,
            reportedSizeBytes = sizeBytes,
            openInput = { context.contentResolver.openInputStream(uri) }
        )
    }

    suspend fun importFromStream(
        displayName: String,
        mimeType: String,
        reportedSizeBytes: Long = 0L,
        openInput: () -> InputStream?
    ): AttachmentImportResult = withContext(Dispatchers.IO) {
        if (!mimeType.startsWith("image/")) {
            return@withContext AttachmentImportResult.Failure(
                "Unsupported attachment '$displayName' ($mimeType). Only image attachments are supported."
            )
        }
        if (reportedSizeBytes > maxPersistBytes) {
            return@withContext AttachmentImportResult.Failure(
                "Attachment '$displayName' exceeds the ${maxPersistBytes / (1024 * 1024)} MB local storage limit."
            )
        }

        val attachmentId = UUID.randomUUID().toString()
        val safeName = sanitizeFileName(displayName)
        if (!attachmentsDir.isDirectory && !attachmentsDir.mkdirs() && !attachmentsDir.isDirectory) {
            return@withContext AttachmentImportResult.Failure("Could not create attachment storage.")
        }
        val destFile = File(attachmentsDir, "${attachmentId}_$safeName")

        try {
            val stream = openInput()
                ?: return@withContext AttachmentImportResult.Failure(
                    "Cannot open attachment stream for '$displayName'."
                )
            var totalBytes = 0L
            stream.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) throw java.io.IOException("Attachment stream returned no data.")
                        totalBytes += read
                        if (totalBytes > maxPersistBytes) {
                            throw IllegalArgumentException(
                                "Attachment '$displayName' exceeds the ${maxPersistBytes / (1024 * 1024)} MB local storage limit."
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            coroutineContext.ensureActive()
            if (totalBytes <= 0L) {
                destFile.delete()
                return@withContext AttachmentImportResult.Failure("Attachment '$displayName' is empty.")
            }
            AttachmentImportResult.Success(
                AttachmentManifestItem(
                    id = attachmentId,
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = totalBytes,
                    localUri = destFile.toURI().toString()
                )
            )
        } catch (e: CancellationException) {
            destFile.delete()
            throw e
        } catch (e: IllegalArgumentException) {
            destFile.delete()
            AttachmentImportResult.Failure("Attachment exceeds the local storage limit or is invalid.")
        } catch (_: Exception) {
            destFile.delete()
            AttachmentImportResult.Failure("Could not import '$displayName'. Check file access and available storage.")
        }
    }

    fun deleteIfOwned(localUri: String): Boolean {
        return try {
            val parsed = URI.create(localUri)
            if (parsed.scheme != "file") return false
            val target = File(parsed).canonicalFile
            val ownedRoot = attachmentsDir.canonicalFile
            if (target.parentFile != ownedRoot) return false
            if (target.exists()) target.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
        return cleaned.ifBlank { "image" }
    }

    companion object {
        const val DEFAULT_MAX_PERSIST_BYTES = 20L * 1024L * 1024L
    }
}

fun resolveLocalAttachmentFile(localUri: String): File? {
    return try {
        val parsed = URI.create(localUri)
        if (parsed.scheme != "file") return null
        val file = File(parsed)
        if (file.exists() && file.isFile) file else null
    } catch (_: Exception) {
        null
    }
}

fun computeSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
    if (srcWidth <= 0 || srcHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
    var inSampleSize = 1
    if (srcHeight > reqHeight || srcWidth > reqWidth) {
        val halfHeight = srcHeight / 2
        val halfWidth = srcWidth / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    // Preserve aspect ratio but cap the decoded pixel count of unusually wide/tall images.
    val budget = (reqWidth.toLong() * reqHeight.toLong()).coerceAtMost(Long.MAX_VALUE / 5) * 5
    while (((srcWidth.toLong() + inSampleSize - 1) / inSampleSize) *
        ((srcHeight.toLong() + inSampleSize - 1) / inSampleSize) > budget &&
        inSampleSize <= Int.MAX_VALUE / 2
    ) {
        inSampleSize *= 2
    }
    return inSampleSize
}
