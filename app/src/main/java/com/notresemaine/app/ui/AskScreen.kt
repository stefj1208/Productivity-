package com.notresemaine.app.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings

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
            // Le micro est maintenant dans le champ lui-même : le bouton séparé
            // qui vivait à côté faisait doublon.
            VoiceField(
                value = question,
                onValueChange = { question = it },
                placeholder = "Votre question…",
                prompt = "Posez votre question",
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
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
