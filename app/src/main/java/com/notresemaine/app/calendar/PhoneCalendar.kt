package com.notresemaine.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Agenda du téléphone.
 *
 * Pourquoi pas l'API Google Agenda directement : elle exige un projet Google
 * Cloud, un écran de consentement OAuth et l'empreinte de signature de
 * l'application — impossible à tenir avec un APK reconstruit à chaque fois.
 *
 * L'agenda du téléphone donne le même résultat sans rien de tout cela : le
 * compte Google est déjà synchronisé dessus par Android. Ce qu'on y écrit
 * remonte dans Google Agenda, et ce qui est dans Google Agenda descend ici.
 */
object PhoneCalendar {

    /** Marque nos événements pour pouvoir les remplacer sans toucher au reste. */
    private const val MARKER = "· Notre Semaine ·"

    data class Cal(val id: Long, val name: String, val account: String)

    data class Slot(val title: String, val startMinutes: Int, val durationMinutes: Int)

    data class Booked(
        val title: String,
        val startMinutes: Int,   // minutes depuis minuit
        val endMinutes: Int,
        val allDay: Boolean,
        val ours: Boolean
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    /** Agendas dans lesquels on a le droit d'écrire (donc « Google » et pas « jours fériés »). */
    fun writableCalendars(context: Context): List<Cal> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val out = mutableListOf<Cal>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val access = c.getInt(3)
                    if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    out += Cal(c.getLong(0), c.getString(1) ?: "Agenda", c.getString(2) ?: "")
                }
            }
        }
        return out
    }

    private fun dayBounds(date: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** Ce qui est déjà pris dans la journée, tous agendas confondus. */
    fun bookedOn(context: Context, date: LocalDate): List<Booked> {
        if (!hasPermission(context)) return emptyList()
        val (start, end) = dayBounds(date)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, start)
        ContentUris.appendId(uri, end)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DESCRIPTION
        )
        val out = mutableListOf<Booked>()
        runCatching {
            context.contentResolver.query(uri.build(), projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { c ->
                    while (c.moveToNext()) {
                        val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(sans titre)"
                        val allDay = c.getInt(3) == 1
                        val ours = c.getString(4)?.contains(MARKER) == true
                        out += Booked(
                            title = title,
                            startMinutes = minutesOfDay(c.getLong(1), start),
                            endMinutes = minutesOfDay(c.getLong(2), start),
                            allDay = allDay,
                            ours = ours
                        )
                    }
                }
        }
        return out
    }

    private fun minutesOfDay(instant: Long, dayStart: Long): Int =
        (((instant - dayStart) / 60_000L).toInt()).coerceIn(0, 24 * 60)

    /**
     * Réécrit nos créneaux de la journée dans l'agenda choisi : on efface les
     * nôtres puis on repose les actuels. Aucun autre événement n'est touché —
     * on ne supprime que ce qui porte notre marque.
     */
    fun writeDay(context: Context, calendarId: Long, date: LocalDate, slots: List<Slot>): Int {
        if (!hasPermission(context) || calendarId <= 0) return 0
        val (start, end) = dayBounds(date)
        val resolver = context.contentResolver
        runCatching {
            resolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                    "${CalendarContract.Events.DESCRIPTION} LIKE ? AND " +
                    "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?",
                arrayOf(calendarId.toString(), "%$MARKER%", start.toString(), end.toString())
            )
        }
        var written = 0
        slots.forEach { slot ->
            val from = start + slot.startMinutes * 60_000L
            val to = from + (if (slot.durationMinutes > 0) slot.durationMinutes else 30) * 60_000L
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, slot.title)
                put(CalendarContract.Events.DESCRIPTION, MARKER)
                put(CalendarContract.Events.DTSTART, from)
                put(CalendarContract.Events.DTEND, to)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            runCatching {
                if (resolver.insert(CalendarContract.Events.CONTENT_URI, values) != null) written++
            }
        }
        return written
    }

    /** Retire tout ce que l'application a écrit sur la période — pour se désengager proprement. */
    fun eraseOurs(context: Context, calendarId: Long) {
        if (!hasPermission(context) || calendarId <= 0) return
        runCatching {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
                arrayOf(calendarId.toString(), "%$MARKER%")
            )
        }
    }
}
