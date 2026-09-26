package com.sohum.bandlog.util

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * v2.13 training routines (spec §12, `bandlog.routines.days`):
 * days [{weekday 1-7 | null, name, exercises: [{name, sets, reps, rest_s, muscles: [..]}]}].
 * One active routine drives the "Today's session" card. Mirrors the web's lib/routines.ts.
 */
object Routines {
    const val DEFAULT_REST_S = 90

    data class Exercise(val name: String, val sets: Int = 3, val reps: Int = 10, val restS: Int = DEFAULT_REST_S) {
        /** Primary + secondary region keys from [MuscleMap], stored with the row so the web shows the same. */
        val muscles: List<String> get() = MuscleMap.of(name)?.all?.map { it.key } ?: emptyList()
    }

    /** [weekday] 1 = Monday … 7 = Sunday, or null for "next in rotation". */
    data class Day(val name: String, val weekday: Int? = null, val exercises: List<Exercise> = emptyList())

    data class Routine(
        val id: String? = null,
        val name: String,
        val days: List<Day>,
        val active: Boolean = false,
        val createdAt: String? = null,
        val updatedAt: String? = null,
    )

    val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    fun weekdayLabel(d: Int?): String = d?.let { WEEKDAYS.getOrNull(it - 1) } ?: "Any day"

    // ---- JSON ----

    fun daysToJson(days: List<Day>): JSONArray = JSONArray().apply {
        days.forEach { d ->
            put(
                JSONObject().put("name", d.name).put("weekday", d.weekday ?: JSONObject.NULL).put(
                    "exercises",
                    JSONArray().apply {
                        d.exercises.forEach { e ->
                            put(
                                JSONObject().put("name", e.name).put("sets", e.sets).put("reps", e.reps).put("rest_s", e.restS)
                                    .put("muscles", JSONArray(e.muscles)),
                            )
                        }
                    },
                ),
            )
        }
    }

    private fun leadingInt(v: Any?, fallback: Int): Int = when (v) {
        is Number -> v.toInt()
        is String -> Regex("\\d+").find(v)?.value?.toIntOrNull() ?: fallback
        else -> fallback
    }

