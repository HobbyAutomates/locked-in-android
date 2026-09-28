package com.sohum.bandlog.util

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * v2.18 coach stream, training side, ported from web src/lib/v218: Indian fasting presets with
 * sunrise / sunset (B10), home & hostel routines (C1), sports presets and steps (C3) and the
 * form-check rep counter (C2, fed by ML Kit pose landmarks: the same 33-point BlazePose layout).
 */
object FastingPresets {
    data class Preset(val key: String, val name: String, val sub: String, val window: String, val fixedHours: Double? = null, val eat: List<String>, val avoid: List<String>, val note: String)
    data class City(val key: String, val name: String, val lat: Double, val lng: Double)
    data class Window(val startMin: Int, val endMin: Int, val hours: Double, val label: String, val sehri: String? = null, val iftar: String? = null)

    val PRESETS = listOf(
        Preset("navratri", "Navratri vrat", "Sunrise to sunset, phalahar allowed · 9 days", "sun_day",
            eat = listOf("Fruits, dates", "Sabudana khichdi / vada", "Kuttu or singhara atta roti", "Rajgira, samak (sama) rice", "Makhana, peanuts", "Curd, paneer, milk, lassi", "Aloo, shakarkandi, lauki", "Sendha namak"),
            avoid = listOf("Grains (wheat, rice), dal", "Onion, garlic", "Regular salt"),
            note = "Paneer, curd and makhana keep protein up on vrat days; fried sabudana and pakoras add up fast."),
        Preset("ramadan", "Ramadan roza", "Sehri before dawn, iftar at sunset", "sun_day",
            eat = listOf("Sehri: oats / roti with eggs or paneer, curd, dates, lots of water", "Iftar: dates and water first, then fruit, then a proper meal", "Haleem, grilled kebabs, dal, chana"),
            avoid = listOf("Only fried snacks at iftar", "Salty food at sehri (thirst)", "Too much sugar at once"),
            note = "Drink 2–3 L between iftar and sehri. Train an hour after iftar, lighter than usual."),
        Preset("ekadashi", "Ekadashi", "Sunrise to next sunrise, no grains or beans", "sunrise_to_sunrise",
            eat = listOf("Fruits, milk, curd", "Sabudana, kuttu, singhara", "Aloo, shakarkandi, pumpkin", "Nuts, makhana"),
            avoid = listOf("Rice, wheat, all grains", "Dal and beans"),
            note = "Some keep nirjala (no water). If you do, skip hard training that day."),
        Preset("jain", "Jain chauvihar", "No food or water after sunset until sunrise", "sun_night",
            eat = listOf("Dinner before sunset", "Dal, roti, sabzi without root vegetables", "Curd, paneer, moong, chana for protein"),
            avoid = listOf("Onion, garlic, potato and other root vegetables", "Eating after sunset"),
            note = "An early dinner works like a 12–14 h overnight fast. Make that last meal protein-rich."),
        Preset("somvar", "Somvar / weekly vrat", "One-meal or phalahar day · 12–16 h", "fixed", fixedHours = 14.0,
            eat = listOf("Fruits, milk, curd", "Sabudana, makhana", "One simple meal in the evening"),
            avoid = listOf("Grains until the evening meal (varies by family)"),
            note = "Keep water up; a glass of lassi or chaas helps with protein and salt."),
    )

    val CITIES = listOf(
        City("delhi", "Delhi", 28.61, 77.21), City("mumbai", "Mumbai", 19.08, 72.88), City("bengaluru", "Bengaluru", 12.97, 77.59),
        City("kolkata", "Kolkata", 22.57, 88.36), City("chennai", "Chennai", 13.08, 80.27), City("hyderabad", "Hyderabad", 17.39, 78.49),
        City("pune", "Pune", 18.52, 73.86), City("ahmedabad", "Ahmedabad", 23.02, 72.57), City("jaipur", "Jaipur", 26.91, 75.79), City("lucknow", "Lucknow", 26.85, 80.95),
    )

    private const val RAD = PI / 180

