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
    val aiReview by vm.aiReview.collectAsState()
    val aiAbandon by vm.aiAbandon.collectAsState()
    val aiReady = settings.aiEnabled && settings.aiApiKey.isNotBlank()
    val ritualLogs by remember { vm.repo.db.ritual().logs() }
        .collectAsState(initial = emptyList())
    val previousMeals by remember(previousWeekStart) {
        val d = Dates.daysOfWeek(previousWeekStart)
        vm.repo.db.meals().between(d.first(), d.last())
    }.collectAsState(initial = emptyList())
    val previousLogs by remember(previousWeekStart) {
        val d = Dates.daysOfWeek(previousWeekStart)
        vm.repo.db.mealLogs().between(d.first(), d.last())
    }.collectAsState(initial = emptyList())

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
        "Poser les tâches sur les jours",
        "Les séances de sport",
        "Les menus",
        "C'est prêt"
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
                    val myUsage = previousUsage.filter { it.userId == myId }
                    val myHealth = previousHealth.filter { it.userId == myId }
                    val nights = myHealth.filter { it.sleepMinutes > 0 }
                    val avgSleep = if (nights.isEmpty()) 0 else nights.sumOf { it.sleepMinutes } / nights.size
                    val sportMinutes = myHealth.sumOf { it.exerciseMinutes }
                    val previousDays = Dates.daysOfWeek(previousWeekStart)
                    val ritualDays = ritualLogs.count {
                        it.userId == myId && !it.deleted && it.date in previousDays
                    }
                    val sessions = previousTasks.count { it.goalId != null }
                    val sessionsDone = previousTasks.count { it.goalId != null && it.done }
                    val mealsDone = previousMeals.count { it.title.isNotBlank() }

                    // Trois anneaux d'abord : les tâches, les séances, le rituel.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProgressRing(
                            progress = if (total == 0) 0f else done / total.toFloat(),
                            center = if (total == 0) "—" else "$done/$total",
                            label = "tâches",
                            accent = accent,
                            size = 88.dp
                        )
                        ProgressRing(
                            progress = if (sessions == 0) 0f else sessionsDone / sessions.toFloat(),
                            center = if (sessions == 0) "—" else "$sessionsDone/$sessions",
                            label = "séances",
                            accent = accent,
                            size = 88.dp
                        )
                        ProgressRing(
                            progress = ritualDays / 7f,
                            center = "$ritualDays/7",
                            label = "rituels",
                            accent = accent,
                            size = 88.dp
                        )
                    }

                    // Le temps d'écran, jour par jour : c'est la forme qui parle.
                    if (myUsage.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        SectionLabel("RÉSEAUX SOCIAUX, JOUR PAR JOUR")
                        MiniBarChart(
                            values = previousDays.map { d ->
                                (myUsage.firstOrNull { it.date == d }?.socialMinutes ?: 0).toFloat()
                            },
                            labels = listOf("L", "M", "M", "J", "V", "S", "D"),
                            accent = accent,
                            valueLabel = { "${it.toInt()}" }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BigStat(
                            value = if (avgSleep > 0) "${avgSleep / 60} h ${"%02d".format(avgSleep % 60)}" else "—",
                            label = "sommeil / nuit",
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                        BigStat(
                            value = if (sportMinutes > 0) "$sportMinutes min" else "—",
                            label = "de sport",
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BigStat(
                            value = if (myUsage.isEmpty()) "—"
                            else "${myUsage.sumOf { it.socialMinutes } / 60} h",
                            label = "de réseaux",
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                        BigStat(
                            value = "$mealsDone",
                            label = "repas décidés",
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ----- Le menu prévu contre ce qui a été mangé -----
                    //
                    // On ne compte que les jours réellement notés : un jour sans photo
                    // n'est pas un jour sans repas, et le présenter comme tel serait faux.
                    val myLogs = previousLogs.filter { it.userId == myId }
                    if (myLogs.isNotEmpty()) {
                        val daysLogged = myLogs.map { it.date }.distinct().size
                        val avgKcal = myLogs.sumOf { it.calories } / daysLogged
                        Spacer(Modifier.height(16.dp))
                        SectionLabel("CE QU'ON A VRAIMENT MANGÉ")
                        Text(
                            text = "${myLogs.size} repas notés sur $daysLogged jour" +
                                (if (daysLogged > 1) "s" else "") +
                                " · ≈ $avgKcal kcal par jour noté.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Estimations d'après photo : un ordre de grandeur, pas un compte.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    val lp = previousPlan?.priority
                    if (lp != null) {
                        Spacer(Modifier.height(16.dp))
                        SectionLabel("LA PRIORITÉ ÉTAIT")
                        Text("« $lp »", style = MaterialTheme.typography.titleMedium)
                    }
                    val la = previousPlan?.abandon
                    if (!la.isNullOrBlank()) {
                        Text(
                            text = "Et on abandonnait : $la",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    // ----- Ce que l'assistant en retient -----
                    if (aiReady) {
                        Spacer(Modifier.height(20.dp))
                        AiButton(
                            text = "Que retenir de cette semaine ?",
                            busy = aiBusy,
                            onClick = {
                                vm.reviewWeekWithAi(
                                    buildString {
                                        append("Tâches : $done faites sur $total planifiées.\n")
                                        append("Séances d'objectifs : $sessionsDone sur $sessions.\n")
                                        append("Rituel du matin : $ritualDays jours sur 7.\n")
                                        if (avgSleep > 0) append("Sommeil moyen : ${avgSleep / 60} h ${avgSleep % 60}.\n")
                                        append("Sport : $sportMinutes min dans la semaine.\n")
                                        if (myUsage.isNotEmpty()) {
                                            append("Réseaux sociaux : ${myUsage.sumOf { it.socialMinutes }} min au total, ")
                                            append("${myUsage.sumOf { it.unlocks }} déverrouillages.\n")
                                        }
                                        append("Repas décidés à l'avance : $mealsDone sur 21.\n")
                                        if (lp != null) append("Priorité annoncée : $lp.")
                                    },
                                    previousWeekStart
                                )
                            }
                        )
                        val r = aiReview
                        if (r != null) {
                            Spacer(Modifier.height(10.dp))
                            AiNote(text = "✅ ${r.worked}")
                            Spacer(Modifier.height(6.dp))
                            AiNote(text = "🤔 ${r.stuck}")
                            Spacer(Modifier.height(6.dp))
                            AiNote(text = "➡️ ${r.lever}")
                        } else {
                            Text(
                                text = "N'envoie que ces chiffres agrégés. Rien du partenaire, " +
                                    "aucun détail jour par jour de la santé.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    val unfinished = previousTasks.filter { !it.done }
                    if (unfinished.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
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
                    } else if (total > 0) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Rien ne traîne. Table rase pour la semaine qui vient.",
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                        if (aiReady) {
                            AiButton(
                                text = "Tout transformer en actions",
                                busy = aiBusy,
                                onClick = { vm.inboxToActionsWithAi(weekStart) },
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Text(
                                text = "Chaque note devient une action qui commence par un verbe, " +
                                    "posée sur le bon jour. Celles qui demandent encore réflexion " +
                                    "restent ici.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                            )
                        }
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
                    VoiceField(
                        value = abandon,
                        onValueChange = { abandon = it },
                        placeholder = "Ex. : les réunions sans ordre du jour",
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (aiReady) {
                        AiButton(
                            text = "Trouver quoi laisser tomber",
                            busy = aiBusy,
                            onClick = {
                                vm.suggestAbandonWithAi(priority, weekTasks.count { !it.done })
                            },
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        if (aiAbandon.isEmpty()) {
                            Text(
                                text = "N'envoie que votre priorité, vos objectifs non privés et le " +
                                    "nombre de tâches en attente.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    if (aiAbandon.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        SectionLabel("PROPOSITIONS — touchez pour choisir")
                        aiAbandon.forEach { suggestion ->
                            ChoiceRow(
                                text = suggestion,
                                selected = abandon == suggestion,
                                onClick = { abandon = suggestion }
                            )
                        }
                        TextButton(onClick = { vm.clearAiAbandon() }) { Text("Effacer les propositions") }
                    }
                }

                3 -> {
                    Text(
                        text = "Une seule. Celle qui rend le reste plus simple ou inutile.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    VoiceField(
                        value = priority,
                        onValueChange = { priority = it },
                        placeholder = "Ma priorité de la semaine",
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
                    // L'étape la plus mal comprise : on explique en deux phrases
                    // ce qu'on attend, et on montre la charge de chaque jour —
                    // « répartir » ne veut rien dire tant qu'on ne voit pas où ça pèse.
                    Text(
                        text = "Ici, on donne un jour à chaque tâche. Une tâche sans jour " +
                            "n'arrive jamais.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Maximum 3 par jour : au-delà, c'est le jour qui décide, pas vous.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                    SectionLabel("LA CHARGE DE CHAQUE JOUR")
                    val weekDays = Dates.daysOfWeek(weekStart)
                    MiniBarChart(
                        values = weekDays.map { d ->
                            weekTasks.count { it.date == d && !it.isSport }.toFloat()
                        },
                        labels = listOf("L", "M", "M", "J", "V", "S", "D"),
                        accent = accent,
                        valueLabel = { "${it.toInt()}" }
                    )

                    Spacer(Modifier.height(16.dp))
                    val myGoals = goals.filter { it.userId == myId && it.active }
                    if (myGoals.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { vm.planGoalSessions(weekStart) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("🎯 Placer les séances de mes objectifs") }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = { vm.spreadTasks(weekStart) },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("⚖️ Étaler tout ce qui n'a pas de jour") }
                    Spacer(Modifier.height(16.dp))
                    var newTask by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VoiceField(
                            value = newTask,
                            onValueChange = { newTask = it },
                            placeholder = "Nouvelle tâche",
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
                        EmptyState(
                            emoji = "📥",
                            text = "Aucune tâche pour cette semaine. Elles arrivent d'ici : " +
                                "le champ ci-dessus, vos notes triées à l'étape 2, ou les " +
                                "tâches reportées de la semaine passée."
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
                        VoiceField(
                            value = newSport,
                            onValueChange = { newSport = it },
                            placeholder = "Ex. : course, yoga…",
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
                    // La dernière étape : la semaine en une image, pas en un paragraphe.
                    val planned = weekTasks.count { it.date != null }
                    val undated = weekTasks.count { it.date == null && !it.isSport }
                    val sport = weekTasks.count { it.isSport }
                    val sessions = weekTasks.count { it.goalId != null }
                    val filledMeals = meals.count { it.title.isNotBlank() }

                    Text(
                        text = priority.ifBlank { "—" },
                        style = MaterialTheme.typography.displaySmall,
                        color = accent
                    )
                    Text(
                        text = "LA priorité de la semaine",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (abandon.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "🙅 J'abandonne : $abandon",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProgressRing(
                            progress = if (weekTasks.isEmpty()) 0f
                            else planned / weekTasks.size.toFloat(),
                            center = "$planned",
                            label = "tâches datées",
                            accent = accent,
                            size = 88.dp
                        )
                        ProgressRing(
                            progress = filledMeals / 21f,
                            center = "$filledMeals",
                            label = "repas sur 21",
                            accent = accent,
                            size = 88.dp
                        )
                        ProgressRing(
                            progress = (sport + sessions) / 6f,
                            center = "${sport + sessions}",
                            label = "séances",
                            accent = accent,
                            size = 88.dp
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionLabel("LA CHECK-LIST")
                    ReadyLine(priority.isNotBlank(), "Une priorité choisie")
                    ReadyLine(abandon.isNotBlank(), "Quelque chose d'abandonné")
                    ReadyLine(undated == 0, if (undated == 0) "Toutes les tâches ont un jour"
                    else "$undated tâche(s) encore sans jour")
                    ReadyLine(sport + sessions > 0, "Au moins une séance placée")
                    ReadyLine(filledMeals >= 7, "Les repas sont décidés ($filledMeals/21)")

                    Spacer(Modifier.height(20.dp))
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

/**
 * Une ligne de la check-list finale. Volontairement sans rouge ni point
 * d'exclamation : un point non coché est une information, pas un reproche —
 * on peut valider la semaine sans que tout soit vert.
 */
@Composable
private fun ReadyLine(ok: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Text(if (ok) "✅" else "⬜", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (ok) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
