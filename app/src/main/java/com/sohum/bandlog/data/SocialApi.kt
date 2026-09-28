package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.util.CoachView
import com.sohum.bandlog.util.Freezes
import com.sohum.bandlog.util.LiveSquad
import com.sohum.bandlog.util.Pledges
import com.sohum.bandlog.util.Stamps
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
 * v2.18 social + platform calls (web docs/schema_v44.sql and schema_v45.sql, NOT applied yet;
 * contract in the web's docs/v218-social-shared.md). Its own file like PlatformApi / V214Api so
 * the other v2.18 streams can change Api.kt without conflicts.
 *
 * Every feature reads its table / RPC in its own call. A missing table / column / function
 * (42P01, 42703, 42883, PGRST202/204/205, "does not exist", "could not find", or a 404) throws
 * [NotYetAvailable], which the screens show as "Coming with the next update" (or hide).
 */
object SocialApi {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())

    /** The web app (invite links, account deletion). */
    val site: String get() = BuildConfig.API_BASE.trimEnd('/').ifBlank { com.sohum.bandlog.ui.squad.WEB_URL }

    private val MISSING = listOf("42P01", "42703", "42883", "PGRST202", "PGRST204", "PGRST205", "does not exist", "could not find")

    fun isMissing(code: Int, body: String): Boolean = code == 404 || MISSING.any { body.contains(it, ignoreCase = true) }

    /** True for [NotYetAvailable] (the v44 / v45 schema isn't applied yet). */
    fun missing(e: Throwable?): Boolean = e is NotYetAvailable

    private suspend fun rest(path: String): Request.Builder {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        return Request.Builder().url("$base/rest/v1/$path")
            .header("apikey", key).header("Authorization", "Bearer $token")
            .header("Accept-Profile", "bandlog").header("Content-Profile", "bandlog")
    }

    private suspend fun run(r: Request.Builder, label: String): String = withContext(Dispatchers.IO) {
        val req = r.build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                android.util.Log.w("LockedIn", "$label failed (${res.code}) ${req.method} ${req.url.encodedPath}: ${body.take(400)}")
                if (isMissing(res.code, body)) throw NotYetAvailable()
                val msg = runCatching { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } }.getOrNull().orEmpty()
                throw ApiException(msg.ifBlank { "$label failed (${res.code})" })
            }
            body
        }
    }

    private suspend fun rpc(fn: String, payload: JSONObject = JSONObject(), label: String): String =
        run(rest("rpc/$fn").post(json(payload.toString())), label)

    private fun uid(): String = Session.userId ?: throw AuthException("Not signed in")

    private fun rows(text: String): List<JSONObject> = runCatching { JSONArray(text) }.getOrNull()?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { k -> if (isNull(k)) null else opt(k) }

    private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }

    // ---------------------------------------------------------------- profile extras

    /** The whole profile row as JSON (select=*): v44 / v45 columns are simply absent until applied. */
    suspend fun profileRaw(): JSONObject = rows(run(rest("profiles?select=*&id=eq.${uid()}&limit=1").get(), "Load profile")).firstOrNull() ?: JSONObject()

    /** PATCH profile columns; false when a column doesn't exist yet (or anything else fails). */
    suspend fun patchProfile(fields: JSONObject): Boolean =
        runCatching { run(rest("profiles?id=eq.${uid()}").header("Prefer", "return=minimal").patch(json(fields.toString())), "Save profile") }.isSuccess

    // ---------------------------------------------------------------- D5 freezes

    data class FreezeSync(val tokens: Int, val earnedNow: Int, val usedNow: Int, val usedDays: List<String>)

    suspend fun freezeSync(): FreezeSync {
        val text = rpc("freeze_sync", label = "Freeze sync")
        val o = runCatching { JSONArray(text).optJSONObject(0) }.getOrNull() ?: runCatching { JSONObject(text) }.getOrNull() ?: JSONObject()
        val used = o.optJSONArray("used_days")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        return FreezeSync(Freezes.clampTokens(o.optInt("tokens", 0)), o.optInt("earned_now", 0), o.optInt("used_now", 0), used)
    }

    suspend fun myFreezeTokens(): Int = rows(run(rest("streak_freezes?select=tokens&user_id=eq.${uid()}").get(), "Load freezes")).firstOrNull()?.optInt("tokens", 0)?.let { Freezes.clampTokens(it) } ?: 0

    suspend fun freezeEvents(): List<Freezes.Event> =
        rows(run(rest("freeze_events?select=kind,ref,other_user,created_at&user_id=eq.${uid()}&order=created_at.desc&limit=200").get(), "Load freeze history"))
            .map { Freezes.Event(it.s("kind"), it.s("ref"), it.s("other_user"), it.s("created_at")) }

    /** Tokens left after gifting one to [to]. */
    suspend fun freezeGift(to: String): Int = rpc("freeze_gift", JSONObject().put("p_to", to), "Gift a freeze").trim().toIntOrNull() ?: 0

    // ---------------------------------------------------------------- D2 referrals

    suspend fun myReferralCode(): String = rpc("my_referral_code", label = "Invite code").trim().trim('"')

    data class Claim(val ok: Boolean, val reason: String?, val referrerName: String?)

    suspend fun referralClaim(code: String): Claim {
        val o = runCatching { JSONObject(rpc("referral_claim", JSONObject().put("p_code", code), "Use invite")) }.getOrElse { if (it is NotYetAvailable) return Claim(false, "missing", null) else throw it }
        return Claim(o.optBoolean("ok", false), o.s("reason"), o.s("referrer_name"))
    }

    data class Referral(val name: String, val avatarPath: String?, val joinedAt: String?)

    suspend fun myReferrals(): List<Referral> = rows(rpc("my_referrals", label = "Invites")).map { Referral(it.s("name") ?: "Friend", it.s("avatar_path"), it.s("joined_at")) }

    // ---------------------------------------------------------------- D3 verified squads

    data class Verified(val verified: Boolean, val orgName: String?, val orgKind: String?)

    /** groups.verified / org_name / org_kind in their own read (v44 columns). */
    suspend fun squadVerified(ids: Collection<String>): Map<String, Verified> {
        if (ids.isEmpty()) return emptyMap()
        return rows(run(rest("groups?select=id,verified,org_name,org_kind&id=in.(${ids.joinToString(",")})").get(), "Load verified"))
            .associate { it.optString("id") to Verified(it.optBoolean("verified", false), it.s("org_name"), it.s("org_kind")) }
    }

    suspend fun requestVerification(groupId: String, org: String, kind: String, proof: String) {
        rpc("request_squad_verification", JSONObject().put("g", groupId).put("p_org", org).put("p_kind", kind).put("p_proof", proof), "Request verification")
    }

    /** The squad's latest verification request status (pending / approved / rejected), or null. */
    suspend fun verificationStatus(groupId: String): String? =
        rows(run(rest("squad_verifications?select=status&group_id=eq.$groupId&order=created_at.desc&limit=1").get(), "Load verification")).firstOrNull()?.s("status")

    // ---------------------------------------------------------------- D4 coach access

    suspend fun coachGrant(username: String) { rpc("coach_grant", JSONObject().put("p_username", username), "Add coach") }
    suspend fun coachRevoke(other: String) { rpc("coach_revoke", JSONObject().put("p_other", other), "Remove") }
    suspend fun myCoaches(): List<CoachView.CoachRow> = rows(rpc("my_coaches", label = "Coaches")).map { CoachView.parseCoach(it) }
    suspend fun myClients(): List<CoachView.ClientRow> = rows(rpc("my_clients", label = "Clients")).map { CoachView.parseClient(it) }
    suspend fun clientOverview(client: String, days: Int = 14): CoachView.Overview =
        CoachView.parseOverview(runCatching { JSONObject(rpc("client_overview", JSONObject().put("p_client", client).put("p_days", days), "Client")) }.getOrNull())

    data class Comment(val id: String, val coachId: String, val day: String?, val body: String, val createdAt: String)

    suspend fun coachComments(client: String): List<Comment> =
        rows(run(rest("coach_comments?select=id,coach_id,day,body,created_at&client_id=eq.$client&order=created_at.desc&limit=50").get(), "Load comments"))
            .map { Comment(it.optString("id"), it.optString("coach_id"), it.s("day"), it.optString("body"), it.optString("created_at")) }

    suspend fun addCoachComment(client: String, body: String, day: String?) {
        val row = JSONObject().put("client_id", client).put("coach_id", uid()).put("body", body.take(1000)).put("day", day ?: JSONObject.NULL)
        run(rest("coach_comments").header("Prefer", "return=minimal").post(json(row.toString())), "Save comment")
    }

    // ---------------------------------------------------------------- D6 stamps

    suspend fun stampCounts(postIds: Collection<String>): Map<String, Stamps.State> {
        if (postIds.isEmpty()) return emptyMap()
        val text = rpc("post_stamp_counts", JSONObject().put("p_posts", JSONArray(postIds.toList())), "Load stamps")
        return Stamps.parseStampRows(rows(text).map { it.toMap() })
    }

    /** [stamp] null = remove mine. */
    suspend fun setStamp(postId: String, stamp: String?) {
        val me = uid()
        if (stamp == null) {
            run(rest("post_stamps?post_id=eq.$postId&user_id=eq.$me").header("Prefer", "return=minimal").delete(), "Remove stamp")
        } else {
            val row = JSONObject().put("post_id", postId).put("user_id", me).put("stamp", stamp).put("created_at", java.time.Instant.now().toString())
            run(rest("post_stamps?on_conflict=post_id,user_id").header("Prefer", "resolution=merge-duplicates,return=minimal").post(json(row.toString())), "Save stamp")
        }
    }

    // ---------------------------------------------------------------- D7 live sessions

    suspend fun upsertLive(label: String, startedAtIso: String) {
        val now = java.time.Instant.now().toString()
        val row = JSONObject().put("user_id", uid()).put("label", label.take(60)).put("started_at", startedAtIso).put("seen_at", now)
        run(rest("live_sessions?on_conflict=user_id").header("Prefer", "resolution=merge-duplicates,return=minimal").post(json(row.toString())), "Live session")
    }

    suspend fun deleteLive() { run(rest("live_sessions?user_id=eq.${uid()}").header("Prefer", "return=minimal").delete(), "End live session") }

    suspend fun squadLive(groupId: String): List<LiveSquad.Row> =
        LiveSquad.parseLiveRows(rows(rpc("squad_live", JSONObject().put("g", groupId), "Live")).map { it.toMap() }, Session.userId)

    /** False when I cheered them in the last 10 minutes. */
    suspend fun liveCheer(user: String): Boolean = rpc("live_cheer", JSONObject().put("p_user", user), "Cheer").trim().equals("true", true)

    // ---------------------------------------------------------------- D8 pledges

    suspend fun myPledges(): List<Pledges.Pledge> =
        rows(run(rest("pledges?select=*&user_id=eq.${uid()}&status=neq.cancelled&order=created_at.desc&limit=50").get(), "Load pledges")).map { Pledges.parsePledge(it.toMap()) }

    suspend fun squadPledges(groupId: String): List<Pledges.Pledge> =
        rows(rpc("squad_pledges", JSONObject().put("g", groupId), "Squad pledges")).map { Pledges.parsePledge(it.toMap()) }

    suspend fun createPledge(d: Pledges.Draft, groupId: String?) {
        val row = JSONObject().put("user_id", uid()).put("group_id", groupId ?: JSONObject.NULL)
            .put("goal", Pledges.goalText(d.kind, d.target, d.days, d.goal).take(120)).put("kind", d.kind)
            .put("target", if (d.kind == "custom") JSONObject.NULL else d.target)
            .put("stake", d.stake.take(120)).put("stake_inr", d.stakeInr)
            .put("starts_on", d.startsOn).put("ends_on", Pledges.endsOn(d.startsOn, d.days))
        run(rest("pledges").header("Prefer", "return=minimal").post(json(row.toString())), "Save pledge")
    }

    suspend fun setPledgeStatus(id: String, status: String) {
        val o = JSONObject().put("status", status).put("closed_at", if (status == "active") JSONObject.NULL else java.time.Instant.now().toString())
        run(rest("pledges?id=eq.$id").header("Prefer", "return=minimal").patch(json(o.toString())), "Update pledge")
    }

    // ---------------------------------------------------------------- D9 events

    suspend fun myEventBadges(): Set<String> = rows(run(rest("event_badges?select=event_id&user_id=eq.${uid()}").get(), "Load event badges")).map { it.optString("event_id") }.toSet()

    suspend fun earnEventBadge(id: String) {
        val row = JSONObject().put("user_id", uid()).put("event_id", id)
        run(rest("event_badges?on_conflict=user_id,event_id").header("Prefer", "resolution=ignore-duplicates,return=minimal").post(json(row.toString())), "Save event badge")
    }

    // ---------------------------------------------------------------- D11 packs

    suspend fun myUnlocks(): Set<String> = rows(run(rest("pack_unlocks?select=pack_id&user_id=eq.${uid()}").get(), "Load packs")).map { it.optString("pack_id") }.toSet()

    suspend fun unlockPack(id: String, priceInr: Int) {
        val row = JSONObject().put("user_id", uid()).put("pack_id", id).put("price_inr", priceInr).put("via", "beta_free")
        run(rest("pack_unlocks?on_conflict=user_id,pack_id").header("Prefer", "resolution=ignore-duplicates,return=minimal").post(json(row.toString())), "Unlock pack")
    }

    // ---------------------------------------------------------------- D12 leagues (flag off; unreachable)

    suspend fun leagueTable(groupId: String): List<JSONObject> = rows(rpc("league_table", JSONObject().put("g", groupId), "League"))

    // ---------------------------------------------------------------- E5 safety

    suspend fun myBlocks(): Set<String> = rows(run(rest("user_blocks?select=blocked_id&blocker_id=eq.${uid()}").get(), "Load blocks")).map { it.optString("blocked_id") }.toSet()

    suspend fun block(user: String) {
        val row = JSONObject().put("blocker_id", uid()).put("blocked_id", user)
        run(rest("user_blocks?on_conflict=blocker_id,blocked_id").header("Prefer", "resolution=ignore-duplicates,return=minimal").post(json(row.toString())), "Block")
    }

    suspend fun unblock(user: String) { run(rest("user_blocks?blocker_id=eq.${uid()}&blocked_id=eq.$user").header("Prefer", "return=minimal").delete(), "Unblock") }

    suspend fun report(reason: String, note: String, reportedUser: String?, groupId: String?, postId: String?, snapshot: String) {
        val row = JSONObject().put("reporter_id", uid()).put("reason", reason).put("note", note.take(500)).put("snapshot", snapshot.take(1000))
            .put("reported_user", reportedUser ?: JSONObject.NULL).put("group_id", groupId ?: JSONObject.NULL).put("post_id", postId ?: JSONObject.NULL)
        run(rest("content_reports").header("Prefer", "return=minimal").post(json(row.toString())), "Report")
    }

    /** Supabase password recovery email (links to the web's /reset page). */
    suspend fun recover(email: String) = withContext(Dispatchers.IO) {
        val redirect = java.net.URLEncoder.encode("$site/reset", "UTF-8")
        val r = Request.Builder().url("$base/auth/v1/recover?redirect_to=$redirect").header("apikey", key)
            .post(json(JSONObject().put("email", email.trim()).toString())).build()
        client.newCall(r).execute().use { res ->
            if (!res.isSuccessful) {
                val text = res.body?.string().orEmpty()
                val msg = runCatching { JSONObject(text).let { it.optString("msg").ifBlank { it.optString("error_description").ifBlank { it.optString("message") } } } }.getOrNull().orEmpty()
                throw ApiException(msg.ifBlank { "Couldn't send the reset email (${res.code})" })
            }
        }
    }

    /** POST <site>/api/account/delete with the bearer token (the web wipes the data and the auth user). */
    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val r = Request.Builder().url("$site/api/account/delete").header("Authorization", "Bearer $token")
            .post(json(JSONObject().put("confirm", com.sohum.bandlog.util.Safety.DELETE_WORD).toString())).build()
        client.newBuilder().callTimeout(90, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build().newCall(r).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 404) throw NotYetAvailable()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                throw ApiException(msg.ifBlank { "Couldn't delete the account (${res.code})" })
            }
        }
    }
}
