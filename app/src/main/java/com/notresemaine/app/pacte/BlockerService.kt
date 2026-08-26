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
        running = true
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { loop() }
    }

    override fun onDestroy() {
        running = false
        clearBlockNotification()
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
                warnAtThresholds(repo, s, social, socialMinutes)
            }

            val curfewOn = Curfew.isActive(s)
            val overLimit = s.dailyLimitMinutes > 0 && socialMinutes >= s.dailyLimitMinutes

            // ----- À quelle cadence aller voir le serveur -----
            //
            // Une demande de pause est le seul moment où la synchronisation est
            // *urgente* des deux côtés : l'un attend derrière un écran de blocage,
            // l'autre doit répondre. Sans push (écarté avec Firebase), la seule
            // réponse honnête est de regarder plus souvent — mais seulement
            // pendant ces quelques minutes, pas toute la journée.
            val iAmWaiting = s.myUserId.isNotBlank() &&
                repo.db.grace().myPendingCount(s.myUserId, Dates.todayIso()) > 0
            val interval = when {
                iAmWaiting -> WAITING_SYNC_MS      // je suis bloqué, j'attends la réponse
                overLimit || curfewOn -> 60_000L   // je peux l'être d'un instant à l'autre
                else -> IDLE_SYNC_MS               // au cas où l'autre demanderait
            }
            if (now - lastSync > interval) {
                lastSync = now
                sync.syncNow()
                // Une demande arrivée de l'autre téléphone s'annonce tout de suite,
                // même si l'application n'a pas été ouverte depuis des heures.
                com.notresemaine.app.notif.Announcements.afterSync(applicationContext, repo)
                val granted = repo.db.grace().lastGranted(s.myUserId, Dates.todayIso())
                if (granted != null) {
                    val until = granted.updatedAt + granted.minutes * 60_000L
                    if (until > s.graceUntil) repo.settings.setGraceUntil(until)
                }
            }

            if (overLimit || curfewOn) {
                val graceActive = repo.settings.current().graceUntil > now
                if (!graceActive) {
                    val foreground = Usage.foregroundPackage(this)
                    // Un laissez-passer pris depuis l'écran de blocage : nominatif
                    // et daté. Sans cette exception, appuyer sur « WhatsApp » pour
                    // répondre à un message rouvrirait le blocage deux secondes après.
                    val current = repo.settings.current()
                    val passOk = current.allowedPackage.isNotBlank() &&
                        current.allowedUntil > now && foreground == current.allowedPackage
                    // Couvre-feu strict : tout est bloqué sauf le strict nécessaire.
                    val blocked = !passOk && foreground != null && foreground !in ALWAYS_ALLOWED &&
                        (foreground in social || (curfewOn && s.curfewStrict))
                    if (blocked) {
                        showBlock(socialMinutes, curfewOn, Curfew.label(s))
                    } else {
                        clearBlockNotification()
                    }
                } else {
                    clearBlockNotification()
                }
            } else {
                clearBlockNotification()
            }
            delay(if (overLimit || curfewOn) 2_000 else 4_000)
        }
    }

    /**
     * Prévient à un quart, à la moitié et aux trois quarts de la limite.
     *
     * Le blocage arrive trop tard pour changer quoi que ce soit : quand il tombe,
     * la journée est déjà consommée. Ces trois avertissements sont les seuls
     * moments où l'on peut encore décider de poser le téléphone.
     *
     * Chacun ne part qu'une fois par jour, et **avec le détail par application** :
     * « 22 min sur 45 » n'apprend rien, « Instagram 14, YouTube 5 » dit où
     * regarder. C'est un coup de coude — pas de son, pas d'écran réveillé : il ne
     * s'agit pas de sanctionner, seulement de rendre visible.
     */
    private suspend fun warnAtThresholds(
        repo: com.notresemaine.app.data.Repository,
        s: com.notresemaine.app.data.AppSettings,
        social: Set<String>,
        minutes: Int
    ) {
        if (s.dailyLimitMinutes <= 0) return
        val today = Dates.todayIso()
        val percent = minutes * 100 / s.dailyLimitMinutes
        // On n'annonce que le palier le plus haut atteint : franchir 25 % et 50 %
        // dans la même minute ne doit pas produire deux écrans à la suite.
        val reached = THRESHOLDS.filter { percent >= it && !s.usageAlertDone(today, it) }
        val step = reached.maxOrNull() ?: return
        // Les paliers plus bas sont marqués sans être annoncés.
        reached.forEach { repo.settings.markUsageAlert(today, it) }

        val top = Usage.topApps(this, social)
        val detail = if (top.isEmpty()) "" else
            top.joinToString(" · ") { "${it.label} ${it.minutes} min" }

        com.notresemaine.app.notif.Alarms.fire(
            context = this,
            emoji = "📵",
            title = when (step) {
                25 -> "Un quart de ton temps d'écran"
                50 -> "La moitié de ton temps d'écran"
                else -> "Trois quarts de ton temps d'écran"
            },
            text = "$minutes min sur ${s.dailyLimitMinutes}" +
                if (detail.isBlank()) "." else ".\n$detail",
            sound = false,
            nudge = true,
            actionRoute = "screentime",
            actionLabel = "Voir le détail"
        )
    }

    /**
     * Affiche l'écran de blocage — par deux chemins, parce qu'aucun des deux
     * n'est garanti seul :
     *
     * 1. Le lancement direct. Depuis Android 10, une application en arrière-plan
     *    n'a pas le droit d'ouvrir un écran : il est ignoré en silence, sauf si
     *    « Afficher par-dessus les autres applications » a été accordé. C'était
     *    la seule route de la version précédente — d'où un pacte qui ne bloquait
     *    rien alors que tout le reste fonctionnait.
     * 2. Une notification plein écran. Android la laisse passer même en
     *    arrière-plan, et si le plein écran est refusé elle s'affiche au moins
     *    en bandeau sonore par-dessus l'application en cours.
     */
    private fun showBlock(minutes: Int, curfew: Boolean, label: String) {
        if (BlockActivity.visible) return
        val intent = BlockActivity.intent(this, minutes, curfew, label)

        runCatching { startActivity(intent) }

        val pending = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(BLOCK_CHANNEL_ID, "Blocage du pacte", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "S'affiche quand la limite convenue est atteinte" }
        )
        val notif = NotificationCompat.Builder(this, BLOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(if (curfew) "🌙 Couvre-feu" else "Limite atteinte")
            .setContentText(
                if (curfew) "C'est l'heure de dormir."
                else "$minutes min sur les applications choisies aujourd'hui."
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(BLOCK_NOTIF_ID, notif) }
    }

    private fun clearBlockNotification() {
        runCatching { getSystemService(NotificationManager::class.java).cancel(BLOCK_NOTIF_ID) }
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
        private const val BLOCK_CHANNEL_ID = "pacte_blocage"
        private const val BLOCK_NOTIF_ID = 11

        /** Les moments où l'on peut encore changer d'avis. */
        private val THRESHOLDS = listOf(25, 50, 75)

        /**
         * Cadences de synchronisation du service.
         *
         * [WAITING_SYNC_MS] ne s'applique que tant qu'une demande de pause est
         * réellement en attente : quelques minutes par jour au pire. [IDLE_SYNC_MS]
         * est le rythme de veille, qui sert à recevoir la demande de l'autre sans
         * attendre l'heure du travail de fond. Le reste du temps, le service ne
         * touche pas au réseau.
         */
        private const val WAITING_SYNC_MS = 15_000L
        private const val IDLE_SYNC_MS = 5 * 60_000L

        /** Vrai tant que la surveillance tourne : affiché dans l'écran du Pacte. */
        @Volatile
        var running: Boolean = false
            private set

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
