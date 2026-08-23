package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.MenuIdeas
import com.notresemaine.app.data.TaskEntity
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor
import java.time.LocalTime

/**
 * La journée heure par heure.
 *
 * Une tâche sans créneau reste une intention : elle attend en bas, dans « à caser ».
 * Dès qu'on lui donne une heure, elle prend sa place dans la colonne — et elle
 * déclenche un rappel à ce moment-là.
 */
@Composable
fun DayScreen(
    vm: AppViewModel,
    settings: AppSettings,
    dateIso: String,
    onPrepare: (String) -> Unit,
    onBack: () -> Unit
) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val isToday = dateIso == Dates.todayIso()

    val tasks by remember(myId, dateIso) { vm.repo.db.tasks().byDate(myId, dateIso) }
        .collectAsState(initial = emptyList())
    val plan by remember(myId, dateIso) { vm.repo.db.dayPlans().byDate(myId, dateIso) }
        .collectAsState(initial = null)
    val meals by remember(dateIso) { vm.repo.db.meals().between(dateIso, dateIso) }
        .collectAsState(initial = emptyList())

    var slotFor by remember { mutableStateOf<TaskEntity?>(null) }

    // Ce qui est déjà pris dans l'agenda du téléphone : sans ça, on réserve
    // un créneau par-dessus une réunion et on ne le découvre qu'au dernier moment.
    val context = androidx.compose.ui.platform.LocalContext.current
    val booked = remember(dateIso, settings.calendarEnabled) {
        if (!settings.calendarEnabled) emptyList()
        else runCatching {
            com.notresemaine.app.calendar.PhoneCalendar.bookedOn(
                context, java.time.LocalDate.parse(dateIso)
            ).filter { !it.ours }
        }.getOrDefault(emptyList())
    }

    val scheduled = tasks.filter { it.startTime.isNotBlank() }
    val unscheduled = tasks.filter { it.startTime.isBlank() }
    val firstHour = (scheduled.mapNotNull { hourOf(it.startTime) }.minOrNull() ?: 7).coerceAtMost(7)
    val lastHour = (scheduled.mapNotNull { hourOf(it.startTime) }.maxOrNull() ?: 21).coerceAtLeast(21)
    val nowHour = LocalTime.now().hour

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
                title = if (isToday) "Aujourd'hui" else Dates.shortLabel(dateIso),
                subtitle = Dates.longLabel(dateIso),
                onBack = onBack
            )

            val p = plan
            if (p?.wakeTime != null || p?.focusBlocks != null) {
                Row(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    if (p.wakeTime != null) {
                        Text(
                            text = "⏰ ${p.wakeTime}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                    if (p.focusBlocks != null) {
                        Text(
                            text = "🎧 ${p.focusBlocks}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val allDay = booked.filter { it.allDay }
            if (allDay.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("TOUTE LA JOURNÉE")
                allDay.forEach { ev ->
                    Text(
                        text = "📅 ${ev.title}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("LA JOURNÉE, HEURE PAR HEURE")

            (firstHour..lastHour).forEach { h ->
                val atThisHour = scheduled.filter { hourOf(it.startTime) == h }
                val mealHere = mealAt(meals.map { it.slot to it.title }, h)
                val eventsHere = booked.filter { !it.allDay && it.startMinutes / 60 == h }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "%02d h".format(h),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday && h == nowHour) accent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(48.dp)
                            .padding(top = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        eventsHere.forEach { ev ->
                            Text(
                                text = "📅 ${ev.title}  ·  %02d:%02d".format(
                                    ev.startMinutes / 60, ev.startMinutes % 60
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        if (mealHere != null) {
                            Text(
                                text = "🍽️ $mealHere",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        atThisHour.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .defaultMinSize(minHeight = 48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { vm.toggleDone(task.id) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = (if (task.done) "✓ " else "") + task.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (task.done) NeutralGray else accent
                                    )
                                    Text(
                                        text = task.startTime +
                                            if (task.durationMinutes > 0) " · ${task.durationMinutes} min" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { slotFor = task }) { Text("🕐") }
                            }
                        }
                        if (atThisHour.isEmpty() && mealHere == null && eventsHere.isEmpty()) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            if (unscheduled.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("À CASER — touchez pour réserver un créneau")
                unscheduled.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { slotFor = task }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (if (task.done) "✓ " else "· ") + task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (task.done) NeutralGray else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text("🕐", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (tasks.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                EmptyState(
                    emoji = "📭",
                    text = "Rien de prévu ce jour-là.",
                    actionLabel = "Préparer cette journée",
                    onAction = { onPrepare(dateIso) }
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = "Modifier cette journée",
            onClick = { onPrepare(dateIso) },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    val task = slotFor
    if (task != null) {
        var time by remember(task.id) { mutableStateOf(task.startTime.ifBlank { "09:00" }) }
        var duration by remember(task.id) {
            mutableStateOf(if (task.durationMinutes > 0) task.durationMinutes.toString() else "30")
        }
        AlertDialog(
            onDismissRequest = { slotFor = null },
            title = { Text("Réserver un créneau") },
            text = {
                Column {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    TimeField(
                        label = "DÉBUT",
                        value = time,
                        onChange = { time = it }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "COMBIEN DE TEMPS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 90).forEach { minutes ->
                            ChoiceChip(
                                label = "$minutes′",
                                selected = duration == minutes.toString(),
                                onClick = { duration = minutes.toString() }
                            )
                        }
                    }
                    Text(
                        text = "Un rappel se déclenchera à cette heure-là.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (task.startTime.isNotBlank()) {
                        TextButton(onClick = {
                            vm.setTaskSlot(task.id, "", 0)
                            slotFor = null
                        }) { Text("Retirer le créneau") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setTaskSlot(task.id, time, duration.toIntOrNull() ?: 30)
                        slotFor = null
                    },
                    enabled = Dates.isValidTime(time)
                ) { Text("Réserver") }
            },
            dismissButton = {
                TextButton(onClick = { slotFor = null }) { Text("Annuler") }
            }
        )
    }
}

private fun hourOf(time: String): Int? =
    time.split(":").getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 }

/** Les repas ont une heure conventionnelle : ils servent de repères dans la colonne. */
private fun mealAt(meals: List<Pair<String, String>>, hour: Int): String? {
    val slot = when (hour) {
        8 -> "matin"
        12 -> "midi"
        19 -> "soir"
        else -> return null
    }
    val title = meals.firstOrNull { it.first == slot }?.second
    return if (title.isNullOrBlank()) null else {
        val label = MenuIdeas.slots.firstOrNull { it.first == slot }?.second ?: slot
        "$label · $title"
    }
}
