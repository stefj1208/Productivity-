package com.notresemaine.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class HealthDay(
    val sleepMinutes: Int,
    val steps: Int,
    val exerciseMinutes: Int
)

/**
 * Lecture de Health Connect (API officielle Android).
 *
 * Honnêteté : Health Connect ne contient QUE ce qu'une autre source y écrit
 * (montre, bracelet, Samsung Health, appli de sport). Sans source, tout vaut zéro —
 * d'où la saisie manuelle de secours dans l'application.
 */
object Health {

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    /** SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED / SDK_AVAILABLE */
    fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(context: Context): Boolean =
        status(context) == HealthConnectClient.SDK_AVAILABLE

    fun client(context: Context): HealthConnectClient? =
        if (isAvailable(context)) HealthConnectClient.getOrCreate(context) else null

    suspend fun hasPermissions(context: Context): Boolean {
        val client = client(context) ?: return false
        return runCatching {
            client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
        }.getOrDefault(false)
    }

    /**
     * Données de [date] : pas et sport sur la journée,
     * sommeil sur la nuit qui la précède (18 h la veille → 12 h le jour même).
     */
    suspend fun readDay(context: Context, date: LocalDate): HealthDay? {
        val client = client(context) ?: return null
        return runCatching {
            val dayStart = date.atStartOfDay()
            val dayEnd = date.plusDays(1).atStartOfDay()

            val steps = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
                )
            )[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0

            val exerciseMinutes = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
                )
            ).records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()

            val nightStart = LocalDateTime.of(date.minusDays(1), LocalTime.of(18, 0))
            val nightEnd = LocalDateTime.of(date, LocalTime.NOON)
            val sleepMinutes = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(nightStart, nightEnd)
                )
            ).records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()

            HealthDay(sleepMinutes, steps, exerciseMinutes)
        }.getOrNull()
    }

    /**
     * Dernière pesée du jour, si une balance connectée l'a écrite dans
     * Health Connect. Sinon null : on garde la saisie à la main.
     */
    suspend fun readWeight(context: Context, date: LocalDate): Double? {
        val client = client(context) ?: return null
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay()
                    )
                )
            ).records.maxByOrNull { it.time }?.weight?.inKilograms
        }.getOrNull()
    }
}
