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
import com.notresemaine.app.MainActivity
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Reminders {

    const val CHANNEL_ID = "rappels"
    private const val TYPE_EVENING = "evening"
    private const val TYPE_SUNDAY = "sunday"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Rappels", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Rappel du soir et revue du dimanche" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** (Re)programme les deux rappels quotidiens selon les réglages. */
    fun reschedule(context: Context, settings: AppSettings) {
        scheduleDaily(context, TYPE_EVENING, settings.eveningReminder, settings.eveningEnabled, 1)
        scheduleDaily(context, TYPE_SUNDAY, settings.sundayReminder, settings.sundayEnabled, 2)
    }

    fun rescheduleAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            reschedule(appContext, Repository.get(appContext).settings.current())
        }
    }

    private fun scheduleDaily(context: Context, type: String, time: String, enabled: Boolean, requestCode: Int) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("type", type)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!enabled) {
            alarm.cancel(pending)
            return
        }
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 21
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        var next = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        if (next.isBefore(LocalDateTime.now())) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAt, AlarmManager.INTERVAL_DAY, pending)
    }

    fun show(context: Context, type: String) {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Le rappel « dimanche » est programmé tous les jours mais ne s'affiche que le dimanche.
        if (type == TYPE_SUNDAY && LocalDate.now().dayOfWeek != DayOfWeek.SUNDAY) return

        val (title, text) = if (type == TYPE_SUNDAY) {
            "Revue du dimanche" to "10 minutes pour préparer la semaine, à deux."
        } else {
            "Préparer demain" to "2 minutes : la priorité de demain, et c'est tout."
        }
        val tap = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(if (type == TYPE_SUNDAY) 2 else 1, notif)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.show(context, intent.getStringExtra("type") ?: return)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Alarms.rescheduleAsync(context)
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.Default).launch {
                val s = Repository.get(appContext).settings.current()
                com.notresemaine.app.pacte.BlockerService.startIfEnabled(appContext, s.pacteEnabled)
            }
        }
    }
}
