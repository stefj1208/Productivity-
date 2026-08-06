package com.notresemaine.app.data

/**
 * Bibliothèque d'objectifs « clé en main ».
 * Chaque modèle encode les recommandations des livres :
 * régularité avant volume (One Thing), séances protégées (Deep Work),
 * peu d'objectifs à la fois (Essentialisme), une prochaine action claire (GTD).
 */
data class GoalTemplate(
    val emoji: String,
    val title: String,
    val domain: String,
    val sessionsPerWeek: Int,
    val minutesPerSession: Int,
    val preferredTime: String,   // matin | midi | soir
    val preferredDays: List<Int>, // jours ISO (1 = lundi … 7 = dimanche)
    val nextActionSuggestion: String,
    val why: String              // le principe du livre, en une phrase
)

object GoalTemplates {

    const val MAX_ACTIVE_GOALS = 3 // Essentialisme : au-delà, rien n'avance vraiment

    val all = listOf(
        GoalTemplate(
            emoji = "🗣️",
            title = "Apprendre une langue",
            domain = "apprentissage",
            sessionsPerWeek = 5,
            minutesPerSession = 20,
            preferredTime = "matin",
            preferredDays = listOf(1, 2, 3, 4, 5),
            nextActionSuggestion = "Faire 20 min de langue juste après le café",
            why = "20 min par jour battent 2 h le dimanche : c'est la régularité qui fait la mémoire (One Thing)."
        ),
        GoalTemplate(
            emoji = "🏃",
            title = "(Re)prendre le sport",
            domain = "sante",
            sessionsPerWeek = 3,
            minutesPerSession = 40,
            preferredTime = "matin",
            preferredDays = listOf(1, 3, 6),
            nextActionSuggestion = "Préparer les affaires de sport la veille au soir",
            why = "3 séances fixes dans la semaine : décider une fois, pas trois (Miracle Morning / Essentialisme)."
        ),
        GoalTemplate(
            emoji = "📚",
            title = "Lire davantage",
            domain = "apprentissage",
            sessionsPerWeek = 5,
            minutesPerSession = 15,
            preferredTime = "soir",
            preferredDays = listOf(1, 2, 3, 4, 7),
            nextActionSuggestion = "Poser le livre sur l'oreiller le matin",
            why = "15 min le soir remplacent le téléphone au lit : élimination + remplacement (Semaine de 4 heures)."
        ),
        GoalTemplate(
            emoji = "🎯",
            title = "Projet personnel",
            domain = "travail",
            sessionsPerWeek = 3,
            minutesPerSession = 50,
            preferredTime = "matin",
            preferredDays = listOf(2, 4, 6),
            nextActionSuggestion = "Définir le tout premier livrable, même minuscule",
            why = "Des blocs profonds sans interruption : 3 × 50 min concentrées valent une semaine d'à-peu-près (Deep Work)."
        ),
        GoalTemplate(
            emoji = "🧘",
            title = "Méditer chaque jour",
            domain = "sante",
            sessionsPerWeek = 7,
            minutesPerSession = 10,
            preferredTime = "matin",
            preferredDays = listOf(1, 2, 3, 4, 5, 6, 7),
            nextActionSuggestion = "S'asseoir 10 min avant de toucher le téléphone",
            why = "Le Silence est la première lettre de S.A.V.E.R.S. (Miracle Morning)."
        ),
        GoalTemplate(
            emoji = "💶",
            title = "Reprendre les finances en main",
            domain = "finances",
            sessionsPerWeek = 1,
            minutesPerSession = 30,
            preferredTime = "soir",
            preferredDays = listOf(7),
            nextActionSuggestion = "Lister les 3 plus gros postes de dépense du mois",
            why = "80 % du résultat vient de 20 % des actions : une revue courte mais chaque semaine (Semaine de 4 heures)."
        ),
        GoalTemplate(
            emoji = "✏️",
            title = "Objectif libre",
            domain = "autre",
            sessionsPerWeek = 3,
            minutesPerSession = 30,
            preferredTime = "soir",
            preferredDays = listOf(1, 3, 5),
            nextActionSuggestion = "",
            why = "Un objectif = des séances récurrentes + UNE prochaine action. L'app s'occupe du reste."
        )
    )
}
