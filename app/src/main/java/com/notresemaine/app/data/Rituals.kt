package com.notresemaine.app.data

/**
 * Ce qu'on fait vraiment pendant chaque étape du rituel.
 *
 * « Silence · 5 min » ne dit rien à quelqu'un qui n'a pas lu le livre : on
 * s'assied, et on se demande quoi faire. Ces descriptions sont là pour que
 * chaque étape se lance sans réfléchir, même sans assistant et sans réseau.
 */
object Rituals {

    /** Comment faire, en une phrase — et un premier geste concret. */
    data class Guide(val how: String, val start: String)

    private val GUIDES: Map<String, Guide> = mapOf(
        "silence" to Guide(
            how = "Assis, dos droit, yeux fermés. On ne cherche pas à vider sa tête : " +
                "on revient à la respiration à chaque fois qu'elle part.",
            start = "Inspire 4 secondes, expire 6 secondes. Recommence."
        ),
        "affirmations" to Guide(
            how = "Une phrase au présent sur qui vous décidez d'être aujourd'hui — " +
                "pas un vœu, un engagement.",
            start = "Dis à voix haute : « Aujourd'hui, je fais d'abord ce qui compte. »"
        ),
        "visualisation" to Guide(
            how = "Se voir en train de faire la chose difficile de la journée, " +
                "en détail : le lieu, le premier geste, la fin.",
            start = "Ferme les yeux et regarde-toi commencer ta priorité."
        ),
        "sport" to Guide(
            how = "Réveiller le corps, pas le fatiguer. L'objectif est d'avoir chaud, " +
                "pas d'avoir mal.",
            start = "Debout : 20 squats lents, puis étire le dos."
        ),
        "exercice" to Guide(
            how = "Réveiller le corps, pas le fatiguer. L'objectif est d'avoir chaud, " +
                "pas d'avoir mal.",
            start = "Debout : 20 squats lents, puis étire le dos."
        ),
        "lecture" to Guide(
            how = "Deux pages suffisent. Un livre qui apprend quelque chose, " +
                "pas l'actualité.",
            start = "Ouvre le livre en cours et lis jusqu'au bout de la page."
        ),
        "écriture" to Guide(
            how = "Vider ce qui encombre, puis nommer ce qui compte. " +
                "Personne ne lira : l'orthographe n'a aucune importance.",
            start = "Appuie sur le ＋ en bas à droite et écris : « Ce qui me préoccupe ce matin, c'est… »"
        ),
        "ecriture" to Guide(
            how = "Vider ce qui encombre, puis nommer ce qui compte. " +
                "Personne ne lira : l'orthographe n'a aucune importance.",
            start = "Appuie sur le ＋ en bas à droite et écris : « Ce qui me préoccupe ce matin, c'est… »"
        )
    )

    /** Reconnaît l'étape par un mot-clé : les intitulés sont modifiables. */
    fun guideFor(stepName: String): Guide? {
        val key = stepName.lowercase()
        return GUIDES.entries.firstOrNull { key.contains(it.key) }?.value
    }
}
