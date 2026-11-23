package com.hydrogen.ai.network

import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.ProviderError
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.RelayConfig
import kotlin.math.ceil

data class PreflightAssessment(
    val estimatedInputTokens: Int,
    val totalImageCount: Int,
    val includedImages: List<AttachmentManifestItem>,
    val omittedImages: List<AttachmentManifestItem>,
    val omittedAttachmentIds: Set<String>,
    val warnings: List<String>,
    val blockingError: ProviderError?
)

object PreflightValidator {

    private const val TOKENS_PER_IMAGE_ESTIMATE = 512

    fun estimateMessageTokens(messages: List<ChatMessage>): Int {
        var total = 0
        for (message in messages) {
            val textTokens = ceil(message.content.length / 3.5).toInt().coerceAtLeast(1)
            val toolArgChars = message.turnMetadata?.toolCalls?.sumOf { it.name.length + it.argumentsJson.length } ?: 0
            val toolTokens = if (toolArgChars > 0) ceil(toolArgChars / 3.5).toInt() else 0
            val imageTokens = message.attachments.size * TOKENS_PER_IMAGE_ESTIMATE
            total += textTokens + toolTokens + imageTokens + 8
        }
        return total
    }

    fun assess(
        messages: List<ChatMessage>,
        config: RelayConfig
    ): PreflightAssessment {
        val allImages = messages.flatMap { it.attachments }
        val maxImages = config.maxImagesPerRequest.coerceAtLeast(1)
        val warnings = mutableListOf<String>()

        val nonImageOrOversized = allImages.filter {
            !it.mimeType.startsWith("image/") || it.sizeBytes > config.maxImageBytes
        }
        val unsupportedError = if (nonImageOrOversized.isNotEmpty()) {
            val first = nonImageOrOversized.first()
            ProviderError(
                category = ProviderErrorCategory.PREFLIGHT_BLOCKED,
                userMessage = "\"${first.displayName}\" can't be sent: it isn't a supported image, or it's bigger than " +
                    "${config.maxImageBytes / (1024 * 1024)} MB. The file is still saved on this phone.",
                technicalDetail = "mime=${first.mimeType}, sizeBytes=${first.sizeBytes}, max=${config.maxImageBytes}",
                sourceLayer = "preflight"
            )
        } else {
            null
        }

        // Preserve all local originals; if route has an image cap, retain newest N (counting user + nested tool screenshots) and surface explicit notice.
        val includedImages: List<AttachmentManifestItem>
        val omittedImages: List<AttachmentManifestItem>

        if (allImages.size > maxImages) {
            val splitIndex = allImages.size - maxImages
            omittedImages = allImages.take(splitIndex).map {
                it.copy(
                    omittedFromRemote = true,
                    omissionReason = "Exceeds route image cap ($maxImages). Original retained locally."
                )
            }
            includedImages = allImages.drop(splitIndex)
            val toolCount = omittedImages.count { it.isFromToolResult }
            val toolNote = if (toolCount > 0) " (including $toolCount nested tool screenshot(s))" else ""
            warnings += "${omittedImages.size} older attachment(s)$toolNote exceed the ${maxImages}-image route budget and will not be sent remotely; all originals remain saved locally."
        } else {
            includedImages = allImages
            omittedImages = emptyList()
        }

        val includedTokensEstimate = estimateMessageTokens(
            messages.map { msg ->
                val kept = msg.attachments.filter { att -> omittedImages.none { it.id == att.id } }
                msg.copy(attachments = kept)
            }
        )

        val blockingError = unsupportedError ?: if (includedTokensEstimate > config.maxContextTokensBudget) {
            ProviderError(
                category = ProviderErrorCategory.PREFLIGHT_BLOCKED,
                userMessage = "This chat is now too long for the model to read at once (about $includedTokensEstimate " +
                    "tokens; the limit is ${config.maxContextTokensBudget}). Start a new chat to keep going.",
                technicalDetail = "estimatedTokens=$includedTokensEstimate, budget=${config.maxContextTokensBudget}",
                sourceLayer = "preflight"
            )
        } else {
            null
        }

        return PreflightAssessment(
            estimatedInputTokens = includedTokensEstimate,
            totalImageCount = allImages.size,
            includedImages = includedImages,
            omittedImages = omittedImages,
            omittedAttachmentIds = omittedImages.map { it.id }.toSet(),
            warnings = warnings,
            blockingError = blockingError
        )
    }
}

