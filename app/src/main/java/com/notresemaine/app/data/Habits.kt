package com.notresemaine.app.data

import java.time.LocalDate

/**
 * Les habitudes, et surtout : à quel moment les rappeler.
 *
 * Le tirage est **pseudo-aléatoire mais stable** : la même habitude, le même
 * jour, tombe toujours aux mêmes minutes. C'est indispensable — l'écran est
 * recomposé des dizaines de fois par jour, et un vrai tirage au hasard ferait
 * réapparaître ou disparaître les rappels à chaque recomposition. Imprévisible
 * pour vous, déterministe pour la machine.
 */
object Habits {

    /** Proposées hors ligne, sans clé d'assistant. */
    val STARTERS: List<Pair<String, String>> = listOf(
        "Une seule chose à la fois" to "Deep Work",
        "Repose le téléphone" to "La semaine de 4 heures",
        "Bois un verre d'eau" to "Miracle Morning",
        "Pas de grignotage entre les repas" to "Décidé une fois, pas dix",
        "Note-le au lieu d'y penser" to "GTD",
        "Est-ce que c'est essentiel ?" to "L'essentialisme",
        "Redresse-toi, respire" to "Deux secondes suffisent",
        "Ferme les onglets inutiles" to "Deep Work"
    )

    /**
     * Les minutes de la journée où [habitId] se rappelle, pour [date].
     * Réparties dans la plage choisie, jamais deux fois au même moment.
     */
    fun momentsOf(habitId: String, date: LocalDate, fromHour: Int, toHour: Int, perDay: Int): List<Int> {
        val start = fromHour.coerceIn(0, 23) * 60
        val end = (toHour.coerceIn(0, 23) * 60).coerceAtLeast(start + 60)
        val count = perDay.coerceIn(1, 8)
        val span = end - start
        val slice = span / count

        // Un générateur simple, alimenté par l'habitude ET le jour : la même
        // habitude ne tombe donc pas à la même heure deux jours de suite.
        var seed = (habitId.hashCode().toLong() * 31 + date.toEpochDay()) and 0x7FFFFFFF
        fun nextIn(bound: Int): Int {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return if (bound <= 0) 0 else (seed % bound).toInt()
        }

        return (0 until count).map { i ->
            // Une occurrence par tranche, décalée au hasard à l'intérieur.
            start + i * slice + nextIn(slice.coerceAtLeast(1))
        }
    }
}
