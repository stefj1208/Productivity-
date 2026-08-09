package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates

/**
 * Les rappels : quand l'application vous interrompt, et avec quelle force.
 *
 * L'écran montre d'abord ce qui va sonner dans la journée — voir la liste vaut
 * mieux que lire une explication — puis laisse régler l'intensité et les heures.
 */
@Composable
fun RemindersScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    var alertsOn by remember(settings.alertsEnabled) { mutableStateOf(settings.alertsEnabled) }
    var alertSound by remember(settings.alertSound) { mutableStateOf(settings.alertSound) }
    var evening by remember(settings.eveningReminder) { mutableStateOf(settings.eveningReminder) }
    var eveningOn by remember(settings.eveningEnabled) { mutableStateOf(settings.eveningEnabled) }
    var sunday by remember(settings.sundayReminder) { mutableStateOf(settings.sundayReminder) }
    var sundayOn by remember(settings.sundayEnabled) { mutableStateOf(settings.sundayEnabled) }

    val timesOk = Dates.isValidTime(evening) && Dates.isValidTime(sunday)

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
                title = "🔔 Rappels",
                subtitle = if (settings.alertsEnabled) "Alarme plein écran au moment d'agir"
                else "Deux notifications discrètes par jour",
                onBack = onBack
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("CE QUI VOUS RAPPELLE QUOI")
            listOf(
                "🌅" to "Ton rituel du matin, à ${settings.wakeAlarm}",
                "🎯" to "Chaque séance d'objectif, le bon jour au bon moment",
                "🌙" to "Préparer demain, à $evening",
                "🗓️" to "La revue du dimanche, à $sunday",
                "📵" to if (settings.curfewEnabled) "15 minutes avant le couvre-feu de ${settings.curfewStart}"
                else "15 minutes avant le couvre-feu (désactivé pour l'instant)"
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

            Spacer(Modifier.height(20.dp))
            SectionLabel("AVEC QUELLE FORCE")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rappel à chaque action",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = alertsOn, onCheckedChange = { alertsOn = it })
            }
            Text(
                text = if (alertsOn) {
                    "Un écran plein s'allume, même téléphone verrouillé. Deux boutons : " +
                        "c'est parti, ou dans 10 minutes."
                } else {
                    "Seulement « préparer demain » et la revue du dimanche, en notification " +
                        "discrète."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (alertsOn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Son et vibration",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = alertSound, onCheckedChange = { alertSound = it })
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("À QUELLE HEURE")
            ReminderRow("Préparer demain (chaque soir)", evening, eveningOn,
                onTime = { evening = it }, onToggle = { eveningOn = it })
            ReminderRow("Revue du dimanche", sunday, sundayOn,
                onTime = { sunday = it }, onToggle = { sundayOn = it })
            if (!timesOk) {
                Text(
                    "Format d'heure attendu : HH:MM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Si le rappel ne s'affiche pas par-dessus l'écran verrouillé : " +
                    "Paramètres Android → Applications → Notre Semaine → autoriser " +
                    "« Alarmes et rappels » et « Notifications plein écran ». " +
                    "Sur le Honor, vérifiez aussi Batterie → Lancement d'applications → manuel.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = "Enregistrer",
            enabled = timesOk,
            onClick = {
                vm.saveReminders(evening, eveningOn, sunday, sundayOn)
                vm.saveAlerts(alertsOn, alertSound)
                onBack()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun ReminderRow(
    label: String,
    time: String,
    enabled: Boolean,
    onTime: (String) -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = time,
            onValueChange = onTime,
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(0.32f)
        )
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
