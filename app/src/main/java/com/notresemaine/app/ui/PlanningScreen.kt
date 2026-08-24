package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.notresemaine.app.data.Compass
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.TaskEntity
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor
import java.time.LocalDate
import java.time.LocalTime

/**
 * « Planning » : le récapitulatif du jour et de la semaine, plus les raccourcis.
 *
 * L'écran ne sert pas à tout faire, il sert à voir où on en est et à partir au
 * bon endroit en un tap. Le détail d'une journée s'ouvre en touchant son carré ;
 * les graphiques ont rejoint « Sommeil & sport », là où on va les chercher.
 */
@Composable
fun PlanningScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onPrepare: (String) -> Unit,
    onDay: (String) -> Unit,
    onRitual: () -> Unit,
    onGoals: () -> Unit,
    onReview: (String) -> Unit,
    onMenus: (String) -> Unit,
    onShopping: (String) -> Unit,
    onScreenTime: () -> Unit,
    onHealth: () -> Unit,
    onPerformance: () -> Unit,
    onWeight: () -> Unit,
    onAgenda: () -> Unit,
    onInbox: () -> Unit,
    onHabits: () -> Unit,
    onSport: () -> Unit,
    onAsk: () -> Unit,
    onMethod: () -> Unit
) {
    val today = Dates.todayIso()
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val hour = LocalTime.now().hour

    // La semaine affichée est TOUJOURS la semaine en cours au départ.
    // Avant, elle basculait sur la suivante le week-end : une tâche rangée
    // « cette semaine » devenait alors invisible — elle existait, mais dans
    // l'autre semaine. C'est ce qui donnait l'impression qu'elle disparaissait.
    var weekOffset by remember { mutableIntStateOf(0) }
    val weekStart = Dates.weekStartIsoOffset(weekOffset)
    var addingTask by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }

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
    val shopping by remember(weekStart) { vm.repo.db.shopping().forWeek(weekStart) }
        .collectAsState(initial = emptyList())

    val targetWeek = Dates.planningTargetWeekIso()
    val targetWeekPlan by remember(myId, targetWeek) { vm.repo.db.weekPlans().byWeek(myId, targetWeek) }
        .collectAsState(initial = null)

    val partner = profiles.firstOrNull { it.id != myId }
    val partnerName = partner?.name

    fun noteFor(task: TaskEntity): String? {
        val parts = buildList {
            if (task.startTime.isNotBlank()) {
                add("🕐 ${task.startTime}" + if (task.durationMinutes > 0) " · ${task.durationMinutes} min" else "")
            }
            if (task.assignedBy.isNotBlank() && task.assignedBy != myId) {
                add("↗ confiée par ${partnerName ?: "l'autre"}")
            }
        }
        return parts.joinToString(" · ").ifBlank { null }
    }

    val priority = tasks.firstOrNull { it.isPriority }
    val others = tasks.filter { !it.isPriority }
    val doneToday = tasks.count { it.done }
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
            ScreenHeader(title = "Planning", subtitle = Dates.longLabel(today))

            if (bravos.isNotEmpty()) {
                val fromName = profiles.firstOrNull { it.id == bravos.first().fromUser }?.name ?: "Ton binôme"
                Text(
                    text = "👏 $fromName t'envoie un bravo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ----- Maintenant -----
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

            // ----- Le pouls : trois anneaux, aucun mot -----
            //
            // Trois chiffres qu'on lisait auparavant dans trois écrans différents.
            // Un anneau se lit sans phrase et sans comparaison : rempli ou non.
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProgressRing(
                    progress = if (tasks.isEmpty()) 0f else doneToday / tasks.size.toFloat(),
                    center = if (tasks.isEmpty()) "—" else "$doneToday/${tasks.size}",
                    label = "aujourd'hui",
                    accent = accent
                )
                val weekDone = weekTasks.count { it.done }
                ProgressRing(
                    progress = if (weekTasks.isEmpty()) 0f else weekDone / weekTasks.size.toFloat(),
                    center = if (weekTasks.isEmpty()) "—" else "$weekDone/${weekTasks.size}",
                    label = "la semaine",
                    accent = accent
                )
                val streak = vm.repo.ritualStreak(ritualLogs, myId)
                ProgressRing(
                    progress = (streak / 21f).coerceAtMost(1f),
                    center = if (streak == 0) "—" else "$streak",
                    label = "jours de suite",
                    accent = accent
                )
            }

            // ----- Récap du jour -----
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("AUJOURD'HUI", modifier = Modifier.weight(1f))
                if (tasks.isNotEmpty()) {
                    Text(
                        text = "$doneToday/${tasks.size} fait",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tasks.isEmpty()) {
                EmptyState(
                    emoji = "🌤️",
                    text = "Rien de prévu. Quelle est la chose qui compte le plus ?",
                    actionLabel = "Choisir ma priorité",
                    onAction = { onPrepare(today) }
                )
            } else {
                if (priority != null) {
                    TaskRow(
                        task = priority,
                        accent = accent,
                        onToggle = { vm.toggleDone(priority.id) },
                        onEdit = { editing = priority },
                        note = noteFor(priority)
                    )
                }
                others.forEach { task ->
                    TaskRow(
                        task = task,
                        accent = accent,
                        onToggle = { vm.toggleDone(task.id) },
                        onEdit = { editing = task },
                        note = noteFor(task)
                    )
                }
                TextButton(onClick = { addingTask = true }) { Text("+ Ajouter une tâche") }
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
            if (ritualDone) {
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

            // ----- Récap de la semaine -----
            Spacer(Modifier.height(24.dp))
            SectionLabel("CETTE SEMAINE")
            WeekNavigator(
                weekStartIso = weekStart,
                onOffsetChange = { delta -> weekOffset += delta }
            )

            val wp = weekPlan?.priority
            Text(
                text = wp ?: "Pas encore de priorité pour cette semaine",
                style = MaterialTheme.typography.titleMedium,
                color = if (wp != null) accent else NeutralGray,
                modifier = Modifier.padding(top = 12.dp)
            )
            val weekDone = weekTasks.count { it.done }
            if (weekTasks.isNotEmpty()) {
                Text(
                    text = "$weekDone tâches faites sur ${weekTasks.size}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            WeekStrip(
                weekStart = weekStart,
                today = today,
                accent = accent,
                counts = Dates.daysOfWeek(weekStart).associateWith { day ->
                    val dayTasks = weekTasks.filter { it.date == day }
                    dayTasks.count { it.done } to dayTasks.size
                },
                onDay = { day -> onDay(day) }
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Touchez un jour pour voir son déroulé.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        // La semaine affichée n'est pas toujours la semaine en cours.
                        val target = if (Dates.daysOfWeek(weekStart).contains(today)) today
                        else Dates.daysOfWeek(weekStart).first()
                        onPrepare(target)
                    }
                ) { Text("✏️ Modifier") }
            }

            val unassigned = weekTasks.filter { it.date == null }
            if (unassigned.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("À RÉPARTIR (${unassigned.size})")
                unassigned.forEach { task ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable { editing = task },
                                contentAlignment = Alignment.Center
                            ) { Text("✏️", style = MaterialTheme.typography.bodyLarge) }
                        }
                        DayChips(
                            weekStart = weekStart,
                            selectedDate = null,
                            onSelect = { date -> vm.assignTaskToDay(task.id, date) }
                        )
                    }
                }
            }

            // ----- Raccourcis -----
            Spacer(Modifier.height(24.dp))
            SectionLabel("RACCOURCIS")
            val remaining = shopping.count { !it.checked }
            // Trois grosses tuiles d'abord : ce qu'on ouvre plusieurs fois par jour.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigShortcut("🕐", "Ma journée", { onDay(today) }, Modifier.weight(1f))
                BigShortcut(
                    "📥", "Notes", onInbox, Modifier.weight(1f),
                    badge = if (inboxCount > 0) "$inboxCount" else null
                )
                BigShortcut("🔁", "Habitudes", onHabits, Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                ShortcutIcon("🍽️", "Menus", { onMenus(weekStart) }, Modifier.weight(1f))
                ShortcutIcon(
                    "🛒", "Courses", { onShopping(weekStart) }, Modifier.weight(1f),
                    badge = if (remaining > 0) "($remaining)" else null
                )
                ShortcutIcon("🌅", "Rituel", onRitual, Modifier.weight(1f))
                ShortcutIcon("🎯", "Objectifs", onGoals, Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                ShortcutIcon("📵", "Pacte", onScreenTime, Modifier.weight(1f))
                ShortcutIcon("😴", "Santé", onHealth, Modifier.weight(1f))
                ShortcutIcon("⚖️", "Poids", onWeight, Modifier.weight(1f))
                ShortcutIcon("📈", "Perfs", onPerformance, Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                ShortcutIcon("📅", "Agenda", onAgenda, Modifier.weight(1f))
                ShortcutIcon("🏃", "Sport", onSport, Modifier.weight(1f))
                ShortcutIcon("💬", "Chat", onAsk, Modifier.weight(1f))
                ShortcutIcon("🔄", "Bilan", { onReview(targetWeek) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onReview(weekStart) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("🗓️ Bilan")
            }
            OutlinedButton(
                onClick = { onPrepare(Dates.tomorrowIso()) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) { Text("🌙 Demain") }
        }
        // Le grand bouton n'est pas figé : il porte l'action que la boussole
        // vient de désigner. Un bouton qui dit toujours la même chose devient
        // du mobilier ; celui-ci se lit à chaque ouverture.
        val action = when (step.route) {
            "ritual" -> "🌅 Faire mon rituel" to { onRitual() }
            "review" -> "🗓️ Faire le bilan de la semaine" to { onReview(targetWeek) }
            "goals" -> "🚀 Choisir un objectif" to { onGoals() }
            "prepare/tomorrow" -> "🌙 Préparer demain" to { onPrepare(Dates.tomorrowIso()) }
            "prepare/today" -> "🎯 Choisir ma priorité" to { onPrepare(today) }
            else -> when {
                priority != null && !priority.done -> "🕐 Voir ma journée" to { onDay(today) }
                else -> "🌙 Préparer demain" to { onPrepare(Dates.tomorrowIso()) }
            }
        }
        BigButton(
            text = action.first,
            onClick = action.second,
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
        )
    }

    // ----- Modifier ou supprimer une tâche -----
    val task = editing
    if (task != null) {
        EditDeleteDialog(
            title = "Modifier la tâche",
            initialText = task.title,
            onSave = {
                vm.renameTask(task.id, it)
                editing = null
            },
            onDelete = {
                vm.deleteTask(task.id)
                editing = null
            },
            onDismiss = { editing = null },
            extraActionLabel = partnerName?.let { "🤝 Confier à $it" },
            onExtraAction = partnerName?.let {
                {
                    vm.giveTaskToPartner(task.id)
                    editing = null
                }
            }
        )
    }

    if (addingTask) {
        var title by remember { mutableStateOf("") }
        var isSport by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { addingTask = false },
            title = { Text("Ajouter — aujourd'hui") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Quoi ?") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
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
                    vm.addTask(title, today, Dates.weekStartIso(LocalDate.parse(today)), isSport)
                    addingTask = false
                }) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { addingTask = false }) { Text("Annuler") }
            }
        )
    }
}

/**
 * Les sept jours en une bande : l'initiale, et ce qui est fait sur ce qui est prévu.
 * Un coup d'œil suffit à voir où la semaine est chargée et où elle est vide.
 */
@Composable
private fun WeekStrip(
    weekStart: String,
    today: String,
    accent: androidx.compose.ui.graphics.Color,
    counts: Map<String, Pair<Int, Int>>,
    onDay: (String) -> Unit
) {
    val initials = listOf("L", "M", "M", "J", "V", "S", "D")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Dates.daysOfWeek(weekStart).forEachIndexed { index, day ->
            val (done, total) = counts[day] ?: (0 to 0)
            val isToday = day == today
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isToday) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onDay(day) }
                    .defaultMinSize(minHeight = 64.dp)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = initials[index],
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (total == 0) "—" else "$done/$total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
