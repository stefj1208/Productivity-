package com.notresemaine.app

import android.app.Application
import com.notresemaine.app.notif.Reminders
import com.notresemaine.app.sync.SyncWorker

class PlannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.rescheduleAsync(this)
        SyncWorker.schedule(this)
    }
}
