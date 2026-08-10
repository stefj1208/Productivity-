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
import androidx.compose.material3.FilterChip
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
import com.notresemaine.app.data.Tips
import com.notresemaine.app.ui.theme.accentFor

/**
 * Revue guidée, écran par écran, pour la semaine [weekStart] :
 * 1 Bilan · 2 Boîte de réception · 3 J'abandonne · 4 LA priorité ·
 * 5 Répartition · 6 Sport · 7 Menus · 8 Validation
 */
@Composable
fun ReviewScreen(
    vm: AppViewModel,
    settings: AppSettings,
    weekStart: String,
    onDone: () -> Unit,
    onMenus: (String) -> Unit
) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val previousWeekStart = Dates.weekBefore(weekStart)

    var step by remember { mutableIntStateOf(0) }
    var abandon by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("") }
    var loadedPlan by remember { mutableStateOf(false) }

    val previousTasks by remember(myId, previousWeekStart) { vm.repo.db.tasks().byWeek(myId, previousWeekStart) }
        .collectAsState(initial = emptyList())
    val previousPlan by remember(myId, previousWeekStart) { vm.repo.db.weekPlans().byWeek(myId, previousWeekStart) }
        .collectAsState(initial = null)
    val weekTasks by remember(myId, weekStart) { vm.repo.db.tasks().byWeek(myId, weekStart) }
        .collectAsState(initial = emptyList())
    val weekPlan by remember(myId, weekStart) { vm.repo.db.weekPlans().byWeek(myId, weekStart) }
        .collectAsState(initial = null)
    val inbox by remember(myId) { vm.repo.db.inbox().pending(myId) }
        .collectAsState(initial = emptyList())
    val goals by remember { vm.repo.db.goals().all() }
        .collectAsState(initial = emptyList())
    val previousUsage by remember(previousWeekStart) { vm.repo.db.usage().since(previousWeekStart) }
        .collectAsState(initial = emptyList())
    val previousHealth by remember(previousWeekStart) { vm.repo.db.health().since(previousWeekStart) }
        .collectAsState(initial = emptyList())
    val days = Dates.daysOfWeek(weekStart)
    val meals by remember(weekStart) { vm.repo.db.meals().between(days.first(), days.last()) }
        .collectAsState(initial = emptyList())

    val aiBusy by vm.aiBusy.collectAsState()
    val aiWeek by vm.aiWeekAdvice.collectAsState()

    // Préremplit une seule fois avec ce qui existe déjà pour cette semaine.
    if (!loadedPlan && weekPlan != null) {
        priority = weekPlan?.priority ?: ""
        abandon = weekPlan?.abandon ?: ""
        loadedPlan = true
    }

    // Une proposition de l'assistant remplit les champs ; elle reste modifiable.
    androidx.compose.runtime.LaunchedEffect(aiWeek) {
        val advice = aiWeek ?: return@LaunchedEffect
        priority = advice.priority
        if (advice.abandon.isNotBlank()) abandon = advice.abandon
        vm.clearAiWeekAdvice()
    }

    val titles = listOf(
        "Bilan de la semaine passée",
        "Vider la boîte de réception",
        "Qu'est-ce que j'abandonne ?",
        "LA priorité de la semaine",
        "Répartir sur les jours",
        "Les séances de sport",
        "Les menus",
        "Validation"
    )
    val lastStep = titles.size - 1

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
            ScreenHeader(
                title = titles[step],
                subtitle = "Étape ${step + 1} sur ${titles.size} · " +
                    Dates.weekRelativeLabel(weekStart),
                onBack = onDone
            )
            Spacer(Modifier.height(14.dp))
            TipCard(Tips.review(step))
            Spacer(Modifier.height(20.dp))

            when (step) {
                0 -> {
                    val done = previousTasks.count { it.done }
                    val total = previousTasks.size
                    Text(
                        text = if (total == 0) "Aucune tâche n'était planifiée la semaine précédente."
                        else "$done tâches faites sur $total planifiées.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val myUsage = previousUsage.filter { it.userId == myId }
                    if (myUsage.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "📱 ${myUsage.sumOf { it.socialMinutes } / 60} h de réseaux sociaux, " +
                                "${myUsage.sumOf { it.unlocks }} déverrouillages.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val myHealth = previousHealth.filter { it.userId == myId }
                    val nights = myHealth.filter { it.sleepMinutes > 0 }
                    if (nights.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        val avg = nights.sumOf { it.sleepMinutes } / nights.size
                        Text(
                            text = "😴 ${avg / 60} h ${avg % 60} de sommeil en moyenne · " +
                                "🏃 ${myHealth.sumOf { it.exerciseMinutes }} min de sport.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val lp = previousPlan?.priority
                    if (lp != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "La priorité était : « $lp »",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val unfinished = previousTasks.filter { !it.done }
                    if (unfinished.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        SectionLabel("PAS TERMINÉ — on en fait quoi ?")
                        unfinished.forEach { task ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.moveTaskToWeek(task.id, weekStart) }) {
                                        Text("Reporter")
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
                    if (inbox.isEmpty()) {
                        Text(
                            text = "Boîte de réception vide. Tête libre ✓",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = "${inbox.size} notes à trier. Une décision par note, pas plus.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        inbox.forEach { item ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(item.text, style = MaterialTheme.typography.bodyLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.resolveInbox(item.id, "planifier") }) {
                                        Text("Planifier")
                                    }
                                    TextButton(onClick = { vm.resolveInbox(item.id, "fait") }) {
                                        Text("Déjà fait")
                                    }
                                    TextButton(onClick = { vm.resolveInbox(item.id, "supprimer") }) {
                                        Text("Jeter")
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
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
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                3 -> {
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
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
                        AiButton(
                            text = "Trancher à ma place",
                            busy = aiBusy,
                            onClick = { vm.suggestWeekPriorityWithAi(weekStart) },
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            text = "Propose une priorité et une chose à laisser tomber, à partir " +
                                "de vos objectifs et de vos notes en attente. Les objectifs privés " +
                                "ne sont jamais envoyés.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    val suggestions = goals
                        .filter { it.userId == myId && it.active && it.nextAction.isNotBlank() }
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        SectionLabel("SUGGESTIONS (mes objectifs)")
                        suggestions.forEach { goal ->
                            ChoiceRow(
                                text = goal.nextAction,
                                selected = priority == goal.nextAction,
                                onClick = { priority = goal.nextAction }
                            )
                        }
                    }
                }

                4 -> {
                    val myGoals = goals.filter { it.userId == myId && it.active }
                    if (myGoals.isNotEmpty()) {
                        OutlinedButton(onClick = { vm.planGoalSessions(weekStart) }) {
                            Text("Placer les séances de mes objectifs")
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    var newTask by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    val pool = weekTasks.filter { !it.isSport && !it.isPriority && it.goalId == null }
                    if (pool.isEmpty()) {
                        Text(
                            text = "Rien à répartir pour l'instant.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    pool.forEach { task ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                5 -> {
                    var newSport by remember { mutableStateOf("") }
                    Text(
                        text = "Quelles séances, quels jours ?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    weekTasks.filter { it.isSport }.forEach { task ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                6 -> {
                    val filled = meals.count { it.title.isNotBlank() }
                    Text(
                        text = "$filled repas sur 14 sont décidés.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Décider maintenant, c'est ne plus se demander « on mange quoi ? » " +
                            "sept soirs de suite. Les ingrédients saisis deviennent la liste de courses.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { onMenus(weekStart) },
                        modifier = Modifier.height(48.dp)
                    ) { Text("Remplir les menus") }
                }

                7 -> {
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
                    val planned = weekTasks.count { it.date != null }
                    val sport = weekTasks.count { it.isSport }
                    val sessions = weekTasks.count { it.goalId != null }
                    val filledMeals = meals.count { it.title.isNotBlank() }
                    Text(
                        text = "$planned tâches réparties, dont $sport séances de sport " +
                            "et $sessions séances d'objectifs. $filledMeals repas décidés.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Une fois validée, la semaine est visible par vous deux.",
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
                text = if (step == lastStep) "Valider notre semaine" else "Continuer",
                enabled = step != 3 || priority.isNotBlank(),
                onClick = {
                    // Enregistre au fil de l'eau pour ne rien perdre si on quitte.
                    vm.saveWeekPlan(weekStart, priority, abandon, validate = step == lastStep)
                    if (step == lastStep) onDone() else step++
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