    /** Sunrise and sunset in minutes after midnight IST (NOAA general solar position formula). */
    fun sunTimes(date: String, lat: Double, lng: Double): Pair<Int, Int> {
        val doy = LocalDate.parse(date).dayOfYear
        val g = (2 * PI / 365) * (doy - 1)
        val eqt = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val cosHa = cos(90.833 * RAD) / (cos(lat * RAD) * cos(decl)) - tan(lat * RAD) * tan(decl)
        val ha = acos(cosHa.coerceIn(-1.0, 1.0)) / RAD
        val noonUtc = 720 - 4 * lng - eqt
        return (noonUtc - 4 * ha + 330).roundToInt() to (noonUtc + 4 * ha + 330).roundToInt()
    }

    fun hhmm(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        val h = m / 60
        return "${if (h % 12 == 0) 12 else h % 12}:${(m % 60).toString().padStart(2, '0')} ${if (h < 12) "am" else "pm"}"
    }

    private fun half(minutes: Int) = (minutes / 60.0 * 2).roundToInt() / 2.0

    fun window(p: Preset, date: String, city: City = CITIES[0]): Window {
        val (rise, set) = sunTimes(date, city.lat, city.lng)
        val next = sunTimes(LocalDate.parse(date).plusDays(1).toString(), city.lat, city.lng).first + 1440
        return when (p.window) {
            "sun_day" -> {
                val start = if (p.key == "ramadan") rise - 80 else rise
                val w = Window(start, set, half(set - start), "${hhmm(start)} → ${hhmm(set)}")
                if (p.key == "ramadan") w.copy(sehri = hhmm(start), iftar = hhmm(set)) else w
            }
            "sun_night" -> Window(set, next, half(next - set), "${hhmm(set)} → ${hhmm(next)} (next day)")
            "sunrise_to_sunrise" -> Window(rise, next, half(next - rise), "${hhmm(rise)} → ${hhmm(next)} next day")
            else -> Window(-1, -1, p.fixedHours ?: 14.0, "${(p.fixedHours ?: 14.0).toInt()} h from when you start")
        }
    }

    /** Minutes to backdate the timer when we're already inside today's window. */
    fun backdateMin(w: Window, nowMin: Int): Int = if (w.startMin >= 0 && nowMin >= w.startMin && nowMin < w.endMin) nowMin - w.startMin else 0
}

object HomeWorkouts {
    data class Template(val key: String, val name: String, val sub: String, val equipment: String, val level: String, val days: List<Routines.Day>)

    private fun ex(n: String, s: Int, r: Int, rest: Int = Routines.DEFAULT_REST_S) = Routines.Exercise(n, s, r, rest)

