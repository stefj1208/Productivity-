package com.notresemaine.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Le tuyau vers l'assistant. Deux fournisseurs possibles, une seule porte d'entrée :
 * la clé que vous collez dans Réglages décide lequel est utilisé.
 *
 *  - clé Google (AI Studio)     → Gemini
 *  - clé Anthropic (sk-ant-…)   → Claude
 *
 * Tout passe par de simples requêtes HTTP, avec la bibliothèque réseau déjà présente
 * dans l'application : pas de dépendance supplémentaire, pas de poids inutile sur le
 * téléphone. Rien n'est envoyé tant que l'assistant n'est pas activé.
 */
object Ai {

    class AiException(message: String) : Exception(message)

    const val GOOGLE = "google"
    const val ANTHROPIC = "anthropic"

    private const val GOOGLE_MODEL = "gemini-3.6-flash"
    private const val ANTHROPIC_MODEL = "claude-opus-5"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /** Les clés Anthropic commencent toutes par « sk-ant- » ; tout le reste est traité comme Google. */
    fun providerOf(apiKey: String): String =
        if (apiKey.trim().startsWith("sk-ant-")) ANTHROPIC else GOOGLE

    /** Nom lisible du fournisseur détecté, affiché dans les réglages. */
    fun providerLabel(apiKey: String): String = when {
        apiKey.isBlank() -> "aucune clé"
        providerOf(apiKey) == ANTHROPIC -> "Claude (Anthropic)"
        else -> "Gemini (Google)"
    }

    /**
     * Pose une question et renvoie le texte de la réponse.
     * [system] cadre le format attendu, [prompt] contient la demande.
     */
    suspend fun ask(
        apiKey: String,
        system: String,
        prompt: String,
        maxTokens: Int = 4000
    ): String = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) throw AiException("Aucune clé enregistrée.")
        val text = when (providerOf(key)) {
            ANTHROPIC -> askAnthropic(key, system, prompt, maxTokens)
            else -> askGoogle(key, system, prompt, maxTokens)
        }
        if (text.isBlank()) throw AiException("Réponse vide de l'assistant.")
        text
    }

    /**
     * Même chose, mais avec une photo jointe.
     *
     * L'image part en clair vers le fournisseur, encodée en base64 dans la requête :
     * c'est la seule fonction de l'application où autre chose que du texte quitte le
     * téléphone. Elle n'est appelée que si l'option « analyse photo » est allumée,
     * et la photo n'est jamais conservée ni synchronisée après la réponse.
     */
    suspend fun askWithImage(
        apiKey: String,
        system: String,
        prompt: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        maxTokens: Int = 1500
    ): String = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) throw AiException("Aucune clé enregistrée.")
        if (imageBase64.isBlank()) throw AiException("Photo illisible.")
        val text = when (providerOf(key)) {
            ANTHROPIC -> askAnthropic(key, system, prompt, maxTokens, imageBase64, mimeType)
            else -> askGoogle(key, system, prompt, maxTokens, imageBase64, mimeType)
        }
        if (text.isBlank()) throw AiException("Réponse vide de l'assistant.")
        text
    }

    // ----- Google (Gemini) -----

    private fun askGoogle(
        key: String,
        system: String,
        prompt: String,
        maxTokens: Int,
        imageBase64: String = "",
        mimeType: String = ""
    ): String {
        // L'image d'abord, la consigne ensuite : c'est l'ordre que le modèle
        // interprète le mieux — il regarde, puis il lit ce qu'on lui demande.
        val requestParts = JSONArray()
        if (imageBase64.isNotBlank()) {
            requestParts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", mimeType.ifBlank { "image/jpeg" })
                        .put("data", imageBase64)
                )
            )
        }
        requestParts.put(JSONObject().put("text", prompt))

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put(
                "contents",
                JSONArray().put(JSONObject().put("role", "user").put("parts", requestParts))
            )
            put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens))
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$GOOGLE_MODEL:generateContent")
            .addHeader("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON))
            .build()

        val json = execute(request)
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blocked = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw AiException(
                if (blocked.isNotBlank()) "Demande refusée par le modèle ($blocked)."
                else "Réponse vide de l'assistant."
            )
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val out = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val text = part.optString("text")
                if (text.isNotBlank()) {
                    if (out.isNotEmpty()) out.append('\n')
                    out.append(text)
                }
            }
        }
        if (out.isEmpty() && candidate.optString("finishReason") == "MAX_TOKENS") {
            throw AiException("Réponse trop longue, réessayez avec moins de contraintes.")
        }
        return out.toString().trim()
    }

    // ----- Anthropic (Claude) -----

    private fun askAnthropic(
        key: String,
        system: String,
        prompt: String,
        maxTokens: Int,
        imageBase64: String = "",
        mimeType: String = ""
    ): String {
        val requestContent = JSONArray()
        if (imageBase64.isNotBlank()) {
            requestContent.put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", mimeType.ifBlank { "image/jpeg" })
                            .put("data", imageBase64)
                    )
            )
        }
        requestContent.put(JSONObject().put("type", "text").put("text", prompt))

        val body = JSONObject().apply {
            put("model", ANTHROPIC_MODEL)
            put("max_tokens", maxTokens)
            put("system", system)
            put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", requestContent))
            )
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val json = execute(request)
        if (json.optString("stop_reason") == "refusal") {
            throw AiException("Demande refusée par le modèle. Reformulez, ou utilisez les idées hors ligne.")
        }
        val content = json.optJSONArray("content") ?: return ""
        val out = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") != "text") continue
            val text = block.optString("text")
            if (text.isNotBlank()) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(text)
            }
        }
        return out.toString().trim()
    }

    // ----- Commun -----

    private fun execute(request: Request): JSONObject {
        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            throw AiException("Pas de réseau (${e.message ?: "connexion impossible"}).")
        }
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw AiException(readableError(it.code, raw))
            return try {
                JSONObject(raw)
            } catch (e: Exception) {
                throw AiException("Réponse illisible de l'assistant.")
            }
        }
    }

    /** Traduit les erreurs de l'API en une phrase compréhensible. */
    private fun readableError(code: Int, raw: String): String {
        val detail = runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            400 -> "Demande refusée par le service${suffix(detail)}"
            401, 403 -> "Clé refusée : vérifiez-la dans Réglages."
            404 -> "Modèle indisponible pour cette clé${suffix(detail)}"
            429 -> "Quota atteint pour aujourd'hui. Réessayez plus tard."
            in 500..599 -> "Le service de l'assistant est indisponible. Réessayez plus tard."
            else -> "Erreur $code${suffix(detail)}"
        }
    }

    private fun suffix(detail: String): String =
        if (detail.isBlank()) "." else " : ${detail.take(160)}"
}
