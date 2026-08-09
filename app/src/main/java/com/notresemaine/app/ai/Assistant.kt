package com.notresemaine.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Assistant facultatif, propulsé par l'API Claude avec VOTRE clé.
 *
 * Confidentialité — à lire avant d'activer :
 * ce qui part vers Anthropic se limite au strict nécessaire (contraintes de menus,
 * intitulé d'un objectif). Jamais de sommeil, de temps d'écran, de tâches, ni de
 * données du partenaire. Désactivé, l'application reste complète : la banque
 * d'idées hors ligne fait le même travail sans aucun appel réseau.
 */
object Assistant {

    private const val MODEL = "claude-opus-5"

    class AiException(message: String) : Exception(message)

    private fun client(apiKey: String): AnthropicClient =
        AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    private fun ask(apiKey: String, system: String, prompt: String): String {
        val client = client(apiKey)
        try {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                // Effort bas : la tâche est simple, et le téléphone attend la réponse.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .system(system)
                .addUserMessage(prompt)
                .build()

            val response = client.messages().create(params)
            if (response.stopReason().map { it.toString() }.orElse("") == "refusal") {
                throw AiException("Demande refusée par le modèle. Reformulez ou passez par les idées hors ligne.")
            }
            val out = StringBuilder()
            for (block in response.content()) {
                val text = block.text()
                if (text.isPresent) {
                    if (out.isNotEmpty()) out.append('\n')
                    out.append(text.get().text())
                }
            }
            return out.toString().trim()
        } finally {
            client.close()
        }
    }

    // ----- Menus -----

    data class MealSuggestion(val dayIndex: Int, val slot: String, val title: String, val ingredients: String)

    private const val MENU_SYSTEM =
        "Tu proposes des menus familiaux français simples, pour deux personnes. " +
            "Réponds UNIQUEMENT par des lignes au format exact :\n" +
            "jour|creneau|plat|ingredients\n" +
            "jour = 0 à 6 (0 = lundi). creneau = matin, midi ou soir. " +
            "ingredients = liste séparée par des virgules, chaque ingrédient sous la forme " +
            "« quantité unité nom » (ex. : 400 g pâtes, 3 œufs, 1 oignon). " +
            "Pas de titre, pas d'introduction, pas de commentaire, pas de puces."

    /** Génère une semaine de repas. [constraints] est libre : « végétarien », « rapide le soir »… */
    suspend fun suggestWeekMenus(apiKey: String, constraints: String): List<MealSuggestion> =
        withContext(Dispatchers.IO) {
            val prompt = buildString {
                append("Propose 21 repas pour la semaine (matin, midi et soir, du lundi au dimanche). ")
                append("Varié, de saison, réaliste en semaine.")
                if (constraints.isNotBlank()) append(" Contraintes : ${constraints.trim()}.")
            }
            parseMeals(ask(apiKey, MENU_SYSTEM, prompt))
        }

    private fun parseMeals(raw: String): List<MealSuggestion> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.contains('|') }
            .mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size < 4) return@mapNotNull null
                val day = parts[0].filter { it.isDigit() }.toIntOrNull() ?: return@mapNotNull null
                val slot = parts[1].lowercase()
                if (day !in 0..6 || slot !in listOf("matin", "midi", "soir")) return@mapNotNull null
                MealSuggestion(day, slot, parts[2], parts[3])
            }
            .toList()

    // ----- Premiers pas d'un objectif -----

    private const val STEPS_SYSTEM =
        "Tu aides à démarrer un objectif personnel. Réponds UNIQUEMENT par 3 lignes, " +
            "une action par ligne, sans numérotation ni puce. " +
            "Chaque action est concrète, faisable en moins de 30 minutes, et formulée à l'infinitif. " +
            "La première doit pouvoir être faite aujourd'hui. Pas de conseil santé, pas de jugement."

    suspend fun suggestFirstSteps(apiKey: String, goalTitle: String): List<String> =
        withContext(Dispatchers.IO) {
            val text = ask(apiKey, STEPS_SYSTEM, "Objectif : ${goalTitle.trim()}")
            text.lineSequence()
                .map { it.trim().trimStart('-', '•', '*', ' ').trimStart { c -> c.isDigit() || c == '.' || c == ')' }.trim() }
                .filter { it.length > 3 }
                .take(3)
                .toList()
        }
}
