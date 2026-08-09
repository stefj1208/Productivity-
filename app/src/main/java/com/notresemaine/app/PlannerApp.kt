package com.notresemaine.app

import android.app.Application
import com.notresemaine.app.data.Repository
import com.notresemaine.app.notif.Reminders
import com.notresemaine.app.pacte.BlockerService
import com.notresemaine.app.pacte.UsageWorker
import com.notresemaine.app.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.rescheduleAsync(this)
        SyncWorker.schedule(this)
        UsageWorker.schedule(this)
        com.notresemaine.app.health.HealthWorker.schedule(this)
        CoroutineScope(Dispatchers.Default).launch {
            val s = Repository.get(this@PlannerApp).settings.current()
            BlockerService.startIfEnabled(this@PlannerApp, s.pacteEnabled)
        }
    }
}
