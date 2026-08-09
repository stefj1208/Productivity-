package com.notresemaine.app.data

import java.time.LocalDate

/**
 * Conseils des 6 livres, en format « carte » : un emoji, une phrase courte qui claque,
 * la source en petit. Jamais de pavé, jamais de ton culpabilisant.
 */
object Tips {

    data class Tip(val emoji: String, val punch: String, val book: String)

    private val morning = listOf(
        Tip("🌅", "La première heure donne le ton.", "Miracle Morning"),
        Tip("🎯", "Ta priorité d'abord. Le reste ensuite.", "The One Thing"),
        Tip("🎧", "Un bloc concentré le matin en vaut trois l'après-midi.", "Deep Work"),
        Tip("⏱️", "Pas le temps ? Six minutes comptent aussi.", "Miracle Morning"),
        Tip("✂️", "Si tout est important, rien ne l'est.", "L'essentialisme")
    )

    private val evening = listOf(
        Tip("🌙", "Décider ce soir, c'est ne plus décider demain.", "The One Thing"),
        Tip("🧠", "Vide ta tête avant l'oreiller.", "GTD"),
        Tip("🛡️", "Protège ton bloc de demain dès ce soir.", "Deep Work"),
        Tip("🐸", "Demain : la tâche qui t'angoisse, en premier.", "Semaine de 4 h")
    )

    private val review = listOf(
        Tip("📊", "Regarde les faits, pas les impressions.", "Semaine de 4 h"),
        Tip("🧠", "Zéro note en attente = tête libre.", "GTD"),
        Tip("🙅", "Dire non à ça, c'est dire oui à ce qui compte.", "L'essentialisme"),
        Tip("🎯", "Une seule chose rend le reste plus simple.", "The One Thing"),
        Tip("🎧", "Bloque la concentration avant de remplir le reste.", "Deep Work"),
        Tip("🏃", "Trois séances fixes battent sept intentions.", "Miracle Morning"),
        Tip("🍽️", "Décider les menus une fois, manger sept fois.", "Semaine de 4 h"),
        Tip("✅", "Semaine décidée. Plus rien à arbitrer.", "L'essentialisme")
    )

    private val goals = listOf(
        Tip("✂️", "Deux objectifs avancent plus vite que cinq.", "L'essentialisme"),
        Tip("📈", "20 min chaque jour battent 2 h le dimanche.", "The One Thing"),
        Tip("👉", "Sans prochaine action, c'est un vœu.", "GTD"),
        Tip("🔗", "Accroche l'habitude à une habitude existante.", "Miracle Morning")
    )

    private fun pick(list: List<Tip>): Tip = list[LocalDate.now().dayOfYear % list.size]

    fun morning(): Tip = pick(morning)
    fun evening(): Tip = pick(evening)
    fun review(step: Int): Tip = review[step % review.size]
    fun goals(): Tip = pick(goals)
}
