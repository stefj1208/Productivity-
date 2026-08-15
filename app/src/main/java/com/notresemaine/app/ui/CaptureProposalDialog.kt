package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.Dates

private val CHOICES = listOf(
    "aujourdhui" to "Aujourd'hui",
    "demain" to "Demain",
    "semaine" to "Cette semaine",
    "inbox" to "Laisser en note"
)

private fun whenLabelOf(key: String): String =
    CHOICES.firstOrNull { it.first == key }?.second ?: "Cette semaine"

/**
 * Ce que l'assistant a compris — montré avant d'agir.
 *
 * C'est le point le plus important de tout l'assistant : une proposition qu'on
 * ne voit pas est une décision prise à votre place. On affiche donc la note
 * d'origine, l'action reformulée, le jour choisi, et le raisonnement en une
 * phrase. Les trois sont modifiables, et rien n'est enregistré avant « Ajouter ».
 */
@Composable
fun CaptureProposalDialog(
    proposal: AppViewModel.CaptureProposal,
    onAccept: (String, String) -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    var action by remember(proposal.action) { mutableStateOf(proposal.action) }
    var whenKey by remember(proposal.whenLabel) { mutableStateOf(proposal.whenLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✨ Voici ce que j'ai compris") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                // 1. Ce que vous aviez écrit — pour pouvoir comparer.
                Text(
                    text = "VOTRE NOTE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "« ${proposal.original} »",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 2. Le raisonnement, en une phrase.
                if (proposal.why.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text("💭", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = proposal.why,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }

                // 3. L'action proposée, modifiable.
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "L'ACTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. Le jour, modifiable aussi.
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "QUAND",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CHOICES.forEach { (key, label) ->
                    ChoiceRow(
                        text = label + when (key) {
                            "aujourdhui" -> " · ${Dates.shortLabel(Dates.todayIso())}"
                            "demain" -> " · ${Dates.shortLabel(Dates.tomorrowIso())}"
                            else -> ""
                        },
                        selected = whenKey == key,
                        onClick = { whenKey = key }
                    )
                }

                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onReject,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Non — garder ma note telle quelle",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAccept(action, whenKey) },
                enabled = action.isNotBlank()
            ) { Text("Ajouter · ${whenLabelOf(whenKey)}") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}
