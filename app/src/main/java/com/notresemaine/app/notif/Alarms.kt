package com.notresemaine.app.notif

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Repository
import com.notresemaine.app.ui.AlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Les rappels sonores : une alarme par action à faire, pas seulement une notification
 * qu'on balaie sans la lire.
 *
 * Chaque rappel s'affiche en plein écran, même téléphone verrouillé, avec le son
 * d'alarme du système — et deux boutons seulement : c'est fait, ou dans 10 minutes.
 *
 * Le planificateur ne pose jamais plus que les rappels des 24 prochaines heures :
 * à chaque déclenchement, il recalcule la suite. Ainsi un objectif ajouté ou un
 * couvre-feu déplacé est pris en compte sans rien avoir à relancer.
 */
object Alarms {

    const val CHANNEL_ID = "alertes"
    const val EXTRA_EMOJI = "emoji"
    const val EXTRA_TITLE = "titre"
    const val EXTRA_TEXT = "texte"
    const val EXTRA_SOUND = "son"
    /** Un « coup de coude » : plein écran aussi, mais sans son ni réveil d'écran. */
    const val EXTRA_NUDGE = "nudge"
    /** Où mène le bouton principal de l'écran de rappel, et comment il s'appelle. */
    const val EXTRA_ROUTE = "route"
    const val EXTRA_ACTION = "action"
    /**
     * Une demande de pause à trancher depuis l'écran de rappel lui-même.
     * Sa présence transforme les deux boutons en « Accorder » et « Refuser ».
     */
    const val EXTRA_GRACE_ID = "graceId"
    const val EXTRA_GRACE_MINUTES = "graceMinutes"

    private const val FIRST_REQUEST_CODE = 1000
    // Trois rappels de repas se sont ajoutés aux séances, tâches et habitudes :
    // à 16, les derniers moments de la journée passaient à la trappe.
    private const val MAX_SCHEDULED = 24
    private const val NOTIFICATION_ID = 4242

