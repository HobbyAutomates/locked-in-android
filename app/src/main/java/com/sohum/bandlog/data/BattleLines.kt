package com.sohum.bandlog.data

/**
 * One-liners for a squad Snap post (docs/food-battle-spec.md: "Snap-to-squad flow"). Ported
 * faithfully from the web's src/lib/battleLines.ts — no LLM call, a local template bank keyed on
 * goal_type and progress band (r = eaten / target). Hype only. Never body- or food-shaming, never
 * "you ate too much" — under-fuelled cutting days get gentler, still-supportive lines, never praise
 * for eating less.
 */
object BattleLines {
    private enum class Band { UNDER, LOW, BAND, HIGH, OVER }

    private fun bandFor(goal: String, r: Double): Band {
        val lo: Double; val hi: Double
        when (goal) {
            "gain" -> { lo = 1.0; hi = 1.10 }
            "lose" -> { lo = 0.90; hi = 1.00 }
            else -> { lo = 0.95; hi = 1.05 }
        }
        return when {
            r < lo - 0.15 -> Band.UNDER
            r < lo -> Band.LOW
            r <= hi -> Band.BAND
            r <= hi + 0.15 -> Band.HIGH
            else -> Band.OVER
        }
    }

    private val GAIN = mapOf(
        Band.UNDER to listOf("Bulk needs fuel — next plate closes the gap 💪", "A quiet start. The next meal is where gains happen.", "Under target for a bulk — stack the next one up."),
        Band.LOW to listOf("Warming up the bulk arc 📈", "Building toward target, one plate at a time.", "Gains loading… keep the plates coming."),
        Band.BAND to listOf("Bulk arc loading 📈", "Right in the growth zone. Textbook.", "Surplus secured. Muscles say thanks 💪", "On-target and stacking — this is the way.", "Perfect bulking rep. Next plate, same energy."),
        Band.HIGH to listOf("Big appetite today — the gym earns it 🔥", "Surplus and then some. Let's put it to work.", "Feasting arc. Log the lift to match."),
        Band.OVER to listOf("Serious hunger today — hydrate and roll with it.", "Big numbers on the board. Balance it out tomorrow.", "That's a heavy plate — no stress, keep logging."),
    )

    private val MAINTAIN = mapOf(
        Band.UNDER to listOf("A light one — your body still needs fuel to maintain.", "Under target today. The next meal can even it out.", "Running light — no need to force it, just keep tracking."),
        Band.LOW to listOf("Close to the mark, nice control.", "Nearly dialed in — one more solid plate does it.", "Steady tracking. Maintenance mode, activated."),
        Band.BAND to listOf("Clean plate, clean stats ✅", "Right on the number. Consistency wins.", "Maintenance mode: locked in 🎯", "Dialed in perfectly today.", "Textbook maintenance rep."),
        Band.HIGH to listOf("A little over, totally normal — keep the streak going.", "Slightly above target. One day doesn't move the needle.", "Bigger plate today, no drama — tomorrow's another rep."),
        Band.OVER to listOf("Big day on the plate — balance finds itself over the week.", "Well above target, that's alright. Zoom out, not one day.", "A hearty one today. Stay consistent tomorrow."),
    )

    private val LOSE = mapOf(
        Band.UNDER to listOf(
            "Under-fuelled — your body needs food, not less of it. Eat the next meal.",
            "That's too low for a cut. Add a proper meal, this isn't the goal.",
            "Running on empty isn't progress — refuel properly next plate.",
        ),
        Band.LOW to listOf("Lean plate, on pace for the cut 🔥", "Nice discipline — right at the edge of the band.", "Solid cutting rep, staying sharp."),
        Band.BAND to listOf("Protein check: passed ✅", "Clean cut, on target 🎯", "Right in the cutting band — that's the move.", "Deficit done right. Textbook cutting day.", "Locked in on the cut, no shortcuts needed."),
        Band.HIGH to listOf("A bit over today — totally fine, cuts aren't a straight line.", "Slightly above target. One plate, not a pattern.", "A little extra today, the week still adds up in your favor."),
        Band.OVER to listOf("Bigger plate today — the deficit still works over the week, don't stress.", "Well over target, no big deal. Reset with the next meal.", "That's a hearty one — balance it out tomorrow, no rush."),
    )

    private fun linesFor(goal: String) = when (goal) { "gain" -> GAIN; "lose" -> LOSE; else -> MAINTAIN }

    private fun pick(lines: List<String>, seed: Long): String {
        val i = ((seed % lines.size) + lines.size) % lines.size
        return lines[i.toInt()]
    }

    /** A hype one-liner for a Snap post, given the member's goal and today's ratio after this meal. */
    fun line(goal: String, eaten: Double, target: Double, seed: Long = System.currentTimeMillis()): String {
        val r = if (target > 0) eaten / target else 0.0
        val band = bandFor(goal, r)
        return pick(linesFor(goal)[band] ?: MAINTAIN[Band.BAND]!!, seed)
    }

    /** True when the line context is an under-fuelled cutting day — pair with a gentler tone/CTA, never praise. */
    fun isUnderFuelledLine(goal: String, eaten: Double, target: Double): Boolean =
        goal == "lose" && (if (target > 0) eaten / target else 0.0) < 0.75
}
