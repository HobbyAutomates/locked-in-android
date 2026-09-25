package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v2.10 beta usage events → bandlog.app_events (schema_v33). `Analytics.track(name, "k" to v)` is
 * fire-and-forget: it queues and returns at once; a background coroutine writes the queue in one
 * insert a few seconds later (or when the app goes to the background). Signed-out events, network
 * errors and a missing table are dropped silently — this never throws and never touches the UI thread.
 *
 * Kill switch: BuildConfig.BETA_ANALYTICS (local.properties BETA_ANALYTICS=false) makes every call
 * a no-op. Turn it off, or cover it in the privacy policy, before public launch. See docs/ADMIN.md
 * in the web repo.
 */
object Analytics {
    private const val FLUSH_MS = 5_000L
    private const val MAX_QUEUE = 100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<JSONObject>()
    private val scheduled = AtomicBoolean(false)
    @Volatile private var dead = !BuildConfig.BETA_ANALYTICS

    /** The last screen_view, attached to error_shown so errors say where they happened. */
    @Volatile var screen: String? = null; private set

    /** One-shot hint for the next meal_logged (a scan hands its food to Add food, which saves it later). */
    @Volatile private var mealHint: String? = null
    fun hintMealMethod(method: String) { mealHint = method }
    fun takeMealMethodHint(): String? = mealHint.also { mealHint = null }

    fun track(name: String, vararg props: Pair<String, Any?>) {
        if (dead) return
        try {
            val p = JSONObject()
            for ((k, v) in props.take(12)) when (v) {
                null -> Unit
                is Boolean, is Int, is Long -> p.put(k.take(40), v)
                is Number -> v.toDouble().takeIf { it.isFinite() }?.let { p.put(k.take(40), it) }
                else -> p.put(k.take(40), v.toString().take(160))
            }
            if (name == "screen_view") screen = p.optString("screen").ifBlank { null }
            if (name == "error_shown" && !p.has("screen")) screen?.let { p.put("screen", it) }
            queue.add(JSONObject().put("name", name).put("props", p))
            while (queue.size > MAX_QUEUE) queue.poll()
            if (scheduled.compareAndSet(false, true)) scope.launch { delay(FLUSH_MS); flush() }
        } catch (_: Throwable) {
            // never let analytics break the app
        }
    }

    /** Send whatever is queued now (the activity going to the background). */
    fun flushSoon() {
        if (dead || queue.isEmpty()) return
        scope.launch { flush() }
    }

    private suspend fun flush() {
        scheduled.set(false)
        val batch = generateSequence { queue.poll() }.toList()
        if (batch.isEmpty() || dead) return
        try {
            val uid = Session.userId ?: return
            val rows = JSONArray()
            for (e in batch) rows.put(
                e.put("user_id", uid).put("platform", "android").put("app_version", BuildConfig.VERSION_NAME)
            )
            Api.insertAppEvents(rows)
        } catch (e: Throwable) {
            val m = e.message.orEmpty()
            // schema_v33 not applied: stop trying for this process.
            if ("(404)" in m || "does not exist" in m || "Could not find the table" in m) dead = true
        }
    }
}
