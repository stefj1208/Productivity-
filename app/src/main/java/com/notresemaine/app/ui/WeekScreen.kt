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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.accentFor
import java.time.LocalDate

@Composable
fun WeekScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onReview: (String) -> Unit,
    onMenus: (String) -> Unit,
    onShopping: (String) -> Unit
) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)

    // Le samedi et le dimanche, on ouvre directement sur la semaine à préparer.
    var weekOffset by remember { mutableIntStateOf(Dates.weekOffsetOf(Dates.planningTargetWeekIso())) }
    val weekStart = Dates.weekStartIsoOffset(weekOffset)

    val tasks by remember(myId, weekStart) { vm.repo.db.tasks().byWeek(myId, weekStart) }
        .collectAsState(initial = emptyList())
    val weekPlan by remember(myId, weekStart) { vm.repo.db.weekPlans().byWeek(myId, weekStart) }
        .collectAsState(initial = null)
    val healthDays by remember { vm.repo.db.health().since(Dates.weekStartIsoOffset(-3)) }
        .collectAsState(initial = emptyList())
    val usageDays by remember { vm.repo.db.usage().since(Dates.weekStartIsoOffset(-3)) }
        .collectAsState(initial = emptyList())

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
            WeekNavigator(
                weekStartIso = weekStart,
                onOffsetChange = { delta -> weekOffset += delta }
            )

            if (weekPlan?.validatedAt != null) {
                Text(
                    text = "Validée ✓",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val wp = weekPlan?.priority
            if (wp != null) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("PRIORITÉ DE LA SEMAINE")
                Text(text = wp, style = MaterialTheme.typography.titleLarge, color = accent)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onMenus(weekStart) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) { Text("🍽️ Menus") }
                OutlinedButton(
                    onClick = { onShopping(weekStart) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) { Text("🛒 Courses") }
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

            // ----- Graphiques sur 4 semaines glissantes -----
            Spacer(Modifier.height(28.dp))
            SectionLabel("4 DERNIÈRES SEMAINES")

            val weekKeys = (-3..0).map { Dates.weekStartIsoOffset(it) }
            val weekLabels = weekKeys.map { key ->
                if (Dates.weekOffsetOf(key) == 0) "cette sem." else "S${LocalDate.parse(key).dayOfMonth}"
            }
            val myHealth = healthDays.filter { it.userId == myId }
            val myUsage = usageDays.filter { it.userId == myId }

            fun weekOf(dateIso: String) = Dates.weekStartIso(LocalDate.parse(dateIso))

            // Sommeil : moyenne par nuit renseignée
            val sleepPerWeek = weekKeys.map { key ->
                val days = myHealth.filter { weekOf(it.date) == key && it.sleepMinutes > 0 }
                if (days.isEmpty()) 0f else days.sumOf { it.sleepMinutes }.toFloat() / days.size / 60f
            }
            // Sport : total des minutes de séance
            val sportPerWeek = weekKeys.map { key ->
                myHealth.filter { weekOf(it.date) == key }.sumOf { it.exerciseMinutes }.toFloat()
            }
            // Écran : moyenne quotidienne des réseaux
            val screenPerWeek = weekKeys.map { key ->
                val days = myUsage.filter { weekOf(it.date) == key }
                if (days.isEmpty()) 0f else days.sumOf { it.socialMinutes }.toFloat() / days.size
            }

            Spacer(Modifier.height(8.dp))
            Text("😴 Sommeil — moyenne par nuit", style = MaterialTheme.typography.bodyLarge)
            MiniBarChart(
                values = sleepPerWeek, labels = weekLabels, accent = accent,
                valueLabel = { "%.1f h".format(it) },
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(20.dp))
            Text("🏃 Sport — total de la semaine", style = MaterialTheme.typography.bodyLarge)
            MiniBarChart(
                values = sportPerWeek, labels = weekLabels, accent = accent,
                valueLabel = { "${it.toInt()} min" },
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(20.dp))
            Text("📱 Réseaux — moyenne par jour", style = MaterialTheme.typography.bodyLarge)
            MiniBarChart(
                values = screenPerWeek, labels = weekLabels, accent = accent,
                valueLabel = { "${it.toInt()} min" },
                modifier = Modifier.padding(top = 6.dp)
            )

            if (myHealth.isEmpty() && myUsage.isEmpty()) {
                Text(
                    text = "Aucune mesure pour l'instant. Active Health Connect ou saisis " +
                        "ton sommeil en 10 secondes depuis Réglages.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = if (weekOffset > 0) "Planifier cette semaine" else "Revue de la semaine",
            onClick = { onReview(weekStart) },
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
