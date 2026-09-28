package com.sohum.bandlog.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * v2.18 coach stream, pure helpers ported from web src/lib/v218: supplements (B7), consistency
 * (B11), festival mode (B9), cycle phases (B6), the "why it changed" copy (B3) and the voice text
 * cleanup (B1). The server does the heavy lifting (the /api/coach routes); these back the phone-side
 * displays and the unit tests.
 */
object Supplements {
    data class Preset(val kind: String, val name: String, val unit: String, val hint: String)

    val PRESETS = listOf(
        Preset("creatine", "Creatine", "g", "Same time every day; it works by staying topped up."),
        Preset("whey", "Whey protein", "scoop", "Counts toward protein when you log the shake."),
        Preset("vitamin_d", "Vitamin D", "IU", "Take with a meal that has some fat."),
        Preset("iron", "Iron", "mg", "Away from tea, coffee and milk; vitamin C helps."),
        Preset("omega3", "Omega-3", "capsule", "With a meal."),
        Preset("multivitamin", "Multivitamin", "tablet", "With breakfast."),
        Preset("b12", "Vitamin B12", "mcg", "Common for vegetarians; as your doctor advised."),
    )

    fun doseText(dose: Double?, unit: String): String {
        if (dose == null || dose <= 0) return ""
        val d = if (dose % 1.0 == 0.0) dose.toLong().toString() else ((dose * 100).roundToInt() / 100.0).toString()
        val plural = if (dose != 1.0 && unit in setOf("scoop", "capsule", "tablet")) "s" else ""
        return "$d $unit$plural"
    }

    data class Streak(val current: Int, val best: Int, val takenToday: Boolean)

    fun streak(dates: Collection<String>, today: String): Streak {
        val days = dates.toSet().map { LocalDate.parse(it) }.sorted()
        val set = days.toSet()
        val t = LocalDate.parse(today)
        val takenToday = t in set
        var cur = 0
        var d = if (takenToday) t else t.minusDays(1)
        while (d in set) { cur++; d = d.minusDays(1) }
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (x in days) {
            run = if (prev != null && ChronoUnit.DAYS.between(prev, x) == 1L) run + 1 else 1
            best = maxOf(best, run)
            prev = x
        }
        return Streak(cur, maxOf(best, cur), takenToday)
    }

    /** "08:00" reminder passed today and not taken yet. */
    fun dueNow(active: Boolean, remindAt: String?, takenToday: Boolean, nowMin: Int): Boolean {
        if (!active || takenToday || remindAt == null) return false
        val p = remindAt.split(":")
        val m = (p.getOrNull(0)?.toIntOrNull() ?: return false) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
        return nowMin >= m
    }
}

object Consistency {
    data class Day(val date: String, val logged: Boolean, val protein: Double, val trained: Boolean, val sleepHours: Double?, val sleepQuality: Int?)
    data class Part(val key: String, val label: String, val pct: Int, val weight: Int)
    data class Result(val score: Int, val label: String, val parts: List<Part>, val weakest: String)

    fun label(score: Int) = when { score >= 80 -> "Locked in"; score >= 60 -> "Building"; score >= 40 -> "Wobbly"; else -> "Restarting" }

    fun score(days: List<Day>, proteinTarget: Int, workoutsPerWeek: Int): Result {
        val d = days.takeLast(14)
        val n = maxOf(1, d.size)
        val logging = d.count { it.logged }.toDouble() / n
        val protein = d.count { it.logged && proteinTarget > 0 && it.protein >= proteinTarget * 0.9 }.toDouble() / n
        val goal = maxOf(1, workoutsPerWeek) * (n / 7.0)
        val training = minOf(1.0, d.count { it.trained } / goal)
        val sleepDays = d.filter { it.sleepHours != null || it.sleepQuality != null }
        val hasSleep = sleepDays.size >= 3
        val sleepGood = sleepDays.count { (it.sleepHours == null || it.sleepHours >= 7) && (it.sleepQuality == null || it.sleepQuality >= 3) }
        val parts = mutableListOf(
            Part("logging", "Logging", (logging * 100).roundToInt(), 35),
            Part("protein", "Protein", (protein * 100).roundToInt(), 25),
            Part("training", "Training", (training * 100).roundToInt(), 25),
        )
        if (hasSleep) parts += Part("sleep", "Sleep", (sleepGood * 100.0 / sleepDays.size).roundToInt(), 15)
        val w = parts.sumOf { it.weight }
        val s = (parts.sumOf { it.pct * it.weight }.toDouble() / w).roundToInt()
        val weakest = parts.sortedWith(compareBy<Part> { it.pct }.thenByDescending { it.weight }).first().label
        return Result(s, label(s), parts, weakest)
    }
}

object Festival {
    data class Mode(val id: String, val kind: String, val name: String, val startDate: String, val endDate: String)
    data class Preset(val kind: String, val name: String, val days: Int, val tip: String)

