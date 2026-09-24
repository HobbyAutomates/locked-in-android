package com.sohum.bandlog.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** ISO yyyy-MM-dd helpers, mirroring the web app's dates.ts. */
object Dates {
    /** Every "today" in the app is an India calendar date, whatever zone the phone is set to. */
    val ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Kolkata")
    fun today(): String = LocalDate.now(ZONE).toString()
    fun parse(s: String): LocalDate = LocalDate.parse(s)
    fun addDays(s: String, n: Long): String = parse(s).plusDays(n).toString()
    fun daysBetween(a: String, b: String): Long = ChronoUnit.DAYS.between(parse(a), parse(b))
    /** Monday of the week containing [s]. */
    fun weekStart(s: String): String = parse(s).with(DayOfWeek.MONDAY).toString()

    private val longFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
    private val shortFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun long(s: String): String = parse(s).format(longFmt)
    fun short(s: String): String = parse(s).format(shortFmt)
    fun month(d: LocalDate): String = d.format(monthFmt)

    fun relative(s: String): String {
        val t = today()
        return when (s) {
            t -> "Today"
            addDays(t, -1) -> "Yesterday"
            else -> short(s)
        }
    }
}
