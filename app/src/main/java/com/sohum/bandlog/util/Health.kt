package com.sohum.bandlog.util

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect bridge. Google Fit and Samsung Health both write here, so one integration
 * covers both: read today's steps + active calories, write band sessions back as workouts.
 */
object Health {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    data class Today(val steps: Long, val activeKcal: Double)

    fun available(context: Context): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(context: Context) = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(context: Context): Boolean =
        runCatching { client(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS) }.getOrDefault(false)

    suspend fun today(context: Context): Today? = runCatching {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(start, Instant.now())
        val c = client(context)
        val steps = c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] ?: 0L
        val kcal = c.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        Today(steps, kcal)
    }.getOrNull()

    /** Push a saved band session so it shows up in Samsung Health / Fit. Ends now, starts [minutes] ago. */
    suspend fun writeSession(context: Context, title: String, minutes: Int, date: String) = runCatching {
        val zone = ZoneId.systemDefault()
        val isToday = date == LocalDate.now().toString()
        val end = if (isToday) Instant.now() else LocalDate.parse(date).atTime(19, 0).atZone(zone).toInstant()
        val start = end.minusSeconds(minutes.coerceAtLeast(5) * 60L)
        client(context).insertRecords(
            listOf(
                ExerciseSessionRecord(
                    startTime = start, startZoneOffset = zone.rules.getOffset(start),
                    endTime = end, endZoneOffset = zone.rules.getOffset(end),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = title,
                    metadata = Metadata(),
                ),
            ),
        )
    }.isSuccess
}
