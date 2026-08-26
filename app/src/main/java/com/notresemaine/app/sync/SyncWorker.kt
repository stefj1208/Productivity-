package com.notresemaine.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notresemaine.app.data.Repository
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        val manager = SyncManager(repo)
        if (manager.syncNow().isFailure) return Result.retry()
        // Une tâche confiée ou une demande de pause arrivées ici doivent
        // s'annoncer, même application fermée : c'est tout l'intérêt du
        // travail de fond.
        com.notresemaine.app.notif.Announcements.afterSync(applicationContext, repo)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
