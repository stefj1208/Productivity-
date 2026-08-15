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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates

/**
 * La boîte de réception (GTD), accessible à tout moment et plus seulement
 * le dimanche.
 *
 * Une note capturée en trois secondes ne sert à rien si on ne la revoit qu'une
 * fois par semaine : elle finit par peser autant que la chose qu'elle devait
 * décharger. D'où le raccourci, avec le nombre en attente écrit dessus.
 */
@Composable
fun InboxScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val myId = settings.myUserId
    val weekStart = Dates.weekStartIso()
    val aiReady = settings.aiEnabled && settings.aiApiKey.isNotBlank()

    val inbox by remember(myId) { vm.repo.db.inbox().pending(myId) }
        .collectAsState(initial = emptyList())
    val aiBusy by vm.aiBusy.collectAsState()
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "📥 Mes notes",
            subtitle = if (inbox.isEmpty()) "Tête libre" else "${inbox.size} en attente",
            onBack = onBack
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Ex. : rappeler le plombier mardi") },
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                vm.capture(note)
                note = ""
            }, enabled = note.isNotBlank()) { Text("Noter") }
        }

        if (inbox.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            EmptyState(
                emoji = "🧠",
                text = "Rien en attente. Tout ce qui vous traverse l'esprit se note ici, " +
                    "et se trie plus tard — c'est tout l'intérêt."
            )
            Spacer(Modifier.height(32.dp))
            return@Column
        }

        if (aiReady) {
            Spacer(Modifier.height(14.dp))
            AiButton(
                text = "Tout transformer en actions",
                busy = aiBusy,
                onClick = { vm.inboxToActionsWithAi(weekStart) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Chaque note devient une action qui commence par un verbe, posée " +
                    "sur le bon jour. Ce qui demande encore réflexion reste ici.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("UNE DÉCISION PAR NOTE")
        inbox.forEach { item ->
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(item.text, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.resolveInbox(item.id, "planifier") },
                        modifier = Modifier.height(48.dp)
                    ) { Text("Planifier") }
                    TextButton(onClick = { vm.resolveInbox(item.id, "fait") }) { Text("Déjà fait") }
                    TextButton(onClick = { vm.resolveInbox(item.id, "supprimer") }) { Text("Jeter") }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
