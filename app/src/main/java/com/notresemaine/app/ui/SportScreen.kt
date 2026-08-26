package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.accentFor

private val DAY_LETTERS = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

/**
 * Sport : les séances de la semaine, ce qui a été fait, et un programme
 * que l'assistant peut bâtir.
 *
 * Une séance est une tâche comme les autres — elle vit dans le même planning,
 * elle se coche au même endroit. Un onglet sport séparé qui tiendrait ses
 * propres séances serait un deuxième agenda à tenir à jour.
 */
@Composable
fun SportScreen(vm: AppViewModel, settings: AppSettings, onGoals: () -> Unit, onBack: () -> Unit) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val weekStart = Dates.weekStartIso()
    val days = Dates.daysOfWeek(weekStart)

    val weekTasks by remember(myId, weekStart) { vm.repo.db.tasks().byWeek(myId, weekStart) }
        .collectAsState(initial = emptyList())
    val healthDays by remember(weekStart) { vm.repo.db.health().since(days.first()) }
        .collectAsState(initial = emptyList())
    val goals by remember { vm.repo.db.goals().all() }.collectAsState(initial = emptyList())

    val aiBusy by vm.aiBusy.collectAsState()
    val program by vm.aiSport.collectAsState()

    val sessions = weekTasks.filter { it.isSport }
    val doneSessions = sessions.count { it.done }
    val myHealth = healthDays.filter { it.userId == myId }
    val minutes = myHealth.sumOf { it.exerciseMinutes }
    val sportGoals = goals.filter { it.userId == myId && it.active && it.domain == "sante" }

    var level by remember { mutableStateOf("") }
    var perWeek by remember { mutableStateOf("3") }
    var aim by remember { mutableStateOf("") }
    var newSession by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "🏃 Sport",
            subtitle = if (sessions.isEmpty()) "Aucune séance cette semaine"
            else "$doneSessions séance(s) faite(s) sur ${sessions.size}",
            onBack = onBack
        )

        // ----- Où j'en suis -----
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProgressRing(
                progress = if (sessions.isEmpty()) 0f else doneSessions / sessions.size.toFloat(),
                center = if (sessions.isEmpty()) "—" else "$doneSessions/${sessions.size}",
                label = "séances",
                accent = accent,
                size = 96.dp
            )
            ProgressRing(
                progress = (minutes / 150f).coerceAtMost(1f),
                center = "$minutes",
                label = "min mesurées",
                accent = accent,
                size = 96.dp
            )
        }

        Spacer(Modifier.height(16.dp))
        MiniBarChart(
            values = days.map { d ->
                (myHealth.firstOrNull { it.date == d }?.exerciseMinutes ?: 0).toFloat()
            },
            labels = listOf("L", "M", "M", "J", "V", "S", "D"),
            accent = accent,
            valueLabel = { "${it.toInt()}" }
        )

        // ----- Les séances de la semaine -----
        Spacer(Modifier.height(20.dp))
        SectionLabel("MES SÉANCES DE LA SEMAINE")
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoiceField(
                value = newSession,
                onValueChange = { newSession = it },
                placeholder = "Ex. : course 30 min",
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                vm.addTask(newSession, null, weekStart, isSport = true)
                newSession = ""
            }, enabled = newSession.isNotBlank()) { Text("Ajouter") }
        }

        if (sessions.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            EmptyState(
                emoji = "🏃",
                text = "Rien de prévu. Une séance posée sur un jour a dix fois plus de " +
                    "chances d'arriver qu'une bonne intention."
            )
        }
        sessions.forEach { task ->
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                TaskRow(
                    task = task,
                    accent = accent,
                    onToggle = { vm.toggleDone(task.id) },
                    note = task.date?.let { Dates.shortLabel(it) }
                )
                DayChips(
                    weekStart = weekStart,
                    selectedDate = task.date,
                    onSelect = { date -> vm.assignTaskToDay(task.id, date) }
                )
            }
        }

        // ----- Mes objectifs de santé -----
        Spacer(Modifier.height(20.dp))
        SectionLabel("MES OBJECTIFS DE SANTÉ")
        if (sportGoals.isEmpty()) {
            Text(
                text = "Aucun objectif « santé » actif. Un objectif place ses séances " +
                    "automatiquement chaque semaine.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        sportGoals.forEach { goal ->
            Text(
                text = "🎯 ${goal.title} · ${goal.sessionsPerWeek}×/semaine · ${goal.minutesPerSession} min",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        OutlinedButton(
            onClick = onGoals,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("🎯 Gérer mes objectifs") }

        // ----- Le programme de l'assistant -----
        if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("UN PROGRAMME SUR MESURE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pas de micro ici : le champ est étroit, et « débutant » se tape
                // plus vite qu'il ne se dicte.
                OutlinedTextField(
                    value = level,
                    onValueChange = { level = it },
                    label = { Text("Niveau") },
                    placeholder = { Text("débutant") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = perWeek,
                    onValueChange = { perWeek = it },
                    label = { Text("×/sem.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            VoiceField(
                value = aim,
                onValueChange = { aim = it },
                label = "Mon but",
                placeholder = "Ex. : tenir 30 min de course sans m'arrêter",
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            AiButton(
                text = "Bâtir mon programme",
                busy = aiBusy,
                onClick = { vm.buildSportProgramWithAi(level, perWeek.toIntOrNull() ?: 3, aim) },
                modifier = Modifier.padding(top = 8.dp)
            )

            if (program.isEmpty()) {
                Text(
                    text = "N'envoie que votre niveau, la fréquence et votre but. " +
                        "Ce n'est pas un avis médical.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(Modifier.height(12.dp))
                program.forEach { s ->
                    Text(
                        text = "${DAY_LETTERS.getOrElse(s.dayIndex) { "?" }} · ${s.title} · ${s.minutes} min",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.applySportProgram(weekStart) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Poser sur la semaine") }
                    TextButton(onClick = { vm.clearAiSport() }) { Text("Annuler") }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
