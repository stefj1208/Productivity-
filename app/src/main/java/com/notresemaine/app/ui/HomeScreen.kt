package com.notresemaine.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Compass
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Tips
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor
import java.time.LocalDate
import java.time.LocalTime

/**
 * L'écran d'accueil : le jour ET la semaine, d'un seul tenant.
 *
 * Deux onglets séparés obligeaient à choisir entre « ce que je fais maintenant »
 * et « où j'en suis » — alors que c'est la même question à deux échelles.
 * Le haut de l'écran répond en une seconde ; la semaine est juste en dessous,
 * dans le même défilement.
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onPrepare: (String) -> Unit,
    onRitual: () -> Unit,
    onGoals: () -> Unit,
    onReview: (String) -> Unit
) {
    val today = Dates.todayIso()
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val hour = LocalTime.now().hour

    // Le samedi et le dimanche, la semaine affichée est celle qu'on prépare.
    var weekOffset by remember { mutableIntStateOf(Dates.weekOffsetOf(Dates.planningTargetWeekIso())) }
    val weekStart = Dates.weekStartIsoOffset(weekOffset)
    var addingForDate by remember { mutableStateOf<String?>(null) }

    val tasks by remember(myId) { vm.repo.db.tasks().byDate(myId, today) }
        .collectAsState(initial = emptyList())
    val plan by remember(myId) { vm.repo.db.dayPlans().byDate(myId, today) }
        .collectAsState(initial = null)
    val bravos by remember(myId) { vm.repo.db.encouragements().forDate(myId, today) }
        .collectAsState(initial = emptyList())
    val profiles by remember { vm.repo.db.profiles().all() }
        .collectAsState(initial = emptyList())
    val ritualLogs by remember { vm.repo.db.ritual().logs() }
        .collectAsState(initial = emptyList())
    val ritualSteps by remember(myId) { vm.repo.db.ritual().steps(myId) }
        .collectAsState(initial = emptyList())
    val inboxCount by remember(myId) { vm.repo.db.inbox().pendingCount(myId) }
        .collectAsState(initial = 0)
    val goals by remember { vm.repo.db.goals().all() }
        .collectAsState(initial = emptyList())

    val weekTasks by remember(myId, weekStart) { vm.repo.db.tasks().byWeek(myId, weekStart) }
        .collectAsState(initial = emptyList())
    val weekPlan by remember(myId, weekStart) { vm.repo.db.weekPlans().byWeek(myId, weekStart) }
        .collectAsState(initial = null)
    val healthDays by remember { vm.repo.db.health().since(Dates.weekStartIsoOffset(-3)) }
        .collectAsState(initial = emptyList())
    val usageDays by remember { vm.repo.db.usage().since(Dates.weekStartIsoOffset(-3)) }
        .collectAsState(initial = emptyList())

    val targetWeek = Dates.planningTargetWeekIso()
    val targetWeekPlan by remember(myId, targetWeek) { vm.repo.db.weekPlans().byWeek(myId, targetWeek) }
        .collectAsState(initial = null)

    val priority = tasks.firstOrNull { it.isPriority }
    val others = tasks.filter { !it.isPriority }
    val ritualDone = ritualLogs.any { it.userId == myId && it.date == today && !it.deleted }

    val step = Compass.next(
        hour = hour,
        ritualDoneToday = ritualDone,
        hasRitualSteps = ritualSteps.any { it.enabled },
        priorityToday = priority,
        remainingToday = others.count { !it.done },
        inboxCount = inboxCount,
        targetWeekPlanned = targetWeekPlan?.validatedAt != null,
        hasActiveGoals = goals.any { it.userId == myId && it.active }
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
            ScreenHeader(title = "Aujourd'hui", subtitle = Dates.longLabel(today))

            if (bravos.isNotEmpty()) {
                val fromName = profiles.firstOrNull { it.id == bravos.first().fromUser }?.name ?: "Ton binôme"
                Text(
                    text = "👏 $fromName t'envoie un bravo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ----- 1. Maintenant -----
            Spacer(Modifier.height(8.dp))
            CompassCard(
                step = step,
                accent = accent,
                onClick = when (step.route) {
                    "ritual" -> ({ onRitual() })
                    "goals" -> ({ onGoals() })
                    "review" -> ({ onReview(targetWeek) })
                    "prepare/today" -> ({ onPrepare(today) })
                    "prepare/tomorrow" -> ({ onPrepare(Dates.tomorrowIso()) })
                    else -> null
                }
            )

            if (priority != null) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("MA PRIORITÉ")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { vm.toggleDone(priority.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (priority.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (priority.done) "Fait" else "À faire",
                        tint = if (priority.done) NeutralGray else accent,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = priority.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (priority.done) NeutralGray else MaterialTheme.colorScheme.onBackground,
                        textDecoration = if (priority.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }

            if (priority == null && others.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                EmptyState(
                    emoji = "🌤️",
                    text = "Rien de prévu aujourd'hui. Une seule décision suffit : " +
                        "quelle est la chose qui compte le plus ?",
                    actionLabel = "Choisir ma priorité",
                    onAction = { onPrepare(today) }
                )
            }

            if (others.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("ENSUITE")
                others.forEach { task ->
                    TaskRow(task = task, accent = accent, onToggle = { vm.toggleDone(task.id) })
                }
            }

            if (ritualDone) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "🌅 Rituel fait · série ${vm.repo.ritualStreak(ritualLogs, myId)} jours",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { onRitual() }
                        .padding(vertical = 10.dp)
                )
            }

            val p = plan
            if (p?.focusBlocks != null) {
                Text(
                    text = "🎧 Concentration : ${p.focusBlocks}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            TipCard(if (hour < 14) Tips.morning() else Tips.evening())

            // ----- 2. La semaine, dans le même écran -----
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
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

            val unassigned = weekTasks.filter { it.date == null }
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
                val dayTasks = weekTasks.filter { it.date == dayIso }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Dates.shortLabel(dayIso) + (if (dayIso == today) " — aujourd'hui" else ""),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dayIso == today) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { addingForDate = dayIso }) { Text("+ Ajouter") }
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

            // ----- 3. Où on en est, sur 4 semaines -----
            Spacer(Modifier.height(28.dp))
            SectionLabel("4 DERNIÈRES SEMAINES")

            val weekKeys = (-3..0).map { Dates.weekStartIsoOffset(it) }
            val weekLabels = weekKeys.map { key ->
                if (Dates.weekOffsetOf(key) == 0) "cette sem." else "S${LocalDate.parse(key).dayOfMonth}"
            }
            val myHealth = healthDays.filter { it.userId == myId }
            val myUsage = usageDays.filter { it.userId == myId }

            fun weekOf(dateIso: String) = Dates.weekStartIso(LocalDate.parse(dateIso))

            val sleepPerWeek = weekKeys.map { key ->
                val days = myHealth.filter { weekOf(it.date) == key && it.sleepMinutes > 0 }
                if (days.isEmpty()) 0f else days.sumOf { it.sleepMinutes }.toFloat() / days.size / 60f
            }
            val sportPerWeek = weekKeys.map { key ->
                myHealth.filter { weekOf(it.date) == key }.sumOf { it.exerciseMinutes }.toFloat()
            }
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

        // Deux actions en bas, dans la zone du pouce.
        OutlinedButton(
            onClick = { onReview(weekStart) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 2.dp)
        ) {
            Text(if (weekOffset > 0) "Planifier cette semaine" else "Revue de la semaine")
        }
        BigButton(
            text = if (hour < 14) "Préparer aujourd'hui" else "Préparer demain",
            onClick = { onPrepare(if (hour < 14) today else Dates.tomorrowIso()) },
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
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
