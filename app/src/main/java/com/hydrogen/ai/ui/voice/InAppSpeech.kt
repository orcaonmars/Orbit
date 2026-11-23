package com.hydrogen.ai.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hydrogen.ai.model.Dictation

/**
 * The phone's own speech recognizer, run inside Orbit instead of the system's speech popup.
 *
 * - On-device recognition when the phone offers it (Android 12+), otherwise the phone's speech service.
 * - [Mode.DICTATION] keeps listening across pauses until you tap the check, stay quiet for 3 s, or hit the cap.
 * - [Mode.TURN] hears one thing you say (voice chat) and ends when you stop talking.
 *
 * Orbit never stores the audio; the recognizer only hands back text. Everything runs on the main thread.
 */
class InAppSpeech(context: Context) {
    enum class Mode { DICTATION, TURN }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recognizerOnDevice = false
    /** Set after the on-device recognizer failed once, so the rest of this screen uses the speech service. */
    private var preferService = false
    /** Bumped for every stretch of listening, so late callbacks from an earlier one are ignored. */
    private var generation = 0
    /** Bumped when a whole session starts or ends, for timers that outlive one stretch. */
    private var session = 0
    private var busyRetries = 0

    private var startedAt = 0L
    private var lastSpeechAt: Long? = null
    private var peakSinceTick = 0f
    private var onResult: ((String?) -> Unit)? = null
    private var onFailure: ((String) -> Unit)? = null

    /** True while the app is on screen; voice chat checks it before it listens again. */
    var inForeground = true
        internal set

    var active by mutableStateOf(false)
        private set
    var mode by mutableStateOf(Mode.DICTATION)
        private set
    /** Finished stretches of speech. */
    var committed by mutableStateOf("")
        private set
    /** Words still being recognized; shown muted until they're final. */
    var partial by mutableStateOf("")
        private set
    /** Recent mic levels for the waveform, oldest first. */
    var levels by mutableStateOf<List<Float>>(emptyList())
        private set
    /** Smoothed current mic level for the voice chat orb, 0 to 1. */
    var level by mutableFloatStateOf(0f)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    /** The check was tapped and Orbit is waiting for the last words. */
    var finishing by mutableStateOf(false)
        private set

