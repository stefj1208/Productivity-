package com.notresemaine.app.data

/**
 * Les catégories de la vie commune.
 *
 * Il n'y a pas de table de catégories : une catégorie existe dès qu'un élément
 * la porte. C'est volontaire — créer une catégorie vide, c'est créer un rangement
 * qu'on ne remplira jamais, et une table de plus à synchroniser pour rien.
 */
object Categories {

    data class Cat(val key: String, val label: String, val emoji: String)

    /** Toujours proposées, même vides : elles couvrent l'essentiel d'un foyer. */
    val BUILT_IN = listOf(
        Cat("repas", "Repas", "🍽️"),
        Cat("finance", "Finance", "💶"),
        Cat("enfants", "Enfants", "🧒"),
        Cat("administratif", "Administratif", "📄"),
        Cat("maison", "Maison", "🏠"),
        Cat("projets", "Projets", "🚀")
    )

    /** Émoji attribué de façon stable à une catégorie inventée par l'utilisateur. */
    private val EXTRA_EMOJIS = listOf("📌", "🎒", "🚗", "🐾", "🎁", "🧰", "🌱", "🎬")

    fun emojiOf(key: String): String =
        BUILT_IN.firstOrNull { it.key == key }?.emoji
            ?: EXTRA_EMOJIS[(key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % EXTRA_EMOJIS.size]

    fun labelOf(key: String): String =
        BUILT_IN.firstOrNull { it.key == key }?.label
            ?: key.replaceFirstChar { it.uppercase() }

    fun keyOf(label: String): String =
        label.trim().lowercase()
            .replace(Regex("[^a-z0-9àâäéèêëîïôöùûüç ]"), "")
            .replace(' ', '-')
            .take(24)

    /** Les catégories intégrées, plus celles que le couple a créées. */
    fun all(existingSections: List<String>): List<Cat> {
        val extra = existingSections
            .filter { it.isNotBlank() && BUILT_IN.none { b -> b.key == it } }
            .distinct()
            .sorted()
            .map { Cat(it, labelOf(it), emojiOf(it)) }
        return BUILT_IN + extra
    }
}
