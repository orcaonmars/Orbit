package com.hydrogen.ai.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hydrogen.ai.model.PlaybackGeneration
import com.hydrogen.ai.model.ReplyPlaybackPolicy
import com.hydrogen.ai.model.SpeakableText
import com.hydrogen.ai.model.TtsVoiceRanker
import com.hydrogen.ai.model.VoiceConversation

/**
 * Reads replies aloud with the phone's own text-to-speech engine (on-device, free, no network of ours).
 * The engine is only started the first time someone taps "Read aloud". [speakingId] is the message
 * currently being read, so its button can turn into Stop.
 */
class MessageSpeaker(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, List<String>>? = null

    var speakingId by mutableStateOf<String?>(null)
        private set

    /**
     * The sentence being read right now, for the voice chat caption. Follows the engine word by word where it
     * reports progress (onRangeStart); otherwise it shows the start of the part being read.
     */
    var caption by mutableStateOf<String?>(null)
        private set
    private var currentChunks: List<String> = emptyList()
    private var currentChunk = 0
    var paused by mutableStateOf(false)
        private set
    var speed by mutableStateOf(1f)
        private set
    private val playback = PlaybackGeneration()

    /**
     * Called on the main thread with the message id when reading ends by itself: [finished] is true when
     * the whole message was read, false on an engine error. Not called after [stop]. Voice chat uses it to
     * listen again.
     */
    var onFinished: ((messageId: String, finished: Boolean) -> Unit)? = null

    /** Starts reading [markdown] for [messageId], or stops if that message is already being read. */
    fun toggle(messageId: String, markdown: String) {
        if (speakingId == messageId) {
            stop()
            return
        }
        speak(messageId, markdown)
    }

    /** Reads [markdown] aloud from the start. False when there is nothing speakable in it. */
    fun speak(messageId: String, markdown: String): Boolean {
        val chunks = SpeakableText.chunk(SpeakableText.fromMarkdown(markdown))
        if (chunks.isEmpty()) return false
        playback.advance()
        paused = false
        currentChunks = chunks
        currentChunk = 0
        speakingId = messageId
        caption = null
        if (ready) speakNow(messageId, chunks) else {
            pending = messageId to chunks
            ensureEngine()
        }
        return true
    }

    fun stop() {
        playback.advance()
        pending = null
        paused = false
        currentChunks = emptyList()
        currentChunk = 0
        speakingId = null
        caption = null
        tts?.stop()
    }

    /** Android TTS has no pause primitive. Resume restarts the current short speech chunk. */
    fun pause() {
        if (speakingId == null || paused) return
        playback.advance()
        pending = null
        paused = true
        tts?.stop()
    }

    fun resume() {
        val id = speakingId ?: return
        if (!paused || currentChunks.isEmpty()) return
        paused = false
        playback.advance()
        val remaining = ReplyPlaybackPolicy.remaining(currentChunks, currentChunk)
        if (ready) speakNow(id, remaining) else {
            pending = id to remaining
            ensureEngine()
        }
    }

    fun setPlaybackSpeed(value: Float) {
        val next = ReplyPlaybackPolicy.normalizeSpeed(value)
        if (next == speed) return
        speed = next
        tts?.setSpeechRate(speed)
        // Apply immediately to queued speech by restarting the current chunk.
        if (speakingId != null && !paused && ready) {
            pause()
            resume()
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun ensureEngine() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setOnUtteranceProgressListener(listener)
                    tts?.setSpeechRate(speed)
                    chooseBestVoice()
                    if (!paused) pending?.let { (id, chunks) -> speakNow(id, chunks) }
                } else {
                    val failedId = speakingId
                    stop()
                    failedId?.let { onFinished?.invoke(it, false) }
                    tts?.shutdown()
                    tts = null
                    Toast.makeText(appContext, "Read aloud needs a text-to-speech engine on this phone.", Toast.LENGTH_LONG).show()
                }
                pending = null
            }
        }
    }

    /** Swaps the engine's default voice for its best installed on-device voice in the phone's language, if any. */
    private fun chooseBestVoice() {
        val engine = tts ?: return
        runCatching {
            val locale = java.util.Locale.getDefault()
            val voices = engine.voices?.toList().orEmpty()
            val candidates = voices.map { v ->
                TtsVoiceRanker.Candidate(
                    name = v.name,
                    language = v.locale.language,
                    country = v.locale.country,
                    quality = v.quality,
                    latency = v.latency,
                    needsNetwork = v.isNetworkConnectionRequired,
                    notInstalled = v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                )
            }
            val best = TtsVoiceRanker.pick(candidates, locale.language, locale.country) ?: return
            voices.firstOrNull { it.name == best.name }?.let { engine.voice = it }
        }
    }

    private fun speakNow(messageId: String, chunks: List<String>) {
        val engine = tts ?: return
        currentChunks = chunks
        currentChunk = 0
        caption = VoiceConversation.captionAt(chunks.first(), 0)
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = playback.utteranceId(index)
            if (engine.speak(chunk, mode, null, utteranceId) == TextToSpeech.ERROR) {
                fail(utteranceId)
                return
            }
        }
    }

    private fun chunkIndex(utteranceId: String?): Int? =
        playback.indexOf(utteranceId)?.takeIf { it in currentChunks.indices }

    private fun fail(utteranceId: String?) {
        main.post {
            if (chunkIndex(utteranceId) == null) return@post
            val id = speakingId ?: return@post
            stop()
            onFinished?.invoke(id, false)
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post {
                val index = chunkIndex(utteranceId) ?: return@post
                if (speakingId == null) return@post
                currentChunk = index
                currentChunks.getOrNull(index)?.let { caption = VoiceConversation.captionAt(it, 0) }
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            main.post {
                if (speakingId == null) return@post
                val index = chunkIndex(utteranceId) ?: return@post
                val next = currentChunks.getOrNull(index)?.let { VoiceConversation.captionAt(it, start) }
                if (next != null && next != caption) caption = next
            }
        }

        override fun onDone(utteranceId: String?) {
            main.post {
                if (chunkIndex(utteranceId) != currentChunks.lastIndex) return@post
                val id = speakingId ?: return@post
                playback.advance()
                speakingId = null
                paused = false
                currentChunks = emptyList()
                currentChunk = 0
                caption = null
                onFinished?.invoke(id, true)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = fail(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = fail(utteranceId)
    }

}

@Composable
fun rememberMessageSpeaker(): MessageSpeaker {
    val context = LocalContext.current
    val speaker = remember(context) { MessageSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}
