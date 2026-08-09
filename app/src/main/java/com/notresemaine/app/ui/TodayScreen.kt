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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import java.time.LocalTime

@Composable
fun TodayScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onPrepare: (String) -> Unit,
    onRitual: () -> Unit,
    onSettings: () -> Unit,
    onGoals: () -> Unit,
    onReview: (String) -> Unit
) {
    val today = Dates.todayIso()
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val hour = LocalTime.now().hour

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
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Dates.longLabel(today),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Réglages",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bravos.isNotEmpty()) {
                val fromName = profiles.firstOrNull { it.id == bravos.first().fromUser }?.name ?: "Ton binôme"
                Text(
                    text = "👏 $fromName t'envoie un bravo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // La boussole : la seule chose à faire maintenant.
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

            // Le détail du jour, sous la boussole.
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
            Spacer(Modifier.height(20.dp))
        }

        // Action principale en bas, dans la zone du pouce.
        BigButton(
            text = if (hour < 14) "Préparer aujourd'hui" else "Préparer demain",
            onClick = { onPrepare(if (hour < 14) today else Dates.tomorrowIso()) },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
