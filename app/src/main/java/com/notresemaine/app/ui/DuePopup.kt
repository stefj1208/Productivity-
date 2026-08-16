package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Un rappel arrivé à échéance pendant que l'application est ouverte. */
data class DueReminder(
    val key: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val actionLabel: String,
    val route: String?
)

/**
 * Le rappel qui s'affiche quand on est DANS l'application.
 *
 * L'alarme plein écran ne se déclenche que téléphone posé : Android ne
 * l'affiche pas par-dessus l'application qui l'a programmée. Résultat, le seul
 * moment où l'on ratait ses créneaux, c'était en ayant l'app sous les yeux.
 *
 * On vérifie donc toutes les trente secondes, et on ne montre chaque rappel
 * qu'une fois par ouverture — un rappel qui revient en boucle se fait ignorer,
 * puis désactiver.
 */
@Composable
fun DueReminderPopup(
    vm: AppViewModel,
    settings: AppSettings,
    onGo: (String) -> Unit
) {
    val myId = settings.myUserId
    var due by remember { mutableStateOf<DueReminder?>(null) }
    val seen = remember { mutableSetOf<String>() }

    LaunchedEffect(myId, settings.alertsEnabled) {
        if (!settings.alertsEnabled) return@LaunchedEffect
        while (true) {
            val today = Dates.todayIso()
            val now = LocalTime.now()
            val nowMinutes = now.hour * 60 + now.minute

            val candidate = run {
                // 1. Un créneau réservé qui commence maintenant (±3 minutes).
                val slot = vm.repo.db.tasks().byDateOnce(myId, today)
                    .filter { it.startTime.isNotBlank() && !it.done && !it.deleted }
                    .firstOrNull { task ->
                        val t = runCatching { LocalTime.parse(task.startTime) }.getOrNull()
                            ?: return@firstOrNull false
                        val start = t.hour * 60 + t.minute
                        nowMinutes - start in 0..3
                    }
                if (slot != null) {
                    return@run DueReminder(
                        key = "slot:${slot.id}:$today",
                        emoji = "✅",
                        title = slot.title,
                        subtitle = "C'est le créneau que tu t'es réservé" +
                            if (slot.durationMinutes > 0) " · ${slot.durationMinutes} min." else ".",
                        actionLabel = "Voir ma journée",
                        route = "day/$today"
                    )
                }

                // 2. Une habitude, à un moment imprévisible de la journée.
                val date = java.time.LocalDate.now()
                com.notresemaine.app.data.Habits.let { habits ->
                    vm.repo.db.habits().activeOnce(myId).forEach { habit ->
                        val moments = habits.momentsOf(
                            habit.id, date, habit.fromHour, habit.toHour, habit.perDay
                        )
                        val hit = moments.firstOrNull { nowMinutes - it in 0..3 }
                        if (hit != null) {
                            return@run DueReminder(
                                key = "habit:${habit.id}:$today:$hit",
                                emoji = "🔁",
                                title = habit.title,
                                subtitle = habit.source.ifBlank { "Une habitude que vous avez choisie." },
                                actionLabel = "Mes habitudes",
                                route = "habits"
                            )
                        }
                    }
                }

                // 3. Le rappel du soir : préparer demain.
                if (settings.eveningEnabled) {
                    val evening = runCatching { LocalTime.parse(settings.eveningReminder) }.getOrNull()
                    if (evening != null) {
                        val target = evening.hour * 60 + evening.minute
                        if (nowMinutes - target in 0..3) {
                            return@run DueReminder(
                                key = "evening:$today",
                                emoji = "🌙",
                                title = "Préparer demain",
                                subtitle = "Deux minutes ce soir, zéro hésitation demain matin.",
                                actionLabel = "Préparer",
                                route = "prepare/${Dates.tomorrowIso()}"
                            )
                        }
                    }
                }
                null
            }

            if (candidate != null && candidate.key !in seen) {
                seen += candidate.key
                due = candidate
            }
            delay(30_000)
        }
    }

    val reminder = due ?: return
    AlertDialog(
        onDismissRequest = { due = null },
        title = { Text("${reminder.emoji} ${reminder.title}") },
        text = {
            Column {
                Text(reminder.subtitle, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                reminder.route?.let(onGo)
                due = null
            }) { Text(reminder.actionLabel) }
        },
        dismissButton = {
            TextButton(onClick = { due = null }) { Text("Plus tard") }
        }
    )
}
