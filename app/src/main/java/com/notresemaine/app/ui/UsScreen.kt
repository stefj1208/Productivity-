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
fun UsScreen(vm: AppViewModel, settings: AppSettings, onGoToSettings: () -> Unit) {
    val myId = settings.myUserId
    val weekStart = Dates.weekStartIso()
    val today = Dates.todayIso()

    val profiles by remember { vm.repo.db.profiles().all() }
        .collectAsState(initial = emptyList())
    val weekPlans by remember { vm.repo.db.weekPlans().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val weekTasks by remember { vm.repo.db.tasks().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())

    val me = profiles.firstOrNull { it.id == myId }
        ?: ProfileEntity(myId, settings.myName, settings.myColor, 0)
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
            }
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
