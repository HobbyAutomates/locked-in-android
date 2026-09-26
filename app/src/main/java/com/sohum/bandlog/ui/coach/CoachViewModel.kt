package com.sohum.bandlog.ui.coach

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohum.bandlog.data.AuthException
import com.sohum.bandlog.data.Buddy
import com.sohum.bandlog.data.CoachMemory
import com.sohum.bandlog.data.CoachMessage
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.NoteBundle
import com.sohum.bandlog.data.V214Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** v2.14 coach pages, pushed over the tab shell by [CoachOverlays]. */
enum class CoachPage { CHAT, MEMORY, STYLE, TUNE, BUDDY }

/** The coach's own little back stack (like PlatformNav), so MainActivity needs one overlay call. */
object CoachNav {
    val stack = mutableStateListOf<CoachPage>()
    val page: CoachPage? get() = stack.lastOrNull()
    /** A message to put in the chat box when it opens (Today's note "Reply" leaves it empty). */
    var prompt by mutableStateOf<String?>(null)

    fun open(p: CoachPage) { if (stack.lastOrNull() == p) return; stack.remove(p); stack.add(p) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun closeAll() { stack.clear() }
}

/**
 * State for the coach surfaces (web routes, bearer token): Home's note, the chat, what the coach
 * knows, and the buddy streaks. `null` availability = not loaded yet; false = v37 / the routes
 * aren't live ("Coming with the next update").
 */
class CoachViewModel : ViewModel() {
    // ---- today's note ----
    var notes by mutableStateOf<NoteBundle?>(null); private set
    var noteAvailable by mutableStateOf<Boolean?>(null); private set
    private var noteLoadedAt = 0L

    fun loadNote(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - noteLoadedAt < 10 * 60_000L && noteAvailable != null) return
        noteLoadedAt = now
        viewModelScope.launch {
            try { notes = V214Api.note(); noteAvailable = true }
            catch (e: NotYetAvailable) { noteAvailable = false; notes = null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.i("LockedIn", "Coach note: ${e.message}"); if (noteAvailable == null) noteAvailable = false }
        }
    }

    // ---- chat ----
    val messages = mutableStateListOf<CoachMessage>()
    var chatAvailable by mutableStateOf<Boolean?>(null); private set
    var chatStyle by mutableStateOf("balanced"); private set
    var remember by mutableStateOf(true); private set
    var teen by mutableStateOf(false); private set
    var sending by mutableStateOf(false); private set
    var chatError by mutableStateOf<String?>(null)
    /** Memory proposals already answered in this session (id → kept). */
    val decided = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    /** Bumped after a reply logged something (Home refreshes). */
    var loggedTick by mutableIntStateOf(0); private set

