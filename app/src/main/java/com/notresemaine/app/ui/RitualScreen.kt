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
fun RitualScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onPrepare: (String) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)

    LaunchedEffect(myId) { vm.ensureRitual() }

    val steps by remember(myId) { vm.repo.db.ritual().steps(myId) }
        .collectAsState(initial = emptyList())
    val logs by remember { vm.repo.db.ritual().logs() }
        .collectAsState(initial = emptyList())
    val todayTasks by remember(myId) { vm.repo.db.tasks().byDate(myId, Dates.todayIso()) }
        .collectAsState(initial = emptyList())
    val guides by vm.aiRitual.collectAsState()
    val aiBusy by vm.aiBusy.collectAsState()

    val priorityToday = todayTasks.firstOrNull { it.isPriority }
    val aiReady = settings.aiEnabled && settings.aiApiKey.isNotBlank()

    fun guideOf(name: String): String? = guides[name.lowercase().trim()]

    val enabledSteps = steps.filter { it.enabled }
    val totalMinutes = enabledSteps.sumOf { it.minutes }
    val streak = vm.repo.ritualStreak(logs, myId)
    val doneToday = logs.any { it.userId == myId && it.date == Dates.todayIso() && !it.deleted }

    var running by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var customising by remember { mutableStateOf<com.notresemaine.app.data.RitualStepEntity?>(null) }
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

            // La consigne du jour d'abord, la fiche générale ensuite :
            // « Silence · 5 min » ne dit à personne quoi faire de ces 5 minutes.
            val stepName = step?.name.orEmpty()
            val todayGuide = guideOf(stepName)
            val custom = step?.detail?.ifBlank { null }
            val offline = com.notresemaine.app.data.Rituals.guideFor(stepName)
            if (todayGuide != null) {
                Text(
                    text = "✨ $todayGuide",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else if (custom != null) {
                Text(
                    text = custom,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else if (offline != null) {
                Text(
                    text = offline.start,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = offline.how,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

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
            ScreenHeader(title = "🌅 Rituel du matin", onBack = onDone)
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

            // ----- Sur quoi porte le rituel aujourd'hui -----
            //
            // Un rituel sans journée décidée tourne à vide : on médite sur
            // « rien », on visualise « rien ». Mieux vaut le dire et proposer
            // de décider maintenant — ou, idéalement, la veille au soir.
            Spacer(Modifier.height(16.dp))
            if (priorityToday == null) {
                EmptyState(
                    emoji = "🎯",
                    text = "Ta journée n'est pas encore décidée. Le rituel prend son sens " +
                        "quand il porte sur quelque chose de précis — et se prépare " +
                        "mieux la veille au soir qu'à moitié réveillé.",
                    actionLabel = "Choisir ma priorité",
                    onAction = { onPrepare(Dates.todayIso()) }
                )
            } else {
                SectionLabel("CE MATIN, ÇA PORTE SUR")
                Text(priorityToday.title, style = MaterialTheme.typography.titleMedium)
            }

            if (aiReady) {
                Spacer(Modifier.height(12.dp))
                AiButton(
                    text = if (guides.isEmpty()) "Guider mon rituel d'aujourd'hui"
                    else "Refaire les consignes",
                    busy = aiBusy,
                    onClick = { vm.guideRitualWithAi() },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (guides.isEmpty())
                        "Une consigne concrète par étape, en lien avec ta priorité du jour. " +
                            "N'envoie que le nom de tes étapes, ta priorité et tes objectifs non privés."
                    else "Consignes du jour ✓ — elles s'affichent sous chaque étape.",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (guides.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (guides.isNotEmpty()) {
                    TextButton(onClick = { vm.clearAiRitual() }) { Text("Effacer les consignes") }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel(if (editing) "RÉGLER LA SÉQUENCE" else "LA SÉQUENCE")
            steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (step.enabled) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!editing && step.enabled) {
                            val todayGuide = guideOf(step.name)
                            val detail = todayGuide
                                ?: step.detail.ifBlank { null }
                                ?: com.notresemaine.app.data.Rituals.guideFor(step.name)?.start
                            if (detail != null) {
                                Text(
                                    text = if (todayGuide != null) "✨ $detail" else detail,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (todayGuide != null) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                        TextButton(onClick = { customising = step }) { Text("✏️") }
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

            // Réserver le rituel comme n'importe quel autre rendez-vous : une
            // intention qui n'a pas d'heure dans le planning n'arrive jamais.
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.bookRitualSlot(totalMinutes) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("🕐 Bloquer le créneau dans mon planning") }

            Spacer(Modifier.height(16.dp))
            SectionLabel("RÉVEIL")
            var alarm by remember(settings.wakeAlarm) { mutableStateOf(settings.wakeAlarm) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeField(
                    label = "HEURE DE LEVER",
                    value = alarm,
                    onChange = { alarm = it },
                    modifier = Modifier.fillMaxWidth(0.45f)
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

    // ----- Personnaliser une étape -----
    val custom = customising
    if (custom != null) {
        var name by remember(custom.id) { mutableStateOf(custom.name) }
        var detail by remember(custom.id) {
            mutableStateOf(
                custom.detail.ifBlank {
                    com.notresemaine.app.data.Rituals.guideFor(custom.name)?.start.orEmpty()
                }
            )
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { customising = null },
            title = { Text("Personnaliser l'étape") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom de l'étape") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text("Quoi faire pendant ce temps") },
                        placeholder = { Text("Ex. : 10 respirations lentes, puis relire ma priorité") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Ce texte s'affiche sous l'étape et pendant le minuteur. " +
                            "Laissé vide, la consigne par défaut revient.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveRitualStep(custom.copy(name = name.trim(), detail = detail.trim()))
                        customising = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { customising = null }) { Text("Annuler") }
            }
        )
    }
}
