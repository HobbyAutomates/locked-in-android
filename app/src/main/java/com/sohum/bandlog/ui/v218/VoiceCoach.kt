package com.sohum.bandlog.ui.v218

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.sohum.bandlog.data.V214Api
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.coach.CoachViewModel
import com.sohum.bandlog.ui.onboarding.OnbIcons
import com.sohum.bandlog.ui.theme.Brand
import com.sohum.bandlog.util.VoiceCoach
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * v2.18 B1 voice coach (Android): hands-free over the same coach chat. SpeechRecognizer hears the
 * question (en-IN or hi-IN), the reply comes back short and speakable (`voice: true`), TextToSpeech
 * reads it out, then it listens again. "Stop" / "bas" / "bye" ends; tapping the orb interrupts.
 * Every turn lands in the normal chat history.
 */
@Composable
fun VoiceCoachDialog(vm: AppViewModel, cvm: CoachViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    val prefs = remember { ctx.getSharedPreferences("v218", android.content.Context.MODE_PRIVATE) }
    var lang by remember { mutableStateOf(prefs.getString("voice_lang", "en-IN") ?: "en-IN") }
    var phase by remember { mutableStateOf("idle") }
    var heard by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var alive by remember { mutableStateOf(true) }
    val available = remember { runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    val listenRef = remember { arrayOf<() -> Unit>({}) }
    val tts = remember { TextToSpeech(ctx.applicationContext) { status -> ttsReady = status == TextToSpeech.SUCCESS } }
    DisposableEffect(Unit) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { if (alive) listenRef[0]() } }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { main.post { if (alive) listenRef[0]() } }
        })
        onDispose {
            alive = false
            runCatching { recognizer?.destroy() }
            runCatching { tts.stop(); tts.shutdown() }
        }
    }

    fun speak(text: String) {
        val say = VoiceCoach.speakable(text)
        if (!ttsReady || say.isBlank()) { listenRef[0](); return }
        phase = "speaking"
        runCatching { tts.language = if (lang == "hi-IN") Locale("hi", "IN") else Locale("en", "IN") }
        tts.speak(say, TextToSpeech.QUEUE_FLUSH, null, "coach-${System.nanoTime()}")
    }

    fun send(text: String) {
        phase = "thinking"; error = null
        scope.launch {
            try {
                val r = V214Api.send(text, null, voice = true)
                r.user?.let { cvm.messages.add(it) }
                r.reply?.let { m ->
                    cvm.messages.add(m)
                    if (m.cards.any { it.optString("type") in setOf("meal_logged", "water_logged", "fast_started") }) vm.refresh()
                    reply = m.text
                    speak(m.text)
                } ?: run { phase = "idle" }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "The coach couldn't answer."; phase = "idle" }
        }
    }

    fun listen() {
        if (!alive || !available) return
        runCatching { recognizer?.destroy() }
        runCatching { tts.stop() }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { phase = "listening" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(code: Int) {
                phase = "idle"
                if (code != SpeechRecognizer.ERROR_NO_MATCH && code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT && code != SpeechRecognizer.ERROR_CLIENT) {
                    error = if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "Microphone permission is off for Locked In." else "Didn't catch that. Tap the mic to try again."
                }
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                heard = t
                when {
                    t.isBlank() -> phase = "idle"
                    VoiceCoach.isStopPhrase(t) -> onClose()
                    else -> send(t)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer = r
        heard = ""
        error = null
        phase = "listening"
        runCatching {
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            })
        }.onFailure { phase = "idle"; error = "Voice input isn't available on this phone." }
    }
    listenRef[0] = { listen() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() else error = "Microphone permission is off for Locked In." }
    fun start() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) runCatching { permission.launch(Manifest.permission.RECORD_AUDIO) } else listen()
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); start() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(Brand.Ink.copy(alpha = 0.96f)).statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Voice coach", fontSize = 15.sp, fontWeight = FontWeight(700), color = Brand.Bone, modifier = Modifier.weight(1f))
                Box(Modifier.height(36.dp).background(Color.White.copy(alpha = 0.1f), CircleShape).clickable {
                    lang = if (lang == "en-IN") "hi-IN" else "en-IN"; prefs.edit().putString("voice_lang", lang).apply()
                }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(if (lang == "hi-IN") "हिंदी" else "EN", fontSize = 13.sp, fontWeight = FontWeight(700), color = Brand.Bone) }
                Spacer(Modifier.size(8.dp))
                Box(Modifier.height(36.dp).background(Brand.Bone, CircleShape).clickable(onClick = onClose).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Done", fontSize = 13.sp, fontWeight = FontWeight(700), color = Brand.Ink) }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!available) {
                    Text("Voice input isn't available on this phone. Type in the chat instead.", fontSize = 15.sp, color = Brand.Bone, textAlign = TextAlign.Center)
                } else {
                    val orb = when (phase) { "listening" -> Brand.Ember; "speaking" -> Color(0xFF7C6CF2); else -> Brand.Bone }
                    Box(
                        Modifier.size(150.dp).border(if (phase == "listening" || phase == "speaking") 14.dp else 0.dp, orb.copy(alpha = 0.25f), CircleShape).padding(14.dp)
                            .background(orb, CircleShape).clickable {
                                when (phase) {
                                    "listening" -> runCatching { recognizer?.stopListening() }
                                    "thinking" -> {}
                                    else -> start()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(OnbIcons.Mic, if (phase == "listening") "Stop listening" else "Speak", tint = Brand.Ink, modifier = Modifier.size(46.dp)) }
                    Text(
                        when (phase) { "listening" -> if (lang == "hi-IN") "सुन रहा हूँ…" else "LISTENING…"; "thinking" -> "THINKING…"; "speaking" -> "SPEAKING · TAP TO INTERRUPT"; else -> "TAP THE MIC AND ASK" },
                        fontSize = 12.sp, fontWeight = FontWeight(700), letterSpacing = 1.sp, color = Brand.Bone.copy(alpha = 0.7f),
                    )
                    if (heard.isNotBlank()) Text("“$heard”", fontSize = 17.sp, lineHeight = 24.sp, color = Brand.Bone, textAlign = TextAlign.Center)
                    if (reply.isNotBlank() && phase != "listening") Text(reply, fontSize = 15.sp, lineHeight = 22.sp, color = Brand.Bone, textAlign = TextAlign.Center, modifier = Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(16.dp, 12.dp))
                    error?.let { Text(it, fontSize = 13.sp, color = Brand.EmberLight, textAlign = TextAlign.Center) }
                }
            }
            Text("Try “how much protein is left?”, “log 2 roti and dal” or “took my creatine”. Say “stop” to finish.", fontSize = 12.sp, lineHeight = 17.sp, color = Brand.Bone.copy(alpha = 0.55f), textAlign = TextAlign.Center)
        }
    }
}
