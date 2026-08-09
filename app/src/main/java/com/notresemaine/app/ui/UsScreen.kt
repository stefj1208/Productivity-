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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.ProfileEntity
import com.notresemaine.app.data.TaskEntity
import com.notresemaine.app.data.WeekPlanEntity
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

@Composable
fun UsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onGoToSettings: () -> Unit,
    onScreenTime: () -> Unit,
    onHealth: () -> Unit
) {
    val myId = settings.myUserId
    val weekStart = Dates.weekStartIso()
    val today = Dates.todayIso()

    val profiles by remember { vm.repo.db.profiles().all() }
        .collectAsState(initial = emptyList())
    val weekPlans by remember { vm.repo.db.weekPlans().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val weekTasks by remember { vm.repo.db.tasks().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val usageDays by remember { vm.repo.db.usage().since(today) }
        .collectAsState(initial = emptyList())
    val graces by remember { vm.repo.db.grace().forDate(today) }
        .collectAsState(initial = emptyList())

    val me = profiles.firstOrNull { it.id == myId }
        ?: ProfileEntity(id = myId, name = settings.myName, color = settings.myColor, updatedAt = 0)
    val partner = profiles.firstOrNull { it.id != myId }

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
            Text(text = "Nous deux", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Semaine du ${Dates.shortLabel(weekStart)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(24.dp))

            if (partner == null) {
                Text(
                    text = "L'autre moitié n'est pas encore connectée.\n\n" +
                        "Activez la synchronisation dans Réglages, créez votre espace couple " +
                        "et partagez le code : sa semaine apparaîtra ici, à côté de la vôtre.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    PersonColumn(
                        profile = me,
                        weekPlan = weekPlans.firstOrNull { it.userId == me.id },
                        todayTasks = weekTasks.filter { it.userId == me.id && it.date == today },
                        modifier = Modifier.weight(1f)
                    )
                    PersonColumn(
                        profile = partner,
                        weekPlan = weekPlans.firstOrNull { it.userId == partner.id },
                        todayTasks = weekTasks.filter { it.userId == partner.id && it.date == today },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ----- Le Pacte d'écran, visible en permanence par les deux -----
                Spacer(Modifier.height(28.dp))
                SectionLabel("NOTRE PACTE D'ÉCRAN")

                val pendingForMe = graces.filter { it.toUser == myId && it.status == "pending" }
                if (pendingForMe.isNotEmpty()) {
                    pendingForMe.forEach { request ->
                        Text(
                            text = "⏸ ${partner.name} demande ${request.minutes} min de pause.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Row {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { vm.answerGrace(request.id, true) },
                                modifier = Modifier.padding(top = 8.dp, end = 12.dp)
                            ) { Text("Accorder ${request.minutes} min") }
                            androidx.compose.material3.TextButton(
                                onClick = { vm.answerGrace(request.id, false) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) { Text("Pas ce soir") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                listOf(me, partner).forEach { person ->
                    val usage = usageDays.firstOrNull { it.userId == person.id }
                    PacteRow(profile = person, socialMinutes = usage?.socialMinutes)
                }

                Text(
                    text = "Le durcir prend effet tout de suite, l'assouplir attend le lendemain. " +
                        "Vous voyez tous les deux les réglages de l'autre — c'est là qu'est le " +
                        "contrôle mutuel.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            ShortcutTile(
                emoji = "📵",
                title = "Mon pacte d'écran",
                subtitle = if (settings.pacteEnabled) "Limite ${settings.dailyLimitMinutes} min/jour" +
                    (if (settings.curfewEnabled) " · couvre-feu ${settings.curfewStart}" else "")
                else "Pas encore d'engagement",
                onClick = onScreenTime
            )
            ShortcutTile(
                emoji = "😴",
                title = "Sommeil & sport",
                subtitle = "Mesures automatiques ou saisie en 10 secondes",
                onClick = onHealth
            )
            Spacer(Modifier.height(20.dp))
        }

        if (partner == null) {
            BigButton(
                text = "Configurer la synchronisation",
                onClick = onGoToSettings,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            BigButton(
                text = "Envoyer un bravo à ${partner.name} 👏",
                onClick = { vm.sendBravo(partner.id) },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

/** L'engagement d'une personne et où elle en est aujourd'hui, en une ligne lisible. */
@Composable
private fun PacteRow(profile: ProfileEntity, socialMinutes: Int?) {
    val accent = accentFor(profile.color)
    val overLimit = socialMinutes != null && profile.dailyLimitMinutes > 0 &&
        socialMinutes >= profile.dailyLimitMinutes
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            Text(
                text = " ${profile.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (profile.pacteEnabled) "Pacte actif" else "Pacte inactif",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (!profile.pacteEnabled) "Aucun engagement pour l'instant."
            else buildString {
                append("Limite ${profile.dailyLimitMinutes} min/jour")
                if (profile.curfewEnabled) {
                    append(" · couvre-feu ${profile.curfewStart}–${profile.curfewEnd}")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = when {
                socialMinutes == null -> "Aujourd'hui : pas encore de relevé."
                overLimit -> "Aujourd'hui : $socialMinutes min — limite atteinte."
                else -> "Aujourd'hui : $socialMinutes min."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (overLimit) NeutralGray else accent,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun PersonColumn(
    profile: ProfileEntity,
    weekPlan: WeekPlanEntity?,
    todayTasks: List<TaskEntity>,
    modifier: Modifier = Modifier
) {
    val accent = accentFor(profile.color)
    Column(modifier = modifier) {
        PersonBadge(name = profile.name, colorRole = profile.color)
        Spacer(Modifier.height(16.dp))

        SectionLabel("PRIORITÉ DE LA SEMAINE")
        Text(
            text = weekPlan?.priority ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = if (weekPlan?.priority != null) accent else NeutralGray
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel("AUJOURD'HUI")
        val priority = todayTasks.firstOrNull { it.isPriority }
        if (priority != null) {
            Text(
                text = (if (priority.done) "✓ " else "· ") + priority.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (priority.done) NeutralGray else MaterialTheme.colorScheme.onBackground
            )
        } else {
            Text(text = "—", style = MaterialTheme.typography.bodyLarge, color = NeutralGray)
        }
        val done = todayTasks.count { it.done }
        val total = todayTasks.size
        if (total > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$done/$total fait" + if (done > 1) "s" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
