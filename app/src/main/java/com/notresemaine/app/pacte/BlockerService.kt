package com.notresemaine.app.pacte

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.notresemaine.app.MainActivity
import com.notresemaine.app.data.Curfew
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Repository
import com.notresemaine.app.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pacte d'écran : surveille l'usage des applications choisies et,
 * une fois la limite quotidienne dépassée, affiche l'écran de blocage.
 * Seul le partenaire peut accorder une pause, à distance.
 */
class BlockerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { loop() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        val repo = Repository.get(applicationContext)
        val sync = SyncManager(repo)
        var socialMinutes = 0
        var lastMeasure = 0L
        var lastSync = 0L

        while (scope.isActive) {
            repo.applyPendingPacteIfDue()
            val s = repo.settings.current()
            if (!s.pacteEnabled || !Usage.hasPermission(this)) {
                stopSelf()
                return
            }
            val social = s.socialApps.split(",").filter { it.isNotBlank() }.toSet()
            val now = System.currentTimeMillis()

            if (now - lastMeasure > 60_000) {
                lastMeasure = now
                val usage = Usage.today(this, social)
                socialMinutes = usage.socialMinutes
                if (s.myUserId.isNotBlank()) {
                    repo.saveUsageDay(s.myUserId, Dates.todayIso(), usage.totalMinutes, usage.socialMinutes, usage.unlocks)
                }
            }

            val curfewOn = Curfew.isActive(s)
            val overLimit = s.dailyLimitMinutes > 0 && socialMinutes >= s.dailyLimitMinutes
            if (overLimit || curfewOn) {
                // Une pause accordée par le partenaire arrive par la synchronisation.
                if (now - lastSync > 60_000) {
                    lastSync = now
                    sync.syncNow()
                    val granted = repo.db.grace().lastGranted(s.myUserId, Dates.todayIso())
                    if (granted != null) {
                        val until = granted.updatedAt + granted.minutes * 60_000L
                        if (until > s.graceUntil) repo.settings.setGraceUntil(until)
                    }
                }
                val graceActive = repo.settings.current().graceUntil > now
                if (!graceActive) {
                    val foreground = Usage.foregroundPackage(this)
                    // Couvre-feu strict : tout est bloqué sauf le strict nécessaire.
                    val blocked = foreground != null && foreground !in ALWAYS_ALLOWED &&
                        (foreground in social || (curfewOn && s.curfewStrict))
                    if (blocked) {
                        startActivity(
                            Intent(this, BlockActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra("minutes", socialMinutes)
                                .putExtra("curfew", curfewOn)
                                .putExtra("curfewLabel", Curfew.label(s))
                        )
                    }
                }
            }
            delay(4_000)
        }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "Pacte d'écran", NotificationManager.IMPORTANCE_MIN
        ).apply { description = "Surveillance de la limite d'écran choisie à deux" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Pacte d'écran actif")
            .setContentText("Limite quotidienne surveillée")
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pacte"
        private const val NOTIF_ID = 10

        /**
         * Jamais bloqué, même en couvre-feu strict : téléphone, messages, urgences,
         * réveil, appareil photo, et l'application elle-même.
         */
        private val ALWAYS_ALLOWED = setOf(
            "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
            "com.android.server.telecom", "com.android.incallui",
            "com.android.messaging", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.hihonor.message",
            "com.android.deskclock", "com.google.android.deskclock",
            "com.sec.android.app.clockpackage", "com.hihonor.deskclock",
            "com.android.camera", "com.sec.android.app.camera", "com.hihonor.camera",
            "com.android.settings", "com.notresemaine.app"
        )

        fun startIfEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, BlockerService::class.java)
            if (enabled && Usage.hasPermission(context)) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
