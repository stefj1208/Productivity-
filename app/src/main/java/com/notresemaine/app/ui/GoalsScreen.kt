package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.notresemaine.app.data.GoalEntity
import com.notresemaine.app.data.GoalTemplate
import com.notresemaine.app.data.GoalTemplates
import com.notresemaine.app.data.Tips
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

@Composable
fun GoalsScreen(vm: AppViewModel, settings: AppSettings) {
    val myId = settings.myUserId
    val weekStart = Dates.weekStartIso()

    val goals by remember { vm.repo.db.goals().all() }.collectAsState(initial = emptyList())
    val weekTasks by remember { vm.repo.db.tasks().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val profiles by remember { vm.repo.db.profiles().all() }.collectAsState(initial = emptyList())

    var wizardTemplate by remember { mutableStateOf<GoalTemplate?>(null) }

    val template = wizardTemplate
    if (template != null) {
        GoalWizard(
            vm = vm,
            template = template,
            onClose = { wizardTemplate = null }
        )
        return
    }

    val myGoals = goals.filter { it.userId == myId }
    val partnerGoals = goals.filter { it.userId != myId }
    val partnerName = profiles.firstOrNull { it.id != myId }?.name

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Objectifs", style = MaterialTheme.typography.titleLarge)
        val tip = Tips.goals()
        Text(
            text = "📖 ${tip.book} — ${tip.text}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (myGoals.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Choisis un objectif ci-dessous : l'application génère les séances " +
                    "de la semaine à ta place, aux bons moments.",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Spacer(Modifier.height(16.dp))
            SectionLabel("MES OBJECTIFS (${myGoals.count { it.active }}/${GoalTemplates.MAX_ACTIVE_GOALS} actifs)")
            myGoals.forEach { goal ->
                GoalCard(
                    goal = goal,
                    doneThisWeek = weekTasks.count { it.goalId == goal.id && it.done },
                    accentRole = settings.myColor,
                    onToggle = { vm.setGoalActive(goal.id, !goal.active) },
                    onDelete = { vm.deleteGoal(goal.id) }
                )
            }
            TextButton(onClick = { vm.planGoalSessions(weekStart) }) {
                Text("Placer les séances de cette semaine")
            }
        }

        if (partnerGoals.isNotEmpty() && partnerName != null) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("OBJECTIFS DE ${partnerName.uppercase()}")
            partnerGoals.forEach { goal ->
                GoalCard(
                    goal = goal,
                    doneThisWeek = weekTasks.count { it.goalId == goal.id && it.done },
                    accentRole = if (settings.myColor == "A") "B" else "A",
                    onToggle = null,
                    onDelete = null
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("BIBLIOTHÈQUE — un tap pour démarrer")
        GoalTemplates.all.forEach { t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${t.emoji} ${t.title}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = t.why,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { wizardTemplate = t }) { Text("Choisir") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GoalCard(
    goal: GoalEntity,
    doneThisWeek: Int,
    accentRole: String,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val accent = accentFor(accentRole)
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = goal.title + if (goal.isPrivate) " 🔒" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (goal.active) MaterialTheme.colorScheme.onBackground else NeutralGray
                )
                Text(
                    text = "${goal.sessionsPerWeek}×/semaine · ${goal.minutesPerSession} min · ${goal.preferredTime}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onToggle != null) {
                Switch(checked = goal.active, onCheckedChange = { onToggle() })
            }
        }
        if (goal.nextAction.isNotBlank()) {
            Text(
                text = "→ ${goal.nextAction}",
                style = MaterialTheme.typography.bodyLarge,
                color = accent,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        LinearProgressIndicator(
            progress = {
                (doneThisWeek.toFloat() / goal.sessionsPerWeek).coerceIn(0f, 1f)
            },
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(6.dp)
        )
        Text(
            text = "$doneThisWeek/${goal.sessionsPerWeek} séances cette semaine",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Retirer cet objectif") }
        }
    }
}

/** Assistant en 3 questions : quoi, combien, quand. Tout est prérempli par le modèle. */
@Composable
private fun GoalWizard(vm: AppViewModel, template: GoalTemplate, onClose: () -> Unit) {
    var title by remember { mutableStateOf(if (template.title == "Objectif libre") "" else template.title) }
    var sessions by remember { mutableStateOf(template.sessionsPerWeek.toString()) }
    var minutes by remember { mutableStateOf(template.minutesPerSession.toString()) }
    var time by remember { mutableStateOf(template.preferredTime) }
    var days by remember { mutableStateOf(template.preferredDays.toSet()) }
    var nextAction by remember { mutableStateOf(template.nextActionSuggestion) }
    var isPrivate by remember { mutableStateOf(false) }

    val dayLabels = listOf(1 to "L", 2 to "M", 3 to "M", 4 to "J", 5 to "V", 6 to "S", 7 to "D")

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
            Text("${template.emoji} Nouvel objectif", style = MaterialTheme.typography.titleLarge)
            Text(
                text = template.why,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("1 · QUOI, PRÉCISÉMENT ?")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Ex. : apprendre l'espagnol") },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("2 · COMBIEN ?")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sessions,
                    onValueChange = { sessions = it },
                    label = { Text("Séances / semaine") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    label = { Text("Minutes / séance") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("3 · QUAND ?")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("matin", "midi", "soir").forEach { t ->
                    FilterChip(
                        selected = time == t,
                        onClick = { time = t },
                        label = { Text(t, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.height(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dayLabels.forEach { (num, label) ->
                    FilterChip(
                        selected = num in days,
                        onClick = { days = if (num in days) days - num else days + num },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.size(width = 44.dp, height = 48.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("LA PROCHAINE ACTION (une seule)")
            OutlinedTextField(
                value = nextAction,
                onValueChange = { nextAction = it },
                placeholder = { Text("Le tout premier pas, concret") },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                Text("Privé (invisible pour l'autre)", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
        }

        TextButton(onClick = onClose) { Text("Annuler") }
        BigButton(
            text = "Créer l'objectif",
            enabled = title.isNotBlank() && days.isNotEmpty()
                && sessions.toIntOrNull() != null && minutes.toIntOrNull() != null,
            onClick = {
                vm.addGoal(
                    title = title,
                    domain = template.domain,
                    sessionsPerWeek = sessions.toIntOrNull() ?: 3,
                    minutesPerSession = minutes.toIntOrNull() ?: 30,
                    preferredTime = time,
                    preferredDays = days.sorted(),
                    nextAction = nextAction,
                    isPrivate = isPrivate
                )
                onClose()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
