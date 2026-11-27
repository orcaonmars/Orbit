package com.hydrogen.ai.data

import android.content.Context
import android.util.Base64
import com.hydrogen.ai.model.HydrogenDefaults
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
        if (read < 0) break
        if (read == 0) return ImageLoadResult.MissingOrUnreadable("Attachment stream returned no data.")
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

/**
 * Loads a photo for sending. Large or sideways photos are shrunk to [ImageSendSizing.MAX_EDGE_PX] on their longest
 * side and sent as JPEG; small compatible files go unchanged. The saved original is only read.
 */
class AndroidContentImagePayloadLoader(
    private val context: Context,
    private val maxBytes: Long = HydrogenDefaults.DEFAULT_MAX_IMAGE_BYTES
) : ImagePayloadLoader {

    override suspend fun loadImage(localUri: String, expectedMimeType: String): ImageLoadResult =
        withContext(Dispatchers.IO) {
            try {
                val read = AndroidImageCodec.read(context, localUri, expectedMimeType, maxBytes)
                val bytes = when (read) {
                    is AndroidImageCodec.Read.Failed -> return@withContext ImageLoadResult.MissingOrUnreadable(read.reason)
                    is AndroidImageCodec.Read.Bytes -> read
                }
                val bounds = AndroidImageCodec.bounds(bytes.data)
                val shrink = ImageSendSizing.needsReencode(
                    bytes.data.size.toLong(), bounds.width, bounds.height, bytes.mimeType, bounds.rotatedOrFlipped
                )
                val shrunk = if (shrink) {
                    AndroidImageCodec.decodeUpright(bytes.data, ImageSendSizing.MAX_EDGE_PX)?.let { bitmap ->
                        try {
                            AndroidImageCodec.encodeJpeg(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                } else {
                    null
                }
                val (data, mime) = if (shrunk != null && shrunk.isNotEmpty()) shrunk to "image/jpeg" else bytes.data to bytes.mimeType
                ImageLoadResult.Success(
                    EncodedImagePayload(
                        mimeType = mime,
                        base64Data = Base64.encodeToString(data, Base64.NO_WRAP),
                        sizeBytes = data.size.toLong()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                ImageLoadResult.MissingOrUnreadable("Not enough memory to prepare this photo.")
            } catch (_: Exception) {
                ImageLoadResult.MissingOrUnreadable("Failed to read attachment.")
            }
        }
}