    /** Un rappel à venir : quand, et quoi dire. */
    data class Alert(
        val at: LocalDateTime,
        val emoji: String,
        val title: String,
        val text: String,
        /**
         * Une habitude n'est pas un rendez-vous : elle s'affiche par-dessus tout,
         * mais sans sonner ni allumer l'écran. Réveiller quelqu'un pour lui dire
         * « une seule chose à la fois » serait absurde.
         */
        val nudge: Boolean = false,
        /** Où mène le bouton de l'écran de rappel, et comment il s'appelle. */
        val route: String = "",
        val action: String = ""
    )

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Rappels sonores", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarme plein écran au moment d'agir"
            setBypassDnd(false)
            // Le son est joué par l'écran d'alarme lui-même, pas par la notification.
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun rescheduleAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { rescheduleAll(appContext) }
    }

    /** Recalcule et repose les rappels des 24 prochaines heures. */
    suspend fun rescheduleAll(context: Context) {
        val repo = Repository.get(context)
        val settings = repo.settings.current()
        cancelAll(context)

        if (!settings.alertsEnabled) {
            // Sans rappels sonores, on retombe sur les deux notifications classiques.
            Reminders.reschedule(context, settings)
            return
        }
        // Les alarmes couvrent déjà le soir et le dimanche : on évite le doublon.
        Reminders.reschedule(
            context,
            settings.copy(eveningEnabled = false, sundayEnabled = false)
        )

        val goals = if (settings.myUserId.isBlank()) emptyList()
        else repo.db.goals().activeOnce(settings.myUserId)
        val hasRitual = settings.myUserId.isNotBlank() &&
            repo.db.ritual().stepsOnce(settings.myUserId).any { it.enabled }

        // Les tâches à créneau : un rendez-vous qu'on s'est fixé mérite le même
        // rappel qu'une séance d'objectif.
        val slots = if (settings.myUserId.isBlank()) emptyList()
        else listOf(Dates.todayIso(), Dates.tomorrowIso()).flatMap { date ->
            repo.db.tasks().byDateOnce(settings.myUserId, date)
                .filter { it.startTime.isNotBlank() && !it.done }
                .map { Triple(date, it.startTime, it.title) }
        }

        // Les habitudes : leurs moments sont tirés au sort mais stables pour la
        // journée, donc programmables comme n'importe quel autre rappel.
        val habitMoments = if (settings.myUserId.isBlank()) emptyList()
        else repo.db.habits().activeOnce(settings.myUserId).flatMap { habit ->
            listOf(LocalDate.now(), LocalDate.now().plusDays(1)).flatMap { day ->
                com.notresemaine.app.data.Habits
                    .momentsOf(habit.id, day, habit.fromHour, habit.toHour, habit.perDay)
                    .map { minutes ->
                        Alert(
                            at = LocalDateTime.of(day, LocalTime.of(minutes / 60, minutes % 60)),
                            emoji = "🔁",
                            title = habit.title,
                            text = habit.source.ifBlank { "Une habitude que tu as choisie." },
                            nudge = true
                        )
                    }
            }
        }

        val alerts = upcoming(
            settings,
            goals.map { it.title to (it.preferredTime to it.preferredDays) },
            hasRitual,
            taskSlots = slots
        )
        val now = LocalDateTime.now()
        val all = (alerts + habitMoments.filter { it.at.isAfter(now) && it.at.isBefore(now.plusHours(24)) })
            .sortedBy { it.at }
        all.take(MAX_SCHEDULED).forEachIndexed { index, alert ->
            scheduleOne(context, FIRST_REQUEST_CODE + index, alert, settings.alertSound && !alert.nudge)
        }
    }

    /**
     * Tous les moments d'action des 24 prochaines heures, dans l'ordre.
     * [goals] : intitulé → (moment de la journée, jours préférés « 1,3,5 »).
     */
    fun upcoming(
        settings: AppSettings,
        goals: List<Pair<String, Pair<String, String>>>,
        hasRitual: Boolean,
        taskSlots: List<Triple<String, String, String>> = emptyList(), // date, HH:MM, intitulé
        now: LocalDateTime = LocalDateTime.now()
    ): List<Alert> {
        val alerts = mutableListOf<Alert>()

        fun add(
            day: LocalDate,
            time: LocalTime?,
            emoji: String,
            title: String,
            text: String,
            route: String = "",
            action: String = "",
            nudge: Boolean = false
        ) {
            if (time == null) return
            val at = LocalDateTime.of(day, time)
            if (at.isAfter(now) && at.isBefore(now.plusHours(24))) {
                alerts += Alert(at, emoji, title, text, nudge, route, action)
            }
        }

        // Aujourd'hui et demain : la fenêtre de 24 h peut être à cheval sur minuit.
        listOf(now.toLocalDate(), now.toLocalDate().plusDays(1)).forEach { day ->
            if (hasRitual) {
                add(
                    day, parseTime(settings.wakeAlarm), "🌅", "Ton rituel du matin",
                    "La première heure donne le ton de la journée."
                )
            }
            add(
                day, parseTime(settings.eveningReminder), "🌙", "Préparer demain",
                "2 minutes : la priorité de demain, et c'est tout."
            )
            if (day.dayOfWeek == DayOfWeek.SUNDAY) {
                add(
                    day, parseTime(settings.sundayReminder), "🗓️", "Revue de la semaine",
                    "10 minutes à deux pour préparer la semaine."
                )
            }
            // Les repas : on note ce qu'on a mangé pendant qu'on s'en souvient.
            // Une heure plus tard, la moitié de l'assiette a déjà disparu de la
            // mémoire — d'où trois rendez-vous fixes plutôt qu'un rappel du soir.
            if (settings.mealRemindersEnabled) {
                add(
                    day, parseTime(settings.mealReminderMorning), "🥐",
                    "Petit-déjeuner", "Notez ce que vous avez mangé, ou dites que vous avez jeûné.",
                    route = "meallog", action = "Noter mon repas", nudge = true
                )
                add(
                    day, parseTime(settings.mealReminderNoon), "🍽️",
                    "Déjeuner", "Une photo suffit, ou cochez le menu prévu.",
                    route = "meallog", action = "Noter mon repas", nudge = true
                )
                add(
                    day, parseTime(settings.mealReminderEvening), "🌙",
                    "Dîner", "Dernier repas de la journée : notez-le avant d'oublier.",
                    route = "meallog", action = "Noter mon repas", nudge = true
                )
            }
            if (settings.curfewEnabled) {
                // Un quart d'heure avant, pour finir ce qu'on est en train de faire.
                add(
                    day, parseTime(settings.curfewStart)?.minusMinutes(15), "📵",
                    "Couvre-feu dans 15 minutes",
                    "On termine, on pose le téléphone, on dort mieux."
                )
            }
            goals.forEach { (title, timing) ->
                val (preferredTime, preferredDays) = timing
                val dayNumbers = preferredDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                if (day.dayOfWeek.value in dayNumbers) {
                    add(
                        day, sessionTime(preferredTime), "🎯", title,
                        "C'est le moment de ta séance."
                    )
                }
            }
        }
        // Les tâches portent leur propre date : on les ajoute telles quelles.
        taskSlots.forEach { (date, time, title) ->
            val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@forEach
            add(day, parseTime(time), "✅", title, "C'est le créneau que tu t'es réservé.")
        }
        return alerts.sortedBy { it.at }
    }

    /** Heure par défaut d'une séance selon le moment choisi pour l'objectif. */
    private fun sessionTime(preferredTime: String): LocalTime = when (preferredTime) {
        "matin" -> LocalTime.of(7, 30)
        "midi" -> LocalTime.of(12, 30)
        else -> LocalTime.of(18, 30)
    }

    private fun parseTime(text: String): LocalTime? {
        if (!Dates.isValidTime(text)) return null
        val parts = text.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    private fun scheduleOne(context: Context, requestCode: Int, alert: Alert, sound: Boolean) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_EMOJI, alert.emoji)
            .putExtra(EXTRA_TITLE, alert.title)
            .putExtra(EXTRA_TEXT, alert.text)
            .putExtra(EXTRA_SOUND, sound)
            .putExtra(EXTRA_NUDGE, alert.nudge)
            .putExtra(EXTRA_ROUTE, alert.route)
            .putExtra(EXTRA_ACTION, alert.action)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = alert.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(
            context, requestCode, Intent(context, AlertActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            if (alert.nudge) {
                // setAlarmClock afficherait l'icône de réveil dans la barre d'état
                // en permanence : disproportionné pour une habitude.
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // setAlarmClock traverse le mode économie d'énergie : un rappel en retard
                // d'une heure ne sert à rien.
                alarm.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pending)
            }
        }.onFailure {
            alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancelAll(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        repeat(MAX_SCHEDULED) { index ->
            val pending = PendingIntent.getBroadcast(
                context, FIRST_REQUEST_CODE + index,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarm.cancel(pending)
        }
    }

    /** « Dans 10 minutes » : on repose exactement le même rappel, plus tard. */
    fun snooze(context: Context, minutes: Long, emoji: String, title: String, text: String, sound: Boolean) {
        scheduleOne(
            context,
            FIRST_REQUEST_CODE + MAX_SCHEDULED,
            Alert(LocalDateTime.now().plusMinutes(minutes), emoji, title, text),
            sound
        )
    }

    /**
     * Affiche le rappel, par les deux mêmes chemins que l'écran de blocage du Pacte :
     *
     * 1. le lancement direct, qu'Android n'autorise en arrière-plan que si
     *    « Afficher par-dessus les autres applications » a été accordé ;
     * 2. la notification plein écran, qui passe toujours — au pire en bandeau.
     *
     * Les deux ensemble, parce qu'aucun des deux n'est garanti seul : c'est ce qui
     * fait qu'un rappel s'affiche vraiment, y compris par-dessus une autre
     * application ou l'écran verrouillé.
     */
    fun fire(
        context: Context,
        emoji: String,
        title: String,
        text: String,
        sound: Boolean,
        nudge: Boolean = false,
        actionRoute: String = "",
        actionLabel: String = "",
        graceId: String = "",
        graceMinutes: Int = 0
    ) {
        val full = Intent(context, AlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_EMOJI, emoji)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_SOUND, sound)
            .putExtra(EXTRA_NUDGE, nudge)
            .putExtra(EXTRA_ROUTE, actionRoute)
            .putExtra(EXTRA_ACTION, actionLabel)
            .putExtra(EXTRA_GRACE_ID, graceId)
            .putExtra(EXTRA_GRACE_MINUTES, graceMinutes)

        if (android.provider.Settings.canDrawOverlays(context)) {
            runCatching { context.startActivity(full) }
        }

        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val pending = PendingIntent.getActivity(
            context, 0, full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$emoji $title")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .build()
        // Une demande de pause ne doit pas écraser un rappel en cours, ni
        // l'inverse : deux messages différents, deux emplacements.
        val id = if (graceId.isNotBlank()) NOTIFICATION_ID + 1 else NOTIFICATION_ID
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun dismissNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.fire(
            context,
            emoji = intent.getStringExtra(Alarms.EXTRA_EMOJI) ?: "⏰",
            title = intent.getStringExtra(Alarms.EXTRA_TITLE) ?: "Rappel",
            text = intent.getStringExtra(Alarms.EXTRA_TEXT).orEmpty(),
            sound = intent.getBooleanExtra(Alarms.EXTRA_SOUND, true),
            nudge = intent.getBooleanExtra(Alarms.EXTRA_NUDGE, false),
            actionRoute = intent.getStringExtra(Alarms.EXTRA_ROUTE).orEmpty(),
            actionLabel = intent.getStringExtra(Alarms.EXTRA_ACTION).orEmpty()
        )
        // Un rappel vient de partir : on recalcule la suite des 24 heures.
        Alarms.rescheduleAsync(context)
    }
}
