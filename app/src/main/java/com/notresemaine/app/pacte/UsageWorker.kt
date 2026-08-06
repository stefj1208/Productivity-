package com.notresemaine.app.pacte

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Repository
import java.util.concurrent.TimeUnit

/**
 * Relève le temps d'écran plusieurs fois par jour et stocke l'historique
 * dans l'application (Android ne le conserve pas longtemps).
 */
class UsageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        collect(applicationContext)
        return Result.success()
    }

    companion object {
        suspend fun collect(context: Context) {
            if (!Usage.hasPermission(context)) return
            val repo = Repository.get(context)
            val s = repo.settings.current()
            if (s.myUserId.isBlank()) return
            val social = s.socialApps.split(",").filter { it.isNotBlank() }.toSet()
            val usage = Usage.today(context, social)
            repo.saveUsageDay(s.myUserId, Dates.todayIso(), usage.totalMinutes, usage.socialMinutes, usage.unlocks)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageWorker>(4, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "usage", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
