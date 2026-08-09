package com.notresemaine.app.data

/**
 * Banque d'idées de repas, hors ligne et instantanée : de quoi remplir une semaine
 * quand on n'a pas d'inspiration, sans réseau, sans clé, sans rien envoyer nulle part.
 */
object MenuIdeas {

    data class Idea(val slot: String, val title: String, val ingredients: String)

    private val matin = listOf(
        Idea("matin", "Porridge banane", "80 g flocons d'avoine, 300 ml lait, 2 bananes, 1 c. à s. miel"),
        Idea("matin", "Tartines avocat œuf", "4 tranches pain complet, 2 avocats, 4 œufs, 1 citron"),
        Idea("matin", "Yaourt granola fruits", "500 g yaourt, 200 g granola, 250 g fruits rouges"),
        Idea("matin", "Omelette aux herbes", "6 œufs, 20 g beurre, persil, ciboulette"),
        Idea("matin", "Pain perdu", "6 tranches brioche, 3 œufs, 200 ml lait, 30 g sucre"),
        Idea("matin", "Smoothie bowl", "2 bananes, 200 g fruits rouges, 200 ml lait, 50 g amandes"),
        Idea("matin", "Tartines beurre confiture", "1 baguette, 50 g beurre, 1 pot confiture")
    )

    private val midi = listOf(
        Idea("midi", "Salade de lentilles", "300 g lentilles, 2 carottes, 1 oignon rouge, 150 g feta, vinaigre"),
        Idea("midi", "Wrap poulet crudités", "6 tortillas, 500 g poulet, 1 salade, 3 tomates, 200 g yaourt"),
        Idea("midi", "Riz sauté aux légumes", "300 g riz, 2 poivrons, 1 courgette, 3 œufs, sauce soja"),
        Idea("midi", "Soupe de potiron", "1 potiron, 2 pommes de terre, 1 oignon, 200 ml crème"),
        Idea("midi", "Quiche aux poireaux", "1 pâte brisée, 3 poireaux, 4 œufs, 200 ml crème, 100 g gruyère"),
        Idea("midi", "Buddha bowl quinoa", "250 g quinoa, 1 avocat, 200 g pois chiches, 2 carottes, citron"),
        Idea("midi", "Croque-monsieur salade", "8 tranches pain de mie, 200 g jambon, 150 g gruyère, 1 salade")
    )

    private val soir = listOf(
        Idea("soir", "Pâtes bolognaise", "400 g pâtes, 500 g bœuf haché, 1 oignon, 400 g tomates pelées, 100 g parmesan"),
        Idea("soir", "Saumon riz brocoli", "4 pavés saumon, 300 g riz, 1 brocoli, 1 citron"),
        Idea("soir", "Curry de pois chiches", "400 g pois chiches, 400 ml lait de coco, 2 oignons, curry, 250 g riz"),
        Idea("soir", "Gratin de courgettes", "4 courgettes, 200 ml crème, 150 g gruyère, 2 œufs"),
        Idea("soir", "Poulet rôti pommes de terre", "1 poulet, 1 kg pommes de terre, 4 gousses ail, thym"),
        Idea("soir", "Omelette salade verte", "8 œufs, 1 salade, 200 g pommes de terre, vinaigre"),
        Idea("soir", "Chili sin carne", "400 g haricots rouges, 400 g tomates pelées, 2 poivrons, 1 oignon, 250 g riz")
    )

    /**
     * Propose un repas par créneau et par jour, sans répétition dans la semaine.
     * [seed] décale la sélection pour varier d'une semaine à l'autre.
     */
    fun weekPlan(seed: Int): Map<Pair<Int, String>, Idea> {
        val result = mutableMapOf<Pair<Int, String>, Idea>()
        listOf(matin, midi, soir).forEach { bank ->
            (0..6).forEach { dayIndex ->
                val idea = bank[(dayIndex + seed) % bank.size]
                result[dayIndex to idea.slot] = idea
            }
        }
        return result
    }

    val slots = listOf("matin" to "Matin", "midi" to "Midi", "soir" to "Soir")

    fun slotLabel(slot: String): String =
        slots.firstOrNull { it.first == slot }?.second ?: slot
}
