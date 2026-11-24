package com.hydrogen.ai.model

object ReplyPlaybackPolicy {
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    fun normalizeSpeed(value: Float): Float =
        if (value in speeds) value else 1f

    fun remaining(chunks: List<String>, index: Int): List<String> =
        if (chunks.isEmpty()) emptyList() else chunks.drop(index.coerceIn(chunks.indices))
}