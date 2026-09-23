package com.sohum.bandlog.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Built-in dictation for people without Wispr Flow: Android's SpeechRecognizer, en-IN by default,
 * long-press the mic for hi-IN. Every call is wrapped in runCatching — a phone without Google's
 * recogniser (or with it disabled) gets "Voice input isn't available on this phone" and nothing else.
 */
class DictationState internal constructor(private val ctx: Context, private val onText: () -> ((String) -> Unit)) {
    var listening by mutableStateOf(false); private set
    var lang by mutableStateOf("en-IN"); private set
    var error by mutableStateOf<String?>(null); private set
    val available: Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)
    private var recognizer: SpeechRecognizer? = null

    val langLabel: String get() = if (lang == "hi-IN") "हिंदी" else "EN"

    fun toggleLang() { stop(); lang = if (lang == "en-IN") "hi-IN" else "en-IN" }

    /** Starts listening; [requestPermission] is invoked when RECORD_AUDIO has not been granted yet. */
    fun start(requestPermission: () -> Unit) {
        error = null
        if (!available) { error = "Voice input isn't available on this phone — use your keyboard's mic instead."; return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermission(); return }
        runCatching {
            stop()
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(code: Int) {
                    listening = false
                    error = when (code) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off for Locked In."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — tap the mic and try again."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice input needs a connection right now."
                        else -> "Voice input isn't available on this phone — use your keyboard's mic instead."
                    }
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotBlank()) onText()(best)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer = r
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }
            r.startListening(intent)
            listening = true
        }.onFailure {
            listening = false
            error = "Voice input isn't available on this phone — use your keyboard's mic instead."
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening(); recognizer?.destroy() }
        recognizer = null
        listening = false
    }
}

/** Dictation bound to this composable's lifetime; [onText] receives each final phrase. */
@Composable
fun rememberDictation(onText: (String) -> Unit): Pair<DictationState, () -> Unit> {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onText)
    val state = remember { DictationState(ctx.applicationContext) { latest } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.start {} else state.stop()
    }
    DisposableEffect(Unit) { onDispose { state.stop() } }
    val toggle: () -> Unit = {
        if (state.listening) state.stop()
        else state.start { runCatching { permission.launch(Manifest.permission.RECORD_AUDIO) } }
    }
    return state to toggle
}
