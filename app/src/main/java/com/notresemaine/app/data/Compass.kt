package com.notresemaine.app.data

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * La boussole : à tout instant, UNE seule chose à faire maintenant.
 * C'est l'application des méthodes qui décide, pas l'utilisateur.
 */
object Compass {

    /** [route] est la destination à ouvrir quand on touche la carte. */
    data class Step(
        val emoji: String,
        val title: String,
        val why: String,
        val route: String?
    )

    fun next(
        hour: Int = LocalTime.now().hour,
        dayOfWeek: DayOfWeek = Dates.today().dayOfWeek,
        ritualDoneToday: Boolean,
        hasRitualSteps: Boolean,
        priorityToday: TaskEntity?,
        remainingToday: Int,
        inboxCount: Int,
        targetWeekPlanned: Boolean,
        hasActiveGoals: Boolean
    ): Step {
        val weekendPlanning = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

        // 1. Le rituel du matin passe avant tout (Miracle Morning).
        if (hour in 4..10 && hasRitualSteps && !ritualDoneToday) {
            return Step("🌅", "Ton rituel du matin", "La première heure donne le ton", "ritual")
        }

        // 2. Pas de priorité pour aujourd'hui : impossible d'avancer sans (One Thing).
        if (priorityToday == null && hour < 20) {
            return Step("🎯", "Choisis ta priorité du jour", "Une seule chose, la plus utile", "prepare/today")
        }

        // 3. Priorité pas encore faite : c'est ça, et rien d'autre.
        if (priorityToday != null && !priorityToday.done && hour < 21) {
            return Step("🎯", priorityToday.title, "Ta priorité — le reste est du bonus", null)
        }

        // 4. Week-end : préparer la semaine qui vient (GTD, revue hebdomadaire).
        if (weekendPlanning && !targetWeekPlanned) {
            return Step("🗓️", "Planifie la semaine qui vient", "10 minutes qui évitent 7 jours de flou", "review")
        }

        // 5. Boîte de réception qui déborde : vider la tête (GTD).
        if (inboxCount >= 5) {
            return Step("🧠", "$inboxCount notes à trier", "Une tête vide travaille mieux", "review")
        }

        // 6. Aucun objectif : l'app ne sert qu'à moitié.
        if (!hasActiveGoals) {
            return Step("🚀", "Choisis un objectif", "L'app planifie les séances pour toi", "goals")
        }

        // 7. Le soir : préparer demain (One Thing).
        if (hour >= 18) {
            return Step("🌙", "Prépare demain", "2 minutes ce soir, zéro hésitation demain", "prepare/tomorrow")
        }

        // 8. Tout est fait.
        return if (remainingToday > 0) {
            Step("✅", "Priorité faite", "$remainingToday tâche(s) en bonus, sans pression", null)
        } else {
            Step("✅", "Journée bouclée", "Rien d'autre n'est nécessaire aujourd'hui", null)
        }
    }
}
