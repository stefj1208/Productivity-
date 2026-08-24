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
    var photo by remember(settings.mealPhotoEnabled) { mutableStateOf(settings.mealPhotoEnabled) }
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
                text = if (active) "Les boutons ✨ sont actifs dans l'application."
                else "Éteint : aucun bouton ✨ n'apparaît.",
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
                "😴" to "Lit votre semaine de sommeil en une phrase",
                "📷" to "Estime un repas d'après une photo (à activer séparément, ci-dessous)"
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
                text = "Chaque bouton a son équivalent hors ligne, gratuit.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("CE QUI SORT DU TÉLÉPHONE")
            Text(
                text = "Contraintes de menus · intitulé d'objectif · note écrite · " +
                    "titres de tâches · moyennes d'écran et de sommeil.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Jamais : objectif privé, données du partenaire, détail de santé, " +
                    "identifiants.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("LA CLÉ")
            Text(
                text = "Google (aistudio.google.com) ou Anthropic (sk-ant-…) — reconnue " +
                    "automatiquement. Chaque appel vous est facturé.",
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

            // ----- L'image, décision à part -----
            //
            // Cet interrupteur est séparé de celui du dessus, et c'est délibéré :
            // allumer l'assistant fait sortir du texte qu'on a tapé soi-même,
            // allumer celui-ci fait sortir une photo de sa cuisine. Ce n'est pas
            // la même décision, donc ce n'est pas le même bouton.
            Spacer(Modifier.height(28.dp))
            SectionLabel("ANALYSE PHOTO DES REPAS")
            Text(
                text = "Photographier une assiette pour que l'assistant reconnaisse le plat " +
                    "et estime les calories.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "C'est la seule fonction de l'application qui envoie une image hors du " +
                    "téléphone. La photo n'est ni enregistrée ni synchronisée : elle est " +
                    "effacée dès la réponse reçue. Seul le résultat est conservé.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "À savoir : avec une clé Google gratuite, Google indique pouvoir " +
                    "utiliser ce qui est envoyé pour améliorer ses modèles. Ce n'est pas le " +
                    "cas des clés payantes. À vous de juger ce que vous photographiez.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Les calories lues sur une photo sont un ordre de grandeur, à ±25 % " +
                    "environ : la photo ne dit pas la taille de l'assiette. L'application " +
                    "affiche donc une fourchette, jamais un compte exact — et ne note personne.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Autoriser l'envoi de photos",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = photo && enabled,
                    enabled = enabled,
                    onCheckedChange = { photo = it }
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = "Enregistrer",
            enabled = !enabled || key.isNotBlank(),
            onClick = {
                vm.saveAiSettings(enabled, key)
                // Sans assistant, l'analyse photo n'a plus de moyen de fonctionner :
                // on l'éteint aussi, plutôt que de laisser un réglage qui ment.
                vm.saveMealPhotoSetting(photo && enabled)
                onBack()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
