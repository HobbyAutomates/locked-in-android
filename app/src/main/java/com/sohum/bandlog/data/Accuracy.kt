package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.util.PerUnit
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
 * v2.15 accuracy (schema_v38). Three best-effort writers that never throw into the UI:
 *  - [Overrides]: the user's remembered "Calories per roti" (bandlog.user_food_overrides).
 *  - [Corrections]: one row per "Correct the numbers" save (bandlog.food_corrections).
 *  - [LogEvents]: the beta log of every entry and note (bandlog.log_events), gated by BETA_ANALYTICS.
 * A missing table (schema_v38 not applied yet) turns each one off for the process, silently.
 */
private val accuracyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun missingTable(e: Throwable): Boolean {
    val m = e.message.orEmpty()
    return "(404)" in m || "does not exist" in m || "Could not find the table" in m || "PGRST205" in m || "42P01" in m
}

private fun r1(d: Double) = (d * 10).let { Math.round(it) / 10.0 }

object Overrides {
    /** null = not loaded yet; false = the table isn't there (the "remember" part of the feature hides). */
    @Volatile var available: Boolean? = null; private set
    @Volatile private var rows: List<PerUnit.Override> = emptyList()

    /** Loads once per sign-in (again with [force]). */
    suspend fun load(force: Boolean = false) {
        if (!force && available != null) return
        if (Session.userId == null) return
        try { rows = Api.foodOverrides(); available = true }
        catch (e: Throwable) { if (missingTable(e)) available = false }
    }

    fun clear() { rows = emptyList(); available = null }

    /** The user's number for this food in [unit] ("roti", or [PerUnit.PER_100G]). */
    fun find(foodId: String?, name: String, unit: String): PerUnit.Override? {
        if (available != true) return null
        val keys = setOfNotNull(PerUnit.foodKey(foodId, name).ifEmpty { null }, foodId?.takeIf { it.isNotBlank() }?.let { "id:$it" })
        return rows.firstOrNull { o -> o.foodKey in keys && (o.unit == unit || (unit != PerUnit.PER_100G && o.unit != PerUnit.PER_100G && PerUnit.sameNoun(o.unit, unit))) }
    }

    /** Any remembered number that fits a fresh row (a per-unit one first, then per 100 g). */
    fun applyTo(item: MealItem): MealItem {
        if (available != true || item.userVerified) return item
        val noun = PerUnit.countOf(item)?.second
        val o = (noun?.let { find(item.foodId, item.name, it) }) ?: find(item.foodId, item.name, PerUnit.PER_100G) ?: return item
        return PerUnit.applyOverride(item, o)?.copy(userVerified = true) ?: item
    }

    /** Remember it (in memory at once, then the table). No-op while the table is missing. */
    fun remember(o: PerUnit.Override) {
        if (available == false) return
        rows = listOf(o) + rows.filterNot { it.foodKey == o.foodKey && it.unit == o.unit }
        accuracyScope.launch {
            try { Api.saveFoodOverride(o) } catch (e: Throwable) { if (missingTable(e)) available = false }
        }
    }
}

object Corrections {
    /** The row a correction came from: the meal (when saved), how it was entered, the scan and the raw words / plate note. */
    data class Ctx(val mealId: String? = null, val inputKind: String = "manual", val scanId: String? = null, val rawInput: String? = null)

    @Volatile private var dead = false

    fun record(ctx: Ctx, before: MealItem, after: MealItem, source: String?, note: String?) {
        if (dead) return
        val row = try {
            JSONObject()
                .put("meal_id", ctx.mealId ?: JSONObject.NULL)
                .put("item_name", after.name.take(160).ifBlank { "Food" })
                .put("food_id", after.foodId ?: JSONObject.NULL)
                .put("input_kind", ctx.inputKind.takeIf { it in KINDS } ?: "manual")
                .put("grams", r1(after.grams))
                // The count noun and count for a counted row; null for a gram-weighed one.
                .put("unit", PerUnit.countOf(after)?.second?.take(40) ?: JSONObject.NULL)
                .put("count", PerUnit.countOf(after)?.first ?: JSONObject.NULL)
                .put("app_kcal", r1(before.calories)).put("app_protein", r1(before.proteinG)).put("app_carbs", r1(before.carbsG)).put("app_fat", r1(before.fatG))
                .put("user_kcal", r1(after.calories)).put("user_protein", r1(after.proteinG)).put("user_carbs", r1(after.carbsG)).put("user_fat", r1(after.fatG))
                .put("source", source?.trim()?.take(300)?.ifBlank { null } ?: JSONObject.NULL)
                .put("note", note?.trim()?.take(1000)?.ifBlank { null } ?: JSONObject.NULL)
                .put("scan_id", ctx.scanId ?: JSONObject.NULL)
                .put("raw_input", ctx.rawInput?.take(2000) ?: JSONObject.NULL)
        } catch (_: Throwable) { return }
        accuracyScope.launch {
            try { Api.insertCorrection(row) } catch (e: Throwable) { if (missingTable(e)) dead = true }
        }
    }

