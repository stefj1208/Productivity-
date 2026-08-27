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

            val exerciseMinutes = mergedMinutes(
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
                    )
                ).records.map { it.startTime to it.endTime },
                dayStart, dayEnd
            )

            val nightStart = LocalDateTime.of(date.minusDays(1), LocalTime.of(18, 0))
            val nightEnd = LocalDateTime.of(date, LocalTime.NOON)
            val sleepMinutes = mergedMinutes(
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(nightStart, nightEnd)
                    )
                ).records.map { it.startTime to it.endTime },
                nightStart, nightEnd
            )

            HealthDay(sleepMinutes, steps, exerciseMinutes)
        }.getOrNull()
    }

    /**
     * Le temps réellement couvert par une liste de périodes — et non leur somme.
     *
     * C'est la correction d'un vrai faux chiffre. Health Connect est un carrefour :
     * la montre y écrit la nuit, le téléphone aussi, et parfois une troisième
     * application par-dessus. Additionner leurs durées comptait donc la même nuit
     * deux ou trois fois — d'où un « 16 h 45 par nuit » qui n'était que 8 h 22
     * enregistrées en double.
     *
     * Deux règles, dans cet ordre :
     *
     *  1. **Découper.** Une période est ramenée à sa seule partie comprise dans la
     *     fenêtre demandée. Une sieste commencée à 17 h et finie à 19 h ne compte
     *     qu'à partir de 18 h dans la nuit qui suit.
     *  2. **Fusionner.** Deux périodes qui se chevauchent n'en font qu'une. On ne
     *     dort pas deux fois entre 23 h et 7 h, quel que soit le nombre
     *     d'applications qui l'ont noté.
     */
    private fun mergedMinutes(
        periods: List<Pair<java.time.Instant, java.time.Instant>>,
        from: LocalDateTime,
        to: LocalDateTime
    ): Int {
        val zone = java.time.ZoneId.systemDefault()
        val windowStart = from.atZone(zone).toInstant()
        val windowEnd = to.atZone(zone).toInstant()

        val clipped = periods
            .map { (start, end) ->
                maxOf(start, windowStart) to minOf(end, windowEnd)
            }
            .filter { (start, end) -> end.isAfter(start) }
            .sortedBy { it.first }
        if (clipped.isEmpty()) return 0

        var total = 0L
        var blockStart = clipped.first().first
        var blockEnd = clipped.first().second
        clipped.drop(1).forEach { (start, end) ->
            if (start.isAfter(blockEnd)) {
                // Un vrai trou : le bloc précédent est clos, on en ouvre un autre.
                total += Duration.between(blockStart, blockEnd).toMinutes()
                blockStart = start
                blockEnd = end
            } else if (end.isAfter(blockEnd)) {
                // Chevauchement : on étire le bloc au lieu d'ajouter une durée.
                blockEnd = end
            }
        }
        total += Duration.between(blockStart, blockEnd).toMinutes()
        return total.toInt()
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