    /** False when the phone has no speech recognizer Orbit can use; the mic buttons are hidden then. */
    val available: Boolean by lazy {
        runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false) || onDeviceAvailable()
    }

    /** Whether speech is turned into text on this phone (for the privacy note). */
    val usesOnDevice: Boolean get() = !preferService && onDeviceAvailable()

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)

    /**
     * Starts listening. [onResult] gets the text heard (null when nothing was said); [onFailure] gets a short
     * message when the recognizer fails. Neither is called after [cancel]. Starting again cancels the last session.
     */
    fun start(mode: Mode, onResult: (String?) -> Unit, onFailure: (String) -> Unit) {
        cancel()
        this.mode = mode
        this.onResult = onResult
        this.onFailure = onFailure
        committed = ""
        partial = ""
        levels = emptyList()
        level = 0f
        elapsedMs = 0L
        finishing = false
        startedAt = SystemClock.elapsedRealtime()
        lastSpeechAt = null
        peakSinceTick = 0f
        active = true
        if (!listen()) return
        main.postDelayed(tick, Dictation.TICK_MS)
    }

    /** The check button: stop listening and keep the words, waiting briefly for the final ones. */
    fun finish() {
        if (!active || finishing) return
        finishing = true
        runCatching { recognizer?.stopListening() }
        val id = session
        main.postDelayed({ if (id == session) complete(Dictation.transcript(committed, partial)) }, Dictation.FINISH_GRACE_MS)
    }

    /** Stops and throws away whatever was heard. No callback. */
    fun cancel() {
        generation++
        session++
        busyRetries = 0
        main.removeCallbacks(tick)
        if (active) runCatching { recognizer?.cancel() }
        active = false
        finishing = false
        partial = ""
        level = 0f
        onResult = null
        onFailure = null
    }

    fun release() {
        cancel()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val wantOnDevice = usesOnDevice
        val created = runCatching {
            if (wantOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        }.getOrNull()
        recognizerOnDevice = wantOnDevice && created != null
        recognizer = created
        return created
    }

    /** Starts one stretch of listening. False if no recognizer could be started (the failure was reported). */
    private fun listen(): Boolean {
        val engine = ensureRecognizer()
        if (engine == null) {
            fail(Dictation.UNAVAILABLE_MESSAGE)
            return false
        }
        val gen = ++generation
        engine.setRecognitionListener(Listener(gen))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        if (mode == Mode.DICTATION) {
            // Hints only: many recognizers ignore them, which is why dictation also restarts after each stretch.
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, Dictation.SILENCE_STOP_MS)
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, Dictation.SILENCE_STOP_MS)
        }
        return try {
            engine.startListening(intent)
            true
        } catch (e: RuntimeException) {
            fail(Dictation.errorMessage(Dictation.Error.CLIENT))
            false
        }
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            elapsedMs = now - startedAt
            levels = Dictation.pushLevel(levels, peakSinceTick)
            level = Dictation.smooth(level, peakSinceTick)
            peakSinceTick = Dictation.MIN_LEVEL
            val cap = if (mode == Mode.DICTATION) Dictation.MAX_DICTATION_MS else Dictation.MAX_TURN_MS
            if (!finishing) {
                if (mode == Mode.DICTATION && Dictation.shouldAutoStop(now, startedAt, lastSpeechAt)) finish()
                else if (now - startedAt >= cap) finish()
            }
            if (active) main.postDelayed(this, Dictation.TICK_MS)
        }
    }

    private fun heardSpeech() {
        lastSpeechAt = SystemClock.elapsedRealtime()
    }

    private fun complete(text: String?) {
        if (!active) return
        val callback = onResult
        cancel()
        callback?.invoke(text)
    }

    private fun fail(message: String) {
        if (!active) return
        val callback = onFailure
        cancel()
        callback?.invoke(message)
    }

    /** After one stretch of speech ends: dictation keeps going unless you're done; a voice turn ends. */
    private fun segmentEnded() {
        val now = SystemClock.elapsedRealtime()
        if (mode == Mode.TURN || finishing || Dictation.shouldAutoStop(now, startedAt, lastSpeechAt)) {
            complete(Dictation.transcript(committed, partial))
        } else {
            listenAgain(RESTART_DELAY_MS)
        }
    }

    /** Starts the next stretch a moment later; recognizers often report "busy" if restarted at once. */
    private fun listenAgain(delayMs: Long) {
        val id = session
        generation++
        main.postDelayed({
            if (id == session && active) {
                if (finishing) complete(Dictation.transcript(committed, partial)) else listen()
            }
        }, delayMs)
    }

    private inner class Listener(private val gen: Int) : RecognitionListener {
        private fun current() = gen == generation && active

        override fun onReadyForSpeech(params: Bundle?) {
            if (current()) busyRetries = 0
        }

        override fun onBeginningOfSpeech() {
            if (current()) heardSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (!current()) return
            peakSinceTick = maxOf(peakSinceTick, Dictation.normalize(rmsdB))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current()) return
            val text = best(partialResults) ?: return
            if (text != partial) {
                partial = text
                heardSpeech()
            }
        }

        override fun onResults(results: Bundle?) {
            if (!current()) return
            best(results)?.let { heardSpeech(); committed = Dictation.join(committed, it) }
            partial = ""
            segmentEnded()
        }

        override fun onError(error: Int) {
            if (!current()) return
            when {
                Dictation.isNoSpeech(error) -> segmentEnded()
                error == Dictation.Error.RECOGNIZER_BUSY && !finishing && busyRetries < MAX_BUSY_RETRIES -> {
                    busyRetries++
                    runCatching { recognizer?.cancel() }
                    listenAgain(BUSY_RETRY_DELAY_MS)
                }
                Dictation.shouldFallBackToService(error, recognizerOnDevice) -> {
                    preferService = true
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    listen()
                }
                // Some recognizers report a client error after stopListening(); keep what was heard.
                finishing || (mode == Mode.DICTATION && Dictation.transcript(committed, partial) != null) ->
                    complete(Dictation.transcript(committed, partial))
                else -> fail(Dictation.errorMessage(error))
            }
        }

        private fun best(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { !it.isNullOrBlank() }
                ?.trim()
    }

    private companion object {
        const val RESTART_DELAY_MS = 120L
        const val BUSY_RETRY_DELAY_MS = 300L
        const val MAX_BUSY_RETRIES = 3
    }
}

/** One recognizer for the whole chat screen; it stops when the app leaves the screen and is freed with it. */
@Composable
fun rememberInAppSpeech(): InAppSpeech {
    val context = LocalContext.current
    val speech = remember(context) { InAppSpeech(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(speech, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> speech.inForeground = true
                Lifecycle.Event.ON_STOP -> {
                    speech.inForeground = false
                    // Android cuts background apps off the mic anyway; stop cleanly instead.
                    speech.cancel()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speech.release()
        }
    }
    return speech
}
