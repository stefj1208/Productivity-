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

    private const val FIRST_REQUEST_CODE = 1000
    private const val MAX_SCHEDULED = 8
    private const val NOTIFICATION_ID = 4242

    /** Un rappel à venir : quand, et quoi dire. */
    data class Alert(
        val at: LocalDateTime,
        val emoji: String,
        val title: String,
        val text: String
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

        val alerts = upcoming(settings, goals.map { it.title to (it.preferredTime to it.preferredDays) }, hasRitual)
        alerts.take(MAX_SCHEDULED).forEachIndexed { index, alert ->
            scheduleOne(context, FIRST_REQUEST_CODE + index, alert, settings.alertSound)
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
        now: LocalDateTime = LocalDateTime.now()
    ): List<Alert> {
        val alerts = mutableListOf<Alert>()

        fun add(day: LocalDate, time: LocalTime?, emoji: String, title: String, text: String) {
            if (time == null) return
            val at = LocalDateTime.of(day, time)
            if (at.isAfter(now) && at.isBefore(now.plusHours(24))) {
                alerts += Alert(at, emoji, title, text)
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
            // setAlarmClock traverse le mode économie d'énergie : un rappel en retard
            // d'une heure ne sert à rien.
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pending)
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

    /** Affiche le rappel : plein écran si Android l'autorise, sinon en bandeau. */
    fun fire(context: Context, emoji: String, title: String, text: String, sound: Boolean) {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val full = Intent(context, AlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_EMOJI, emoji)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_SOUND, sound)
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
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
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
            sound = intent.getBooleanExtra(Alarms.EXTRA_SOUND, true)
        )
        // Un rappel vient de partir : on recalcule la suite des 24 heures.
        Alarms.rescheduleAsync(context)
    }
}
