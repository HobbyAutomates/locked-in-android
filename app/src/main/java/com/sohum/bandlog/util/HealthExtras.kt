package com.sohum.bandlog.util

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * v2.18 E4 smart scales and watches through Health Connect (Mi Fit / Zepp / Amazfit, Samsung and
 * Google Fit all write there): weight, daily steps and resting heart rate. Opt-in and gentle: a
 * weight is only copied into weight_log when it's newer than the last logged one and that day
 * has no entry (see [weightsToImport]). Kept apart from [Health] (steps + calories + sessions).
 */
object HealthExtras {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    /** Steps per day read this session (for seasonal events and the sync screen). */
    val stepsByDay = mutableStateMapOf<String, Long>()

    data class Reading(val weights: List<Pair<String, Double>>, val steps: Map<String, Long>, val restingHr: Pair<String, Long>?)

    private fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx)

    suspend fun granted(ctx: Context): Set<String> =
        runCatching { client(ctx).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** The last [days] days: the latest weight per day, steps per day, and the latest resting heart rate. */
    suspend fun read(ctx: Context, days: Int = 30): Reading {
        val zone = ZoneId.systemDefault()
        val c = client(ctx)
        val have = granted(ctx)
        val startDay = LocalDate.now(zone).minusDays(days.toLong() - 1)
        val range = TimeRangeFilter.between(startDay.atStartOfDay(zone).toInstant(), Instant.now())
        val weights = if (HealthPermission.getReadPermission(WeightRecord::class) in have) runCatching {
            c.readRecords(ReadRecordsRequest(WeightRecord::class, range, ascendingOrder = true, pageSize = 500)).records
                .groupBy { it.time.atZone(zone).toLocalDate().toString() }
                .map { (d, rs) -> d to rs.maxByOrNull { it.time }!!.weight.inKilograms }
                .sortedBy { it.first }
        }.getOrDefault(emptyList()) else emptyList()
        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in have) runCatching {
            c.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startDay.atStartOfDay(), LocalDate.now(zone).plusDays(1).atStartOfDay()), Period.ofDays(1)),
            ).associate { it.startTime.toLocalDate().toString() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }.filterValues { it > 0 }
        }.getOrDefault(emptyMap()) else emptyMap()
        val hr = if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in have) runCatching {
            c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range, ascendingOrder = false, pageSize = 1)).records.firstOrNull()
                ?.let { it.time.atZone(zone).toLocalDate().toString() to it.beatsPerMinute }
        }.getOrNull() else null
        stepsByDay.putAll(steps)
        return Reading(weights, steps, hr)
    }

}

/** Pure: which Health Connect weights to copy into weight_log (unit-tested without Health Connect). */
object WeightImport {
    /**
     * Newer than the newest logged date, on days without an entry, rounded to 0.1 kg, oldest first
     * (so the profile's weight ends on the newest).
     */
    fun weightsToImport(hc: List<Pair<String, Double>>, logged: List<String>): List<Pair<String, Double>> {
        val newest = logged.maxOrNull()
        val have = logged.toSet()
        return hc.filter { (d, kg) -> (newest == null || d > newest) && d !in have && kg in 20.0..400.0 }
            .map { (d, kg) -> d to Math.round(kg * 10) / 10.0 }.sortedBy { it.first }
    }
}
