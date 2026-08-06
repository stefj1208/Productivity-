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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.accentFor

@Composable
fun WeekScreen(vm: AppViewModel, settings: AppSettings, onReview: () -> Unit) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val weekStart = Dates.weekStartIso()

    val tasks by remember(myId) { vm.repo.db.tasks().byWeek(myId, weekStart) }
        .collectAsState(initial = emptyList())
    val weekPlan by remember(myId) { vm.repo.db.weekPlans().byWeek(myId, weekStart) }
        .collectAsState(initial = null)

    var addingForDate by remember { mutableStateOf<String?>(null) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Semaine du ${Dates.shortLabel(weekStart)}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (weekPlan?.validatedAt != null) {
                    Text(
                        text = "Validée ✓",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val wp = weekPlan?.priority
            if (wp != null) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("PRIORITÉ DE LA SEMAINE")
                Text(text = wp, style = MaterialTheme.typography.titleLarge, color = accent)
            }

            // Tâches pas encore réparties sur un jour
            val unassigned = tasks.filter { it.date == null }
            if (unassigned.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("À RÉPARTIR — touche un jour")
                unassigned.forEach { task ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        DayChips(
                            weekStart = weekStart,
                            selectedDate = null,
                            onSelect = { date -> vm.assignTaskToDay(task.id, date) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Dates.daysOfWeek(weekStart).forEach { dayIso ->
                val dayTasks = tasks.filter { it.date == dayIso }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Dates.shortLabel(dayIso) + (if (dayIso == Dates.todayIso()) " — aujourd'hui" else ""),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dayIso == Dates.todayIso()) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { addingForDate = dayIso }) {
                        Text("+ Ajouter")
                    }
                }
                if (dayTasks.isEmpty()) {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    dayTasks.forEach { task ->
                        TaskRow(task = task, accent = accent, onToggle = { vm.toggleDone(task.id) })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = "Revue du dimanche",
            onClick = onReview,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    val dialogDate = addingForDate
    if (dialogDate != null) {
        var title by remember { mutableStateOf("") }
        var isSport by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { addingForDate = null },
            title = { Text("Ajouter — ${Dates.shortLabel(dialogDate)}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Quoi ?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSport, onCheckedChange = { isSport = it })
                        Text("Séance de sport", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addTask(title, dialogDate, weekStart, isSport)
                    addingForDate = null
                }) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { addingForDate = null }) { Text("Annuler") }
            }
        )
    }
}
