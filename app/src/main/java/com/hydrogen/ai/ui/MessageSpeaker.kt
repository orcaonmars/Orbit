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
import com.hydrogen.ai.model.SpeakableText
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
        speakingId = messageId
        if (ready) speakNow(messageId, chunks) else {
            pending = messageId to chunks
            ensureEngine()
        }
        return true
    }

    fun stop() {
        pending = null
        speakingId = null
        caption = null
        tts?.stop()
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
                    pending?.let { (id, chunks) -> speakNow(id, chunks) }
                } else {
                    val failedId = speakingId
                    speakingId = null
                    failedId?.let { onFinished?.invoke(it, false) }
                    tts?.shutdown()
                    tts = null
                    Toast.makeText(appContext, "Read aloud needs a text-to-speech engine on this phone.", Toast.LENGTH_LONG).show()
                }
                pending = null
            }
        }
    }

    private fun speakNow(messageId: String, chunks: List<String>) {
        val engine = tts ?: return
        currentChunks = chunks
        currentChunk = 0
        caption = VoiceConversation.captionAt(chunks.first(), 0)
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (index == chunks.lastIndex) "$messageId$LAST" else "$messageId#$index"
            engine.speak(chunk, mode, null, utteranceId)
        }
    }

    private fun chunkIndex(utteranceId: String?): Int? = when {
        utteranceId == null -> null
        utteranceId.endsWith(LAST) -> currentChunks.lastIndex
        else -> utteranceId.substringAfterLast('#').toIntOrNull()
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
                val index = chunkIndex(utteranceId) ?: currentChunk
                val next = currentChunks.getOrNull(index)?.let { VoiceConversation.captionAt(it, start) }
                if (next != null && next != caption) caption = next
            }
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId?.endsWith(LAST) == true) {
                val id = utteranceId.removeSuffix(LAST)
                main.post {
                    if (speakingId == id) {
                        speakingId = null
                        onFinished?.invoke(id, true)
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = failed()

        override fun onError(utteranceId: String?, errorCode: Int) = failed()

        private fun failed() {
            main.post {
                val id = speakingId ?: return@post
                speakingId = null
                tts?.stop()
                onFinished?.invoke(id, false)
            }
        }
    }

    private companion object {
        const val LAST = "#last"
    }
}

@Composable
fun rememberMessageSpeaker(): MessageSpeaker {
    val context = LocalContext.current
    val speaker = remember(context) { MessageSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}
