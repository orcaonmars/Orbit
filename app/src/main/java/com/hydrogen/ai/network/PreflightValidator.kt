package com.hydrogen.ai.network

import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.ImageLimits
import com.hydrogen.ai.model.ImageSheetPlanner
import com.hydrogen.ai.model.MessageRole
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
    val blockingError: ProviderError?,
    /** The line in [warnings] about the current message's own photos going into grids, if any. */
    val currentPhotosNote: String? = null
) {
    /** [warnings] without [currentPhotosNote], for while photos are staged: the tray already explains them. */
    val warningsWhileStaging: List<String> get() = warnings.filter { it != currentPhotosNote }
}

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
        val maxImages = ImageLimits.effectiveCap(config).coerceAtLeast(1)
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

        // The newest message with photos is the one being sent: all of its photos always go, and any beyond the
        // limit are combined into numbered grids at send time. Earlier photos fill what is left, newest first; the
        // rest are not sent again but stay saved on this phone.
        val currentIndex = messages.indexOfLast { it.role == MessageRole.USER && it.attachments.isNotEmpty() }
        val currentIds = if (currentIndex >= 0) messages[currentIndex].attachments.map { it.id }.toSet() else emptySet()
        val current = allImages.filter { it.id in currentIds }
        val earlier = allImages.filter { it.id !in currentIds }
        val earlierRoom = (maxImages - current.size).coerceAtLeast(0)
        val omittedImages: List<AttachmentManifestItem> = earlier.dropLast(earlierRoom).map {
            it.copy(
                omittedFromRemote = true,
                omissionReason = "Exceeds route image cap ($maxImages). Original retained locally."
            )
        }
        val omittedIds = omittedImages.map { it.id }.toSet()
        val includedImages: List<AttachmentManifestItem> = allImages.filter { it.id !in omittedIds }

        if (omittedImages.isNotEmpty()) {
            val toolCount = omittedImages.count { it.isFromToolResult }
            val toolNote = if (toolCount > 0) " (including $toolCount nested tool screenshot(s))" else ""
            warnings += "${omittedImages.size} older attachment(s)$toolNote exceed the ${maxImages}-image route " +
                "budget and will not be sent remotely; all originals remain saved locally."
        }
        var currentPhotosNote: String? = null
        // Only about the message being sent: on a text-only follow-up those photos were explained when they went.
        val sendingThoseNow = currentIndex >= 0 && currentIndex == messages.indexOfLast { it.role == MessageRole.USER }
        if (sendingThoseNow && current.size > maxImages) {
            val plan = ImageSheetPlanner.plan(current, maxImages)
            currentPhotosNote = if (plan.dropped.isEmpty()) {
                "This message has ${current.size} photos and the service takes $maxImages per message, so " +
                    "${current.size - plan.singles.size} of them will be combined into numbered grid images."
            } else {
                "This message has ${current.size} photos. ${plan.dropped.size} of them won't fit even in grids " +
                    "and will not be sent; they stay saved on this phone."
            }
            warnings += currentPhotosNote
        }

        val includedTokensEstimate = estimateMessageTokens(
            messages.map { msg ->
                val kept = msg.attachments.filter { att -> att.id !in omittedIds }
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
            omittedAttachmentIds = omittedIds,
            warnings = warnings,
            blockingError = blockingError,
            currentPhotosNote = currentPhotosNote
        )
    }
}

