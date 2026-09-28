package com.sohum.bandlog.util

/**
 * v2.18 E1 offline logging: the queue maths (pure). Port of the web's
 * src/lib/social/offlineQueue.ts; the store and the replay live in data/OfflineQueue.kt.
 *
 *   - A log is queued when the phone is offline, or the save failed with a network error (it never
 *     reached the server). Server errors (validation, auth) are NOT queued: they'd fail again.
 *   - The queue replays oldest first when the network is back / the app opens. An item that keeps
 *     failing with a server error is dropped after [MAX_TRIES].
 *   - The chip says "3 waiting to sync" while anything is queued.
 */
object OfflineRules {
    const val MAX_TRIES = 5
    const val QUEUE_LIMIT = 200
    val KINDS = listOf("meal", "workout", "exercise", "water")

    /** [payload] is a JSON object string (the save call's arguments). */
    data class Item(val id: String, val kind: String, val payload: String, val createdAt: String, val tries: Int = 0, val lastError: String? = null)

    private var seq = 0
    fun newId(nowMs: Long = System.currentTimeMillis()): String {
        seq = (seq + 1) % 1_000_000
        return "q-${nowMs.toString(36)}-${seq.toString(36)}-${(Math.random() * 1e9).toLong().toString(36).take(5)}"
    }

    private val NETWORK = Regex(
        "failed to fetch|networkerror|network request failed|load failed|fetch failed|err_internet_disconnected|the internet connection appears to be offline|unable to resolve host|failed to connect|timeout|timed out|connection (reset|refused|closed)",
        RegexOption.IGNORE_CASE,
    )

    /** Network failures: an IOException from OkHttp (DNS, connect, timeout, reset), or a message that says so. */
    fun isNetworkError(e: Throwable?): Boolean {
        if (e == null) return false
        if (e is kotlinx.coroutines.CancellationException) return false
        if (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException || e is javax.net.ssl.SSLException) return true
        if (e is java.net.SocketException) return true
        val msg = e.message ?: e.toString()
        if (NETWORK.containsMatchIn(msg)) return true
        return e.cause?.let { it !== e && isNetworkError(it) } == true
    }

    /** Queue it? Offline, or the save failed before reaching the server. */
    fun shouldQueue(online: Boolean, error: Throwable? = null): Boolean = !online || (error != null && isNetworkError(error))

    fun enqueue(queue: List<Item>, kind: String, payload: String, nowIso: String, id: String = newId()): List<Item> {
        val out = queue + Item(id, kind, payload, nowIso)
        return if (out.size > QUEUE_LIMIT) out.takeLast(QUEUE_LIMIT) else out
    }

    /** Oldest first. */
    fun ordered(queue: List<Item>): List<Item> = queue.sortedWith(compareBy<Item> { it.createdAt }.thenBy { it.id })

    enum class Outcome { OK, NETWORK, ERROR }
    data class After(val queue: List<Item>, val dropped: Item?, val stop: Boolean)

    /** ok → removed; network → kept and the run stops (still offline); error → tries + 1, dropped at [MAX_TRIES]. */
    fun afterAttempt(queue: List<Item>, id: String, outcome: Outcome, error: String? = null): After {
        if (outcome == Outcome.OK) return After(queue.filter { it.id != id }, null, false)
        if (outcome == Outcome.NETWORK) return After(queue, null, true)
        var dropped: Item? = null
        val next = mutableListOf<Item>()
        for (q in queue) {
            if (q.id != id) { next.add(q); continue }
            val tries = q.tries + 1
            if (tries >= MAX_TRIES) dropped = q.copy(tries = tries, lastError = error) else next.add(q.copy(tries = tries, lastError = error))
        }
        return After(next, dropped, false)
    }

    /** "Dal chawal, 2 roti" for a queued meal. */
    fun label(q: Item): String {
        val p = runCatching { org.json.JSONObject(q.payload) }.getOrNull()
        return when (q.kind) {
            "meal" -> (p?.optString("raw_text")?.ifBlank { null } ?: "Meal").take(60)
            "water" -> "${p?.optInt("ml", 250)?.takeIf { it > 0 } ?: 250} ml water"
            "exercise" -> p?.optString("name")?.ifBlank { null } ?: "Activity"
            else -> "Workout"
        }
    }
}
