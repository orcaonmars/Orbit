package com.hydrogen.ai.model

/** Main-thread playback generation; callbacks from a flushed queue must not affect a replacement. */
class PlaybackGeneration {
    private var generation = 0L

    fun advance() { generation++ }

    fun utteranceId(index: Int): String = "$generation:$index"

    fun indexOf(id: String?): Int? {
        if (id == null || id.substringBefore(':') != generation.toString()) return null
        return id.substringAfter(':', "").toIntOrNull()?.takeIf { it >= 0 }
    }
}
