package com.notresemaine.app.data

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Le jeûne, déduit de ce qu'on a noté — jamais saisi à la main.
 *
 * Personne n'a envie de déclarer « j'ai commencé mon jeûne à 20 h 12 ». En
 * revanche, chacun note ses repas. L'écart entre deux repas *est* le jeûne : il
 * suffit de le lire. C'est pour ça que chaque ligne du journal porte une heure.
 *
 * Un repas marqué « jeûné » n'est pas un repas : il ne coupe pas la fenêtre, il
 * la confirme. Le sauter sans rien dire donne exactement le même calcul — la
 * touche sert à l'assumer, et à distinguer « j'ai jeûné » de « j'ai oublié de
 * noter », que rien d'autre ne saurait départager.
 */
object Fasting {

    const val SOURCE = "jeune"

    /** En dessous, ce n'est pas un jeûne, c'est une nuit. */
    private const val MIN_HOURS = 12

    /** Une fenêtre sans nourriture, d'un repas au suivant. */
    data class Window(
        val fromIso: String,
        val fromTime: String,
        val toIso: String,
        val toTime: String,
        val minutes: Int
    ) {
        val hours: Int get() = minutes / 60
        val label: String get() = "${minutes / 60} h" + if (minutes % 60 > 0) " ${minutes % 60}" else ""
    }

    /**
     * L'heure retenue quand on n'en a pas saisi.
     *
     * Cocher « j'ai mangé ce qui était prévu » à 22 h ne veut pas dire qu'on a
     * pris son petit-déjeuner à 22 h : on retient donc l'heure habituelle du
     * créneau, pas l'heure du geste. C'est modifiable partout où ça compte.
     */
    fun defaultTime(slot: String): String = when (slot) {
        "matin" -> "08:00"
        "midi" -> "12:30"
        "soir" -> "19:30"
        else -> "16:00"
    }

    /** Les vrais repas, remis dans l'ordre du temps. */
    private fun mealMoments(logs: List<MealLogEntity>): List<LocalDateTime> =
        logs.asSequence()
            .filter { !it.deleted && it.source != SOURCE }
            .mapNotNull { log ->
                val day = Dates.parseOrNull(log.date) ?: return@mapNotNull null
                val time = parseTime(log.time.ifBlank { defaultTime(log.slot) })
                    ?: return@mapNotNull null
                LocalDateTime.of(day, time)
            }
            .sorted()
            .toList()

    /**
     * Toutes les fenêtres d'au moins [MIN_HOURS] heures, de la plus ancienne à la
     * plus récente. Le seuil évite de présenter chaque nuit comme un exploit.
     */
    fun windows(logs: List<MealLogEntity>): List<Window> {
        val moments = mealMoments(logs)
        if (moments.size < 2) return emptyList()
        return moments.zipWithNext().mapNotNull { (from, to) ->
            val minutes = Duration.between(from, to).toMinutes().toInt()
            if (minutes < MIN_HOURS * 60) return@mapNotNull null
            Window(
                fromIso = from.toLocalDate().toString(),
                fromTime = String.format("%02d:%02d", from.hour, from.minute),
                toIso = to.toLocalDate().toString(),
                toTime = String.format("%02d:%02d", to.hour, to.minute),
                minutes = minutes
            )
        }
    }

    fun longest(logs: List<MealLogEntity>): Window? = windows(logs).maxByOrNull { it.minutes }

    /**
     * Le jeûne en cours, en minutes depuis le dernier repas noté.
     *
     * Renvoie null si rien n'a été noté, ou si le dernier repas est dans le futur
     * (une heure saisie de travers ne doit pas afficher un jeûne négatif).
     */
    fun currentMinutes(logs: List<MealLogEntity>, now: LocalDateTime = LocalDateTime.now()): Int? {
        val last = mealMoments(logs).lastOrNull() ?: return null
        val minutes = Duration.between(last, now).toMinutes().toInt()
        return if (minutes < 0) null else minutes
    }

    /**
     * Les jours de jeûne complet : aucun repas noté, mais un « jeûné » assumé.
     *
     * La deuxième condition est essentielle. Sans elle, chaque journée où l'on a
     * simplement oublié d'ouvrir l'application compterait comme un jour de jeûne —
     * l'indicateur mesurerait l'oubli, pas le jeûne.
     */
    fun fullDays(logs: List<MealLogEntity>, days: List<String>): List<String> =
        days.filter { day ->
            val ofDay = logs.filter { it.date == day && !it.deleted }
            ofDay.isNotEmpty() && ofDay.all { it.source == SOURCE }
        }

    /** « 16 h 30 » à partir d'un nombre de minutes. */
    fun label(minutes: Int): String =
        "${minutes / 60} h" + if (minutes % 60 > 0) " ${minutes % 60}" else ""

    private fun parseTime(text: String): LocalTime? {
        val parts = text.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}