    val PRESETS = listOf(
        Preset("diwali", "Diwali", 5, "Mithai: pick one piece you love, not five you don't. Protein at breakfast keeps the grazing down."),
        Preset("shaadi", "Shaadi", 3, "Fill half the plate at the live counters with tandoori / paneer tikka first, then the rest. Dance counts as cardio."),
        Preset("holi", "Holi", 2, "Thandai and gujiya are the big ones. Water between rounds."),
        Preset("eid", "Eid", 2, "Biryani and kebabs are protein-rich; go easy on the sheer khurma refills."),
        Preset("navratri", "Navratri / Garba", 9, "Garba nights burn a lot: eat enough. Sabudana and fried vrat snacks add up fast."),
        Preset("trip", "Trip / holiday", 5, "Walk everywhere, one local treat a day, log what you can."),
        Preset("exams", "Exam week", 7, "Sleep beats cramming. Keep meals regular; nuts and fruit for study snacks."),
    )
    const val MAX_DAYS = 21
    const val WARN_DAYS = 3

    fun active(modes: List<Mode>, date: String): Mode? = modes.firstOrNull { it.startDate <= date && date <= it.endDate }

    fun upcoming(modes: List<Mode>, date: String): Pair<Mode, Int>? {
        val t = LocalDate.parse(date)
        return modes.map { it to ChronoUnit.DAYS.between(t, LocalDate.parse(it.startDate)).toInt() }
            .filter { it.second in 1..WARN_DAYS }.minByOrNull { it.second }
    }

    /** Every covered date up to [today]: these count toward the day streak. */
    fun protectedDates(modes: List<Mode>, today: String): List<String> {
        val t = LocalDate.parse(today)
        val out = sortedSetOf<String>()
        for (m in modes) {
            var d = LocalDate.parse(m.startDate)
            val end = minOf(LocalDate.parse(m.endDate), t)
            while (!d.isAfter(end)) { out += d.toString(); d = d.plusDays(1) }
        }
        return out.toList()
    }

    fun maintenanceBump(target: Int, maintenance: Int?): Int = if (maintenance == null) 0 else maxOf(0, ((maintenance - target) / 10.0).roundToInt() * 10)

    /** Null when the dates are fine, else the error to show. */
    fun validate(start: String, end: String, today: String): String? {
        val s = runCatching { LocalDate.parse(start) }.getOrNull() ?: return "Pick the dates"
        val e = runCatching { LocalDate.parse(end) }.getOrNull() ?: return "Pick the dates"
        if (e.isBefore(s)) return "The end date is before the start"
        if (ChronoUnit.DAYS.between(s, e) + 1 > MAX_DAYS) return "Keep it to $MAX_DAYS days or less"
        if (e.isBefore(LocalDate.parse(today))) return "Those dates are in the past"
        return null
    }
}

object Cycle {
    data class Settings(val enabled: Boolean, val lastPeriodStart: String?, val cycleLength: Int = 28, val periodLength: Int = 5)
    data class Today(val day: Int, val phase: String, val label: String, val hunger: String, val waterMl: Int, val training: String, val scale: String, val nextPeriodIn: Int)

    fun today(s: Settings, date: String): Today? {
        if (!s.enabled || s.lastPeriodStart == null) return null
        val diff = ChronoUnit.DAYS.between(LocalDate.parse(s.lastPeriodStart), LocalDate.parse(date)).toInt()
        if (diff < 0) return null
        val len = s.cycleLength.coerceIn(21, 40)
        val day = diff % len + 1
        val ovu = len - 14
        val phase = when { day <= s.periodLength.coerceIn(2, 9) -> "menstrual"; day < ovu - 1 -> "follicular"; day <= ovu + 1 -> "ovulation"; else -> "luteal" }
        return when (phase) {
            "menstrual" -> Today(day, phase, "Period", "Appetite is usually normal; iron-rich food (dal, rajma, spinach, jaggery) helps.", 250, "Go by feel: lighter sessions, walks or yoga are fine if you have cramps.", "Bloating can add 0.5–1 kg of water. It passes.", len - day + 1)
            "follicular" -> Today(day, phase, "Follicular", "Hunger is often lower. A good week to stay on target.", 0, "Energy tends to be high: a good time to push weights and try new bests.", "The scale usually reads truest this week.", len - day + 1)
            "ovulation" -> Today(day, phase, "Ovulation", "Hunger may tick up slightly.", 0, "Strong days for many: warm up well, joints can feel looser.", "Small water shifts are normal.", len - day + 1)
            else -> Today(day, phase, "Luteal", "Hunger and cravings often rise (your burn goes up ~100–300 kcal too). Protein and fibre first; a planned treat beats a binge.", 300, "Steady, moderate training. Recovery can feel slower; sleep matters more.", "Water retention can add 0.5–1.5 kg before your period. It's not fat.", len - day + 1)
        }
    }
}

