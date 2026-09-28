package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.util.FoodBits
import com.sohum.bandlog.util.OrderHelper
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
 * v2.18 Area A (food) calls, in their own file so the other v2.18 streams can change Api.kt freely.
 * Web routes (the user's bearer token): /api/scan-voice, /api/order-helper, /api/label-check.
 * PostgREST (RLS applies) on the schema_v42 tables: leftovers, meal_splits, shared_recipes,
 * pantry_items and profiles.water_from_food. Every "table / column / route missing" becomes
 * [NotYetAvailable], so the feature hides instead of breaking. Web twin: src/lib/food/foodActions.ts.
 */
object FoodApi {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())
    private fun uid(): String = Session.userId ?: throw AuthException("Not signed in")

    private suspend fun rest(path: String): Request.Builder {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        return Request.Builder().url("$base/rest/v1/$path")
            .header("apikey", key).header("Authorization", "Bearer $token")
            .header("Accept-Profile", "bandlog").header("Content-Profile", "bandlog")
    }

    private fun run(r: Request, label: String): String = client.newCall(r).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (!res.isSuccessful) {
            android.util.Log.w("LockedIn", "$label failed (${res.code}) ${r.method} ${r.url.encodedPath}: ${body.take(600)}")
            if (NutritionApi.isMissing(res.code, body)) throw NotYetAvailable()
            val msg = runCatching { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } }.getOrNull().orEmpty()
            throw ApiException("$label failed (${res.code})${if (msg.isNotBlank()) ": $msg" else ""}")
        }
        body
    }

    /** POST to the web app's /api/[path]. A 404 / 405 (route not deployed yet) is [NotYetAvailable]. */
    private suspend fun web(path: String, payload: JSONObject, label: String, timeoutSec: Long = 90): JSONObject {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val apiBase = BuildConfig.API_BASE.trimEnd('/')
        if (apiBase.isBlank()) throw ApiException("API_BASE is not set in this build")
        val r = Request.Builder().url("$apiBase/api/$path").header("Authorization", "Bearer $token").post(json(payload.toString())).build()
        val c = client.newBuilder().callTimeout(timeoutSec, TimeUnit.SECONDS).readTimeout(timeoutSec, TimeUnit.SECONDS).build()
        val body = c.newCall(r).execute().use { res ->
            val b = res.body?.string().orEmpty()
            if (res.code == 404 || res.code == 405) throw NotYetAvailable()
            if (!res.isSuccessful) {
                android.util.Log.w("LockedIn", "$label failed (${res.code}) /api/$path: ${b.take(600)}")
                val msg = runCatching { JSONObject(b).optString("error") }.getOrNull().orEmpty()
                throw ApiException(if (msg.isNotBlank()) msg else "$label failed (${res.code})")
            }
            b
        }
        return JSONObject(body)
    }

    // ---------------------------------------------------------------- JSON helpers

    /** A plate item as the web's PlateItem JSON (for /api/scan-voice). */
    fun plateJson(it: PlateItem): JSONObject = JSONObject()
        .put("name", it.name).put("grams", it.grams).put("confidence", it.confidence).put("calories", it.calories)
        .put("protein_g", it.proteinG).put("carbs_g", it.carbsG).put("fat_g", it.fatG).put("micros", JSONObject(it.micros))
        .put("source", it.source).put("food_id", it.foodId ?: JSONObject.NULL)
        .apply {
            it.cookedIn?.let { c -> put("cooked_in", c) }
            it.gramsLow?.let { g -> put("grams_low", g) }
            it.gramsHigh?.let { g -> put("grams_high", g) }
            if (it.sourceUrls.isNotEmpty()) put("source_urls", JSONArray(it.sourceUrls))
            if (it.userVerified) put("user_verified", true)
            if (it.fromVoice) put("from_voice", true)
        }

    /** A meal item as the web's MealItem JSON (stored in leftovers / meal_splits items). */
    fun itemJson(m: MealItem): JSONObject = m.toJson("", "", extras = true).apply {
        remove("meal_id"); remove("user_id")
        if (m.kcalLow != null && m.kcalHigh != null) { put("kcal_low", Math.round(m.kcalLow)); put("kcal_high", Math.round(m.kcalHigh)) }
    }

    private fun items(a: JSONArray?): List<MealItem> = a?.let { x -> (0 until x.length()).mapNotNull { x.optJSONObject(it)?.let { o -> MealItem.from(o) } } } ?: emptyList()
    private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }

    // ---------------------------------------------------------------- A1 voice after the photo

    data class VoiceResult(val items: List<PlateItem>, val changes: List<String>, val notes: List<String>)

    suspend fun scanVoice(items: List<PlateItem>, voice: String, plateNote: String): VoiceResult = withContext(Dispatchers.IO) {
        val o = web("scan-voice", JSONObject().put("items", JSONArray().apply { items.forEach { put(plateJson(it)) } }).put("voice", voice).put("plate_note", plateNote), "Add details")
        val arr = o.optJSONArray("items") ?: JSONArray()
        fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        VoiceResult((0 until arr.length()).map { PlateItem.from(arr.getJSONObject(it)) }, strs("changes"), strs("notes"))
    }

    // ---------------------------------------------------------------- A4 order helper

    data class OrderResult(val restaurant: String?, val note: String, val dietMode: String, val remainingKcal: Double, val dishes: List<OrderHelper.OrderDish>, val plan: OrderHelper.OrderPlan)

    suspend fun orderHelper(text: String?, imageB64: String?, remaining: JSONObject?): OrderResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        if (!text.isNullOrBlank()) payload.put("text", text)
        if (imageB64 != null) payload.put("image", imageB64).put("media_type", "image/jpeg")
        if (remaining != null) payload.put("remaining", remaining)
        val o = web("order-helper", payload, "Order helper", timeoutSec = 150)
        val arr = o.optJSONArray("dishes") ?: JSONArray()
        val dishes = (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            MenuDish.from(d)?.let { OrderHelper.OrderDish(it, d.optInt("qty", 1).coerceIn(1, 20)) }
        }
        val rem = o.optJSONObject("remaining")?.optDouble("kcal", 0.0) ?: 0.0
        // The plan is worked out here with the same rule as the server (util/OrderHelper.kt).
        OrderResult(o.s("restaurant"), o.s("note").orEmpty(), o.s("diet_mode") ?: "balanced", rem, dishes, OrderHelper.orderPlan(dishes, rem))
    }

    // ---------------------------------------------------------------- A8 label vs reality

    data class LabelCheck(val lines: List<String>, val sourceLabel: String?, val sourceUrl: String?, val matchedName: String?)

    /** null = nothing to show (agrees, no web answer, or the route isn't deployed). */
    suspend fun labelCheck(product: String, per100: FoodBits.Per100): LabelCheck? = withContext(Dispatchers.IO) {
        runCatching {
            val p = JSONObject().apply {
                per100.calories?.let { put("calories", it) }; per100.proteinG?.let { put("protein_g", it) }
                per100.carbsG?.let { put("carbs_g", it) }; per100.fatG?.let { put("fat_g", it) }
            }
            val o = web("label-check", JSONObject().put("product", product).put("per_100g", p), "Label check", timeoutSec = 60)
            val lines = o.optJSONArray("lines")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            if (!o.optBoolean("checked", false) || lines.isEmpty()) null
            else LabelCheck(lines, o.optJSONObject("source")?.s("label"), o.optJSONObject("source")?.s("url"), o.s("matched_name"))
        }.getOrNull()
    }

    // ---------------------------------------------------------------- Home: leftovers, splits, water

    data class Leftover(val id: String, val name: String, val items: List<MealItem>, val kcal: Double, val fractionLeft: Double, val createdAt: String)
    data class PendingSplit(val id: String, val fromName: String?, val dish: String, val items: List<MealItem>, val kcal: Double, val share: Double, val date: String, val mealType: String?)
    /** Each part null = its schema_v42 piece isn't there (the card hides). */
    data class FoodHome(val leftovers: List<Leftover>?, val splits: List<PendingSplit>?, val waterFromFood: Boolean?)

    suspend fun home(): FoodHome = withContext(Dispatchers.IO) {
        val me = uid()
        val since = java.time.Instant.now().minusSeconds(4 * 86_400L).toString()
        val leftovers = runCatching {
            val arr = JSONArray(run(rest("leftovers?select=id,name,items,kcal,fraction_left,created_at,used_at,dismissed_at&user_id=eq.$me&used_at=is.null&dismissed_at=is.null&created_at=gte.$since&order=created_at.desc&limit=5").get().build(), "Load leftovers"))
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { FoodBits.leftoverActive(it.optString("created_at"), it.s("used_at"), it.s("dismissed_at")) }
                .map { Leftover(it.optString("id"), it.optString("name"), items(it.optJSONArray("items")), it.optDouble("kcal", 0.0), it.optDouble("fraction_left", 0.5), it.optString("created_at")) }
        }.getOrNull()
        val splits = runCatching {
            val arr = JSONArray(run(rest("meal_splits?select=id,from_name,dish,items,kcal,share,date,meal_type,created_at&to_user=eq.$me&status=eq.pending&order=created_at.desc&limit=10").get().build(), "Load splits"))
            (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                PendingSplit(it.optString("id"), it.s("from_name"), it.optString("dish"), items(it.optJSONArray("items")), it.optDouble("kcal", 0.0), it.optDouble("share", 0.0), it.optString("date"), it.s("meal_type"))
            }
        }.getOrNull()
        val water = runCatching {
            val arr = JSONArray(run(rest("profiles?select=water_from_food&id=eq.$me").get().build(), "Load setting"))
            arr.optJSONObject(0)?.optBoolean("water_from_food", false) ?: false
        }.getOrNull()
        FoodHome(leftovers, splits, water)
    }

    suspend fun saveLeftover(name: String, items: List<MealItem>, fractionLeft: Double, mealId: String?) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("user_id", uid()).put("name", name.take(120).ifBlank { items.firstOrNull()?.name ?: "Leftovers" })
            .put("items", JSONArray().apply { items.forEach { put(itemJson(it)) } }).put("kcal", Math.round(items.sumOf { it.calories }))
            .put("fraction_left", Math.round(fractionLeft.coerceIn(0.01, 0.99) * 1000) / 1000.0).put("meal_id", mealId ?: JSONObject.NULL)
        run(rest("leftovers").header("Prefer", "return=minimal").post(json(body.toString())).build(), "Save leftovers"); Unit
    }

    /** [used] true = logged, false = dismissed ("not eating it"). */
    suspend fun closeLeftover(id: String, used: Boolean) = withContext(Dispatchers.IO) {
        val now = java.time.Instant.now().toString()
        run(rest("leftovers?id=eq.$id").header("Prefer", "return=minimal").patch(json(JSONObject().put(if (used) "used_at" else "dismissed_at", now).toString())).build(), "Update leftovers"); Unit
    }

    data class Person(val id: String, val name: String, val squad: String)

    /** Everyone I share a squad with (deduped). */
    suspend fun splitPeople(): List<Person> = withContext(Dispatchers.IO) {
        val me = uid()
        val seen = LinkedHashMap<String, Person>()
        for (s in runCatching { Api.mySquads() }.getOrDefault(emptyList()).take(8)) {
            for (m in runCatching { Api.groupMembersDetail(s.id) }.getOrDefault(emptyList())) {
                if (m.userId != me && m.userId !in seen) seen[m.userId] = Person(m.userId, m.name.ifBlank { m.username ?: "Squadmate" }, s.name)
            }
        }
        seen.values.sortedBy { it.name.lowercase() }
    }

    /** One pending split per squadmate (their share already scaled). */
    suspend fun sendSplits(dish: String, fromName: String?, date: String, mealType: String, shares: List<Triple<String, List<MealItem>, Double>>) = withContext(Dispatchers.IO) {
        val me = uid()
        val arr = JSONArray()
        shares.forEach { (to, its, share) ->
            arr.put(
                JSONObject().put("from_user", me).put("to_user", to).put("from_name", fromName ?: JSONObject.NULL).put("dish", dish.take(120).ifBlank { "A shared dish" })
                    .put("items", JSONArray().apply { its.forEach { put(itemJson(it)) } }).put("kcal", Math.round(its.sumOf { it.calories }))
                    .put("share", Math.round(share * 10000) / 10000.0).put("date", date).put("meal_type", mealType),
            )
        }
        if (arr.length() > 0) run(rest("meal_splits").header("Prefer", "return=minimal").post(json(arr.toString())).build(), "Send split")
        Unit
    }

    suspend fun decideSplit(id: String, accept: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("status", if (accept) "accepted" else "declined").put("decided_at", java.time.Instant.now().toString())
        run(rest("meal_splits?id=eq.$id").header("Prefer", "return=minimal").patch(json(body.toString())).build(), "Update split"); Unit
    }

    suspend fun setWaterFromFood(on: Boolean) = withContext(Dispatchers.IO) {
        run(rest("profiles?id=eq.${uid()}").header("Prefer", "return=minimal").patch(json(JSONObject().put("water_from_food", on).toString())).build(), "Save setting"); Unit
    }

    // ---------------------------------------------------------------- A2 recipes shared into squads

    data class SharedRecipe(val id: String, val groupId: String, val squad: String, val authorName: String?, val name: String, val kcal: Int, val protein: Double, val mine: Boolean, val raw: JSONObject)

    suspend fun sharedRecipes(squads: List<Squad>): List<SharedRecipe> = withContext(Dispatchers.IO) {
        if (squads.isEmpty()) return@withContext emptyList()
        val names = squads.associate { it.id to it.name }
        val me = uid()
        val arr = JSONArray(run(rest("shared_recipes?select=*&group_id=in.(${squads.joinToString(",") { it.id }})&order=created_at.desc&limit=40").get().build(), "Load squad recipes"))
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            val ps = o.optJSONObject("per_serving") ?: JSONObject()
            SharedRecipe(o.optString("id"), o.optString("group_id"), names[o.optString("group_id")] ?: "Squad", o.s("author_name"), o.optString("name"),
                ps.optDouble("kcal", 0.0).let { Math.round(it).toInt() }, Math.round(ps.optDouble("protein_g", 0.0) * 10) / 10.0, o.optString("user_id") == me, o)
        }
    }

    suspend fun shareRecipe(r: Recipe, groupId: String, authorName: String?) = withContext(Dispatchers.IO) {
        val me = uid()
        val full = r.toJson(me)
        val body = JSONObject().put("group_id", groupId).put("user_id", me).put("author_name", authorName ?: JSONObject.NULL)
            .put("name", r.name).put("servings", r.servings).put("cooked_weight_g", r.cookedWeightG ?: JSONObject.NULL)
            .put("items", full.optJSONArray("items") ?: JSONArray()).put("per_serving", full.optJSONObject("per_serving") ?: JSONObject())
            .put("note", r.note.ifBlank { JSONObject.NULL })
        run(rest("shared_recipes").header("Prefer", "return=minimal").post(json(body.toString())).build(), "Share recipe")
        // The squad chat hears about it (a plain message post), best effort.
        val ps = r.perServing
        runCatching {
            val post = JSONObject().put("group_id", groupId).put("user_id", me).put("kind", "message")
                .put("body", "Shared a recipe: ${r.name} (${Math.round(ps.kcal)} kcal, ${com.sohum.bandlog.ui.today.fmt(Math.round(ps.protein * 10) / 10.0)} g protein a serving). Find it in Recipes.")
            run(rest("group_posts").header("Prefer", "return=minimal").post(json(post.toString())).build(), "Post to squad")
        }
        Unit
    }

    /** A squadmate's shared recipe → my own recipe (saved through NutritionApi like any recipe). */
    fun sharedAsRecipe(s: SharedRecipe): Recipe {
        val copy = JSONObject(s.raw.toString()).apply { remove("id"); remove("group_id"); remove("user_id"); remove("created_at") }
        val r = Recipe.from(copy)
        return r.copy(id = null, note = listOfNotNull(r.note.ifBlank { null }, s.authorName?.let { "Shared by $it." }).joinToString(" "))
    }

    // ---------------------------------------------------------------- A11 pantry

    data class PantryItem(val id: String, val name: String, val qty: String?, val category: String?, val inStock: Boolean)

    suspend fun pantry(): List<PantryItem> = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("pantry_items?select=id,name,qty,category,in_stock&order=name").get().build(), "Load pantry"))
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { PantryItem(it.optString("id"), it.optString("name"), it.s("qty"), it.s("category"), it.optBoolean("in_stock", true)) }
    }

    /** Adds [name] (or marks it in stock again when it's already there). */
    suspend fun addPantry(name: String, existing: List<PantryItem>) = withContext(Dispatchers.IO) {
        val n = name.replace(Regex("\\s+"), " ").trim().take(80)
        if (n.isEmpty()) return@withContext
        val same = existing.firstOrNull { it.name.equals(n, ignoreCase = true) }
        val now = java.time.Instant.now().toString()
        if (same != null) {
            run(rest("pantry_items?id=eq.${same.id}").header("Prefer", "return=minimal").patch(json(JSONObject().put("in_stock", true).put("updated_at", now).toString())).build(), "Update pantry")
        } else {
            val body = JSONObject().put("user_id", uid()).put("name", n).put("category", com.sohum.bandlog.util.Grocery.pantryCategory(n)).put("in_stock", true).put("updated_at", now)
            run(rest("pantry_items").header("Prefer", "return=minimal").post(json(body.toString())).build(), "Add to pantry")
        }
        Unit
    }

    suspend fun setPantryStock(id: String, inStock: Boolean) = withContext(Dispatchers.IO) {
        run(rest("pantry_items?id=eq.$id").header("Prefer", "return=minimal").patch(json(JSONObject().put("in_stock", inStock).put("updated_at", java.time.Instant.now().toString()).toString())).build(), "Update pantry"); Unit
    }

    suspend fun deletePantry(id: String) = withContext(Dispatchers.IO) {
        run(rest("pantry_items?id=eq.$id").delete().build(), "Remove from pantry"); Unit
    }
}