    val TEMPLATES = listOf(
        Template("home_beginner", "Hostel room · beginner", "3 days · no equipment · 25 min", "none", "beginner", listOf(
            Routines.Day("Full body A", 1, listOf(ex("Bodyweight squat", 3, 12, 60), ex("Push-up", 3, 8, 75), ex("Glute bridge", 3, 12, 60), ex("Plank", 3, 30, 45))),
            Routines.Day("Full body B", 3, listOf(ex("Lunge", 3, 10, 60), ex("Pike push-up", 3, 6, 75), ex("Superman", 3, 12, 45), ex("Mountain climber", 3, 20, 45))),
            Routines.Day("Full body C", 5, listOf(ex("Step-up", 3, 10, 60), ex("Diamond push-up", 3, 6, 75), ex("Glute bridge", 3, 15, 60), ex("Crunch", 3, 15, 45))),
        )),
        Template("home_intermediate", "No-equipment · intermediate", "4 days · upper / lower · 35 min", "none", "intermediate", listOf(
            Routines.Day("Upper", 1, listOf(ex("Push-up", 4, 15, 75), ex("Pike push-up", 4, 10, 75), ex("Dips", 3, 10, 75), ex("Diamond push-up", 3, 12, 60), ex("Plank", 3, 45, 45))),
            Routines.Day("Lower", 2, listOf(ex("Bulgarian split squat", 4, 10, 75), ex("Jump squat", 3, 12, 75), ex("Glute bridge", 4, 15, 60), ex("Calf raise", 4, 20, 45))),
            Routines.Day("Upper + core", 4, listOf(ex("Push-up", 4, 12, 75), ex("Inverted row", 4, 10, 75), ex("Pike push-up", 3, 10, 75), ex("Russian twist", 3, 20, 45), ex("Mountain climber", 3, 30, 45))),
            Routines.Day("Lower + conditioning", 5, listOf(ex("Lunge", 4, 12, 75), ex("Step-up", 3, 12, 60), ex("Burpee", 4, 10, 90), ex("Superman", 3, 15, 45))),
        )),
        Template("band_beginner", "Band only · beginner", "3 days · one resistance band · 30 min", "band", "beginner", listOf(
            Routines.Day("Full body A", 1, listOf(ex("Band squat", 3, 12, 60), ex("Band row", 3, 12, 60), ex("Band chest press", 3, 12, 60), ex("Band pull-apart", 3, 15, 45))),
            Routines.Day("Full body B", 3, listOf(ex("Band lateral walk", 3, 12, 45), ex("Band overhead press", 3, 10, 60), ex("Band curl", 3, 12, 45), ex("Band tricep extension", 3, 12, 45))),
            Routines.Day("Full body C", 5, listOf(ex("Band squat", 3, 15, 60), ex("Band row", 3, 15, 60), ex("Push-up", 3, 8, 75), ex("Glute bridge", 3, 15, 45))),
        )),
        Template("band_intermediate", "Band + bodyweight · intermediate", "4 days · push / pull / legs / full · 40 min", "band", "intermediate", listOf(
            Routines.Day("Push", 1, listOf(ex("Band chest press", 4, 12, 75), ex("Push-up", 4, 15, 75), ex("Band overhead press", 3, 12, 60), ex("Band tricep extension", 3, 15, 45))),
            Routines.Day("Pull", 2, listOf(ex("Band row", 4, 12, 75), ex("Inverted row", 3, 10, 75), ex("Band pull-apart", 3, 20, 45), ex("Band curl", 3, 15, 45))),
            Routines.Day("Legs", 4, listOf(ex("Band squat", 4, 15, 75), ex("Bulgarian split squat", 3, 10, 75), ex("Band lateral walk", 3, 15, 45), ex("Glute bridge", 4, 15, 45))),
            Routines.Day("Full body", 6, listOf(ex("Burpee", 3, 10, 90), ex("Band row", 3, 15, 60), ex("Push-up", 3, 15, 60), ex("Lunge", 3, 12, 60), ex("Plank", 3, 45, 45))),
        )),
    )

    fun routine(t: Template) = Routines.Routine(name = t.name, days = t.days, active = true)
}

object Sports {
    data class Variant(val key: String, val label: String, val met: Double, val code: String?, val minutes: Int, val note: String)
    data class Sport(val key: String, val name: String, val variants: List<Variant>)
    data class Pace(val key: String, val label: String, val cadence: Int, val met: Double)
    data class Steps(val km: Double, val minutes: Int, val kcal: Double)

    val SPORTS = listOf(
        Sport("cricket", "Cricket", listOf(
            Variant("gully", "Gully / tennis-ball", 5.0, "LI-15150", 60, "Lots of running between wickets and chasing."),
            Variant("batting", "Net session (batting / bowling)", 4.8, "LI-15150", 60, "Compendium 15150: batting, bowling, fielding."),
            Variant("match", "Match, mostly fielding", 4.0, "LI-15150", 120, "A lot of standing; bowlers burn more."),
            Variant("fast_bowling", "Fast bowling spell", 6.0, null, 45, "Run-ups add up: priced a notch higher."),
        )),
        Sport("football", "Football", listOf(
            Variant("casual", "Casual / 5-a-side", 7.0, "15610", 60, "Compendium 15610: casual, general."),
            Variant("match", "Competitive match", 10.0, "15605", 90, "Compendium 15605: competitive."),
            Variant("drills", "Drills / practice", 6.0, null, 60, "Passing and shooting drills."),
        )),
        Sport("badminton", "Badminton", listOf(
            Variant("social", "Social doubles", 5.5, "LI-15030", 60, "Compendium 15030: social, general."),
            Variant("singles", "Competitive singles", 7.0, "15020", 45, "Compendium 15020: competitive."),
        )),
        Sport("kabaddi", "Kabaddi", listOf(
            Variant("practice", "Practice", 6.0, null, 60, "Raids, holds and footwork drills."),
            Variant("match", "Match", 8.0, null, 40, "Two 20-minute halves of sprints and tackles."),
        )),
    )
    val PACES = listOf(Pace("easy", "Easy stroll", 90, 3.0), Pace("brisk", "Brisk walk", 110, 4.3), Pace("fast", "Very brisk", 125, 5.0))

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    fun kcal(met: Double, weightKg: Double?, minutes: Int): Double = round1(met * (weightKg ?: Burn.DEFAULT_WEIGHT_KG) * maxOf(0, minutes) / 60.0)

