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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.accentFor
import java.util.Locale

private fun kg(v: Double): String = String.format(Locale.FRANCE, "%.1f kg", v)

/**
 * Le poids : une courbe, un écart à l'objectif, une pesée en trois touches.
 *
 * Un chiffre isolé ne dit rien et fait culpabiliser ; c'est la pente qui informe.
 * D'où la courbe en premier, le dernier chiffre ensuite, et aucune couleur
 * d'alerte — un poids qui monte s'affiche exactement comme un poids qui baisse.
 */
@Composable
fun WeightScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)

    val all by remember { vm.repo.db.weights().all() }.collectAsState(initial = emptyList())
    val mine = all.filter { it.userId == myId }.sortedBy { it.date }
    val partnerEntries = all.filter { it.userId != myId }.sortedBy { it.date }

    val last = mine.lastOrNull()
    val first = mine.firstOrNull()
    val previous = mine.dropLast(1).lastOrNull()

    var entry by remember { mutableStateOf("") }
    var target by remember(settings.weightTarget) {
        mutableStateOf(if (settings.weightTarget > 0) settings.weightTarget.toString() else "")
    }
    var shared by remember(settings.weightShared) { mutableStateOf(settings.weightShared) }

    val aiBusy by vm.aiBusy.collectAsState()
    val aiRead by vm.aiWeightRead.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "⚖️ Mon poids",
            subtitle = last?.let { "Dernière pesée : ${kg(it.kilos)}" } ?: "Aucune pesée",
            onBack = onBack
        )

        // ----- La courbe d'abord -----
        Spacer(Modifier.height(8.dp))
        val shown = mine.takeLast(14)
        LineChart(
            values = shown.map { it.kilos.toFloat() },
            labels = shown.map { Dates.shortLabel(it.date) },
            accent = accent,
            valueLabel = { String.format(Locale.FRANCE, "%.1f", it) },
            target = settings.weightTarget.takeIf { it > 0 }?.toFloat()
        )

        // ----- Les trois chiffres qui comptent -----
        if (last != null) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BigStat(
                    value = String.format(Locale.FRANCE, "%.1f", last.kilos),
                    label = "aujourd'hui (kg)",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                if (previous != null) {
                    val delta = last.kilos - previous.kilos
                    BigStat(
                        value = String.format(Locale.FRANCE, "%+.1f", delta),
                        label = "depuis la précédente",
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (settings.weightTarget > 0) {
                val gap = last.kilos - settings.weightTarget
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (kotlin.math.abs(gap) < 0.15) "Objectif atteint."
                    else String.format(
                        Locale.FRANCE,
                        "%.1f kg %s l'objectif de %.1f kg.",
                        kotlin.math.abs(gap),
                        if (gap > 0) "au-dessus de" else "en dessous de",
                        settings.weightTarget
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (first != null && mine.size > 1) {
                Text(
                    text = String.format(
                        Locale.FRANCE, "%+.1f kg depuis la première pesée (%s).",
                        last.kilos - first.kilos, Dates.shortLabel(first.date)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ----- Se peser -----
        Spacer(Modifier.height(20.dp))
        SectionLabel("ME PESER AUJOURD'HUI")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it },
                label = { Text("kg") },
                placeholder = { Text("72,4") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            OutlinedButton(
                onClick = {
                    vm.saveWeight(entry)
                    entry = ""
                },
                enabled = entry.replace(',', '.').toDoubleOrNull() != null,
                modifier = Modifier.height(48.dp)
            ) { Text("Noter") }
        }
        TextButton(onClick = { vm.importWeightFromHealth() }) {
            Text("Récupérer depuis ma balance connectée")
        }

        // ----- Objectif -----
        Spacer(Modifier.height(12.dp))
        SectionLabel("MON OBJECTIF")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("kg visés") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            OutlinedButton(
                onClick = { vm.saveWeightGoal(target, shared) },
                modifier = Modifier.height(48.dp)
            ) { Text("Enregistrer") }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Partager avec mon binôme", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Éteint, la pesée ne quitte jamais ce téléphone.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = shared,
                onCheckedChange = {
                    shared = it
                    vm.saveWeightGoal(target, it)
                }
            )
        }

        if (shared && partnerEntries.isNotEmpty()) {
            val theirs = partnerEntries.last()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Binôme : ${kg(theirs.kilos)} le ${Dates.shortLabel(theirs.date)}.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ----- Assistant -----
        if (settings.aiEnabled && settings.aiApiKey.isNotBlank() && mine.size >= 2) {
            Spacer(Modifier.height(16.dp))
            AiButton(
                text = "Lire ma tendance",
                busy = aiBusy,
                onClick = { vm.readWeightWithAi() }
            )
            if (aiRead.isNotBlank()) {
                AiNote(text = aiRead, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text(
                    text = "N'envoie que des kilos : ni date, ni prénom, ni rien de l'autre.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ----- Historique -----
        if (mine.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("HISTORIQUE")
            mine.reversed().take(10).forEach { w ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Text(
                        text = Dates.shortLabel(w.date),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(kg(w.kilos), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { vm.deleteWeight(w.id) }) { Text("Retirer") }
                }
            }
        } else {
            Spacer(Modifier.height(20.dp))
            EmptyState(
                emoji = "⚖️",
                text = "Toujours au même moment — au lever, à jeun. Sinon la courbe " +
                    "mesure surtout les repas."
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
