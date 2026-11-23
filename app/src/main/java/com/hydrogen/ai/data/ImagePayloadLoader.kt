package com.hydrogen.ai.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.hydrogen.ai.model.HydrogenDefaults
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EncodedImagePayload(
    val mimeType: String,
    val base64Data: String,
    val sizeBytes: Long
)

sealed interface ImageLoadResult {
    data class Success(val payload: EncodedImagePayload) : ImageLoadResult
    data class MissingOrUnreadable(val reason: String) : ImageLoadResult
}

interface ImagePayloadLoader {
    suspend fun loadImage(localUri: String, expectedMimeType: String): ImageLoadResult
}

internal fun readAndEncodeImageStream(
    input: InputStream,
    resolvedMime: String,
    maxBytes: Long,
    encodeBase64: (ByteArray) -> String
): ImageLoadResult {
    val buffer = ByteArray(8192)
    val out = ByteArrayOutputStream()
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        total += read
        if (total > maxBytes) {
            return ImageLoadResult.MissingOrUnreadable(
                "Image exceeds max allowed size (${maxBytes / (1024 * 1024)} MB)."
            )
        }
        out.write(buffer, 0, read)
    }
    val bytes = out.toByteArray()
    if (bytes.isEmpty()) {
        return ImageLoadResult.MissingOrUnreadable("Attachment file is empty.")
    }
    return ImageLoadResult.Success(
        EncodedImagePayload(
            mimeType = resolvedMime,
            base64Data = encodeBase64(bytes),
            sizeBytes = bytes.size.toLong()
        )
    )
}

class AndroidContentImagePayloadLoader(
    private val context: Context,
    private val maxBytes: Long = HydrogenDefaults.DEFAULT_MAX_IMAGE_BYTES
) : ImagePayloadLoader {

    override suspend fun loadImage(localUri: String, expectedMimeType: String): ImageLoadResult =
        withContext(Dispatchers.IO) {
            try {
                val parsed = URI.create(localUri)
                if (parsed.scheme == "file") {
                    val resolvedMime = expectedMimeType.takeIf { it.startsWith("image/") }
                        ?: return@withContext ImageLoadResult.MissingOrUnreadable(
                            "Unsupported or unknown image MIME type for $localUri"
                        )
                    val file = File(parsed)
                    if (!file.exists() || !file.isFile) {
                        return@withContext ImageLoadResult.MissingOrUnreadable(
                            "Persisted attachment file missing: ${file.name}"
                        )
                    }
                    return@withContext file.inputStream().use { input ->
                        readAndEncodeImageStream(
                            input = input,
                            resolvedMime = resolvedMime,
                            maxBytes = maxBytes,
                            encodeBase64 = { Base64.encodeToString(it, Base64.NO_WRAP) }
                        )
                    }
                }

                val uri = Uri.parse(localUri)
                val resolvedMime = context.contentResolver.getType(uri)
                    ?.takeIf { it.startsWith("image/") }
                    ?: expectedMimeType.takeIf { it.startsWith("image/") }
                    ?: return@withContext ImageLoadResult.MissingOrUnreadable(
                        "Unsupported or unknown image MIME type for $localUri"
                    )
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImageLoadResult.MissingOrUnreadable(
                        "Cannot open attachment stream for $localUri"
                    )
                stream.use { input ->
                    readAndEncodeImageStream(
                        input = input,
                        resolvedMime = resolvedMime,
                        maxBytes = maxBytes,
                        encodeBase64 = { Base64.encodeToString(it, Base64.NO_WRAP) }
                    )
                }
            } catch (e: Exception) {
                ImageLoadResult.MissingOrUnreadable(e.message ?: "Failed to read attachment")
            }
        }
}