object VoiceCoach {
    private val EMOJI = Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")

    /** Reply text → something TextToSpeech reads naturally. */
    fun speakable(text: String): String = text
        .replace(EMOJI, "")
        .replace(Regex("\\*\\*|__|[*_`#>]"), "")
        .replace(Regex("\\s*[•·]\\s*"), ", ")
        .replace(Regex("(\\d)\\s?kcal\\b", RegexOption.IGNORE_CASE), "$1 calories")
        .replace(Regex("(\\d)\\s?g\\b"), "$1 grams")
        .replace(Regex("(\\d)\\s?ml\\b", RegexOption.IGNORE_CASE), "$1 millilitres")
        .replace(Regex("(\\d)\\s?kg\\b", RegexOption.IGNORE_CASE), "$1 kilos")
        .replace(Regex("(\\d)\\s?h\\b"), "$1 hours")
        .replace(Regex("\\s*→\\s*"), " to ")
        .replace(Regex("\\s*[–—]\\s*"), ", ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val STOP = Regex("^\\s*(stop|stop listening|that'?s (all|it)|bye|goodbye|thank(s| you)( coach)?|bas|bas karo|ruk(o|ja)|band karo|ok bye)\\s*[.!]?\\s*$", RegexOption.IGNORE_CASE)
    fun isStopPhrase(text: String): Boolean = STOP.matches(text)
}

object TargetsWhy {
    data class Why(val en: List<String>, val hi: List<String>, val direction: String)

    private fun n(v: Int) = java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(v)
    private fun kg(v: Double): String { val r = (abs(v) * 10).roundToInt() / 10.0; return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString() }

    fun why(oldTarget: Int, newTarget: Int, trendKgPerWeek: Double, goalRateKgPerWeek: Double, avgKcal: Int): Why {
        val delta = newTarget - oldTarget
        val direction = if (delta > 0) "up" else if (delta < 0) "down" else "same"
        val moving = if (trendKgPerWeek < -0.05) "down" else if (trendKgPerWeek > 0.05) "up" else "flat"
        val want = if (goalRateKgPerWeek < -0.05) "lose" else if (goalRateKgPerWeek > 0.05) "gain" else "hold"
        val en = mutableListOf<String>()
        val hi = mutableListOf<String>()
        en += if (moving == "flat") "Your weight has stayed about the same for 2 weeks." else "Your weight is going $moving about ${kg(trendKgPerWeek)} kg a week."
        hi += if (moving == "flat") "पिछले 2 हफ़्तों से आपका वज़न लगभग एक जैसा है।" else "आपका वज़न हर हफ़्ते लगभग ${kg(trendKgPerWeek)} kg ${if (moving == "down") "घट" else "बढ़"} रहा है।"
        en += if (want == "hold") "Your goal is to stay where you are." else "Your goal is to $want about ${kg(goalRateKgPerWeek)} kg a week."
        hi += if (want == "hold") "आपका लक्ष्य वज़न को वैसा ही रखना है।" else "आपका लक्ष्य हर हफ़्ते ${kg(goalRateKgPerWeek)} kg ${if (want == "lose") "कम" else "ज़्यादा"} करना है।"
        en += "You ate about ${n(avgKcal)} kcal a day."
        hi += "आपने रोज़ लगभग ${n(avgKcal)} kcal खाया।"
        when (direction) {
            "same" -> { en += "That's right on track, so your target stays at ${n(oldTarget)} kcal."; hi += "सब सही चल रहा है, इसलिए टारगेट ${n(oldTarget)} kcal ही रहेगा।" }
            "down" -> {
                en += "You're moving slower than planned, so we're trimming ${n(-delta)} kcal: ${n(oldTarget)} → ${n(newTarget)}. About one roti less a day."
                hi += "प्रगति प्लान से धीमी है, इसलिए टारगेट ${n(-delta)} kcal कम कर रहे हैं: ${n(oldTarget)} → ${n(newTarget)}। यानी दिन में लगभग एक रोटी कम।"
            }
            else -> {
                en += "You're moving faster than planned (or your body needs more), so we're adding ${n(delta)} kcal: ${n(oldTarget)} → ${n(newTarget)}. Safer and easier to keep up."
                hi += "आप प्लान से तेज़ चल रहे हैं (या शरीर को ज़्यादा चाहिए), इसलिए ${n(delta)} kcal बढ़ा रहे हैं: ${n(oldTarget)} → ${n(newTarget)}। यह ज़्यादा सुरक्षित और आसान है।"
            }
        }
        en += "We only ever move it by 150 kcal at most, and never below your safe minimum."
        hi += "हम एक बार में 150 kcal से ज़्यादा नहीं बदलते, और कभी सुरक्षित सीमा से नीचे नहीं जाते।"
        return Why(en, hi, direction)
    }
}
