package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notresemaine.app.calendar.PhoneCalendar
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor
import java.time.LocalDate

private fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * L'agenda de la semaine : ce qui est déjà pris, ce qu'on s'est réservé.
 *
 * Les deux sources sont mélangées volontairement. Une réunion et un créneau
 * de travail occupent le même temps ; les regarder séparément, c'est exactement
 * comme ça qu'on se retrouve à deux endroits à la fois.
 */
@Composable
fun AgendaScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onDay: (String) -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val today = Dates.todayIso()

    var weekOffset by remember { mutableIntStateOf(0) }
    val weekStart = Dates.weekStartIsoOffset(weekOffset)
    val days = Dates.daysOfWeek(weekStart)

    val weekTasks by remember(myId, weekStart) { vm.repo.db.tasks().byWeek(myId, weekStart) }
        .collectAsState(initial = emptyList())

    val aiBusy by vm.aiBusy.collectAsState()
    val note by vm.aiAgenda.collectAsState()

    val granted = PhoneCalendar.hasPermission(context)
    val events = remember(weekStart, granted) {
        if (!granted) emptyMap()
        else days.associateWith { d ->
            runCatching { PhoneCalendar.bookedOn(context, LocalDate.parse(d)) }
                .getOrDefault(emptyList())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "📅 Mon agenda",
            subtitle = Dates.weekRangeLabel(weekStart),
            onBack = onBack
        )

        WeekNavigator(
            weekStartIso = weekStart,
            onOffsetChange = { delta -> weekOffset += delta }
        )

        if (!granted || !settings.calendarEnabled) {
            Spacer(Modifier.height(16.dp))
            EmptyState(
                emoji = "📅",
                text = if (!granted) "L'agenda du téléphone n'est pas encore autorisé. " +
                    "Une fois relié, vos vraies réunions apparaissent ici, à côté de vos créneaux."
                else "L'agenda est autorisé mais pas activé.",
                actionLabel = "Relier mon agenda",
                onAction = onSettings
            )
        }

        // ----- La semaine, jour par jour -----
        days.forEach { day ->
            val dayEvents = events[day].orEmpty()
            val daySlots = weekTasks.filter { it.date == day && it.startTime.isNotBlank() }
            val dayOther = weekTasks.filter { it.date == day && it.startTime.isBlank() }
            val busy = dayEvents.sumOf { (it.endMinutes - it.startMinutes).coerceAtLeast(0) } +
                daySlots.sumOf { if (it.durationMinutes > 0) it.durationMinutes else 30 }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Dates.shortLabel(day),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (day == today) accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (busy == 0) "libre" else "${busy / 60} h ${"%02d".format(busy % 60)} pris",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val entries = (
                dayEvents.map {
                    Triple(
                        if (it.allDay) -1 else it.startMinutes,
                        "📅 ${it.title}",
                        if (it.allDay) "toute la journée" else "${hhmm(it.startMinutes)} – ${hhmm(it.endMinutes)}"
                    )
                } + daySlots.map { task ->
                    val start = runCatching { java.time.LocalTime.parse(task.startTime) }.getOrNull()
                    Triple(
                        start?.let { it.hour * 60 + it.minute } ?: 0,
                        (if (task.done) "✓ " else "🎯 ") + task.title,
                        task.startTime + if (task.durationMinutes > 0) " · ${task.durationMinutes} min" else ""
                    )
                }
                ).sortedBy { it.first }

            if (entries.isEmpty() && dayOther.isEmpty()) {
                Text(
                    text = "Rien de posé.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeutralGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDay(day) }
                        .padding(vertical = 10.dp)
                )
            }

            entries.forEach { (_, label, detail) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .clickable { onDay(day) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (dayOther.isNotEmpty()) {
                Text(
                    text = "+ ${dayOther.size} sans heure : ${dayOther.joinToString(", ") { it.title }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDay(day) }
                        .padding(vertical = 8.dp)
                )
            }
        }

        // ----- Ce que l'assistant en dit -----
        if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            AiButton(
                text = "Lire ma semaine",
                busy = aiBusy,
                onClick = {
                    vm.readAgendaWithAi(
                        buildString {
                            append("Semaine du ${Dates.weekRangeLabel(weekStart)}.\n")
                            days.forEach { day ->
                                val e = events[day].orEmpty()
                                val s = weekTasks.filter { it.date == day }
                                append("${Dates.shortLabel(day)} : ")
                                if (e.isEmpty() && s.isEmpty()) append("rien")
                                else {
                                    append(
                                        (e.map { ev ->
                                            if (ev.allDay) ev.title
                                            else "${ev.title} (${hhmm(ev.startMinutes)}-${hhmm(ev.endMinutes)})"
                                        } + s.map { t ->
                                            t.title + if (t.startTime.isNotBlank()) " (${t.startTime})" else ""
                                        }).joinToString(", ")
                                    )
                                }
                                append("\n")
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (note.isNotBlank()) {
                AiNote(text = note, modifier = Modifier.padding(top = 10.dp))
            } else {
                Text(
                    text = "N'envoie que les intitulés et horaires déjà présents ici.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("⚙️ Réglages de l'agenda") }

        Spacer(Modifier.height(32.dp))
    }
}