    fun strideM(heightCm: Double?): Double = if (heightCm != null && heightCm > 100 && heightCm < 230) (heightCm * 0.415).roundToInt() / 100.0 else 0.72

    /** Steps → km, minutes and kcal above resting (MET − 1), like the web. */
    fun steps(steps: Int, weightKg: Double?, heightCm: Double?, pace: String = "brisk"): Steps {
        val s = maxOf(0, steps)
        val p = PACES.firstOrNull { it.key == pace } ?: PACES[1]
        val km = (s * strideM(heightCm) / 1000 * 100).roundToInt() / 100.0
        val minutes = maxOf(1, (s.toDouble() / p.cadence).roundToInt())
        return Steps(km, minutes, round1((p.met - 1) * (weightKg ?: Burn.DEFAULT_WEIGHT_KG) * minutes / 60.0))
    }
}

/** v2.18 C2: rep counting + form tips from pose landmarks (normalised x / y, visibility 0–1). */
object FormCheck {
    data class P(val x: Double, val y: Double, val v: Double = 1.0)
    data class Exercise(val key: String, val label: String, val setup: String, val up: Double, val down: Double, val deep: Double)

    val EXERCISES = listOf(
        Exercise("squat", "Squat", "Phone at hip height, side-on, whole body in frame.", 160.0, 115.0, 100.0),
        Exercise("pushup", "Push-up", "Phone on the floor 2 m away, side-on, head to heels in frame.", 150.0, 110.0, 95.0),
        Exercise("lunge", "Lunge", "Phone at hip height, side-on, whole body in frame.", 155.0, 115.0, 105.0),
    )
    fun ex(key: String) = EXERCISES.first { it.key == key }

    val TIPS = mapOf(
        "squat" to mapOf("depth" to "Go deeper: hips down to knee level.", "lean" to "Chest up: you're folding forward.", "tempo" to "Slow down: 2 seconds down, 1 up."),
        "pushup" to mapOf("depth" to "Go lower: chest to a fist's height from the floor.", "hips" to "Straight line from shoulders to heels: squeeze your glutes.", "tempo" to "Control the way down."),
        "lunge" to mapOf("depth" to "Drop the back knee closer to the floor.", "lean" to "Stay tall: torso upright over the hips.", "tempo" to "Slow and steady: no bouncing."),
    )

    fun angle(a: P, b: P, c: P): Double {
        val v1x = a.x - b.x; val v1y = a.y - b.y
        val v2x = c.x - b.x; val v2y = c.y - b.y
        val m = hypot(v1x, v1y) * hypot(v2x, v2y)
        if (m == 0.0) return 180.0
        return acos(((v1x * v2x + v1y * v2y) / m).coerceIn(-1.0, 1.0)) * 180 / PI
    }

    fun lean(shoulder: P, hip: P): Double = abs(atan2(shoulder.x - hip.x, hip.y - shoulder.y) * 180 / PI)

    data class Measures(val main: Double, val lean: Double?, val line: Double?)