    fun loadChat() {
        viewModelScope.launch {
            try {
                val s = V214Api.chat()
                chatStyle = s.style; remember = s.remember; teen = s.teen
                messages.clear(); messages.addAll(s.messages)
                chatAvailable = true
            } catch (e: NotYetAvailable) { chatAvailable = false }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: AuthException) { chatError = e.message; if (chatAvailable == null) chatAvailable = false }
            catch (e: Exception) { chatError = e.message ?: "Couldn't load the chat"; if (chatAvailable == null) chatAvailable = true }
        }
    }

    /** Sends [text] (and an optional base64 JPEG). Returns the text to put back in the box on failure. */
    fun send(text: String, imageBase64: String?, onRestore: (String) -> Unit) {
        val m = text.trim()
        if ((m.isEmpty() && imageBase64 == null) || sending) return
        sending = true; chatError = null
        val temp = CoachMessage("tmp-${System.nanoTime()}", "user", m.ifEmpty { "(photo)" }, emptyList(), false, null)
        messages.add(temp)
        viewModelScope.launch {
            try {
                val r = V214Api.send(m, imageBase64)
                messages.remove(temp)
                r.user?.let { messages.add(it) } ?: messages.add(temp)
                r.reply?.let { reply ->
                    // Learned memories the reply didn't already carry as cards.
                    val carried = reply.cards.filter { it.optString("type") == "memory" }.map { it.optString("id") }.toSet()
                    val extra = r.learned.filter { it.id !in carried }.map { mem ->
                        org.json.JSONObject().put("type", "memory").put("id", mem.id).put("kind", mem.kind).put("text", mem.text)
                    }
                    messages.add(if (extra.isEmpty()) reply else reply.copy(cards = reply.cards + extra))
                    if (reply.cards.any { it.optString("type") in setOf("meal_logged", "water_logged", "fast_started") }) loggedTick++
                }
            } catch (e: NotYetAvailable) { messages.remove(temp); chatAvailable = false; onRestore(m) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { messages.remove(temp); onRestore(m); chatError = e.message ?: "The coach couldn't answer. Try again?" }
            finally { sending = false }
        }
    }

    fun decide(id: String, keep: Boolean) {
        decided[id] = keep
        viewModelScope.launch {
            runCatching { if (keep) V214Api.keepMemory(id) else V214Api.forgetMemory(id) }
            memories = memories.mapNotNull { if (it.id == id) (if (keep) it.copy(kept = true) else null) else it }
        }
    }

    // ---- memory ----
    var memories by mutableStateOf<List<CoachMemory>>(emptyList()); private set
    var memoryAvailable by mutableStateOf<Boolean?>(null); private set
    var memoryError by mutableStateOf<String?>(null)
    var memoryNotice by mutableStateOf<String?>(null)

    fun loadMemory() {
        viewModelScope.launch {
            try {
                val s = V214Api.memory()
                memories = s.memories; remember = s.remember; memoryAvailable = true
            } catch (e: NotYetAvailable) { memoryAvailable = false }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { memoryError = e.message ?: "Couldn't load what your coach knows"; if (memoryAvailable == null) memoryAvailable = true }
        }
    }

    fun forget(id: String) {
        val before = memories
        memories = memories.filter { it.id != id }
        viewModelScope.launch { runCatching { V214Api.forgetMemory(id) }.onFailure { memories = before; memoryError = it.message ?: "Couldn't forget that" } }
    }

    fun add(text: String, kind: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val m = V214Api.addMemory(text.trim(), kind)
                if (m != null) memories = listOf(m) + memories else loadMemory()
                onDone(true)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { memoryError = e.message ?: "Couldn't add that"; onDone(false) }
        }
    }

    fun setRememberLocal(on: Boolean) { remember = on }

    fun deleteChat() {
        viewModelScope.launch {
            runCatching { V214Api.deleteChat() }
                .onSuccess { messages.clear(); memoryNotice = "Chat history deleted." }
                .onFailure { memoryError = it.message ?: "Couldn't delete the chat" }
        }
    }

    fun forgetEverything() {
        viewModelScope.launch {
            runCatching { V214Api.forgetMemory("all") }
                .onSuccess { memories = emptyList(); memoryNotice = "Your coach forgot everything." }
                .onFailure { memoryError = it.message ?: "Couldn't do that right now" }
        }
    }

    /** Downloads the export and hands the JSON file to the share sheet. */
    fun export(ctx: Context) {
        viewModelScope.launch {
            try {
                val json = V214Api.export()
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                    val f = File(dir, "locked-in-coach.json")
                    f.writeText(json)
                    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(send, "Export coach data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { memoryError = e.message ?: "Couldn't export right now" }
        }
    }

    // ---- buddies ----
    var buddies by mutableStateOf<List<Buddy>?>(null); private set
    /** False when my_buddies() isn't there (schema_v37): the card hides. */
    var buddyAvailable by mutableStateOf<Boolean?>(null); private set
    var buddyNote by mutableStateOf<String?>(null)
    val nudged = androidx.compose.runtime.mutableStateMapOf<String, String>()
    var buddyBusy by mutableStateOf(false); private set

    fun loadBuddies() {
        viewModelScope.launch {
            try { buddies = V214Api.myBuddies(); buddyAvailable = true }
            catch (e: NotYetAvailable) { buddyAvailable = false; buddies = null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.i("LockedIn", "Buddies: ${e.message}"); if (buddyAvailable == null) buddyAvailable = false }
        }
    }

    fun nudge(b: Buddy) {
        nudged[b.id] = "…"
        viewModelScope.launch {
            nudged[b.id] = runCatching { if (V214Api.buddyNudge(b.id)) "Nudged" else "Sent today" }.getOrDefault("Try later")
        }
    }

    fun invite(ctx: Context) {
        if (buddyBusy) return
        buddyBusy = true
        viewModelScope.launch {
            try {
                val c = V214Api.buddyInvite()
                com.sohum.bandlog.ui.squad.shareInvite(ctx, "Be my Locked In buddy: we both log, the streak grows. ${V214Api.buddyLink(c)} (code $c)")
            } catch (e: NotYetAvailable) { buddyNote = "Buddy streaks are coming with the next update." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { buddyNote = e.message ?: "Couldn't make an invite" }
            finally { buddyBusy = false }
        }
    }

    fun accept(code: String, onDone: (Boolean) -> Unit) {
        if (buddyBusy) return
        buddyBusy = true; buddyNote = null
        viewModelScope.launch {
            try { V214Api.buddyAccept(code); buddyNote = "You're buddies now. Both log today to start the streak."; loadBuddies(); onDone(true) }
            catch (e: NotYetAvailable) { buddyNote = "Buddy streaks are coming with the next update."; onDone(false) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { buddyNote = e.message ?: "That code didn't work"; onDone(false) }
            finally { buddyBusy = false }
        }
    }
}
