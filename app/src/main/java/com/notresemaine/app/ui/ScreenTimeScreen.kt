package com.notresemaine.app.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.pacte.Usage

/**
 * Temps d'écran & Pacte : permission, choix des applications « réseaux sociaux »,
 * limite quotidienne, et activation du blocage tenu à deux.
 */
@Composable
fun ScreenTimeScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val hasPermission = Usage.hasPermission(context)

    var enabled by remember(settings.pacteEnabled) { mutableStateOf(settings.pacteEnabled) }
    var limit by remember(settings.dailyLimitMinutes) { mutableStateOf(settings.dailyLimitMinutes.toString()) }
    var selected by remember(settings.socialApps) {
        mutableStateOf(
            settings.socialApps.split(",").filter { it.isNotBlank() }.toSet()
                .ifEmpty { Usage.KNOWN_SOCIAL }
        )
    }
    var showApps by remember { mutableStateOf(false) }
    var curfewEnabled by remember(settings.curfewEnabled) { mutableStateOf(settings.curfewEnabled) }
    var curfewStart by remember(settings.curfewStart) { mutableStateOf(settings.curfewStart) }
    var curfewEnd by remember(settings.curfewEnd) { mutableStateOf(settings.curfewEnd) }
    var curfewStrict by remember(settings.curfewStrict) { mutableStateOf(settings.curfewStrict) }

    val usageDays by remember { vm.repo.db.usage().since(Dates.previousWeekStartIso()) }
        .collectAsState(initial = emptyList())
    val myToday = usageDays.firstOrNull { it.userId == settings.myUserId && it.date == Dates.todayIso() }

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
            Text("Temps d'écran & Pacte", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(16.dp))
            SectionLabel("ÉTAPE 1 · PERMISSION ANDROID")
            if (hasPermission) {
                Text("Accès aux données d'utilisation : accordé ✓", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = "Android demande une permission spéciale, hors de l'application :\n" +
                        "1. Touche le bouton ci-dessous.\n" +
                        "2. Dans la liste, cherche « Notre Semaine ».\n" +
                        "3. Active « Autoriser l'accès aux données d'utilisation ».\n" +
                        "4. Reviens ici avec le bouton Retour.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    modifier = Modifier.height(48.dp)
                ) { Text("Ouvrir les réglages Android") }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("ÉTAPE 2 · MES APPLICATIONS À LIMITER")
            Text(
                text = "${selected.size} applications suivies",
                style = MaterialTheme.typography.bodyLarge
            )
            TextButton(onClick = { showApps = !showApps }) {
                Text(if (showApps) "Fermer la liste" else "Choisir les applications")
            }
            if (showApps) {
                val apps = remember { Usage.launchableApps(context) }
                apps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = app.packageName in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + app.packageName
                                else selected - app.packageName
                            }
                        )
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("ÉTAPE 3 · LIMITE QUOTIDIENNE (minutes)")
            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.4f)
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("ÉTAPE 4 · COUVRE-FEU")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Plus d'écran à partir d'une heure fixe, pour ne pas se coucher trop tard.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = curfewEnabled, onCheckedChange = { curfewEnabled = it })
            }
            if (curfewEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = curfewStart, onValueChange = { curfewStart = it },
                        label = { Text("De") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = curfewEnd, onValueChange = { curfewEnd = it },
                        label = { Text("À") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Mode strict : toutes les applications, pas seulement les réseaux. " +
                            "Téléphone, messages, réveil et appareil photo restent toujours accessibles.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = curfewStrict, onCheckedChange = { curfewStrict = it })
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("ÉTAPE 5 · LE PACTE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Au-delà de la limite ou pendant le couvre-feu, blocage.\n" +
                        "Seule l'autre moitié peut accorder une pause.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Text(
                text = "⏳ Un engagement ne se relâche pas dans l'instant : durcir le pacte prend " +
                    "effet immédiatement, l'assouplir attend le lendemain. Vos réglages sont " +
                    "visibles par l'autre dans l'onglet Nous.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (settings.pendingFromDate.isNotBlank()) {
                Text(
                    text = "Assouplissement en attente, effectif le ${Dates.shortLabel(settings.pendingFromDate)}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (myToday != null) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("AUJOURD'HUI")
                Text(
                    text = "Écran : ${myToday.totalMinutes} min · réseaux : ${myToday.socialMinutes} min · " +
                        "${myToday.unlocks} déverrouillages",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            TextButton(onClick = { vm.refreshUsage() }, enabled = hasPermission) {
                Text("Relever maintenant")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Sur le Honor 400 Pro : Paramètres → Batterie → Lancement d'applications → " +
                    "Notre Semaine → désactiver « Gestion automatique » et tout autoriser en manuel, " +
                    "sinon MagicOS tuera la surveillance en arrière-plan. " +
                    "Sur le S23 : Paramètres → Batterie → mettre l'application en « Non restreinte ».",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        TextButton(onClick = onBack) { Text("Retour") }
        BigButton(
            text = "Enregistrer le pacte",
            enabled = limit.toIntOrNull() != null && (!enabled || hasPermission) &&
                (!curfewEnabled || (Dates.isValidTime(curfewStart) && Dates.isValidTime(curfewEnd))),
            onClick = {
                vm.savePacte(
                    enabled, selected.toList(), limit.toIntOrNull() ?: 45,
                    curfewEnabled, curfewStart, curfewEnd, curfewStrict
                )
                onBack()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
