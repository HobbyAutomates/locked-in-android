package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Squad Food Battle (v2.7, docs/food-battle-spec.md): the daily in-squad calorie game. Talks to
 * the same `bandlog` schema PostgREST endpoint as [Api] (own small client so this feature's data
 * layer stays a self-contained new file rather than editing Api.kt's meal/water code paths).
 *
 * Mirrors the web's src/lib/battle.ts / battleLines.ts and the SQL in supabase/schema_v29.sql:
 * `bandlog.battle_board(g, d)`, `bandlog.battle_close(g, d)`, `bandlog.my_graffiti(u)`,
 * `groups.battle_enabled`, and the `group_posts` row a Snap enriches with a kcal range + one-liner.
 */
object BattleRepo {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private const val SCHEMA = "bandlog"
    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())

    private suspend fun rest(path: String): Request.Builder {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        return Request.Builder().url("$base/rest/v1/$path")
            .header("apikey", key)
            .header("Authorization", "Bearer $token")
            .header("Accept-Profile", SCHEMA)
            .header("Content-Profile", SCHEMA)
    }

    private fun run(r: Request, label: String): String = client.newCall(r).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (!res.isSuccessful) {
            android.util.Log.w("LockedIn", "$label failed (${res.code}) ${r.method} ${r.url.encodedPath}: ${body.take(800)}")
            val msg = runCatching { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } }.getOrNull().orEmpty()
            throw ApiException("$label failed (${res.code})${if (msg.isNotBlank()) ": $msg" else ""}")
        }
        body
    }

    private suspend fun rpc(fn: String, payload: JSONObject, label: String): String = withContext(Dispatchers.IO) {
        run(rest("rpc/$fn").post(json(payload.toString())).build(), label)
    }

    // ---- battle_board(g, d) ----

    /** One row of `bandlog.battle_board(g, d)`. */
    data class BattleRow(
        val userId: String,
        val name: String,
        val avatarPath: String?,
        /** lose | maintain | gain */
        val goalType: String,
        val eaten: Double,
        val target: Double,
        val r: Double,
        val score: Double,
        val meals: Int,
        val eligible: Boolean,
        val private: Boolean,
    ) {
        /** Eligible (>=2 meals) and sharing stats — the only rows the crown can go to. */
        val canWin: Boolean get() = eligible && !private
        /** goal=lose and r < 0.75: the board must show "under-fuelled", never praise. */
        val underFuelled: Boolean get() = goalType == "lose" && r < 0.75
        val goalLabel: String get() = when (goalType) { "gain" -> "Bulk"; "lose" -> "Cut"; else -> "Maintain" }

        companion object {
            private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
            fun from(o: JSONObject) = BattleRow(
                userId = o.getString("user_id"),
                name = o.optString("name").ifBlank { "Member" },
                avatarPath = o.s("avatar_path"),
                goalType = o.optString("goal_type", "maintain").ifBlank { "maintain" },
                eaten = o.optDouble("eaten", 0.0),
                target = o.optDouble("target", 0.0),
                r = o.optDouble("r", 0.0),
                score = o.optDouble("score", 0.0),
                meals = o.optInt("meals", 0),
                eligible = o.optBoolean("eligible", false),
                private = o.optBoolean("private", false),
            )
        }
    }

    /** `bandlog.battle_board(g, d)`, ordered by the SQL exactly as the web reads it (leader first). */
    suspend fun board(groupId: String, date: String): List<BattleRow> {
        val arr = JSONArray(rpc("battle_board", JSONObject().put("g", groupId).put("d", date), "Load battle board"))
        return (0 until arr.length()).map { BattleRow.from(arr.getJSONObject(it)) }
    }

    /** The leader's score among rows that can win, or null when nobody is eligible yet. */
    fun leaderScore(rows: List<BattleRow>): Double? = rows.filter { it.canWin }.maxOfOrNull { it.score }

    /** "X pts to lead" for a non-leading, eligible row; null for the leader or the ineligible/private. */
    fun pointsToLead(row: BattleRow, rows: List<BattleRow>): Int? {
        if (!row.canWin) return null
        val lead = leaderScore(rows) ?: return null
        val gap = Math.round(lead - row.score).toInt()
        return if (gap > 0) gap else null
    }

    // ---- battle_close(g, d) ----

    /** A row of `bandlog.battle_close(g, d)`: the crown winner for a day that has closed (empty when nothing to close/insert). */
    data class BattleWinner(val userId: String, val name: String, val score: Double, val goalType: String) {
        val goalLabel: String get() = when (goalType) { "gain" -> "Bulk"; "lose" -> "Cut"; else -> "Maintain" }
        companion object {
            fun from(o: JSONObject) = BattleWinner(o.getString("user_id"), o.optString("name").ifBlank { "Member" }, o.optDouble("score", 0.0), o.optString("goal_type", "maintain"))
        }
    }

    /**
     * Idempotently closes [date] (must be before the caller's own today) for [groupId]: the first
     * read after midnight inserts the win + crown post; later reads just return it. No-op (empty
     * result) when battle isn't enabled or the day hasn't closed yet — safe to call on every load.
     */
    suspend fun closeDay(groupId: String, date: String): BattleWinner? {
        val arr = JSONArray(rpc("battle_close", JSONObject().put("g", groupId).put("d", date), "Close battle day"))
        return if (arr.length() == 0) null else BattleWinner.from(arr.getJSONObject(0))
    }

    // ---- my_graffiti(u) ----

    data class GraffitiWin(val groupId: String, val groupName: String, val date: String, val score: Double, val goalType: String) {
        val goalLabel: String get() = when (goalType) { "gain" -> "Bulk"; "lose" -> "Cut"; else -> "Maintain" }
        companion object {
            fun from(o: JSONObject) = GraffitiWin(o.getString("group_id"), o.optString("group_name").ifBlank { "Squad" }, o.optString("date").take(10), o.optDouble("score", 0.0), o.optString("goal_type", "maintain"))
        }
    }

    data class Graffiti(val total: Int, val recent: List<GraffitiWin>)

    /** `bandlog.my_graffiti(u)`: total crown count + the last 7 wins, for the profile's graffiti wall. */
    suspend fun myGraffiti(): Graffiti = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val arr = JSONArray(rpc("my_graffiti", JSONObject().put("u", uid), "Load graffiti wall"))
        if (arr.length() == 0) return@withContext Graffiti(0, emptyList())
        val total = arr.getJSONObject(0).optInt("total", 0)
        Graffiti(total, (0 until arr.length()).map { GraffitiWin.from(arr.getJSONObject(it)) })
    }

    // ---- groups.battle_enabled ----

    /** Owner-only in practice (RLS on the update): true when the PATCH actually changed a row. */
    suspend fun setBattleEnabled(groupId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = run(
                rest("groups?id=eq.$groupId").header("Prefer", "return=representation")
                    .patch(json(JSONObject().put("battle_enabled", enabled).toString())).build(),
                "Save battle setting",
            )
            JSONArray(body).length() > 0
        }.getOrDefault(false)
    }

    /** False (feature off / column not there yet) when it can't be read. */
    suspend fun battleEnabled(groupId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(run(rest("groups?select=battle_enabled&id=eq.$groupId").get().build(), "Load battle setting"))
            arr.optJSONObject(0)?.optBoolean("battle_enabled", false) ?: false
        }.getOrDefault(false)
    }

    // ---- Snap-to-squad enrichment ----

    /**
     * After a meal photo is saved the normal way (auto-posts to every squad as kind `meal`), this
     * enriches *this* squad's post with the item summary, a kcal range and a hype one-liner —
     * matching `postBattleSnap`'s web behaviour. Silently a no-op if the post row isn't found yet
     * (post_to_my_groups races the meal save by a beat) — callers may retry once after a short delay.
     */
    suspend fun enrichSnapPost(groupId: String, mealId: String, body: String): Boolean = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        runCatching {
            val path = "group_posts?group_id=eq.$groupId&user_id=eq.$uid&kind=eq.meal&ref_id=eq.$mealId"
            val res = run(rest(path).header("Prefer", "return=representation").patch(json(JSONObject().put("body", body.take(500)).toString())).build(), "Update squad post")
            JSONArray(res).length() > 0
        }.getOrDefault(false)
    }

    /** "~520 kcal (440–610)" — grams_low/grams_high aren't on the Android plate model, so ±15% of the total. */
    fun kcalRangeText(totalKcal: Double): String {
        val lo = Math.round(totalKcal * 0.85).toInt()
        val hi = Math.round(totalKcal * 1.15).toInt()
        return "~${Math.round(totalKcal).toInt()} kcal ($lo–$hi)"
    }
}