    fun daysFrom(v: Any?): List<Day> {
        val arr = when (v) { is JSONArray -> v; is String -> runCatching { JSONArray(v) }.getOrNull(); else -> null } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ex = o.optJSONArray("exercises") ?: JSONArray()
            Day(
                name = o.optString("name").ifBlank { "Day ${i + 1}" },
                weekday = if (!o.has("weekday") || o.isNull("weekday")) null else o.optInt("weekday").takeIf { it in 1..7 },
                exercises = (0 until ex.length()).mapNotNull { j ->
                    val e = ex.optJSONObject(j) ?: return@mapNotNull null
                    val name = e.optString("name").trim().ifBlank { return@mapNotNull null }
                    Exercise(
                        name,
                        leadingInt(e.opt("sets"), 3).coerceIn(1, 20),
                        leadingInt(e.opt("reps"), 10).coerceIn(1, 100),
                        leadingInt(e.opt("rest_s"), DEFAULT_REST_S).coerceIn(0, 900),
                    )
                },
            )
        }
    }

    fun from(o: JSONObject) = Routine(
        id = o.optString("id").ifBlank { null },
        name = o.optString("name").ifBlank { "Routine" },
        days = daysFrom(o.opt("days")),
        active = o.optBoolean("active", false),
        createdAt = if (o.isNull("created_at")) null else o.optString("created_at").ifBlank { null },
        updatedAt = if (o.isNull("updated_at")) null else o.optString("updated_at").ifBlank { null },
    )

    // ---- templates ----

    private fun ex(name: String, sets: Int, reps: Int, rest: Int = DEFAULT_REST_S) = Exercise(name, sets, reps, rest)

    val TEMPLATES: List<Routine> = listOf(
        Routine(
            name = "Push / Pull / Legs",
            days = listOf(
                Day("Push", 1, listOf(ex("Bench press", 4, 8, 120), ex("Overhead press", 3, 8, 120), ex("Incline dumbbell press", 3, 10), ex("Lateral raise", 3, 15, 60), ex("Tricep pushdown", 3, 12, 60))),
                Day("Pull", 3, listOf(ex("Deadlift", 3, 5, 180), ex("Pull-up", 3, 8, 120), ex("Barbell row", 3, 10), ex("Face pull", 3, 15, 60), ex("Barbell curl", 3, 12, 60))),
                Day("Legs", 5, listOf(ex("Squat", 4, 8, 150), ex("Romanian deadlift", 3, 10, 120), ex("Leg press", 3, 12), ex("Leg curl", 3, 12, 60), ex("Calf raise", 4, 15, 60))),
            ),
        ),
        Routine(
            name = "Upper / Lower",
            days = listOf(
                Day("Upper A", 1, listOf(ex("Bench press", 4, 6, 150), ex("Barbell row", 4, 8, 120), ex("Overhead press", 3, 10), ex("Lat pulldown", 3, 10), ex("Dumbbell curl", 2, 12, 60), ex("Tricep pushdown", 2, 12, 60))),
                Day("Lower A", 2, listOf(ex("Squat", 4, 6, 150), ex("Romanian deadlift", 3, 8, 120), ex("Leg extension", 3, 12, 60), ex("Calf raise", 4, 12, 60), ex("Plank", 3, 45, 60))),
                Day("Upper B", 4, listOf(ex("Incline dumbbell press", 4, 10), ex("Pull-up", 4, 8, 120), ex("Dumbbell shoulder press", 3, 10), ex("Seated cable row", 3, 12), ex("Hammer curl", 2, 12, 60), ex("Skull crusher", 2, 12, 60))),
                Day("Lower B", 5, listOf(ex("Deadlift", 3, 5, 180), ex("Bulgarian split squat", 3, 10), ex("Leg curl", 3, 12, 60), ex("Hip thrust", 3, 10), ex("Hanging leg raise", 3, 12, 60))),
            ),
        ),
        Routine(
            name = "Full body 3×",
            days = listOf(
                Day("Full body A", 1, listOf(ex("Squat", 3, 8, 150), ex("Bench press", 3, 8, 120), ex("Barbell row", 3, 10), ex("Plank", 3, 45, 60))),
                Day("Full body B", 3, listOf(ex("Deadlift", 3, 5, 180), ex("Overhead press", 3, 8, 120), ex("Pull-up", 3, 8, 120), ex("Lunge", 3, 10))),
                Day("Full body C", 5, listOf(ex("Goblet squat", 3, 12), ex("Incline dumbbell press", 3, 10), ex("Lat pulldown", 3, 10), ex("Hip thrust", 3, 10), ex("Russian twist", 3, 20, 60))),
            ),
        ),
        Routine(
            name = "Bro split",
            days = listOf(
                Day("Chest", 1, listOf(ex("Bench press", 4, 8, 120), ex("Incline dumbbell press", 3, 10), ex("Chest fly", 3, 12, 60), ex("Dip", 3, 10))),
                Day("Back", 2, listOf(ex("Deadlift", 3, 5, 180), ex("Pull-up", 3, 8, 120), ex("Barbell row", 3, 10), ex("Seated cable row", 3, 12))),
                Day("Shoulders", 3, listOf(ex("Overhead press", 4, 8, 120), ex("Lateral raise", 4, 15, 60), ex("Rear delt fly", 3, 15, 60), ex("Shrug", 3, 12, 60))),
                Day("Arms", 4, listOf(ex("Barbell curl", 3, 10, 60), ex("Close-grip bench press", 3, 8), ex("Hammer curl", 3, 12, 60), ex("Overhead tricep extension", 3, 12, 60))),
                Day("Legs", 5, listOf(ex("Squat", 4, 8, 150), ex("Leg press", 3, 12), ex("Romanian deadlift", 3, 10, 120), ex("Leg curl", 3, 12, 60), ex("Calf raise", 4, 15, 60))),
            ),
        ),
    )

    // ---- today's session ----

    /** What the "Today's session" card shows. [day] null = rest day; [next] = the next planned day then. */
    data class Today(val day: Day?, val index: Int?, val next: Day? = null, val nextWeekday: Int? = null)

    /** ISO weekday 1 (Mon) … 7 (Sun) of [date]. */
    fun weekdayOf(date: String): Int = LocalDate.parse(date).dayOfWeek.value

    /**
     * Weekday-planned routines: the day planned for today's weekday, else a rest day with the next
     * planned one. Unplanned routines rotate: the day after the last one done, counted as training
     * dates since the routine started ([startDate]) before [today].
     */
    fun today(r: Routine, today: String, trainedDates: Collection<String> = emptyList(), startDate: String? = null): Today {
        if (r.days.isEmpty()) return Today(null, null)
        val planned = r.days.any { it.weekday != null }
        if (planned) {
            val wd = weekdayOf(today)
            val i = r.days.indexOfFirst { it.weekday == wd }
            if (i >= 0) return Today(r.days[i], i)
            // Rest day: the next planned day this week or next.
            val next = (1..7).map { ((wd - 1 + it) % 7) + 1 }.firstNotNullOfOrNull { d -> r.days.firstOrNull { it.weekday == d }?.let { day -> day to d } }
            return Today(null, null, next?.first, next?.second)
        }
        val start = startDate?.take(10)
        val done = trainedDates.filter { it < today && (start == null || it >= start) }.distinct().size
        val i = done % r.days.size
        return Today(r.days[i], i)
    }

    /** Total planned sets in a day (for "5 exercises · 18 sets"). */
    fun setCount(d: Day): Int = d.exercises.sumOf { it.sets }

    /** A rough session length: 40 s per set plus the rest between sets. */
    fun estimateMinutes(d: Day): Int = (d.exercises.sumOf { it.sets * 40 + (it.sets - 1).coerceAtLeast(0) * it.restS } / 60.0).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(5)
}
