package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.notresemaine.app.ai.Ai
import com.notresemaine.app.data.AppSettings

/**
 * L'assistant a son propre écran, et non plus une section perdue au milieu des
 * réglages : c'est une fonctionnalité, pas une préférence.
 *
 * L'écran répond dans l'ordre aux trois questions qu'on se pose avant d'activer
 * quoi que ce soit : à quoi ça sert, qu'est-ce que ça envoie, et comment on l'allume.
 */
@Composable
fun AssistantScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    var enabled by remember(settings.aiEnabled) { mutableStateOf(settings.aiEnabled) }
    var key by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
    val active = settings.aiEnabled && settings.aiApiKey.isNotBlank()

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
                title = "✨ Assistant",
                subtitle = if (active) "Activé · ${Ai.providerLabel(settings.aiApiKey)}" else "Éteint",
                onBack = onBack
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (active) {
                    "Les boutons ✨ sont visibles là où ils servent : Objectifs, revue du " +
                        "dimanche, Préparer demain, bouton +, Menus, Courses, Temps d'écran, Sommeil."
                } else {
                    "Tant qu'il est éteint, aucun bouton ✨ n'apparaît dans l'application. " +
                        "Allumez-le ci-dessous : il servira dans Objectifs, la revue du dimanche, " +
                        "Préparer demain, le bouton +, Menus, Courses, Temps d'écran et Sommeil."
                },
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("CE QU'IL FAIT")
            listOf(
                "🎯" to "Bâtit le rythme d'un objectif : séances, durée, jours, premier pas",
                "🗓️" to "Propose LA priorité de la semaine, et ce qu'on laisse tomber",
                "🌙" to "Choisit la priorité de demain d'après vos objectifs",
                "✏️" to "Transforme une note jetée en action concrète, au bon jour",
                "🍽️" to "Compose une semaine de menus sur mesure",
                "🛒" to "Range les courses restées dans « Divers »",
                "📵" to "Propose un pacte d'écran tenable, fondé sur votre usage réel",
                "😴" to "Lit votre semaine de sommeil en une phrase"
            ).forEach { (emoji, text) ->
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(text = emoji, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Chaque bouton a son équivalent hors ligne, instantané et gratuit. " +
                    "L'application reste entière sans clé.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("CE QUI SORT DU TÉLÉPHONE")
            Text(
                text = "Vos contraintes de menus, l'intitulé d'un objectif, la note que vous " +
                    "venez d'écrire, les titres de vos tâches en attente, vos moyennes d'écran " +
                    "et de sommeil.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Jamais un objectif marqué privé. Jamais quoi que ce soit du partenaire. " +
                    "Jamais le détail jour par jour de votre santé. Jamais vos identifiants.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("LA CLÉ")
            Text(
                text = "Deux clés possibles, l'application reconnaît laquelle toute seule : " +
                    "une clé Google (aistudio.google.com) ou une clé Anthropic (sk-ant-…). " +
                    "Chaque appel vous est facturé par le fournisseur choisi.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activer l'assistant",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            if (enabled) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Clé API (Google ou Anthropic)") },
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Détecté : ${Ai.providerLabel(key)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = "Enregistrer",
            enabled = !enabled || key.isNotBlank(),
            onClick = {
                vm.saveAiSettings(enabled, key)
                onBack()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
