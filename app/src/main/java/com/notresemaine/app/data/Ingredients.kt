package com.notresemaine.app.data

import java.util.Locale

/**
 * Génération de la liste de courses à partir des menus, sans IA :
 * découpage des ingrédients, addition des quantités identiques, regroupement par rayon.
 */
object Ingredients {

    data class Item(val label: String, val aisle: String)

    private data class Parsed(val quantity: Double?, val unit: String, val name: String)

    private const val OTHER = "Divers"

    /** Rayons, dans l'ordre du parcours en magasin. */
    private val aisles: List<Pair<String, List<String>>> = listOf(
        "Fruits & légumes" to listOf(
            "tomate", "salade", "laitue", "carotte", "oignon", "ail", "échalote", "pomme de terre",
            "patate", "courgette", "aubergine", "poivron", "concombre", "champignon", "brocoli",
            "haricot", "épinard", "poireau", "chou", "citron", "orange", "pomme", "banane",
            "fraise", "raisin", "poire", "avocat", "persil", "basilic", "coriandre", "menthe",
            "gingembre", "céleri", "navet", "potiron", "melon", "pêche", "abricot", "kiwi", "mangue"
        ),
        "Boucherie & poisson" to listOf(
            "poulet", "bœuf", "boeuf", "porc", "veau", "agneau", "steak", "escalope", "jambon",
            "lardon", "saucisse", "merguez", "dinde", "canard", "saumon", "cabillaud", "thon",
            "crevette", "poisson", "moule", "viande", "haché"
        ),
        "Crèmerie" to listOf(
            "lait", "beurre", "crème", "creme", "œuf", "oeuf", "fromage", "yaourt", "yogourt",
            "gruyère", "gruyere", "emmental", "parmesan", "mozzarella", "chèvre", "chevre",
            "comté", "comte", "ricotta", "mascarpone", "feta"
        ),
        "Épicerie" to listOf(
            "farine", "sucre", "sel", "poivre", "riz", "pâtes", "pates", "spaghetti", "semoule",
            "huile", "vinaigre", "moutarde", "conserve", "tomates pelées", "lentille", "pois chiche",
            "quinoa", "boulgour", "levure", "chocolat", "miel", "confiture", "café", "cafe", "thé",
            "the", "épice", "epice", "curry", "paprika", "cumin", "bouillon", "sauce", "olive",
            "noix", "amande", "céréale", "cereale", "biscuit", "compote"
        ),
        "Boulangerie" to listOf(
            "pain", "baguette", "brioche", "croissant", "tortilla", "wrap", "pita", "burger"
        ),
        "Surgelés" to listOf(
            "surgelé", "surgele", "glace", "sorbet", "petits pois surgelés"
        ),
        "Boissons" to listOf(
            "eau", "vin", "bière", "biere", "jus", "soda", "limonade", "cidre"
        ),
        "Maison" to listOf(
            "papier", "éponge", "eponge", "lessive", "liquide vaisselle", "sac poubelle", "essuie"
        )
    )

    /** Unités reconnues et leur forme normalisée. */
    private val units = mapOf(
        "g" to "g", "gr" to "g", "gramme" to "g", "grammes" to "g",
        "kg" to "kg", "kilo" to "kg", "kilos" to "kg",
        "ml" to "ml", "cl" to "cl", "l" to "L", "litre" to "L", "litres" to "L",
        "cs" to "c. à s.", "cas" to "c. à s.", "cc" to "c. à c.", "cac" to "c. à c.",
        "pincée" to "pincée", "pincee" to "pincée",
        "boîte" to "boîte", "boite" to "boîte", "boîtes" to "boîte", "boites" to "boîte",
        "sachet" to "sachet", "sachets" to "sachet",
        "tranche" to "tranche", "tranches" to "tranche",
        "gousse" to "gousse", "gousses" to "gousse"
    )

    fun aisleFor(name: String): String {
        val n = name.lowercase(Locale.FRENCH)
        aisles.forEach { (aisle, keywords) ->
            if (keywords.any { n.contains(it) }) return aisle
        }
        return OTHER
    }

    /** "200 g farine" → (200, "g", "farine") ; "3 œufs" → (3, "", "œufs") ; "sel" → (null, "", "sel") */
    private fun parse(raw: String): Parsed {
        val text = raw.trim().trim('-', '•', '·', ' ')
        if (text.isBlank()) return Parsed(null, "", "")
        val m = Regex("^(\\d+(?:[.,]\\d+)?)\\s*([\\p{L}.]+)?\\s+(.+)$").find(text)
            ?: return Parsed(null, "", text)
        val quantity = m.groupValues[1].replace(',', '.').toDoubleOrNull()
        val maybeUnit = m.groupValues[2].lowercase(Locale.FRENCH).trim('.')
        val rest = m.groupValues[3].trim()
        val unit = units[maybeUnit]
        return if (unit != null) {
            Parsed(quantity, unit, rest)
        } else {
            // Pas d'unité : le mot fait partie du nom ("3 œufs entiers")
            val name = listOf(m.groupValues[2], rest).filter { it.isNotBlank() }.joinToString(" ")
            Parsed(quantity, "", name)
        }
    }

    private fun normalizeKey(name: String): String =
        name.lowercase(Locale.FRENCH)
            .replace(Regex("s$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else value.toString().trimEnd('0').trimEnd('.')

    /**
     * Additionne les ingrédients de plusieurs repas et les regroupe par rayon.
     * "200 g farine" + "100 g farine" → "300 g farine".
     */
    fun aggregate(ingredientLines: List<String>): List<Item> {
        data class Bucket(var quantity: Double?, val unit: String, val displayName: String)

        val buckets = LinkedHashMap<String, Bucket>()
        ingredientLines
            .flatMap { it.split(",", ";", "\n") }
            .map { parse(it) }
            .filter { it.name.isNotBlank() }
            .forEach { p ->
                val key = normalizeKey(p.name) + "|" + p.unit
                val existing = buckets[key]
                if (existing == null) {
                    buckets[key] = Bucket(p.quantity, p.unit, p.name.trim())
                } else if (p.quantity != null) {
                    existing.quantity = (existing.quantity ?: 0.0) + p.quantity
                }
            }

        return buckets.values.map { b ->
            val label = when {
                b.quantity == null -> b.displayName
                b.unit.isBlank() -> "${formatQuantity(b.quantity!!)} ${b.displayName}"
                else -> "${formatQuantity(b.quantity!!)} ${b.unit} ${b.displayName}"
            }
            Item(label = label, aisle = aisleFor(b.displayName))
        }.sortedBy { item -> aisles.indexOfFirst { it.first == item.aisle }.let { if (it < 0) 99 else it } }
    }
}
