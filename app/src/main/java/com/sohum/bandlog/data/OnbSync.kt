package com.sohum.bandlog.data

import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.DietModes
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.OnbStore
import com.sohum.bandlog.util.OnboardingV2
import org.json.JSONArray
import org.json.JSONObject

/**
 * v2.14: sends a finished onboarding to the account. The flow ends before (or, for a signed-in
 * user with an incomplete profile, without) an account; [replayIfPending] runs right after
 * sign-up / the first sign-in and saves everything through `POST /api/onboarding/finish`
 * (`mode: "full"`, the answers and the pre-account first log, which the server replays once).
 *
 * While that route isn't deployed (or fails), the app saves what it can itself: the classic
 * profile columns + targets from the local plan maths, a first weigh-in, the v36 diet mode and the
 * v37 answers when those columns exist, and the first meal through the normal meal save. Either
 * way the flow is marked done on the device.
 */
object OnbSync {
    /** Returns true when a pending onboarding was saved (the caller refreshes). */
    suspend fun replayIfPending(): Boolean {
        if (!OnbStore.pending || !Session.signedIn) return false
        val a = OnbStore.answers
        val log = OnbStore.firstLog
        try {
            V214Api.finish("full", a, log)
        } catch (e: AuthException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("LockedIn", "Onboarding finish route failed, saving locally: ${e.message}")
            saveLocally(a, log)
        }
        OnbStore.doneLocally = true
        OnbStore.clearFlow()
        return true
    }

    /** The fallback save (see the class comment). Throws only when the classic profile save fails. */
    suspend fun saveLocally(a: OnboardingV2.Answers, log: OnbStore.FirstLog?) {
        val current = runCatching { Api.profile() }.getOrDefault(Profile())
        val merged = a.copy(
            heightCm = a.heightCm ?: current.heightCm, weightKg = a.weightKg ?: current.weightKg,
            dob = a.dob ?: current.dob, gender = a.gender ?: current.gender,
        )
        val today = Dates.today()
        val base = OnboardingV2.toProfile(merged, current, today)
        val plan = OnboardingV2.plan(merged, today)
        val p = plan?.let { base.copy(calorieTarget = it.targets.calories, proteinTargetG = it.targets.protein, carbTargetGSet = it.targets.carbs, fatTargetGSet = it.targets.fat, fiberTarget = it.targets.fiber) } ?: base
        Api.saveProfile(p)
        // A first weigh-in so Progress has a starting point (only when there's none yet).
        p.weightKg?.let { kg -> runCatching { if (Api.weights().isEmpty()) Api.logWeight(today, kg, "") } }
        // v36 diet mode, quietly skipped without schema_v36.
        a.dietMode?.let { m -> runCatching { NutritionApi.patchSettings(JSONObject().put("diet_mode", DietModes.effective(m, Goals.ageYears(p.dob, today)))) } }
        // v37 answers, quietly skipped without schema_v37.
        Api.patchProfile(v37Fields(a, Goals.ageYears(p.dob, today), full = true))
        // The pre-account first log, once.
        if (log != null && log.items.isNotEmpty()) {
            val date = log.date.takeIf { it <= today && it >= Dates.addDays(today, -2) } ?: today
            runCatching { Api.saveMeal(date, log.text.ifBlank { "Meal" }, log.items, null, log.mealType) }
        }
    }

    /** The profiles columns schema_v37 adds, from the answers. */
    fun v37Fields(a: OnboardingV2.Answers, age: Int?, full: Boolean): JSONObject = JSONObject().apply {
        put("onboarded_v2", true)
        a.coachStyle?.let { put("coach_style", OnboardingV2.effectiveCoachStyle(it, age)) }
        a.obstacles?.let { put("obstacles", JSONArray(it)) }
        a.trainingDays?.let { put("training_days", it) }
        a.sports?.let { put("sports", JSONArray(it)) }
        if (full) {
            a.heardFrom?.let { put("heard_from", it) }
            a.firstChallenge?.let { put("first_challenge", it) }
        }
    }

    /** "Tune your plan" (existing users): coach style, obstacles, training days. */
    suspend fun tune(a: OnboardingV2.Answers, age: Int?): Boolean {
        val ok = try {
            V214Api.finish("tune", a, null); true
        } catch (e: AuthException) { throw e }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Api.patchProfile(v37Fields(a, age, full = false)) }
        OnbStore.tuneDismissed = true
        return ok
    }
}
