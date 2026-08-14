package com.notresemaine.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.notresemaine.app.calendar.PhoneCalendar
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates

/**
 * Agenda : brancher l'application sur l'agenda du téléphone.
 *
 * Google Agenda n'est pas contacté directement — Android le synchronise déjà
 * sur l'appareil. On écrit dans l'agenda du compte Google, il remonte dans
 * Google Agenda tout seul, sur tous vos appareils. C'est plus fiable qu'un
 * accès direct, et il n'y a rien à créer chez Google.
 */
@Composable
fun CalendarScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var checks by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val granted = remember(checks) { PhoneCalendar.hasPermission(context) }
    val calendars = remember(checks, granted) { PhoneCalendar.writableCalendars(context) }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { checks++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "📅 Agenda",
            subtitle = if (settings.calendarEnabled && settings.calendarName.isNotBlank())
                "Relié à ${settings.calendarName}" else "Non relié",
            onBack = onBack
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Vos créneaux vont dans l'agenda du téléphone. Si c'est un agenda " +
                "Google, ils apparaissent dans Google Agenda sur tous vos appareils.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel("1 · AUTORISATION")
        CalendarCheck(granted, "Accès à l'agenda")
        if (!granted) {
            OutlinedButton(
                onClick = { ask.launch(PhoneCalendar.PERMISSIONS) },
                modifier = Modifier.height(48.dp)
            ) { Text("Autoriser") }
        }

        if (granted) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("2 · DANS QUEL AGENDA ÉCRIRE")
            if (calendars.isEmpty()) {
                Text(
                    text = "Aucun agenda modifiable trouvé. Ouvrez l'application Agenda " +
                        "et connectez votre compte Google.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            calendars.forEach { cal ->
                ChoiceRow(
                    text = if (cal.account.isBlank() || cal.account == cal.name) cal.name
                    else "${cal.name} · ${cal.account}",
                    selected = cal.id == settings.calendarId,
                    onClick = { vm.chooseCalendar(cal.id, cal.name) }
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("3 · CE QUI SE PASSE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Poser mes créneaux dans l'agenda", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Chaque tâche à laquelle vous donnez une heure devient un " +
                            "rendez-vous. Vos autres événements ne sont jamais touchés.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.calendarEnabled,
                    enabled = settings.calendarId > 0,
                    onCheckedChange = { vm.setCalendarEnabled(it) }
                )
            }

            if (settings.calendarEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { vm.pushWeekToCalendar(context, Dates.weekStartIso()) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Envoyer la semaine en cours") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.pushWeekToCalendar(context, Dates.planningTargetWeekIso()) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Envoyer la semaine suivante") }
                TextButton(onClick = { vm.eraseCalendar(context) }) {
                    Text("Retirer tout ce que l'app a écrit")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "L'inverse marche aussi : ce qui est déjà dans votre agenda " +
                    "apparaît dans la vue de la journée, pour ne pas réserver un créneau " +
                    "déjà pris.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CalendarCheck(ok: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(if (ok) "✅" else "⚠️", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
