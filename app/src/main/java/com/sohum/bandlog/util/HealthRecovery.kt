package com.sohum.bandlog.util

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate

/**
 * v2.18 B8 recovery inputs from Health Connect: last night's sleep (sessions ending today) and the
 * resting heart rate, today's plus a 14-day baseline. Its own permission set (asked from the coach
 * card), separate from [Health]'s steps / calories so the two never block each other. Anything
 * missing just comes back null and the check-in covers it.
 */
object HealthRecovery {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    data class Night(val sleepMin: Int?, val restingHr: Int?, val baselineHr: Int?)

    fun available(context: Context): Boolean = runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    suspend fun granted(context: Context): Boolean =
        runCatching { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().any { it in PERMISSIONS } }.getOrDefault(false)

    suspend fun read(context: Context): Night? = runCatching {
        val c = HealthConnectClient.getOrCreate(context)
        val today = LocalDate.now(Dates.ZONE)
        val dayStart = today.atStartOfDay(Dates.ZONE).toInstant()
        val sleep = runCatching {
            // Sessions that ended between 6 pm yesterday and now: "last night".
            val from = dayStart.minus(Duration.ofHours(6))
            val recs = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from, java.time.Instant.now()))).records
            recs.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt().takeIf { it > 60 }
        }.getOrNull()
        val hr = runCatching {
            val recs = c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(dayStart.minus(Duration.ofDays(15)), java.time.Instant.now()))).records
            val byDay = recs.groupBy { it.time.atZone(Dates.ZONE).toLocalDate() }.mapValues { (_, v) -> v.map { it.beatsPerMinute.toInt() }.average().toInt() }
            val todayHr = byDay[today] ?: byDay[today.minusDays(1)]
            todayHr to DailyCheckin.baseline(byDay.filterKeys { it < today }.values.toList())
        }.getOrNull()
        Night(sleep, hr?.first, hr?.second)
    }.getOrNull()
}
