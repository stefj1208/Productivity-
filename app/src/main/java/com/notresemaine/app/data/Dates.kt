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

    /** Date d'il y a [days] jours, pour calculer des moyennes récentes. */
    fun daysAgoIso(days: Long): String = today().minusDays(days).format(ISO)

    /** Lundi de la semaine contenant [date]. */
    fun weekStart(date: LocalDate = today()): LocalDate =
        date.with(DayOfWeek.MONDAY)

    fun weekStartIso(date: LocalDate = today()): String = weekStart(date).format(ISO)

    fun previousWeekStartIso(): String = weekStart().minusWeeks(1).format(ISO)

    /** Lundi de la semaine décalée de [offset] semaines (négatif = passé). */
    fun weekStartIsoOffset(offset: Int): String =
        weekStart().plusWeeks(offset.toLong()).format(ISO)

    /** Lundi de la semaine précédant [weekStartIso]. */
    fun weekBefore(weekStartIso: String): String =
        LocalDate.parse(weekStartIso).minusWeeks(1).format(ISO)

    /** Nombre de semaines entre la semaine courante et [weekStartIso]. */
    fun weekOffsetOf(weekStartIso: String): Int =
        java.time.temporal.ChronoUnit.WEEKS.between(weekStart(), LocalDate.parse(weekStartIso)).toInt()

    /** "4 – 10 août" */
    fun weekRangeLabel(weekStartIso: String): String {
        val start = LocalDate.parse(weekStartIso)
        val end = start.plusDays(6)
        val endMonth = end.month.getDisplayName(TextStyle.FULL, FR)
        return if (start.month == end.month) {
            "${start.dayOfMonth} – ${end.dayOfMonth} $endMonth"
        } else {
            val startMonth = start.month.getDisplayName(TextStyle.FULL, FR)
            "${start.dayOfMonth} $startMonth – ${end.dayOfMonth} $endMonth"
        }
    }

    /** "Cette semaine", "Semaine prochaine", "Il y a 2 semaines"… */
    fun weekRelativeLabel(weekStartIso: String): String = when (val o = weekOffsetOf(weekStartIso)) {
        0 -> "Cette semaine"
        1 -> "Semaine prochaine"
        -1 -> "Semaine dernière"
        else -> if (o > 0) "Dans $o semaines" else "Il y a ${-o} semaines"
    }

    /** Le dimanche approche : à partir du samedi, on prépare la semaine suivante. */
    fun planningTargetWeekIso(): String =
        if (today().dayOfWeek == DayOfWeek.SATURDAY || today().dayOfWeek == DayOfWeek.SUNDAY) {
            weekStartIsoOffset(1)
        } else {
            weekStartIsoOffset(0)
        }

    /**
     * "mercredi 6 août".
     *
     * Ne lève jamais : une partie des dates vient de champs libres, et une date
     * mal tapée doit s'afficher telle quelle, pas faire tomber tout l'écran.
     * (C'est exactement ce qui arrivait à l'onglet Enfants : une échéance
     * saisie autrement qu'en AAAA-MM-JJ plantait l'application à chaque
     * ouverture, tant que la ligne existait.)
     */
    fun longLabel(iso: String): String {
        val d = parseOrNull(iso) ?: return iso
        val day = d.dayOfWeek.getDisplayName(TextStyle.FULL, FR)
        val month = d.month.getDisplayName(TextStyle.FULL, FR)
        return "$day ${d.dayOfMonth} $month"
    }

    /** "lun. 4" — ne lève jamais, pour la même raison. */
    fun shortLabel(iso: String): String {
        val d = parseOrNull(iso) ?: return iso
        val day = d.dayOfWeek.getDisplayName(TextStyle.SHORT, FR)
        return "$day ${d.dayOfMonth}"
    }

    fun parseOrNull(iso: String): LocalDate? =
        runCatching { LocalDate.parse(iso.trim()) }.getOrNull()

    /**
     * Accepte aussi ce qu'on tape naturellement en français : 12/03/2026,
     * 12-03-2026, 12.03.2026. L'étiquette dit « AAAA-MM-JJ », mais personne
     * n'écrit une date comme ça — autant comprendre les deux.
     * Renvoie toujours la forme ISO, ou null si c'est illisible.
     */
    fun normalizeDate(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null
        parseOrNull(t)?.let { return it.format(ISO) }
        val parts = t.split('/', '-', '.').map { it.trim() }
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: return null
        return runCatching { LocalDate.of(year, month, day).format(ISO) }.getOrNull()
    }

    fun isValidDate(text: String): Boolean = normalizeDate(text) != null

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
