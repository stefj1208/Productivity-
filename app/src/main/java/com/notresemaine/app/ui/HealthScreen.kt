package com.notresemaine.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.health.Health

/**
 * Sommeil, pas et sport : Health Connect quand une source alimente les données,
 * saisie manuelle de secours en moins de 10 secondes sinon.
 */
@Composable
fun HealthScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val myId = settings.myUserId
    val today = Dates.todayIso()

    val status = remember { Health.status(context) }
    var granted by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        granted = Health.hasPermissions(context)
        checked = true
        if (granted) vm.refreshHealth()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { result ->
        granted = result.containsAll(Health.PERMISSIONS)
        if (granted) vm.refreshHealth()
    }

    val healthDays by remember { vm.repo.db.health().since(Dates.weekStartIsoOffset(-1)) }
        .collectAsState(initial = emptyList())
    val todayHealth = healthDays.firstOrNull { it.userId == myId && it.date == today }

    var bedTime by remember { mutableStateOf("23:00") }
    var wakeTime by remember { mutableStateOf(settings.wakeAlarm) }

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
            ScreenHeader(title = "😴 Sommeil & sport", onBack = onBack)

            Spacer(Modifier.height(16.dp))
            SectionLabel("MESURE AUTOMATIQUE (HEALTH CONNECT)")
            when {
                status == HealthConnectClient.SDK_UNAVAILABLE -> {
                    Text(
                        text = "Health Connect n'est pas disponible sur ce téléphone. " +
                            "La saisie manuelle ci-dessous fait le travail.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    Text(
                        text = "Health Connect doit être mis à jour depuis le Play Store.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).setData(
                                    android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")
                                )
                            )
                        },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(48.dp)
                    ) { Text("Ouvrir le Play Store") }
                }
                granted -> {
                    Text("Autorisé ✓ Les données sont relevées plusieurs fois par jour.",
                        style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { vm.refreshHealth() }) { Text("Relever maintenant") }
                }
                checked -> {
                    Text(
                        text = "Lecture seule du sommeil, des pas et des séances.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(Health.PERMISSIONS) },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(48.dp)
                    ) { Text("Autoriser Health Connect") }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Sans montre ni appli qui l'alimente, Health Connect reste à zéro. " +
                    "C'est normal : la saisie ci-dessous prend 10 secondes.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("AUJOURD'HUI")
            Text(
                text = if (todayHealth == null) "Aucune donnée pour l'instant."
                else "Sommeil ${todayHealth.sleepMinutes / 60} h ${todayHealth.sleepMinutes % 60} · " +
                    "${todayHealth.steps} pas · ${todayHealth.exerciseMinutes} min de sport" +
                    if (todayHealth.source == "manuel") " (saisi à la main)" else "",
                style = MaterialTheme.typography.bodyLarge
            )

            if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
                val aiBusy by vm.aiBusy.collectAsState()
                val aiRead by vm.aiHealthRead.collectAsState()
                AiButton(
                    text = "Lire ma semaine",
                    busy = aiBusy,
                    onClick = { vm.readHealthWithAi(Dates.weekStartIso()) },
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    text = aiRead.ifBlank { "Une phrase sur votre semaine, un levier à essayer." },
                    style = if (aiRead.isBlank()) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.bodyLarge,
                    color = if (aiRead.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("SAISIE RAPIDE — SOMMEIL")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = bedTime,
                    onValueChange = { bedTime = it },
                    label = { Text("Couché") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = wakeTime,
                    onValueChange = { wakeTime = it },
                    label = { Text("Levé") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = { vm.saveSleepManually(today, bedTime, wakeTime) },
                enabled = Dates.isValidTime(bedTime) && Dates.isValidTime(wakeTime),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .height(48.dp)
            ) { Text("Enregistrer la nuit") }

            Spacer(Modifier.height(24.dp))
            SectionLabel("SAISIE RAPIDE — SÉANCE DE SPORT")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(20, 30, 45, 60).forEach { minutes ->
                    OutlinedButton(
                        onClick = { vm.addExerciseManually(today, minutes) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) { Text("$minutes′") }
                }
            }
            Text(
                text = "Un tap = séance ajoutée à la journée.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(20.dp))
        }

    }
}
