package com.notresemaine.app.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Analyseur de texte français pour la capture rapide (sans IA, sans réseau) :
 * « rappeler le plombier mardi » → tâche datée mardi prochain.
 * Si aucune date n'est reconnue, la note part dans la boîte de réception.
 */
object CaptureParser {

    data class Parsed(val title: String, val date: String?)

    private val weekdays = mapOf(
        "lundi" to DayOfWeek.MONDAY,
        "mardi" to DayOfWeek.TUESDAY,
        "mercredi" to DayOfWeek.WEDNESDAY,
        "jeudi" to DayOfWeek.THURSDAY,
        "vendredi" to DayOfWeek.FRIDAY,
        "samedi" to DayOfWeek.SATURDAY,
        "dimanche" to DayOfWeek.SUNDAY
    )

    fun parse(raw: String, today: LocalDate = LocalDate.now()): Parsed {
        var text = raw.trim()
        if (text.isEmpty()) return Parsed("", null)
        val lower = text.lowercase()

        // 1) dates numériques : 12/09 ou 12/09/2026
        Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b").find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearRaw = m.groupValues[3]
            val year = when {
                yearRaw.isEmpty() -> today.year
                yearRaw.length == 2 -> 2000 + yearRaw.toInt()
                else -> yearRaw.toInt()
            }
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { parsed ->
                val date = if (yearRaw.isEmpty() && parsed.isBefore(today)) parsed.plusYears(1) else parsed
                return Parsed(clean(text, m.value), date.format(Dates.ISO))
            }
        }

        // 2) mots-clés relatifs
        listOf(
            "après-demain" to 2L, "apres-demain" to 2L, "après demain" to 2L, "apres demain" to 2L,
            "demain matin" to 1L, "demain soir" to 1L, "demain" to 1L,
            "aujourd'hui" to 0L, "aujourd hui" to 0L, "ce soir" to 0L, "ce midi" to 0L
        ).forEach { (word, offset) ->
            if (lower.contains(word)) {
                return Parsed(clean(text, word), today.plusDays(offset).format(Dates.ISO))
            }
        }

        // 3) jours de la semaine : « mardi », « mardi matin », « mardi prochain »
        for ((word, dow) in weekdays) {
            val m = Regex("\\b$word(\\s+(matin|midi|soir|prochain[e]?))?\\b").find(lower) ?: continue
            var date = today.plusDays(1)
            while (date.dayOfWeek != dow) date = date.plusDays(1)
            return Parsed(clean(text, m.value), date.format(Dates.ISO))
        }

        return Parsed(text, null)
    }

    private fun clean(text: String, matched: String): String {
        val cleaned = Regex(Regex.escape(matched), RegexOption.IGNORE_CASE)
            .replaceFirst(text, " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim(',', ';', '.', ' ')
        return cleaned.ifBlank { text.trim() }
    }
}
