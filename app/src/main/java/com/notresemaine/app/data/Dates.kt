package com.notresemaine.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Dates {
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val FR = Locale.FRENCH

    fun today(): LocalDate = LocalDate.now()
    fun todayIso(): String = today().format(ISO)
    fun tomorrowIso(): String = today().plusDays(1).format(ISO)

    /** Lundi de la semaine contenant [date]. */
    fun weekStart(date: LocalDate = today()): LocalDate =
        date.with(DayOfWeek.MONDAY)

    fun weekStartIso(date: LocalDate = today()): String = weekStart(date).format(ISO)

    fun previousWeekStartIso(): String = weekStart().minusWeeks(1).format(ISO)

    /** "mercredi 6 août" */
    fun longLabel(iso: String): String {
        val d = LocalDate.parse(iso)
        val day = d.dayOfWeek.getDisplayName(TextStyle.FULL, FR)
        val month = d.month.getDisplayName(TextStyle.FULL, FR)
        return "$day ${d.dayOfMonth} $month"
    }

    /** "lun. 4" */
    fun shortLabel(iso: String): String {
        val d = LocalDate.parse(iso)
        val day = d.dayOfWeek.getDisplayName(TextStyle.SHORT, FR)
        return "$day ${d.dayOfMonth}"
    }

    /** Initiale du jour pour les puces : L M M J V S D */
    fun dayInitial(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "L"
        DayOfWeek.TUESDAY -> "M"
        DayOfWeek.WEDNESDAY -> "M"
        DayOfWeek.THURSDAY -> "J"
        DayOfWeek.FRIDAY -> "V"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "D"
    }

    fun daysOfWeek(weekStartIso: String): List<String> {
        val start = LocalDate.parse(weekStartIso)
        return (0..6L).map { start.plusDays(it).format(ISO) }
    }

    fun isValidTime(text: String): Boolean =
        Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(text)
}
