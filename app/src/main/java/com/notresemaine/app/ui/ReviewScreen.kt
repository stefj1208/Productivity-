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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.accentFor

/**
 * Revue du dimanche, écran par écran :
 * 1 Bilan · 2 J'abandonne · 3 LA priorité · 4 Répartition · 5 Sport · 6 Validation
 */
@Composable
fun ReviewScreen(vm: AppViewModel, settings: AppSettings, onDone: () -> Unit) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val weekStart = Dates.weekStartIso()
    val lastWeekStart = Dates.previousWeekStartIso()

    var step by remember { mutableIntStateOf(0) }
    var abandon by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("") }
    var loadedPlan by remember { mutableStateOf(false) }

    val lastWeekTasks by remember(myId) { vm.repo.db.tasks().byWeek(myId, lastWeekStart) }
        .collectAsState(initial = emptyList())
    val lastWeekPlan by remember(myId) { vm.repo.db.weekPlans().byWeek(myId, lastWeekStart) }
        .collectAsState(initial = null)
    val thisWeekTasks by remember(myId) { vm.repo.db.tasks().byWeek(myId, weekStart) }
        .collectAsState(initial = emptyList())
    val thisWeekPlan by remember(myId) { vm.repo.db.weekPlans().byWeek(myId, weekStart) }
        .collectAsState(initial = null)

    // Préremplit une seule fois avec ce qui existe déjà pour cette semaine.
    if (!loadedPlan && thisWeekPlan != null) {
        priority = thisWeekPlan?.priority ?: ""
        abandon = thisWeekPlan?.abandon ?: ""
        loadedPlan = true
    }

    val titles = listOf(
        "Bilan de la semaine passée",
        "Qu'est-ce que j'abandonne ?",
        "LA priorité de la semaine",
        "Répartir sur les jours",
        "Les séances de sport",
        "Validation"
    )

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
            Text(
                text = "${step + 1}/6 · ${titles[step]}",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(20.dp))

            when (step) {
                0 -> {
                    val done = lastWeekTasks.count { it.done }
                    val total = lastWeekTasks.size
                    Text(
                        text = if (total == 0) "Aucune tâche n'était planifiée la semaine passée."
                        else "$done tâches faites sur $total planifiées.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val lp = lastWeekPlan?.priority
                    if (lp != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "La priorité était : « $lp »",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val unfinished = lastWeekTasks.filter { !it.done }
                    if (unfinished.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        SectionLabel("PAS TERMINÉ — on en fait quoi ?")
                        unfinished.forEach { task ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.moveTaskToWeek(task.id, weekStart) }) {
                                        Text("Cette semaine")
                                    }
                                    TextButton(onClick = { vm.deleteTask(task.id) }) {
                                        Text("Laisser tomber")
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Text(
                        text = "Faire moins, mais mieux. À quoi dis-tu non cette semaine ?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = abandon,
                        onValueChange = { abandon = it },
                        placeholder = { Text("Ex. : les réunions sans ordre du jour") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                2 -> {
                    Text(
                        text = "Une seule. Celle qui rend le reste plus simple ou inutile.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it },
                        placeholder = { Text("Ma priorité de la semaine") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                3 -> {
                    var newTask by remember { mutableStateOf("") }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTask,
                            onValueChange = { newTask = it },
                            placeholder = { Text("Nouvelle tâche") },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            vm.addTask(newTask, null, weekStart)
                            newTask = ""
                        }) { Text("Ajouter") }
                    }
                    Spacer(Modifier.height(12.dp))
                    val pool = thisWeekTasks.filter { !it.isSport && !it.isPriority }
                    if (pool.isEmpty()) {
                        Text(
                            text = "Rien à répartir pour l'instant.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    pool.forEach { task ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.deleteTask(task.id) }) { Text("Retirer") }
                            }
                            DayChips(
                                weekStart = weekStart,
                                selectedDate = task.date,
                                onSelect = { date -> vm.assignTaskToDay(task.id, date) }
                            )
                        }
                    }
                }

                4 -> {
                    var newSport by remember { mutableStateOf("") }
                    Text(
                        text = "Quelles séances, quels jours ?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newSport,
                            onValueChange = { newSport = it },
                            placeholder = { Text("Ex. : course, yoga…") },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            vm.addTask(newSport, null, weekStart, isSport = true)
                            newSport = ""
                        }) { Text("Ajouter") }
                    }
                    Spacer(Modifier.height(12.dp))
                    thisWeekTasks.filter { it.isSport }.forEach { task ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    "🏃 ${task.title}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.deleteTask(task.id) }) { Text("Retirer") }
                            }
                            DayChips(
                                weekStart = weekStart,
                                selectedDate = task.date,
                                onSelect = { date -> vm.assignTaskToDay(task.id, date) }
                            )
                        }
                    }
                }

                5 -> {
                    Text(
                        text = "Priorité : ${priority.ifBlank { "—" }}",
                        style = MaterialTheme.typography.titleLarge,
                        color = accent
                    )
                    if (abandon.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "J'abandonne : $abandon",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val planned = thisWeekTasks.count { it.date != null }
                    val sport = thisWeekTasks.count { it.isSport }
                    Text(
                        text = "$planned tâches réparties, dont $sport séances de sport.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Une fois validée, la semaine est visible par vous deux. " +
                            "Les menus et la liste de courses arriveront dans une prochaine étape du projet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = { if (step == 0) onDone() else step-- },
                modifier = Modifier.height(56.dp)
            ) {
                Text(if (step == 0) "Quitter" else "Retour")
            }
            BigButton(
                text = if (step == 5) "Valider notre semaine" else "Continuer",
                enabled = step != 2 || priority.isNotBlank(),
                onClick = {
                    // Enregistre au fil de l'eau pour ne rien perdre si on quitte.
                    vm.saveWeekPlan(weekStart, priority, abandon, validate = step == 5)
                    if (step == 5) onDone() else step++
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