    /** [lm] indexed by BlazePose landmark (ML Kit PoseLandmark types use the same numbers). */
    fun measure(key: String, lm: Map<Int, P>): Measures? {
        fun vis(i: Int) = (lm[i]?.v ?: 0.0) >= 0.5
        fun better(l: Int, r: Int) = if ((lm[l]?.v ?: 0.0) >= (lm[r]?.v ?: 0.0)) l else r
        if (key == "pushup") {
            val left = better(11, 12) == 11
            val (sh, el, wr, hip, an) = if (left) listOf(11, 13, 15, 23, 27) else listOf(12, 14, 16, 24, 28)
            if (!(vis(sh) && vis(el) && vis(wr))) return null
            val line = if (vis(hip) && vis(an)) angle(lm[sh]!!, lm[hip]!!, lm[an]!!) else null
            return Measures(angle(lm[sh]!!, lm[el]!!, lm[wr]!!), null, line)
        }
        fun knee(h: Int, k: Int, a: Int) = if (vis(h) && vis(k) && vis(a)) angle(lm[h]!!, lm[k]!!, lm[a]!!) else null
        val lk = knee(23, 25, 27)
        val rk = knee(24, 26, 28)
        if (lk == null && rk == null) return null
        val main = if (key == "lunge") minOf(lk ?: 180.0, rk ?: 180.0) else if (better(25, 26) == 25) (lk ?: rk!!) else (rk ?: lk!!)
        val sh = better(11, 12)
        val hp = if (sh == 11) 23 else 24
        return Measures(main, if (vis(sh) && vis(hp)) lean(lm[sh]!!, lm[hp]!!) else null, null)
    }

    data class State(
        val key: String, val down: Boolean = false, val reps: Int = 0, val smooth: Double? = null, val minMain: Double = 180.0,
        val maxLean: Double = 0.0, val minLine: Double = 180.0, val downAt: Long? = null, val tips: Map<String, Int> = emptyMap(),
        val last: String? = null, val goodReps: Int = 0,
    )

    fun step(s: State, m: Measures?, t: Long): State {
        if (m == null) return s
        val e = ex(s.key)
        val smooth = s.smooth?.let { it * 0.6 + m.main * 0.4 } ?: m.main
        var n = s.copy(smooth = smooth, minMain = minOf(s.minMain, smooth), maxLean = m.lean?.let { maxOf(s.maxLean, it) } ?: s.maxLean, minLine = m.line?.let { minOf(s.minLine, it) } ?: s.minLine)
        if (!s.down && smooth < e.down) {
            n = n.copy(down = true, downAt = t)
        } else if (s.down && smooth > e.up) {
            val tip = when {
                n.minMain > e.deep -> "depth"
                s.key != "pushup" && n.maxLean > (if (s.key == "squat") 50.0 else 30.0) -> "lean"
                s.key == "pushup" && n.minLine < 155 -> "hips"
                s.downAt != null && t - s.downAt < 350 -> "tempo"
                else -> null
            }
            n = n.copy(
                down = false, reps = s.reps + 1, last = tip ?: "good", goodReps = if (tip == null) s.goodReps + 1 else s.goodReps,
                tips = if (tip == null) s.tips else s.tips + (tip to (s.tips[tip] ?: 0) + 1),
                minMain = 180.0, maxLean = 0.0, minLine = 180.0, downAt = null,
            )
        }
        return n
    }

    data class Summary(val reps: Int, val clean: Int, val tips: List<String>)

    fun summary(s: State): Summary {
        val tips = s.tips.filter { it.value > 0 && it.value >= maxOf(1.0, s.reps * 0.25) }.entries.sortedByDescending { it.value }.take(2).mapNotNull { TIPS[s.key]?.get(it.key) }.toMutableList()
        if (tips.isEmpty() && s.reps > 0) tips += "Clean set: depth and position looked good."
        return Summary(s.reps, if (s.reps > 0) (s.goodReps * 100.0 / s.reps).roundToInt() else 0, tips)
    }

    fun tipText(s: State): String? = when (s.last) { null -> null; "good" -> "Good rep"; else -> TIPS[s.key]?.get(s.last) }
}
