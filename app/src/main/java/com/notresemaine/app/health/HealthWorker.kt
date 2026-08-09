package com.notresemaine.app.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Repository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Relève quotidienne du sommeil, des pas et du sport depuis Health Connect. */
class HealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        collect(applicationContext)
        return Result.success()
    }

    companion object {
        /** Relève aujourd'hui et les 2 jours précédents (une source peut écrire en retard). */
        suspend fun collect(context: Context) {
            if (!Health.hasPermissions(context)) return
            val repo = Repository.get(context)
            val userId = repo.settings.current().myUserId
            if (userId.isBlank()) return
            (0L..2L).forEach { back ->
                val date = LocalDate.now().minusDays(back)
                val day = Health.readDay(context, date) ?: return@forEach
                if (day.sleepMinutes == 0 && day.steps == 0 && day.exerciseMinutes == 0) return@forEach
                repo.saveHealthDay(
                    userId = userId,
                    date = date.format(Dates.ISO),
                    sleepMinutes = day.sleepMinutes,
                    steps = day.steps,
                    exerciseMinutes = day.exerciseMinutes,
                    source = "health_connect"
                )
            }
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "health", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
