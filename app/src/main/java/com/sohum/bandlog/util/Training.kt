package com.sohum.bandlog.util

import com.sohum.bandlog.data.LiftSet
import com.sohum.bandlog.data.Workout
import kotlin.math.roundToInt

/**
 * v2.13 PR charts (spec §12): per-exercise history, the best set per session and an estimated
 * 1-rep max with Epley: w × (1 + reps / 30). Mirrors the web's lib/training.ts.
 */
object Training {
    /** Epley estimated 1RM. A single rep is the weight itself; no weight or no reps is 0. */
    fun epley(kg: Double, reps: Int): Double {
        if (kg <= 0.0 || reps <= 0) return 0.0
        if (reps == 1) return kg
        return kg * (1.0 + reps / 30.0)
    }

    fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0

    /** The set with the highest e1RM (ties: heavier, then more reps); null when no set has kg and reps. */
    fun bestSet(sets: List<LiftSet>): LiftSet? =
        sets.filter { (it.kg ?: 0.0) > 0.0 && (it.reps ?: 0) > 0 }
            .maxWithOrNull(compareBy<LiftSet>({ epley(it.kg ?: 0.0, it.reps ?: 0) }, { it.kg }, { it.reps }))

    /** One session of one exercise on the chart. [pr] = its e1RM beat every earlier session's. */
    data class Point(val date: String, val workoutId: String, val kg: Double, val reps: Int, val e1rm: Double, val pr: Boolean)

    /** Every session of [exercise] (case-insensitive), oldest first, with PR flags. */
    fun history(workouts: List<Workout>, exercise: String): List<Point> {
        val rows = workouts.flatMap { w ->
            w.lifts.filter { it.name.equals(exercise, ignoreCase = true) }.mapNotNull { l ->
                val b = bestSet(l.sets) ?: return@mapNotNull null
                Triple(w, b, epley(b.kg ?: 0.0, b.reps ?: 0))
            }
        }
            // Several entries of the same lift in one session: keep the best.
            .groupBy { it.first.id }.values.map { g -> g.maxBy { it.third } }
            .sortedWith(compareBy({ it.first.date }, { it.first.id }))
        var best = 0.0
        return rows.map { (w, s, e) ->
            val pr = best > 0.0 && e > best + 1e-9
            if (e > best) best = e
            Point(w.date, w.id, s.kg ?: 0.0, s.reps ?: 0, round1(e), pr)
        }
    }

    /** Exercises with at least one weighted set, most sessions first. */
    fun exercisesWithHistory(workouts: List<Workout>): List<Pair<String, Int>> =
        workouts.flatMap { w -> w.lifts.filter { l -> bestSet(l.sets) != null }.map { it.name.trim() to w.id } }
            .groupBy { it.first.lowercase() }
            .map { (_, v) -> v.first().first to v.map { it.second }.distinct().size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })

    /** Best e1RM ever for [exercise] (0 when none). */
    fun bestE1rm(workouts: List<Workout>, exercise: String): Double = history(workouts, exercise).maxOfOrNull { it.e1rm } ?: 0.0

    /** PRs set between [from] and [to] (inclusive ISO dates): the exercise and its new best point. */
    fun prsBetween(workouts: List<Workout>, from: String, to: String): List<Pair<String, Point>> =
        exercisesWithHistory(workouts).mapNotNull { (name, _) ->
            history(workouts, name).filter { it.pr && it.date >= from && it.date <= to }.maxByOrNull { it.e1rm }?.let { name to it }
        }.sortedByDescending { it.second.e1rm }
}
