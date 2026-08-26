package com.notresemaine.app.data

/**
 * Ce qu'il y avait dans l'assiette, au-delà du nombre de calories.
 *
 * Deux journées à 2 000 kcal n'ont rien à voir selon qu'elles apportent 110 g de
 * protéines ou 40, 30 g de fibres ou 5. Les calories disent *combien* on a mangé ;
 * ces quatre chiffres disent *quoi* — et c'est cela qui explique une fatigue, une
 * faim à 17 h ou un poids qui ne bouge pas.
 *
 * Règle unique et non négociable : **zéro veut dire « inconnu », jamais « aucun »**.
 * Un repas coché depuis un vieux menu n'a pas de nutriments enregistrés ; le compter
 * comme « 0 g de protéines » ferait chuter toutes les moyennes sans que personne
 * n'ait changé quoi que ce soit. Les repas non renseignés sont donc écartés du
 * calcul, et leur nombre est affiché à côté du résultat.
 */
object Nutrition {

    /** Un bilan nutritionnel moyen, par jour. */
    data class Summary(
        val days: Int,
        val mealsCounted: Int,
        val mealsIgnored: Int,
        val calories: Int,
        val protein: Int,
        val carbs: Int,
        val fat: Int,
        val fiber: Int
    ) {
        val hasData: Boolean get() = mealsCounted > 0

        /**
         * La part de chaque macronutriment dans les calories, en pourcentage.
         *
         * Recalculée depuis les grammes (4 kcal/g pour les protéines et les
         * glucides, 9 pour les lipides) plutôt que depuis [calories] : les deux
         * chiffres viennent d'estimations différentes et ne tombent jamais juste
         * ensemble. Mieux vaut une répartition cohérente avec elle-même.
         */
        val split: Triple<Int, Int, Int>
            get() {
                val kcal = protein * 4 + carbs * 4 + fat * 9
                if (kcal <= 0) return Triple(0, 0, 0)
                return Triple(
                    protein * 4 * 100 / kcal,
                    carbs * 4 * 100 / kcal,
                    fat * 9 * 100 / kcal
                )
            }
    }

    /** Un repas est renseigné dès qu'un seul de ses macronutriments est connu. */
    private fun isKnown(log: MealLogEntity): Boolean =
        log.protein > 0 || log.carbs > 0 || log.fat > 0 || log.fiber > 0

    /**
     * Moyennes par jour sur les repas renseignés.
     *
     * On divise par le nombre de **jours où quelque chose a été noté**, pas par la
     * longueur de la période : une semaine notée trois jours donnerait sinon des
     * moyennes deux fois trop basses, et ferait croire à un régime qui n'existe pas.
     */
    fun summarize(logs: List<MealLogEntity>): Summary {
        val real = logs.filter { !it.deleted && it.source != Fasting.SOURCE }
        val known = real.filter { isKnown(it) }
        val days = known.map { it.date }.distinct().size.coerceAtLeast(1)
        return Summary(
            days = days,
            mealsCounted = known.size,
            mealsIgnored = real.size - known.size,
            calories = known.sumOf { it.calories } / days,
            protein = known.sumOf { it.protein } / days,
            carbs = known.sumOf { it.carbs } / days,
            fat = known.sumOf { it.fat } / days,
            fiber = known.sumOf { it.fiber } / days
        )
    }

    /** Les calories notées jour par jour, dans l'ordre des [days] demandés. */
    fun caloriesPerDay(logs: List<MealLogEntity>, days: List<String>): List<Float> {
        val real = logs.filter { !it.deleted }
        return days.map { day ->
            real.filter { it.date == day }.sumOf { it.calories }.toFloat()
        }
    }

    /**
     * Une phrase sur ce qui manque, ou rien du tout.
     *
     * Volontairement descriptive : « il manque des fibres » est une observation,
     * « vous mangez mal » serait un jugement — et l'application n'en porte aucun.
     * Les repères sont ceux, très larges, des recommandations générales ; ils ne
     * remplacent l'avis de personne.
     */
    fun observation(s: Summary): String {
        if (!s.hasData) return ""
        val notes = buildList {
            if (s.fiber in 1..24) add("peu de fibres (${s.fiber} g par jour)")
            if (s.protein in 1..59) add("peu de protéines (${s.protein} g)")
            val (p, c, f) = s.split
            if (f >= 45) add("part de lipides élevée ($f %)")
            if (c >= 60) add("part de glucides élevée ($c %)")
            if (p >= 35) add("part de protéines élevée ($p %)")
        }
        return if (notes.isEmpty()) "Répartition équilibrée sur les repas renseignés."
        else "À noter : " + notes.joinToString(", ") + "."
    }

    /** Une ligne compacte pour l'assistant : « 95 g protéines, 210 g glucides… ». */
    fun forAi(s: Summary): String {
        if (!s.hasData) return ""
        val (p, c, f) = s.split
        return "Nutrition moyenne par jour noté : ${s.calories} kcal, ${s.protein} g de " +
            "protéines, ${s.carbs} g de glucides, ${s.fat} g de lipides, ${s.fiber} g de " +
            "fibres (soit $p % / $c % / $f %), sur ${s.mealsCounted} repas renseignés."
    }
}
