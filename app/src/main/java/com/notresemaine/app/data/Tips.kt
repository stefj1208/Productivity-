package com.notresemaine.app.data

import java.time.LocalDate

/**
 * Conseils courts tirés des 6 livres, affichés au bon moment.
 * Ton neutre, jamais culpabilisant.
 */
object Tips {

    data class Tip(val book: String, val text: String)

    private val morning = listOf(
        Tip("Miracle Morning", "La première heure donne le ton : rituel d'abord, téléphone ensuite."),
        Tip("One Thing", "Commence par LA priorité, tant que la volonté est fraîche."),
        Tip("Deep Work", "Un bloc de concentration le matin vaut trois l'après-midi."),
        Tip("Miracle Morning", "Pas le temps ? Le rituel en version 6 minutes compte aussi."),
        Tip("Essentialisme", "Si tout est important, rien ne l'est. Une seule chose d'abord.")
    )

    private val evening = listOf(
        Tip("One Thing", "Décider ce soir = ne pas décider demain matin. 2 minutes suffisent."),
        Tip("GTD", "Note tout ce qui traîne dans la tête avant de dormir : la boîte de réception s'en souviendra."),
        Tip("Deep Work", "Planifie ton bloc de concentration de demain avant de fermer la journée."),
        Tip("Semaine de 4 h", "Demain : la tâche qui te rend le plus nerveux, en premier.")
    )

    private val review = listOf(
        Tip("Essentialisme", "Dire non à une chose, c'est dire oui à une autre. Choisis laquelle."),
        Tip("GTD", "La revue hebdomadaire remet le compteur à zéro : rien d'oublié, tête libre."),
        Tip("One Thing", "Quelle est LA chose qui rend le reste plus simple ou inutile cette semaine ?"),
        Tip("Semaine de 4 h", "Cherche les 20 % d'actions qui produisent 80 % du résultat."),
        Tip("Deep Work", "Bloque d'abord les créneaux de concentration, remplis le reste ensuite.")
    )

    private val goals = listOf(
        Tip("Essentialisme", "Moins mais mieux : 2 objectifs actifs avancent plus vite que 5."),
        Tip("One Thing", "La régularité bat le volume : mieux vaut 20 min par jour que 2 h le dimanche."),
        Tip("GTD", "Un objectif sans prochaine action est un vœu, pas un projet."),
        Tip("Miracle Morning", "Accroche la nouvelle habitude à une habitude existante : après le café, avant la douche.")
    )

    private fun pick(list: List<Tip>): Tip =
        list[LocalDate.now().dayOfYear % list.size]

    fun morning(): Tip = pick(morning)
    fun evening(): Tip = pick(evening)
    fun review(step: Int): Tip = review[step % review.size]
    fun goals(): Tip = pick(goals)
}
