package com.sohum.bandlog.util

/**
 * v2.18 D8 accountability pledges (web docs/schema_v44.sql pledges + RPC squad_pledges). Honour
 * system: no money moves. The stake is text and an optional rupee amount people promise to "pay
 * into the squad pot"; the in-app money step is "coming soon". Pure. Port of the web's
 * src/lib/social/pledges.ts.
 */
object Pledges {
    const val PLEDGE_PAYMENTS_LIVE = false
    val KINDS = listOf("log_days", "train_days", "protein_days", "custom")
    val KIND_LABEL = mapOf("log_days" to "Log food on", "train_days" to "Train on", "protein_days" to "Hit protein on", "custom" to "My own goal")
    const val MONEY_STEP_NOTE = "Paying into the pot in the app is coming soon. For now it's on your honour."

    data class Pledge(
        val id: String, val userId: String, val name: String?, val goal: String, val kind: String, val target: Int?,
        val stake: String, val stakeInr: Int, val startsOn: String, val endsOn: String, val status: String, val createdAt: String,
        val groupId: String? = null,
    )

    fun parseKind(v: Any?): String = if (v is String && v in KINDS) v else "custom"

    private fun int(v: Any?): Int? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }?.let { kotlin.math.floor(it).toInt() }

    fun parsePledge(r: Map<String, Any?>): Pledge {
        val st = r["status"]
        return Pledge(
            id = r["id"]?.toString() ?: "",
            userId = r["user_id"]?.toString() ?: "",
            name = r["name"] as? String,
            goal = r["goal"]?.toString() ?: "",
            kind = parseKind(r["kind"]),
            target = if (r["target"] == null) null else (int(r["target"]) ?: 1).coerceAtLeast(1).let { if (it < 1) 1 else it },
            stake = r["stake"]?.toString() ?: "",
            stakeInr = (int(r["stake_inr"]) ?: 0).coerceAtLeast(0),
            startsOn = r["starts_on"]?.toString() ?: "",
            endsOn = r["ends_on"]?.toString() ?: "",
            status = if (st == "kept" || st == "broken" || st == "cancelled") st as String else "active",
            createdAt = r["created_at"]?.toString() ?: "",
            groupId = r["group_id"] as? String,
        )
    }

    data class Draft(val goal: String, val kind: String, val target: Int?, val stake: String, val stakeInr: Int, val startsOn: String, val days: Int)

    /** An error message, or null when the draft can be saved. */
    fun validate(d: Draft, today: String): String? {
        if (d.goal.trim().length < 2) return "Say what you're pledging"
        if (d.goal.trim().length > 120) return "Keep the goal under 120 characters"
        if (d.days < 1 || d.days > 90) return "Pick 1 to 90 days"
        if (d.startsOn < today) return "Start today or later"
        if (d.kind != "custom") {
            if (d.target == null || d.target < 1) return "Set how many days"
            if (d.target > d.days) return "That's more days than the pledge has (${d.days})"
        }
        if (d.stake.length > 120) return "Keep the stake under 120 characters"
        if (d.stakeInr < 0 || d.stakeInr > 100000) return "Stake between ₹0 and ₹1,00,000"
        return null
    }

    fun endsOn(starts: String, days: Int): String = Dates.addDays(starts, (days.coerceAtLeast(1) - 1).toLong())

    /** "Log food on 6 of 7 days"; custom keeps the typed text. */
    fun goalText(kind: String, target: Int?, days: Int, custom: String): String =
        if (kind == "custom") custom.trim() else "${KIND_LABEL[kind]} ${target ?: days} of $days days"

    data class Progress(val done: Int, val needed: Int, val daysLeft: Int, val outcome: String, val onTrack: Boolean)

    /** Progress from the days that counted (inside the window, up to today). See the web for the rules. */
    fun progress(kind: String, target: Int?, startsOn: String, endsOn: String, status: String, counted: Collection<String>, today: String): Progress {
        val total = (Dates.daysBetween(startsOn, endsOn) + 1).toInt()
        val needed = if (kind == "custom") 0 else minOf(total, target ?: total)
        val set = counted.filter { it >= startsOn && it <= endsOn && it <= today }.toSet()
        val done = set.size
        val from = if (today < startsOn) startsOn else today
        val daysLeft = if (today > endsOn) 0 else (Dates.daysBetween(from, endsOn) + 1).toInt() - (if (today in set) 1 else 0)
        if (status == "kept" || status == "broken") return Progress(done, needed, daysLeft, status, status == "kept")
        if (kind == "custom") return Progress(done, needed, daysLeft, "active", true)
        if (done >= needed) return Progress(done, needed, daysLeft, "kept", true)
        if (done + daysLeft < needed) return Progress(done, needed, daysLeft, "broken", false)
        val elapsed = if (today < startsOn) 0 else minOf(total, (Dates.daysBetween(startsOn, today) + 1).toInt())
        val pace = if (total > 0) needed.toDouble() * elapsed / total else 0.0
        return Progress(done, needed, daysLeft, "active", done + 1 >= pace)
    }

    fun progress(p: Pledge, counted: Collection<String>, today: String): Progress = progress(p.kind, p.target, p.startsOn, p.endsOn, p.status, counted, today)

    /** "Stake: chai for the squad · ₹200 into the squad pot". */
    fun stakeLine(stake: String, inr: Int): String {
        val parts = listOfNotNull(stake.trim().ifBlank { null }, if (inr > 0) "${Money.inr(inr)} into the squad pot" else null)
        return if (parts.isNotEmpty()) "Stake: ${parts.joinToString(" · ")}" else "No stake, just pride"
    }
}

/** Indian digit grouping ("₹1,00,000"), like toLocaleString("en-IN"). */
object Money {
    fun group(n: Long): String {
        val neg = n < 0
        val s = kotlin.math.abs(n).toString()
        if (s.length <= 3) return (if (neg) "-" else "") + s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = mutableListOf<String>()
        while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return (if (neg) "-" else "") + parts.joinToString(",") + "," + last3
    }

    fun inr(n: Int): String = "₹${group(n.toLong())}"
}
