package com.notresemaine.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import java.util.Locale

private val EXAMPLES = listOf(
    "Qu'est-ce que j'oublie cette semaine ?",
    "Ma semaine est-elle trop chargée ?",
    "Par quoi je commence demain matin ?",
    "Qu'est-ce que je pourrais laisser tomber ?",
    "Est-ce que j'avance sur mes objectifs ?"
)

/**
 * Poser une question sur sa propre semaine.
 *
 * Les autres boutons ✨ répondent à des questions que l'application a décidées
 * d'avance. Ici, c'est vous qui posez la vôtre — et la réponse s'appuie
 * uniquement sur ce que l'application contient déjà, jamais sur des généralités.
 */
@Composable
fun AskScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val ready = settings.aiEnabled && settings.aiApiKey.isNotBlank()
    val busy by vm.aiBusy.collectAsState()
    val conversation by vm.aiConversation.collectAsState()
    var question by remember { mutableStateOf("") }

    val listen = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let { question = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ScreenHeader(
                title = "💬 Chat",
                subtitle = "Une question sur votre semaine",
                onBack = onBack
            )

            if (!ready) {
                Spacer(Modifier.height(16.dp))
                EmptyState(
                    emoji = "✨",
                    text = "L'assistant est éteint. Activez-le dans Moi → Assistant pour " +
                        "pouvoir lui poser des questions."
                )
                Spacer(Modifier.height(32.dp))
                return@Column
            }

            if (conversation.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("PAR EXEMPLE")
                EXAMPLES.forEach { example ->
                    TextButton(
                        onClick = { question = example },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = example,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            conversation.forEach { exchange ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = exchange.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("✨", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = exchange.answer,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }

            if (conversation.isNotEmpty()) {
                TextButton(onClick = { vm.clearConversation() }) { Text("Effacer l'échange") }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "N'envoie que le résumé de VOS données : ni objectif marqué privé, " +
                    "ni détail de santé, ni rien de votre binôme.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }

        if (ready) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("Votre question…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRANCE.toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Posez votre question")
                    }
                    runCatching { listen.launch(intent) }.onFailure {
                        vm.messages.tryEmit("Aucune dictée vocale disponible sur ce téléphone.")
                    }
                }) { Text("🎤") }
            }
            BigButton(
                text = if (busy) "…" else "Demander",
                enabled = question.isNotBlank() && !busy,
                onClick = {
                    vm.askAssistant(question)
                    question = ""
                },
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
        }
    }
}
