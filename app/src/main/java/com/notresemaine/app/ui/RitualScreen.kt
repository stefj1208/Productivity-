package com.notresemaine.app.ui

import android.content.Intent
import android.provider.AlarmClock
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Tips
import com.notresemaine.app.ui.theme.accentFor
import kotlinx.coroutines.delay

/**
 * Rituel du matin (S.A.V.E.R.S. du Miracle Morning) :
 * séquence personnalisable, minuteur enchaîné, série de jours, réveil réglable en un tap.
 */
@Composable
fun RitualScreen(vm: AppViewModel, settings: AppSettings, onDone: () -> Unit) {
    val context = LocalContext.current
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)

    LaunchedEffect(myId) { vm.ensureRitual() }

    val steps by remember(myId) { vm.repo.db.ritual().steps(myId) }
        .collectAsState(initial = emptyList())
    val logs by remember { vm.repo.db.ritual().logs() }
        .collectAsState(initial = emptyList())

    val enabledSteps = steps.filter { it.enabled }
    val totalMinutes = enabledSteps.sumOf { it.minutes }
    val streak = vm.repo.ritualStreak(logs, myId)
    val doneToday = logs.any { it.userId == myId && it.date == Dates.todayIso() && !it.deleted }

    var running by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    if (running && enabledSteps.isNotEmpty()) {
        val step = enabledSteps.getOrNull(stepIndex)
        LaunchedEffect(stepIndex) { secondsLeft = (step?.minutes ?: 0) * 60 }
        LaunchedEffect(stepIndex, secondsLeft > 0) {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "${stepIndex + 1}/${enabledSteps.size}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = step?.name ?: "", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                style = MaterialTheme.typography.displaySmall,
                color = if (secondsLeft == 0) accent else MaterialTheme.colorScheme.onBackground
            )
            if (secondsLeft == 0) {
                Text(
                    text = "Étape terminée",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { running = false }) { Text("Interrompre") }
            BigButton(
                text = if (stepIndex == enabledSteps.size - 1) "Terminer le rituel" else "Étape suivante",
                onClick = {
                    if (stepIndex == enabledSteps.size - 1) {
                        vm.completeRitual(totalMinutes)
                        running = false
                        onDone()
                    } else {
                        stepIndex++
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        return
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
            Spacer(Modifier.height(20.dp))
            Text("Rituel du matin", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            TipCard(Tips.morning())
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    doneToday -> "Fait ce matin ✓ · série : $streak jours"
                    streak > 0 -> "Série en cours : $streak jours"
                    else -> "$totalMinutes minutes, en ${enabledSteps.size} étapes"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = accent
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel(if (editing) "RÉGLER LA SÉQUENCE" else "LA SÉQUENCE")
            steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (step.enabled) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (editing) {
                        OutlinedTextField(
                            value = step.minutes.toString(),
                            onValueChange = { text ->
                                text.toIntOrNull()?.let { m ->
                                    vm.saveRitualStep(step.copy(minutes = m.coerceIn(1, 60)))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fillMaxWidth(0.3f)
                        )
                        Switch(
                            checked = step.enabled,
                            onCheckedChange = { vm.saveRitualStep(step.copy(enabled = it)) }
                        )
                    } else {
                        Text(
                            text = "${step.minutes} min",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            TextButton(onClick = { editing = !editing }) {
                Text(if (editing) "Terminé" else "Modifier la séquence")
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("RÉVEIL")
            var alarm by remember(settings.wakeAlarm) { mutableStateOf(settings.wakeAlarm) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = alarm,
                    onValueChange = { alarm = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.32f)
                )
                OutlinedButton(
                    onClick = {
                        val parts = alarm.split(":")
                        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 5
                        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        vm.setWakeAlarm(alarm)
                        context.startActivity(
                            Intent(AlarmClock.ACTION_SET_ALARM)
                                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                                .putExtra(AlarmClock.EXTRA_MESSAGE, "Rituel du matin")
                                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        )
                    },
                    enabled = Dates.isValidTime(alarm),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .height(48.dp)
                ) { Text("Régler le réveil") }
            }
            Spacer(Modifier.height(16.dp))
        }

        TextButton(onClick = onDone) { Text("Retour") }
        BigButton(
            text = if (doneToday) "Refaire quand même" else "Commencer ($totalMinutes min)",
            enabled = enabledSteps.isNotEmpty(),
            onClick = {
                stepIndex = 0
                running = true
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
