package com.notresemaine.app.ai

/**
 * L'assistant, fonctionnalité par fonctionnalité.
 *
 * Confidentialité — à lire avant d'activer :
 * chaque fonction ci-dessous indique exactement ce qui sort du téléphone. Ce sont
 * toujours des libellés que vous avez écrits (un objectif, un repas, une note), jamais
 * vos données de santé détaillées, jamais celles de l'autre, et jamais un objectif que
 * vous avez marqué privé. Sans clé, l'application reste complète : chaque bouton
 * « assistant » a son équivalent hors ligne.
 */
object Assistant {

    /**
     * Retire les puces, numéros de liste et blocs de code que les modèles ajoutent parfois.
     *
     * Attention : dans les formats à barres verticales, le premier champ est souvent un
     * chiffre qui compte (le numéro du jour, le nombre de séances). On ne retire donc les
     * numéros de liste que sur les lignes de texte libre.
     */
    private fun cleanLines(raw: String): List<String> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("```") }
            .map { line ->
                val withoutBullet = line.trimStart('-', '•', '*', '·', ' ').trim()
                if (withoutBullet.contains('|')) withoutBullet
                else withoutBullet.trimStart { c -> c.isDigit() }.trimStart('.', ')', ' ').trim()
            }
            .filter { it.isNotBlank() }
            .toList()

    private fun fields(line: String): List<String> = line.split("|").map { it.trim() }

    // ----- 1. Menus de la semaine -----

    data class MealSuggestion(
        val dayIndex: Int,
        val slot: String,
        val title: String,
        val ingredients: String,
        val quantities: String = "",
        val calories: Int = 0
    )

    private fun menuSystem(people: Int): String =
        "Tu proposes des menus familiaux français simples, pour $people personnes. " +
            "Réponds UNIQUEMENT par des lignes au format exact :\n" +
            "jour|creneau|plat|ingredients|par_personne|kcal\n" +
            "jour = 0 à 6 (0 = lundi). creneau = matin, midi ou soir. " +
            "ingredients = les courses pour $people personnes, séparées par des virgules, " +
            "chacune sous la forme « quantité unité nom » (ex. : 400 g pâtes, 3 œufs, 1 oignon). " +
            "par_personne = ce qu'il y a dans UNE assiette (ex. : 120 g pâtes, 1 œuf, 80 g sauce). " +
            "kcal = calories d'une assiette, nombre entier seul, sans unité. " +
            "Pas de titre, pas d'introduction, pas de commentaire, pas de puces."

    private fun parseMeal(line: String): MealSuggestion? {
        val parts = fields(line)
        if (parts.size < 4) return null
        val day = parts[0].filter { it.isDigit() }.toIntOrNull() ?: return null
        val slot = parts[1].lowercase()
        if (day !in 0..6 || slot !in listOf("matin", "midi", "soir")) return null
        return MealSuggestion(
            dayIndex = day,
            slot = slot,
            title = parts[2],
            ingredients = parts[3],
            quantities = parts.getOrElse(4) { "" },
            calories = parts.getOrElse(5) { "" }.filter { it.isDigit() }.toIntOrNull() ?: 0
        )
    }

    /** Envoie : uniquement vos contraintes de repas et le nombre de couverts. */
    suspend fun suggestWeekMenus(apiKey: String, constraints: String, people: Int = 2): List<MealSuggestion> {
        val prompt = buildString {
            append("Propose 21 repas pour la semaine (matin, midi et soir, du lundi au dimanche). ")
            append("Varié, de saison, réaliste en semaine.")
            if (constraints.isNotBlank()) append(" Contraintes : ${constraints.trim()}.")
        }
        return cleanLines(Ai.ask(apiKey, menuSystem(people), prompt, maxTokens = 8000))
            .filter { it.contains('|') }
            .mapNotNull { parseMeal(it) }
    }

    /**
     * Refaire UN repas à la demande : « plus léger », « il me reste du poulet »,
     * « sans gluten », « on est quatre ce soir ».
     *
     * Envoie : le repas actuel, le créneau, et votre consigne. Rien d'autre.
     */
    suspend fun reworkMeal(
        apiKey: String,
        slot: String,
        currentTitle: String,
        currentIngredients: String,
        instruction: String,
        people: Int,
        targetCalories: Int
    ): MealSuggestion? {
        val system =
            "Tu réécris UN repas pour $people personnes. " +
                "Réponds UNIQUEMENT par une seule ligne au format exact :\n" +
                "0|$slot|plat|ingredients|par_personne|kcal\n" +
                "ingredients = les courses pour $people personnes. " +
                "par_personne = ce qu'il y a dans une assiette. " +
                "kcal = calories d'une assiette, nombre entier seul. " +
                "Pas de commentaire, pas de puce, une seule ligne."
        val prompt = buildString {
            append("Repas actuel ($slot) : ")
            append(currentTitle.ifBlank { "aucun" })
            if (currentIngredients.isNotBlank()) append(" — ingrédients : $currentIngredients")
            append(".\nConsigne : ${instruction.ifBlank { "propose autre chose, plus simple" }}.")
            if (targetCalories > 0) {
                val share = when (slot) { "matin" -> 0.25; "midi" -> 0.4; else -> 0.35 }
                append("\nViser environ ${(targetCalories * share).toInt()} kcal par assiette.")
            }
        }
        return cleanLines(Ai.ask(apiKey, system, prompt, maxTokens = 1500))
            .firstOrNull { it.contains('|') }
            ?.let { parseMeal(it) }
    }

    /**
     * Une phrase sur l'évolution du poids et UN levier concret.
     * Envoie : une suite de nombres et l'objectif. Aucune date, aucun nom.
     */
    suspend fun readWeight(apiKey: String, kilos: List<Double>, target: Double): String {
        if (kilos.size < 2) return ""
        val system =
            "Tu commentes une évolution de poids en deux phrases maximum, en français, " +
                "sans jugement, sans culpabilisation, sans conseil médical. " +
                "Première phrase : la tendance. Deuxième phrase : UN levier concret et " +
                "réaliste à essayer cette semaine. Pas de puce, pas de titre."
        val serie = kilos.joinToString(", ") { String.format(java.util.Locale.FRANCE, "%.1f", it) }
        val prompt = buildString {
            append("Pesées successives, de la plus ancienne à la plus récente : $serie kg.")
            if (target > 0) append(" Objectif : ${String.format(java.util.Locale.FRANCE, "%.1f", target)} kg.")
        }
        return cleanLines(Ai.ask(apiKey, system, prompt, maxTokens = 700)).joinToString(" ")
    }

    // ----- 2. Premiers pas d'un objectif -----

    private const val STEPS_SYSTEM =
        "Tu aides à démarrer un objectif personnel. Réponds UNIQUEMENT par 3 lignes, " +
            "une action par ligne, sans numérotation ni puce. " +
            "Chaque action est concrète, faisable en moins de 30 minutes, et formulée à l'infinitif. " +
            "La première doit pouvoir être faite aujourd'hui. Pas de conseil santé, pas de jugement."

    /** Envoie : uniquement l'intitulé de l'objectif. */
    suspend fun suggestFirstSteps(apiKey: String, goalTitle: String): List<String> =
        cleanLines(Ai.ask(apiKey, STEPS_SYSTEM, "Objectif : ${goalTitle.trim()}", maxTokens = 2000))
            .filter { it.length > 3 }
            .take(3)

    // ----- 3. Plan complet d'un objectif -----

    data class GoalPlan(
        val sessionsPerWeek: Int,
        val minutesPerSession: Int,
        val preferredTime: String,   // matin | midi | soir
        val preferredDays: List<Int>, // 1 = lundi … 7 = dimanche
        val nextAction: String
    )

    private const val PLAN_SYSTEM =
        "Tu transformes un objectif vague en rythme hebdomadaire tenable sur la durée. " +
            "Réponds UNIQUEMENT par une seule ligne au format exact :\n" +
            "seances|minutes|moment|jours|premiere_action\n" +
            "seances = nombre de séances par semaine (1 à 7, reste modeste : on vise la régularité). " +
            "minutes = durée d'une séance (10 à 90). " +
            "moment = matin, midi ou soir. " +
            "jours = numéros séparés par des virgules (1 = lundi … 7 = dimanche), autant que de séances, " +
            "espacés dans la semaine. " +
            "premiere_action = la toute première chose à faire, concrète, en moins de 30 minutes. " +
            "Pas d'introduction, pas de commentaire."

    /** Envoie : uniquement l'intitulé de l'objectif et son domaine de vie. */
    suspend fun suggestGoalPlan(apiKey: String, goalTitle: String, domain: String): GoalPlan? {
        val raw = Ai.ask(
            apiKey, PLAN_SYSTEM,
            "Objectif : ${goalTitle.trim()}\nDomaine de vie : $domain",
            maxTokens = 2000
        )
        val line = cleanLines(raw).firstOrNull { it.contains('|') } ?: return null
        val parts = fields(line)
        if (parts.size < 5) return null
        val sessions = parts[0].filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 7) ?: return null
        val minutes = parts[1].filter { it.isDigit() }.toIntOrNull()?.coerceIn(5, 180) ?: return null
        val moment = parts[2].lowercase().let { if (it in listOf("matin", "midi", "soir")) it else "soir" }
        val days = parts[3].split(",")
            .mapNotNull { it.trim().filter { c -> c.isDigit() }.toIntOrNull() }
            .filter { it in 1..7 }
            .distinct()
            .take(sessions)
        return GoalPlan(
            sessionsPerWeek = sessions,
            minutesPerSession = minutes,
            preferredTime = moment,
            preferredDays = days.ifEmpty { defaultDays(sessions) },
            nextAction = parts[4]
        )
    }

    /** Répartition de secours quand le modèle ne donne pas de jours exploitables. */
    private fun defaultDays(sessions: Int): List<Int> = when (sessions) {
        1 -> listOf(3)
        2 -> listOf(2, 5)
        3 -> listOf(1, 3, 5)
        4 -> listOf(1, 3, 5, 6)
        5 -> listOf(1, 2, 3, 4, 5)
        6 -> listOf(1, 2, 3, 4, 5, 6)
        else -> listOf(1, 2, 3, 4, 5, 6, 7)
    }

    // ----- 4. Priorité de la semaine (revue du dimanche) -----

    data class WeekAdvice(val priority: String, val abandon: String)

    private const val WEEK_SYSTEM =
        "Tu aides à choisir UNE priorité pour la semaine, et UNE chose à laisser tomber. " +
            "Le principe : la seule chose qui, si elle avance, rend le reste plus simple ; " +
            "et l'essentialisme, qui impose de renoncer à quelque chose. " +
            "Réponds UNIQUEMENT par deux lignes au format exact :\n" +
            "PRIORITE|…\nABANDON|…\n" +
            "Chaque texte fait moins de 12 mots, formulé simplement, sans jargon."

    /** Envoie : vos objectifs actifs non privés et vos notes en attente. */
    suspend fun suggestWeekPriority(
        apiKey: String,
        goals: List<String>,
        inbox: List<String>
    ): WeekAdvice? {
        val prompt = buildString {
            append("Objectifs en cours :\n")
            if (goals.isEmpty()) append("- aucun\n") else goals.forEach { append("- $it\n") }
            append("\nEn attente de décision :\n")
            if (inbox.isEmpty()) append("- rien\n") else inbox.take(15).forEach { append("- $it\n") }
        }
        val lines = cleanLines(Ai.ask(apiKey, WEEK_SYSTEM, prompt, maxTokens = 2000))
        val priority = lines.firstOrNull { it.uppercase().startsWith("PRIORITE") }
            ?.substringAfter('|', "")?.trim().orEmpty()
        val abandon = lines.firstOrNull { it.uppercase().startsWith("ABANDON") }
            ?.substringAfter('|', "")?.trim().orEmpty()
        if (priority.isBlank()) return null
        return WeekAdvice(priority, abandon)
    }

    // ----- 5. Préparer demain -----

    data class DayAdvice(val priority: String, val secondary: List<String>)

    private const val DAY_SYSTEM =
        "Tu prépares la journée de quelqu'un. UNE priorité, et au plus deux tâches secondaires. " +
            "Réponds UNIQUEMENT par des lignes au format exact :\n" +
            "PRIORITE|…\nSECONDAIRE|…\nSECONDAIRE|…\n" +
            "La priorité est la tâche qui compte le plus, faisable dans la journée. " +
            "Chaque texte fait moins de 10 mots, à l'infinitif. Pas de commentaire."

    /** Envoie : la priorité de votre semaine, vos objectifs non privés, vos tâches non planifiées. */
    suspend fun suggestTomorrow(
        apiKey: String,
        weekPriority: String,
        goals: List<String>,
        backlog: List<String>
    ): DayAdvice? {
        val prompt = buildString {
            append("Priorité de la semaine : ${weekPriority.ifBlank { "non définie" }}\n\n")
            append("Objectifs en cours :\n")
            if (goals.isEmpty()) append("- aucun\n") else goals.forEach { append("- $it\n") }
            append("\nTâches en attente :\n")
            if (backlog.isEmpty()) append("- aucune\n") else backlog.take(15).forEach { append("- $it\n") }
        }
        val lines = cleanLines(Ai.ask(apiKey, DAY_SYSTEM, prompt, maxTokens = 2000))
        val priority = lines.firstOrNull { it.uppercase().startsWith("PRIORITE") }
            ?.substringAfter('|', "")?.trim().orEmpty()
        if (priority.isBlank()) return null
        val secondary = lines.filter { it.uppercase().startsWith("SECONDAIRE") }
            .mapNotNull { it.substringAfter('|', "").trim().ifBlank { null } }
            .take(2)
        return DayAdvice(priority, secondary)
    }

    // ----- 6. Clarifier une note capturée -----

    data class Clarified(val action: String, val whenLabel: String) // aujourdhui | demain | semaine | inbox

    private const val CAPTURE_SYSTEM =
        "Tu transformes une note jetée à la volée en action concrète, " +
            "en te demandant : quelle est la toute prochaine action physique ? " +
            "Réponds UNIQUEMENT par une ligne au format exact :\n" +
            "action|quand\n" +
            "action = moins de 10 mots, commence par un verbe à l'infinitif. " +
            "quand = aujourdhui, demain, semaine ou inbox (inbox si ça demande encore réflexion). " +
            "Pas de commentaire."

    /** Envoie : uniquement la note que vous venez d'écrire. */
    suspend fun clarifyCapture(apiKey: String, text: String): Clarified? {
        val line = cleanLines(Ai.ask(apiKey, CAPTURE_SYSTEM, "Note : ${text.trim()}", maxTokens = 1000))
            .firstOrNull { it.contains('|') } ?: return null
        val parts = fields(line)
        if (parts.size < 2 || parts[0].isBlank()) return null
        val whenLabel = parts[1].lowercase()
            .replace("'", "").replace("î", "i").replace("é", "e")
        val normalized = when {
            whenLabel.startsWith("aujourd") -> "aujourdhui"
            whenLabel.startsWith("demain") -> "demain"
            whenLabel.startsWith("semaine") -> "semaine"
            else -> "inbox"
        }
        return Clarified(parts[0], normalized)
    }

    // ----- 7. Rayon d'un ingrédient inconnu -----

    private const val AISLE_SYSTEM =
        "Tu ranges des courses par rayon de supermarché. " +
            "Réponds UNIQUEMENT par des lignes au format exact :\n" +
            "article|rayon\n" +
            "rayon doit être exactement l'un de : Fruits & légumes, Boucherie & poisson, Crèmerie, " +
            "Épicerie, Boulangerie, Surgelés, Boissons, Maison, Divers. " +
            "Reprends l'article tel quel. Pas de commentaire."

    /** Envoie : uniquement les articles que l'application n'a pas su classer. */
    suspend fun classifyAisles(apiKey: String, labels: List<String>): Map<String, String> {
        if (labels.isEmpty()) return emptyMap()
        val prompt = labels.take(40).joinToString("\n") { "- $it" }
        return cleanLines(Ai.ask(apiKey, AISLE_SYSTEM, prompt, maxTokens = 3000))
            .filter { it.contains('|') }
            .mapNotNull { line ->
                val parts = fields(line)
                if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) null
                else parts[0] to parts[1]
            }
            .toMap()
    }

    // ----- 8. Un pacte d'écran réaliste -----

    data class PacteAdvice(
        val limitMinutes: Int,
        val curfewStart: String,
        val curfewEnd: String,
        val why: String
    )

    private const val PACTE_SYSTEM =
        "Tu proposes un engagement d'écran tenable, à partir d'un usage réellement mesuré. " +
            "Une marche trop haute est abandonnée en trois jours : vise une baisse d'environ " +
            "un quart, jamais plus de la moitié. " +
            "Réponds UNIQUEMENT par une ligne au format exact :\n" +
            "limite|debut|fin|raison\n" +
            "limite = minutes par jour sur les réseaux. debut et fin = heures du couvre-feu au " +
            "format HH:MM. raison = moins de 15 mots, encourageant, sans culpabiliser. " +
            "Pas de commentaire."

    /** Envoie : uniquement vos moyennes d'écran et votre heure de lever. */
    suspend fun suggestPacte(
        apiKey: String,
        avgSocialMinutes: Int,
        avgUnlocks: Int,
        wakeTime: String
    ): PacteAdvice? {
        val prompt = "Moyenne réseaux sociaux : $avgSocialMinutes min/jour\n" +
            "Déverrouillages : $avgUnlocks par jour\n" +
            "Heure de lever souhaitée : $wakeTime"
        val line = cleanLines(Ai.ask(apiKey, PACTE_SYSTEM, prompt, maxTokens = 1500))
            .firstOrNull { it.contains('|') } ?: return null
        val parts = fields(line)
        if (parts.size < 4) return null
        val limit = parts[0].filter { it.isDigit() }.toIntOrNull()?.coerceIn(5, 480) ?: return null
        val start = normalizeTime(parts[1]) ?: return null
        val end = normalizeTime(parts[2]) ?: return null
        return PacteAdvice(limit, start, end, parts[3])
    }

    private fun normalizeTime(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 3) return null
        val hour = digits.take(digits.length - 2).toIntOrNull() ?: return null
        val minute = digits.takeLast(2).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return String.format("%02d:%02d", hour, minute)
    }

    // ----- 9. Lecture de la semaine (sommeil, pas, sport) -----

    private const val HEALTH_SYSTEM =
        "Tu commentes une semaine de sommeil et d'activité en UNE phrase de moins de 25 mots. " +
            "Factuel et bienveillant : tu constates, tu ne prescris pas, tu ne juges pas, " +
            "et tu ne donnes aucun conseil médical. Termine par un seul levier simple à essayer. " +
            "Pas de puce, pas de titre."

    /** Envoie : uniquement vos moyennes de la semaine (jamais celles de l'autre). */
    suspend fun readWeek(
        apiKey: String,
        avgSleepMinutes: Int,
        avgSteps: Int,
        exerciseMinutes: Int
    ): String {
        val prompt = "Sommeil moyen : ${avgSleepMinutes / 60} h ${avgSleepMinutes % 60} min\n" +
            "Pas par jour : $avgSteps\n" +
            "Sport dans la semaine : $exerciseMinutes min"
        return cleanLines(Ai.ask(apiKey, HEALTH_SYSTEM, prompt, maxTokens = 1500))
            .joinToString(" ")
            .trim()
    }

    // ----- 10. Bilan de la semaine passée -----

    /** Ce qui a marché, ce qui a coincé, le levier à essayer. */
    data class WeekReview(val worked: String, val stuck: String, val lever: String)

    private const val REVIEW_SYSTEM =
        "Tu fais le bilan d'une semaine à partir de chiffres. " +
            "Réponds UNIQUEMENT par trois lignes, dans cet ordre, sans titre ni puce :\n" +
            "ligne 1 : ce qui a marché, une phrase\n" +
            "ligne 2 : ce qui a coincé, une phrase, sans reproche\n" +
            "ligne 3 : UN seul levier concret pour la semaine qui vient, une phrase\n" +
            "Jamais de culpabilisation, jamais de conseil médical, jamais de note ou de score."

    /**
     * Envoie : uniquement vos chiffres agrégés de la semaine et votre priorité.
     * Ni les données du partenaire, ni le détail jour par jour de la santé.
     */
    suspend fun reviewWeek(apiKey: String, facts: String): WeekReview? {
        val lines = cleanLines(Ai.ask(apiKey, REVIEW_SYSTEM, facts, maxTokens = 1200))
            .filter { it.length > 3 }
        if (lines.isEmpty()) return null
        return WeekReview(
            worked = lines.getOrElse(0) { "" },
            stuck = lines.getOrElse(1) { "" },
            lever = lines.getOrElse(2) { "" }
        )
    }

    // ----- 11. Ce qu'on abandonne -----

    private const val ABANDON_SYSTEM =
        "Tu aides à renoncer à quelque chose pour la semaine qui vient (méthode " +
            "essentialiste). Réponds UNIQUEMENT par trois lignes, une proposition par " +
            "ligne, sans puce ni numéro. Chaque proposition est une chose précise à " +
            "arrêter, refuser ou reporter, en moins de 12 mots. Rien de moralisateur."

    /** Envoie : votre priorité, vos objectifs non privés, le nombre de tâches en attente. */
    suspend fun suggestAbandon(
        apiKey: String,
        priority: String,
        goals: List<String>,
        pendingTasks: Int
    ): List<String> {
        val prompt = buildString {
            append("Priorité de la semaine : ${priority.ifBlank { "pas encore choisie" }}.\n")
            if (goals.isNotEmpty()) append("Objectifs en cours : ${goals.joinToString(", ")}.\n")
            append("Tâches déjà en attente : $pendingTasks.")
        }
        return cleanLines(Ai.ask(apiKey, ABANDON_SYSTEM, prompt, maxTokens = 800))
            .filter { it.length > 3 }
            .take(3)
    }

    // ----- 12. Vider la boîte de réception d'un coup -----

    private const val INBOX_SYSTEM =
        "Tu transformes des notes jetées en vrac en actions concrètes. " +
            "Réponds UNIQUEMENT par des lignes au format exact :\n" +
            "numero|action|quand\n" +
            "numero = le numéro de la note, tel quel. " +
            "action = moins de 10 mots, commence par un verbe à l'infinitif. " +
            "quand = aujourdhui, demain, semaine ou inbox (inbox si ça demande encore " +
            "réflexion, ou si ce n'est pas une action). " +
            "Une ligne par note, dans l'ordre. Pas de commentaire, pas de puce."

    data class InboxAction(val index: Int, val action: String, val whenLabel: String)

    /** Envoie : uniquement le texte de vos notes en attente. */
    suspend fun inboxToActions(apiKey: String, notes: List<String>): List<InboxAction> {
        if (notes.isEmpty()) return emptyList()
        val prompt = notes.take(25).mapIndexed { i, n -> "$i. ${n.trim()}" }.joinToString("\n")
        return cleanLines(Ai.ask(apiKey, INBOX_SYSTEM, prompt, maxTokens = 2500))
            .filter { it.contains('|') }
            .mapNotNull { line ->
                val parts = fields(line)
                if (parts.size < 3) return@mapNotNull null
                val index = parts[0].filter { it.isDigit() }.toIntOrNull() ?: return@mapNotNull null
                if (parts[1].isBlank()) return@mapNotNull null
                val raw = parts[2].lowercase().replace("'", "").replace("é", "e")
                val whenLabel = when {
                    raw.startsWith("aujourd") -> "aujourdhui"
                    raw.startsWith("demain") -> "demain"
                    raw.startsWith("semaine") -> "semaine"
                    else -> "inbox"
                }
                InboxAction(index, parts[1], whenLabel)
            }
    }

    // ----- 13. Transformer une idée de catégorie en plan d'action -----

    /**
     * [kind] vaut « routine » (ça revient chaque semaine) ou « tache » (ça se fait
     * une fois). [steps] sont les actions concrètes, dans l'ordre.
     */
    data class ActionPlan(val kind: String, val steps: List<String>, val note: String)

    private const val ACTION_PLAN_SYSTEM =
        "Tu transformes une intention en plan d'action concret pour un couple. " +
            "Réponds UNIQUEMENT par des lignes, sans puce ni numéro :\n" +
            "ligne 1 : exactement « routine » ou « tache » — routine si ça doit revenir " +
            "régulièrement, tache si ça se règle une fois\n" +
            "ligne 2 : une phrase qui dit pourquoi, moins de 20 mots\n" +
            "lignes suivantes : de 2 à 5 actions concrètes, dans l'ordre, chacune " +
            "commençant par un verbe à l'infinitif et faisable en moins d'une heure.\n" +
            "Pas de titre, pas de commentaire."

    /** Envoie : la catégorie, l'intitulé et la description que vous avez écrits. */
    suspend fun planAction(
        apiKey: String,
        category: String,
        title: String,
        description: String
    ): ActionPlan? {
        val prompt = buildString {
            append("Catégorie : $category.\n")
            append("Intitulé : ${title.trim()}.")
            if (description.isNotBlank()) append("\nPrécisions : ${description.trim()}.")
        }
        val lines = cleanLines(Ai.ask(apiKey, ACTION_PLAN_SYSTEM, prompt, maxTokens = 1500))
            .filter { it.length > 1 }
        if (lines.size < 2) return null
        val kind = if (lines[0].lowercase().startsWith("routine")) "routine" else "tache"
        return ActionPlan(
            kind = kind,
            steps = lines.drop(2).filter { it.length > 3 }.take(5),
            note = lines[1]
        )
    }
}
