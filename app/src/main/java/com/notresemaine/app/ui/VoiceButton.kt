package com.notresemaine.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import java.util.Locale

/**
 * Le bouton micro : dire une phrase, l'application la range au bon endroit.
 *
 * On passe par la dictée du système (la même que le clavier) plutôt que par un
 * enregistrement envoyé quelque part : la voix ne quitte jamais le téléphone,
 * seul le texte reconnu part à l'assistant — et seulement s'il est activé.
 */
@Composable
fun VoiceButton(vm: AppViewModel, settings: AppSettings, modifier: Modifier = Modifier) {
    val ready = settings.aiEnabled && settings.aiApiKey.isNotBlank()

    val listen = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) vm.understandVoice(spoken)
    }

    SmallFloatingActionButton(
        onClick = {
            if (!ready) {
                vm.messages.tryEmit("Activez d'abord l'assistant dans Moi → Assistant.")
                return@SmallFloatingActionButton
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRANCE.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites ce que vous voulez noter")
            }
            runCatching { listen.launch(intent) }.onFailure {
                vm.messages.tryEmit("Aucune dictée vocale disponible sur ce téléphone.")
            }
        },
        modifier = modifier
    ) { Text("🎤", style = MaterialTheme.typography.titleMedium) }
}

private fun kindLabel(kind: String): String = when (kind) {
    "tache" -> "une tâche"
    "menus" -> "les menus de la semaine"
    "poids" -> "une pesée"
    "habitude" -> "une habitude"
    else -> "une note"
}

private fun whenLabel(key: String): String = when (key) {
    "aujourdhui" -> "aujourd'hui · ${Dates.shortLabel(Dates.todayIso())}"
    "demain" -> "demain · ${Dates.shortLabel(Dates.tomorrowIso())}"
    "semaine" -> "cette semaine"
    else -> "à trier plus tard"
}

/**
 * Ce que l'assistant a compris de la phrase dictée — montré avant d'agir,
 * comme partout ailleurs. Se tromper à l'oral est trop facile pour appliquer
 * sans confirmer.
 */
@Composable
fun VoiceProposalDialog(vm: AppViewModel) {
    val command by vm.aiVoice.collectAsState()
    val c = command ?: return

    AlertDialog(
        onDismissRequest = { vm.clearAiVoice() },
        title = { Text("🎤 J'ai compris") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (c.say.isNotBlank()) {
                    Text(c.say, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = "CE QUE ÇA VA FAIRE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ajouter ${kindLabel(c.kind)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(10.dp))
                Text(c.payload, style = MaterialTheme.typography.bodyLarge)
                if (c.detail.isNotBlank()) {
                    Text(
                        text = c.detail,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (c.kind == "tache") {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "QUAND",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(whenLabel(c.whenLabel), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { vm.applyVoice(c.copy(kind = "note")) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = "Non — garder comme simple note",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.applyVoice(c) }) { Text("C'est ça") }
        },
        dismissButton = {
            TextButton(onClick = { vm.clearAiVoice() }) { Text("Annuler") }
        }
    )
}