    val KINDS = setOf("text", "photo", "barcode", "label", "manual")
}

/**
 * Beta: one row per meal logged, item edited / deleted / skipped, scan accepted / dismissed and note
 * typed. Queued and written in one insert a few seconds later, like [Analytics]; the same kill
 * switch (BuildConfig.BETA_ANALYTICS) turns it off.
 */
object LogEvents {
    private const val FLUSH_MS = 4_000L
    private const val MAX_QUEUE = 200
    private val queue = ConcurrentLinkedQueue<JSONObject>()
    private val scheduled = AtomicBoolean(false)
    @Volatile private var dead = !BuildConfig.BETA_ANALYTICS

    val KINDS = setOf("log", "edit", "delete", "skip", "scan_accept", "scan_dismiss", "note")

    /** The numbers of one row, for a payload's before / after. */
    fun numbers(i: MealItem): JSONObject = JSONObject()
        .put("name", i.name).put("grams", r1(i.grams)).put("kcal", r1(i.calories))
        .put("protein", r1(i.proteinG)).put("carbs", r1(i.carbsG)).put("fat", r1(i.fatG))
        .put("unit", i.unit ?: JSONObject.NULL).put("servings", i.servings ?: JSONObject.NULL)
        .put("source", i.source).put("food_id", i.foodId ?: JSONObject.NULL)
        .put("user_verified", i.userVerified).put("per_unit_kcal", i.perUnitKcal ?: JSONObject.NULL)
        .apply { if (i.sourceUrls.isNotEmpty()) put("source_urls", JSONArray(i.sourceUrls.take(3))) }

    fun record(kind: String, mealId: String? = null, itemName: String? = null, payload: JSONObject = JSONObject()) {
        if (dead || kind !in KINDS) return
        try {
            queue.add(
                JSONObject().put("kind", kind).put("meal_id", mealId ?: JSONObject.NULL)
                    .put("item_name", itemName?.take(160) ?: JSONObject.NULL).put("payload", payload)
                    .put("created_at", java.time.Instant.now().toString())
            )
            while (queue.size > MAX_QUEUE) queue.poll()
            if (scheduled.compareAndSet(false, true)) accuracyScope.launch { delay(FLUSH_MS); flush() }
        } catch (_: Throwable) { }
    }

    /** An item edit: its numbers before and after, and what changed. */
    fun edit(mealId: String?, before: MealItem, after: MealItem, what: String) {
        if (dead) return
        if (before.calories == after.calories && before.grams == after.grams && before.proteinG == after.proteinG && before.userVerified == after.userVerified) return
        record("edit", mealId, after.name, JSONObject().put("what", what).put("before", numbers(before)).put("after", numbers(after)))
    }

    /** An item removed: a fresh AI suggestion is a "skip" (it was probably wrong), a saved row a "delete". */
    fun removed(mealId: String?, item: MealItem, aiSuggested: Boolean) {
        if (dead) return
        record(if (aiSuggested) "skip" else "delete", mealId, item.name, JSONObject().put("item", numbers(item)).put("input_kind", item.inputKind ?: JSONObject.NULL))
    }

    fun note(text: String, where: String, mealId: String? = null) {
        if (dead || text.isBlank()) return
        record("note", mealId, null, JSONObject().put("text", text.take(1000)).put("where", where))
    }

    fun flushSoon() { if (!dead && queue.isNotEmpty()) accuracyScope.launch { flush() } }

    private suspend fun flush() {
        scheduled.set(false)
        val batch = generateSequence { queue.poll() }.toList()
        if (batch.isEmpty() || dead) return
        try {
            val uid = Session.userId ?: return
            val rows = JSONArray()
            for (e in batch) rows.put(e.put("user_id", uid).put("platform", "android").put("app_version", BuildConfig.VERSION_NAME.take(20)))
            Api.insertLogEvents(rows)
        } catch (e: Throwable) {
            if (missingTable(e)) dead = true
        }
    }
}
