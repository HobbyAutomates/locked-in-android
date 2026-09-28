package com.sohum.bandlog.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * v2.18 D1 Wrapped: weekly, monthly and yearly story cards (9:16, 1080 × 1920). Port of the web's
 * src/lib/social/wrapped.ts: picks the period and turns recap numbers into slides (the drawing is
 * ui/social/WrappedCards.kt). Calories are never on a slide (hide-numbers safe).
 *
 *   week   the previous Monday–Sunday      month  the previous calendar month
 *   year   1 Jan to yesterday ("2026 so far"); in the first 15 days of January, last year in full
 */
object Wrapped {
    enum class Kind(val id: String) { WEEK("week"), MONTH("month"), YEAR("year") }

    fun parseKind(v: Any?): Kind? = Kind.entries.firstOrNull { it.id == v }

    data class Period(val kind: Kind, val from: String, val to: String, val label: String, val key: String) {
        val days: Int get() = (Dates.daysBetween(from, to) + 1).toInt()
    }

    private fun fmt(iso: String, p: String) = LocalDate.parse(iso).format(DateTimeFormatter.ofPattern(p, Locale.ENGLISH))

    fun period(kind: Kind, today: String): Period = when (kind) {
        Kind.WEEK -> {
            val to = Dates.addDays(Dates.weekStart(today), -1)
            val from = Dates.addDays(to, -6)
            Period(kind, from, to, "${fmt(from, "d MMM")} – ${fmt(to, "d MMM")}", "w-$from")
        }
        Kind.MONTH -> {
            val first = LocalDate.parse(today).withDayOfMonth(1).minusMonths(1)
            val last = first.plusMonths(1).minusDays(1)
            Period(kind, first.toString(), last.toString(), fmt(first.toString(), "MMMM yyyy"), "m-${first.toString().take(7)}")
        }
        Kind.YEAR -> {
            val d = LocalDate.parse(today)
            val y = d.year
            if (d.monthValue == 1 && d.dayOfMonth <= 15) Period(kind, "${y - 1}-01-01", "${y - 1}-12-31", "${y - 1}", "y-${y - 1}")
            else Period(kind, "$y-01-01", Dates.addDays(today, -1), "$y so far", "y-$y")
        }
    }

    data class Lift(val name: String, val kg: Double?, val reps: Int, val pr: Boolean)
    data class Weight(val start: Double, val end: Double, val delta: Double)
    data class Food(val name: String, val count: Int)
    data class Squad(val name: String, val rank: Int, val of: Int)

    /** The recap numbers a Wrapped needs (the web's Recap shape). */
    data class Stats(
        val period: Period, val days: Int, val daysLogged: Int, val workouts: Int, val minutes: Int,
        val proteinTarget: Int, val proteinDays: Int, val bestLift: Lift?, val weight: Weight?,
        val topFoods: List<Food>, val streak: Int, val squad: Squad?, val nextGoal: String,
    )

    /** A Recaps.Recap computed over a Wrapped period → Stats (weight only with 2+ weigh-ins inside, like the web). */
    fun fromRecap(r: Recaps.Recap, period: Period, squad: Squad? = null): Stats {
        val ws = r.weightSeries
        val weight = if (ws.size >= 2) Weight(ws.first().second, ws.last().second, Math.round((ws.last().second - ws.first().second) * 10) / 10.0) else null
        return Stats(
            period, period.days, r.daysLogged, r.workouts, r.minutes, r.proteinTarget, r.proteinDaysHit,
            r.bestLift?.let { (n, pt) -> Lift(n, pt.kg, pt.reps, pt.pr) }, weight,
            r.topFoods.map { Food(it.first, it.second) }, r.streak, squad, r.nextGoal,
        )
    }

    data class Slide(val key: String, val eyebrow: String, val big: String, val line: String, val tone: String)

    private fun daysLine(logged: Int, days: Int): String {
        val pct = if (days > 0) logged.toDouble() / days else 0.0
        return when {
            pct >= 1 -> "of $days. Every single day."
            pct >= 0.85 -> "of $days. Barely missed one."
            pct >= 0.5 -> "of $days. More in than out."
            else -> "of $days. Every log counts."
        }
    }

    /** 0.8 → "0.8", 80.0 → "80" (like JS number printing for one decimal). */
    fun num(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    /** The slides for one Wrapped, in order. Empty stats are skipped. */
    fun slides(r: Stats, kind: Kind, name: String? = null): List<Slide> {
        val out = mutableListOf<Slide>()
        val who = name?.trim()?.ifBlank { null }?.split(Regex("\\s+"))?.first()
        val word = kind.id
        val title = when (kind) {
            Kind.WEEK -> "Your week"
            Kind.MONTH -> r.period.label.split(" ").first()
            Kind.YEAR -> r.period.label.replace(" so far", "")
        }
        val eyebrow = when (kind) { Kind.WEEK -> "Weekly"; Kind.MONTH -> "Monthly"; Kind.YEAR -> "Yearly" }
        out.add(Slide("cover", "$eyebrow wrapped", title, if (who != null) "Locked in, $who." else "Locked in.", "ember"))
        out.add(Slide("days", "Days logged", "${r.daysLogged}", daysLine(r.daysLogged, r.days), "ink"))
        if (r.workouts > 0) out.add(Slide("training", "Sessions", "${r.workouts}", if (r.minutes > 0) "${Money.group(r.minutes.toLong())} minutes moving." else "Showed up. That's the job.", "ink"))
        if (r.proteinTarget > 0 && r.proteinDays > 0) out.add(Slide("protein", "Protein days", "${r.proteinDays}", "days at ${r.proteinTarget} g or close.", "bone"))
        r.bestLift?.let { l ->
            val big = if (l.kg != null) "${num(l.kg)} kg" else "${l.reps} reps"
            out.add(Slide("lift", if (l.pr) "New PR" else "Best lift", big, if (l.kg != null) "${l.name} × ${l.reps}" else l.name, "ink"))
        }
        r.weight?.takeIf { it.delta != 0.0 }?.let { w ->
            out.add(Slide("weight", "Weight", "${if (w.delta > 0) "+" else "−"}${num(kotlin.math.abs(w.delta))} kg", "${num(w.start)} → ${num(w.end)} kg this $word.", "bone"))
        }
        r.topFoods.firstOrNull()?.let { f -> out.add(Slide("food", "On repeat", f.name, "logged ${f.count} ${if (f.count == 1) "time" else "times"}.", "ink")) }
        if (r.streak > 0) out.add(Slide("streak", "Day streak", "${r.streak}", if (r.streak >= 7) "and counting." else "Keep it going.", "ember"))
        r.squad?.let { s -> out.add(Slide("squad", s.name, "#${s.rank}", "of ${s.of} in the squad.", "ink")) }
        out.add(Slide("next", "Next $word", "Next", r.nextGoal + ".", "bone"))
        return out
    }

    fun filename(kind: Kind, period: Period, slide: Slide): String = "locked-in-${kind.id}-${period.from}-${slide.key}.png"
}
