package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import kotlinx.coroutines.flow.first

/**
 * « Préparer demain » (ou aujourd'hui) : 2 minutes maximum.
 * Une priorité, 2 tâches secondaires maximum, réveil, bloc de concentration.
 */
@Composable
fun PrepareScreen(vm: AppViewModel, settings: AppSettings, dateIso: String, onDone: () -> Unit) {
    var priority by remember { mutableStateOf("") }
    var task1 by remember { mutableStateOf("") }
    var task2 by remember { mutableStateOf("") }
    var wake by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(dateIso) {
        val tasks = vm.repo.db.tasks().byDate(settings.myUserId, dateIso).first()
        priority = tasks.firstOrNull { it.isPriority }?.title ?: ""
        val secondary = tasks.filter { !it.isPriority && !it.isSport }.map { it.title }
        task1 = secondary.getOrNull(0) ?: ""
        task2 = secondary.getOrNull(1) ?: ""
        val plan = vm.repo.db.dayPlans().byDate(settings.myUserId, dateIso).first()
        wake = plan?.wakeTime ?: ""
        focus = plan?.focusBlocks ?: ""
        loaded = true
    }

    val isToday = dateIso == Dates.todayIso()
    val wakeOk = wake.isBlank() || Dates.isValidTime(wake)
    val aiBusy by vm.aiBusy.collectAsState()
    val aiDay by vm.aiDayAdvice.collectAsState()
    val aiAvailable = settings.aiEnabled && settings.aiApiKey.isNotBlank()

    // La proposition remplit les champs ; rien n'est enregistré tant que vous n'avez pas validé.
    LaunchedEffect(aiDay) {
        val advice = aiDay ?: return@LaunchedEffect
        priority = advice.priority
        task1 = advice.secondary.getOrNull(0) ?: task1
        task2 = advice.secondary.getOrNull(1) ?: task2
        vm.clearAiDayAdvice()
    }

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
                text = if (isToday) "Aujourd'hui" else "Préparer demain",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = Dates.longLabel(dateIso),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("LA PRIORITÉ (une seule)")
            OutlinedTextField(
                value = priority,
                onValueChange = { priority = it },
                placeholder = { Text("La chose qui rend le reste plus simple") },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = loaded
            )

            if (aiAvailable) {
                AiButton(
                    text = "Choisir la priorité à ma place",
                    busy = aiBusy,
                    enabled = loaded,
                    onClick = { vm.suggestDayWithAi(dateIso) },
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    text = "Décide à partir de la priorité de votre semaine, de vos objectifs " +
                        "et des tâches en attente. Rien n'est enregistré avant « C'est prêt ».",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("ENSUITE (2 maximum)")
            OutlinedTextField(
                value = task1,
                onValueChange = { task1 = it },
                placeholder = { Text("Tâche secondaire 1") },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = loaded
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = task2,
                onValueChange = { task2 = it },
                placeholder = { Text("Tâche secondaire 2") },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = loaded
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("RÉVEIL")
            OutlinedTextField(
                value = wake,
                onValueChange = { wake = it },
                placeholder = { Text("06:30") },
                isError = !wakeOk,
                supportingText = if (!wakeOk) {
                    { Text("Format attendu : HH:MM") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("BLOC DE CONCENTRATION À PROTÉGER")
            OutlinedTextField(
                value = focus,
                onValueChange = { focus = it },
                placeholder = { Text("Ex. : 9h–11h dossier client") },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(20.dp))
        }

        TextButton(onClick = onDone, modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Annuler")
        }
        BigButton(
            text = "C'est prêt",
            enabled = loaded && priority.isNotBlank() && wakeOk,
            onClick = {
                vm.savePreparation(
                    date = dateIso,
                    priority = priority,
                    secondary = listOf(task1, task2),
                    wakeTime = wake,
                    focusBlocks = focus
                )
                onDone()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
