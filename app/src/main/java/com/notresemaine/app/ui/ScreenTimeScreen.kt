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
import androidx.compose.foundation.layout.width
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.pacte.BlockerService
import com.notresemaine.app.pacte.Usage

/** Une ligne de diagnostic : ce qui va, ce qui manque — sans jargon. */
@Composable
private fun CheckLine(ok: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(if (ok) "✅" else "⚠️", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (ok) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Temps d'écran & Pacte : permission, choix des applications « réseaux sociaux »,
 * limite quotidienne, et activation du blocage tenu à deux.
 */
@Composable
fun ScreenTimeScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    // Les autorisations se donnent hors de l'application : on les relit à
    // chaque retour sur cet écran, sinon l'affichage ment.
    var checks by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val hasPermission = remember(checks) { Usage.hasPermission(context) }
    val canOverlay = remember(checks) { Usage.canOverlay(context) }
    val watching = remember(checks) { BlockerService.running }

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

    val aiBusy by vm.aiBusy.collectAsState()
    val aiPacte by vm.aiPacte.collectAsState()
    var aiWhy by remember { mutableStateOf("") }

    // La proposition remplit les réglages ci-dessus ; elle ne s'applique qu'à l'enregistrement.
    androidx.compose.runtime.LaunchedEffect(aiPacte) {
        val advice = aiPacte ?: return@LaunchedEffect
        limit = advice.limitMinutes.toString()
        curfewEnabled = true
        curfewStart = advice.curfewStart
        curfewEnd = advice.curfewEnd
        aiWhy = advice.why
        vm.clearAiPacte()
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
            ScreenHeader(
                title = "📵 Pacte d'écran",
                subtitle = if (settings.pacteEnabled) "Actif · ${settings.dailyLimitMinutes} min/jour"
                else "Aucun engagement",
                onBack = onBack
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("1 · DEUX AUTORISATIONS ANDROID")
            CheckLine(hasPermission, "Lire le temps d'écran")
            if (!hasPermission) {
                Text(
                    text = "Cherchez « Notre Semaine » dans la liste qui s'ouvre.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    modifier = Modifier.height(48.dp)
                ) { Text("Ouvrir les réglages Android") }
                Spacer(Modifier.height(8.dp))
            }
            CheckLine(canOverlay, "Afficher par-dessus les autres applications")
            if (!canOverlay) {
                Text(
                    text = "Sans elle, Android empêche l'écran de blocage de s'ouvrir : " +
                        "la limite serait comptée mais rien ne se passerait.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { context.startActivity(Usage.overlaySettingsIntent(context)) },
                    modifier = Modifier.height(48.dp)
                ) { Text("Ouvrir les réglages Android") }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("2 · APPLICATIONS À LIMITER")
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
            SectionLabel("3 · LIMITE PAR JOUR (MINUTES)")
            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.4f)
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("4 · COUVRE-FEU")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Plus d'écran à partir d'une heure fixe.",
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
                        text = "Mode strict : tout, pas seulement les réseaux. " +
                            "Téléphone, messages, réveil et photo restent accessibles.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = curfewStrict, onCheckedChange = { curfewStrict = it })
                }
            }

            if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
                AiButton(
                    text = "Proposer un pacte réaliste",
                    busy = aiBusy,
                    onClick = { vm.suggestPacteWithAi() },
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = aiWhy.ifBlank { "D'après vos 7 derniers jours mesurés." },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (aiWhy.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("5 · LE PACTE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Au-delà de la limite, blocage. Seule l'autre moitié peut " +
                        "accorder une pause.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Text(
                text = "⏳ Durcir s'applique tout de suite. Assouplir attend demain.",
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

            // ----- Vérification : quel maillon manque -----
            Spacer(Modifier.height(16.dp))
            SectionLabel("6 · LE BLOCAGE MARCHE-T-IL ?")
            CheckLine(hasPermission, "Lire le temps d'écran")
            CheckLine(canOverlay, "Afficher par-dessus les autres applications")
            CheckLine(watching, "Surveillance en marche")
            if (!watching && settings.pacteEnabled) {
                OutlinedButton(
                    onClick = {
                        BlockerService.startIfEnabled(context, true)
                        checks++
                    },
                    modifier = Modifier.height(48.dp)
                ) { Text("Relancer la surveillance") }
            }
            CheckLine(selected.isNotEmpty(), "${selected.size} applications suivies")
            val minutesToday = myToday?.socialMinutes ?: 0
            val cap = limit.toIntOrNull() ?: 0
            CheckLine(
                cap > 0 && minutesToday < cap,
                "$minutesToday min aujourd'hui sur $cap min autorisées"
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        com.notresemaine.app.pacte.BlockActivity.intent(
                            context, minutesToday, false, ""
                        )
                    )
                },
                modifier = Modifier.height(48.dp)
            ) { Text("Voir l'écran de blocage") }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Honor : Batterie → Lancement d'applications → gestion manuelle. " +
                    "S23 : Batterie → Non restreinte. Sinon la surveillance est tuée.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

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
