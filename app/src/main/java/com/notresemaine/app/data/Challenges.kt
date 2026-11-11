package com.notresemaine.app.data

import java.time.LocalDate

/**
 * Les défis : un engagement borné dans le temps, un geste par jour.
 *
 * C'est le point d'extension de l'application. Tout ce qu'un défi *est* tient
 * dans un [Template] : un titre, une durée, une règle et une unité. Ajouter un
 * défi au catalogue, c'est ajouter une entrée à [CATALOG] — pas une table, pas
 * un écran, pas une migration. Les défis inventés par l'utilisateur passent par
 * exactement le même moule (templateId = « perso »).
 *
 * Trois règles suffisent à décrire tout ce qu'on a rencontré :
 *  - constant  : la même quantité chaque jour (« lire 20 pages ») ;
 *  - croissant : jour n = n fois la quantité (« se débarrasser de n objets ») ;
 *  - libre     : une simple case, sans quantité (« pas de sucre ajouté »).
 *
 * Aucune série, aucun score. Un jour manqué n'est noté nulle part : il reste
 * gris dans la grille, et on reprend le lendemain. Le défi n'est pas « raté »
 * parce qu'un jour l'est — il est fini quand sa durée est écoulée.
 */
object Challenges {

    const val CUSTOM = "perso"

    data class Template(
        val id: String,
        val emoji: String,
        val title: String,
        val description: String,
        val days: Int,
        val rule: String = "constant",
        val unit: String = "",
        val amount: Int = 1,
        /** D'où vient l'idée, pour qu'on sache qu'on ne l'invente pas. */
        val source: String = ""
    )

    val CATALOG: List<Template> = listOf(
        Template(
            id = "minimalisme-30",
            emoji = "📦",
            title = "30 jours de minimalisme",
            description = "Jour 1, on se sépare d'un objet. Jour 2, de deux. Jour 30, de trente. " +
                "Donner, vendre, jeter — peu importe, pourvu que ça sorte de la maison. " +
                "Le premier tiers est facile, le dernier change une maison.",
            days = 30,
            rule = "croissant",
            unit = "objet(s)",
            source = "Le « Minimalism Game » de Joshua Fields Millburn et Ryan Nicodemus"
        ),
        Template(
            id = "lever-21",
            emoji = "🌅",
            title = "21 matins à la même heure",
            description = "Se lever à l'heure choisie, week-end compris. Trois semaines : le temps " +
                "qu'il faut pour que le corps n'ait plus besoin du réveil.",
            days = 21,
            rule = "libre",
            source = "Miracle Morning, Hal Elrod"
        ),
        Template(
            id = "sucre-14",
            emoji = "🍬",
            title = "14 jours sans sucre ajouté",
            description = "Ni sodas, ni biscuits, ni sucre dans le café. Les fruits restent. " +
                "Deux semaines suffisent pour que le goût se recalibre.",
            days = 14,
            rule = "libre"
        ),
        Template(
            id = "pas-30",
            emoji = "👟",
            title = "30 jours à 8 000 pas",
            description = "Une marche de 20 minutes le matin et une le soir y arrivent presque " +
                "seules. Les pas viennent de Health Connect, si relié.",
            days = 30,
            rule = "constant",
            unit = "pas",
            amount = 8000
        ),
        Template(
            id = "lecture-30",
            emoji = "📖",
            title = "30 jours de lecture",
            description = "20 pages par jour, c'est un livre tous les quinze jours. Le livre " +
                "remplace le téléphone sur la table de nuit.",
            days = 30,
            rule = "constant",
            unit = "page(s)",
            amount = 20
        ),
        Template(
            id = "merci-21",
            emoji = "🙏",
            title = "21 jours de merci",
            description = "Chaque jour, remercier quelqu'un pour quelque chose de précis — de vive " +
                "voix ou par message. Pas un « merci » de politesse : un vrai.",
            days = 21,
            rule = "libre"
        ),
        Template(
            id = "desabonnement-14",
            emoji = "📭",
            title = "14 jours de désabonnement",
            description = "Un courriel, une chaîne ou un compte en moins par jour. Au bout de deux " +
                "semaines, la boîte de réception respire.",
            days = 14,
            rule = "constant",
            unit = "désabonnement(s)"
        ),
        Template(
            id = "eau-14",
            emoji = "💧",
            title = "14 jours à 1,5 L d'eau",
            description = "Une bouteille remplie le matin, finie le soir. Le plus simple des " +
                "défis, et celui qu'on oublie le plus.",
            days = 14,
            rule = "libre"
        )
    )

    fun template(id: String): Template? = CATALOG.firstOrNull { it.id == id }

    /** Numéro du jour pour une date donnée : 1 le jour de départ. Peut dépasser [ChallengeEntity.days]. */
    fun dayNumber(challenge: ChallengeEntity, date: LocalDate = Dates.today()): Int {
        val start = runCatching { LocalDate.parse(challenge.startDate) }.getOrElse { date }
        return java.time.temporal.ChronoUnit.DAYS.between(start, date).toInt() + 1
    }

    /** La date du jour n (1 = départ). */
    fun dateOf(challenge: ChallengeEntity, day: Int): LocalDate =
        runCatching { LocalDate.parse(challenge.startDate) }.getOrElse { Dates.today() }
            .plusDays((day - 1).toLong())

    fun doneDays(challenge: ChallengeEntity): Set<Int> =
        challenge.doneDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    /** Quantité attendue le jour n selon la règle. 0 pour « libre ». */
    fun amountOn(challenge: ChallengeEntity, day: Int): Int = when (challenge.rule) {
        "croissant" -> challenge.amount * day
        "constant" -> challenge.amount
        else -> 0
    }

    /** Ce qu'il y a à faire le jour n, en une phrase : « 7 objet(s) », « 20 page(s) », ou le titre. */
    fun taskLabel(challenge: ChallengeEntity, day: Int): String {
        val amount = amountOn(challenge, day)
        return if (amount > 0 && challenge.unit.isNotBlank()) "$amount ${challenge.unit}"
        else challenge.title
    }

    /** Total sur toute la durée : utile pour « 465 objets en 30 jours ». */
    fun total(challenge: ChallengeEntity): Int = when (challenge.rule) {
        "croissant" -> challenge.amount * challenge.days * (challenge.days + 1) / 2
        "constant" -> challenge.amount * challenge.days
        else -> challenge.days
    }

    /** Ce qui a été accompli jusqu'ici, dans la même unité que [total]. */
    fun achieved(challenge: ChallengeEntity): Int {
        val done = doneDays(challenge)
        return when (challenge.rule) {
            "libre" -> done.size
            else -> done.sumOf { amountOn(challenge, it) }
        }
    }

    fun isOver(challenge: ChallengeEntity, date: LocalDate = Dates.today()): Boolean =
        dayNumber(challenge, date) > challenge.days

    /**
     * La phrase du jour, pour la carte et le rappel : « Jour 7 sur 30 · 7 objet(s) ».
     * Après la fin : un bilan, jamais un reproche.
     */
    fun todayLine(challenge: ChallengeEntity, date: LocalDate = Dates.today()): String {
        val day = dayNumber(challenge, date)
        if (day < 1) return "Commence le ${Dates.shortLabel(challenge.startDate)}"
        if (day > challenge.days) {
            val done = doneDays(challenge).size
            return "Terminé · $done jour(s) sur ${challenge.days}"
        }
        return "Jour $day sur ${challenge.days} · ${taskLabel(challenge, day)}"
    }
}
