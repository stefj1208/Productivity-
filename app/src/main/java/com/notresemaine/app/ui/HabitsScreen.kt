package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.notresemaine.app.data.HabitEntity
import com.notresemaine.app.data.Habits

/**
 * Les habitudes qui font la différence.
 *
 * Ce ne sont pas des tâches : rien à cocher, rien à réussir. Ce sont des
 * consignes courtes qui reviennent **à des moments imprévisibles** de la
 * journée — un rappel toujours à la même heure devient un meuble qu'on ne voit
 * plus, et un rappel qu'on ne voit plus ne change rien.
 */
@Composable
fun HabitsScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val myId = settings.myUserId
    val aiReady = settings.aiEnabled && settings.aiApiKey.isNotBlank()

    val all by remember { vm.repo.db.habits().all() }.collectAsState(initial = emptyList())
    val mine = all.filter { it.userId == myId }
    val aiBusy by vm.aiBusy.collectAsState()
    val ideas by vm.aiHabits.collectAsState()

    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf("") }

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
                title = "🔁 Mes habitudes",
                subtitle = if (mine.none { it.enabled }) "Aucune active"
                else "${mine.count { it.enabled }} active(s) · rappels au hasard",
                onBack = onBack
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Une consigne courte, rappelée quand vous ne l'attendez pas.",
                style = MaterialTheme.typography.bodyLarge
            )

            if (mine.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("POUR COMMENCER")
                Habits.STARTERS.forEach { idea ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(idea.first, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = idea.second,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { vm.addHabit(idea.first, idea.second) }) { Text("Ajouter") }
                    }
                }
            }

            if (mine.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("MES HABITUDES")
                mine.forEach { habit ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(habit.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = buildString {
                                    if (habit.source.isNotBlank()) append("${habit.source} · ")
                                    append("${habit.perDay}×/jour entre ${habit.fromHour} h et ${habit.toHour} h")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = habit.enabled,
                            onCheckedChange = { vm.toggleHabit(habit.id) }
                        )
                        TextButton(onClick = { editing = habit }) { Text("✏️") }
                    }
                }
            }

            // ----- L'assistant en propose d'après les six livres -----
            if (aiReady) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("EN TROUVER D'AUTRES")
                OutlinedTextField(
                    value = focus,
                    onValueChange = { focus = it },
                    placeholder = { Text("Thème : concentration, alimentation, écrans…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                AiButton(
                    text = "Proposer des habitudes",
                    busy = aiBusy,
                    onClick = { vm.suggestHabitsWithAi(focus) },
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (ideas.isEmpty()) {
                    Text(
                        text = "Tirées des six livres. N'envoie que le thème que vous écrivez.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    ideas.forEach { idea ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(idea.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = idea.source,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { vm.addHabit(idea.title, idea.source) }) { Text("Ajouter") }
                        }
                    }
                    TextButton(onClick = { vm.clearAiHabits() }) { Text("Effacer les propositions") }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Les rappels s'affichent pendant que l'application est ouverte. " +
                    "Android n'autorise pas une fenêtre par-dessus une autre application " +
                    "sans en faire une alarme — et une alarme pour « repose ton téléphone » " +
                    "serait pire que le mal.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }

        BigButton(
            text = "Écrire ma propre habitude",
            onClick = { creating = true },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    val edited = editing
    if (creating || edited != null) {
        HabitDialog(
            existing = edited,
            onSave = { title, source, from, to, perDay ->
                vm.saveHabit(edited?.id, title, source, from, to, perDay)
                creating = false
                editing = null
            },
            onDelete = edited?.let {
                {
                    vm.deleteHabit(it.id)
                    editing = null
                }
            },
            onDismiss = {
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun HabitDialog(
    existing: HabitEntity?,
    onSave: (String, String, Int, Int, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var source by remember { mutableStateOf(existing?.source ?: "") }
    var from by remember { mutableStateOf((existing?.fromHour ?: 8).toString()) }
    var to by remember { mutableStateOf((existing?.toHour ?: 21).toString()) }
    var perDay by remember { mutableStateOf((existing?.perDay ?: 2).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nouvelle habitude" else "Modifier") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("La consigne") },
                    placeholder = { Text("Ex. : une seule chose à la fois") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Pourquoi (facultatif)") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "QUAND VOUS RAPPELER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = from, onValueChange = { from = it },
                        label = { Text("De (h)") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = to, onValueChange = { to = it },
                        label = { Text("À (h)") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = perDay, onValueChange = { perDay = it },
                        label = { Text("×/jour") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                        Text("🗑 Supprimer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title, source,
                        from.toIntOrNull() ?: 8,
                        to.toIntOrNull() ?: 21,
                        perDay.toIntOrNull() ?: 2
                    )
                },
                enabled = title.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
