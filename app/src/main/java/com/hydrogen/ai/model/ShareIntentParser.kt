package com.hydrogen.ai.model

/**
 * Content another app shared into Hydrogen (ACTION_SEND / ACTION_SEND_MULTIPLE).
 * It is only ever placed in the composer for the user to review; it is never auto-sent.
 */
data class SharedContent(
    val text: String?,
    val imageUris: List<String>,
    val droppedItems: Int = 0,
    /** Concrete image type the sender declared (never a wildcard), used if the resolver reports none. */
    val declaredImageType: String? = null
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && imageUris.isEmpty()
}

/** Pure share-intent rules, kept out of Android/Compose so they can be unit tested. */
object ShareIntentParser {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    const val MAX_SHARED_TEXT_CHARS = 20_000
    const val MAX_SHARED_IMAGES = 10
    private val CONCRETE_IMAGE_TYPE = Regex("image/[a-z0-9.+-]+")

    /**
     * @param streams the EXTRA_STREAM uri(s) as strings, in order.
     * Only `content://` streams are accepted: a `file://` uri could point into Hydrogen's own
     * private storage and must never be read on another app's behalf.
     */
    fun parse(
        action: String?,
        mimeType: String?,
        text: String?,
        subject: String?,
        streams: List<String?>
    ): SharedContent? {
        if (action != ACTION_SEND && action != ACTION_SEND_MULTIPLE) return null
        val type = mimeType?.lowercase().orEmpty()

        val body = text?.trim().orEmpty()
        val title = subject?.trim().orEmpty()
        val combined = when {
            title.isEmpty() -> body
            body.isEmpty() -> title
            body.contains(title) -> body
            else -> "$title\n\n$body"
        }
        val sharedText = combined.take(MAX_SHARED_TEXT_CHARS).ifBlank { null }

        val offered = streams.filterNotNull().filter { it.isNotBlank() }
        val images = if (type.startsWith("image/")) {
            offered.filter { isAcceptableStream(it) }.distinct().take(MAX_SHARED_IMAGES)
        } else {
            emptyList()
        }
        // Streams we could not take (non-image, non-content://, or over the cap) are reported, not read.
        val dropped = offered.distinct().size - images.size

        val declaredImageType = type.takeIf { images.isNotEmpty() && CONCRETE_IMAGE_TYPE.matches(it) }
        val result = SharedContent(
            text = sharedText,
            imageUris = images,
            droppedItems = dropped,
            declaredImageType = declaredImageType
        )
        return result.takeUnless { it.isEmpty && dropped == 0 }
    }

    fun isAcceptableStream(uri: String): Boolean {
        val trimmed = uri.trim()
        if (!trimmed.regionMatches(0, "content://", 0, 10, ignoreCase = true)) return false
        return trimmed.length > 10
    }

    /** Adds shared text to whatever the user had already typed, without losing it. */
    fun mergeIntoDraft(existingDraft: String, sharedText: String?): String {
        val addition = sharedText?.trim().orEmpty()
        if (addition.isEmpty()) return existingDraft
        if (existingDraft.isBlank()) return addition
        return existingDraft.trimEnd() + "\n\n" + addition
    }
}
