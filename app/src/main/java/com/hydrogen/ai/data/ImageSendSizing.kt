package com.hydrogen.ai.data

import com.hydrogen.ai.model.ImageSheetPlanner

/**
 * When a photo is shrunk before sending. Full-size phone photos are often 3 to 12 MB and 4000 pixels or more across;
 * providers reject single images over 4 to 10 MB and whole requests over 20 to 32 MB, and scale anything over about
 * 1568 pixels down themselves anyway. Shrinking on the phone keeps the detail the model actually sees and lets many
 * more photos fit in one request. The saved original is never changed.
 */
object ImageSendSizing {

    const val MAX_EDGE_PX = ImageSheetPlanner.MAX_EDGE_PX

    /** Small, already-compatible files go as they are, so screenshots stay pixel-exact. */
    const val PASSTHROUGH_MAX_BYTES = 1_500_000L

    const val JPEG_QUALITY = 85

    private val PASSTHROUGH_MIME = setOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif")

    /**
     * True when the photo must be decoded and re-encoded: it is large, unusually formatted (HEIC, for example), or
     * stored sideways with an orientation flag that not every provider honours. [width] and [height] are 0 when the
     * size couldn't be read.
     */
    fun needsReencode(sizeBytes: Long, width: Int, height: Int, mimeType: String, rotatedOrFlipped: Boolean): Boolean {
        if (width <= 0 || height <= 0) return false
        if (rotatedOrFlipped) return true
        if (mimeType.lowercase() !in PASSTHROUGH_MIME) return true
        if (sizeBytes > PASSTHROUGH_MAX_BYTES) return true
        return maxOf(width, height) > MAX_EDGE_PX
    }

    /** The size that fits within [maxEdge] on its longest side, keeping the aspect ratio; never enlarges. */
    fun fitWithin(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
}
