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
import androidx.compose.material3.Icon
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
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

@Composable
fun TodayScreen(vm: AppViewModel, settings: AppSettings, onPrepare: (String) -> Unit) {
    val today = Dates.todayIso()
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)

    val tasks by remember(myId) { vm.repo.db.tasks().byDate(myId, today) }
        .collectAsState(initial = emptyList())
    val plan by remember(myId) { vm.repo.db.dayPlans().byDate(myId, today) }
        .collectAsState(initial = null)
    val bravos by remember(myId) { vm.repo.db.encouragements().forDate(myId, today) }
        .collectAsState(initial = emptyList())
    val profiles by remember { vm.repo.db.profiles().all() }
        .collectAsState(initial = emptyList())

    val priority = tasks.firstOrNull { it.isPriority }
    val others = tasks.filter { !it.isPriority }

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
            Text(
                text = Dates.longLabel(today),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (bravos.isNotEmpty()) {
                val fromName = profiles.firstOrNull { it.id == bravos.first().fromUser }?.name ?: "Ton binôme"
                Text(
                    text = "👏 $fromName t'envoie un bravo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel("MA PRIORITÉ")

            if (priority == null) {
                Text(
                    text = "Définir ma priorité",
                    style = MaterialTheme.typography.displaySmall,
                    color = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { onPrepare(today) }
                        .padding(vertical = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { vm.toggleDone(priority.id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (priority.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (priority.done) "Fait" else "À faire",
                        tint = if (priority.done) NeutralGray else accent,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = priority.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = if (priority.done) NeutralGray else MaterialTheme.colorScheme.onBackground,
                        textDecoration = if (priority.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            if (others.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("ENSUITE")
                others.forEach { task ->
                    TaskRow(task = task, accent = accent, onToggle = { vm.toggleDone(task.id) })
                }
            }

            val p = plan
            if (p != null && (p.wakeTime != null || p.focusBlocks != null)) {
                Spacer(Modifier.height(20.dp))
                if (p.wakeTime != null) {
                    Text(
                        text = "⏰ Réveil ${p.wakeTime}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (p.focusBlocks != null) {
                    Text(
                        text = "🎧 Concentration : ${p.focusBlocks}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Action principale en bas, dans la zone du pouce.
        BigButton(
            text = "Préparer demain",
            onClick = { onPrepare(Dates.tomorrowIso()) },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
