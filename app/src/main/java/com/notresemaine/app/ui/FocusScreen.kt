package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.pacte.Usage
import com.notresemaine.app.ui.theme.accentFor
import kotlinx.coroutines.delay

/**
 * « Concentration » — le travail profond de Cal Newport, en un bouton.
 *
 * Le planning dit *quoi* faire ; il restait à protéger le moment où on le
 * fait. Une séance, c'est trois choses : une seule tâche, une durée décidée à
 * l'avance, et les réseaux tenus à distance. L'écran reste allumé, le minuteur
 * tient même si l'on quitte l'application (l'heure de fin est enregistrée, pas
 * décomptée), et une sonnerie marque la fin.
 *
 * Rien ne se gagne ici : le carnet note les minutes passées, comme un journal,
 * et une séance arrêtée tôt compte pour ce qu'elle a duré — sans commentaire.
 */
@Composable
fun FocusScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onScreenTime: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = accentFor(settings.myColor)
    val myId = settings.myUserId
    val today = Dates.todayIso()

    val tasks by remember(myId, today) { vm.repo.db.tasks().byDate(myId, today) }
        .collectAsState(initial = emptyList())
    val open = tasks.filter { !it.done }

    // L'horloge de l'écran : une seconde suffit, rien n'est recalculé ailleurs.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settings.focusUntil) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val active = settings.focusUntil > now
    val finished = !active && settings.focusStartedAt > 0L

    // Pendant la séance, l'écran ne s'éteint pas : on doit pouvoir y jeter un œil
    // sans déverrouiller — c'est ce qui évite de reprendre le téléphone en main.
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
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
                title = "🎧 Concentration",
                subtitle = when {
                    active -> "Une seule chose, jusqu'au bout"
                    finished -> "Séance terminée"
                    else -> "Une tâche, une durée, rien d'autre"
                },
                onBack = onBack
            )

            when {
                active -> ActiveFocus(settings, now, accent)
                finished -> FinishedFocus(settings, now)
                else -> FocusSetup(vm, settings, open, accent)
            }

            // Ce qui protège vraiment la séance : sans réseaux choisis ni accès à
            // l'usage, rien ne peut être écarté. On le dit, et on dit où le régler.
            val social = settings.socialApps.split(",").filter { it.isNotBlank() }
            val canWatch = remember(context) { runCatching { Usage.hasPermission(context) }.getOrDefault(false) }
            Spacer(Modifier.height(20.dp))
            if (social.isEmpty() || !canWatch) {
                EmptyState(
                    emoji = "📵",
                    text = "Pendant une séance, l'application peut écarter les réseaux : " +
                        "il suffit de les choisir dans le Pacte d'écran" +
                        (if (!canWatch) " et d'autoriser l'accès à l'utilisation." else ".") +
                        " Le Pacte lui-même peut rester éteint.",
                    actionLabel = "Choisir les réseaux",
                    onAction = onScreenTime
                )
            } else {
                Text(
                    text = "📵 Pendant la séance, ${social.size} application(s) choisie(s) dans le " +
                        "Pacte sont écartées. Téléphone, messages et réveil restent ouverts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ----- Le carnet de la semaine -----
            Spacer(Modifier.height(24.dp))
            SectionLabel("MA CONCENTRATION CETTE SEMAINE")
            val byDay = settings.focusMinutesByDay()
            val days = Dates.daysOfWeek(Dates.weekStartIso())
            val total = days.sumOf { byDay[it] ?: 0 }
            MiniBarChart(
                values = days.map { (byDay[it] ?: 0).toFloat() },
                labels = listOf("L", "M", "M", "J", "V", "S", "D"),
                accent = accent,
                valueLabel = { "${it.toInt()}" },
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = if (total == 0) "Aucune séance cette semaine pour l'instant."
                else "${formatMinutes(total)} au total cette semaine.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
        }

        // ----- Le geste principal, en bas, sous le pouce -----
        when {
            active -> {
                val match = open.firstOrNull { it.title == settings.focusTitle }
                if (match != null) {
                    BigButton(
                        text = "✅ C'est fait — cocher et clore",
                        onClick = { vm.endFocus(match.id) },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                TextButton(
                    onClick = { vm.endFocus() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(bottom = 8.dp)
                ) { Text("Arrêter la séance") }
            }
            finished -> {
                val match = open.firstOrNull { it.title == settings.focusTitle }
                if (match != null) {
                    BigButton(
                        text = "✅ Cocher « ${match.title.take(24)} »",
                        onClick = { vm.endFocus(match.id) },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                TextButton(
                    onClick = { vm.endFocus() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(bottom = 8.dp)
                ) { Text(if (match != null) "Clore sans cocher" else "Clore la séance") }
            }
            else -> Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActiveFocus(settings: AppSettings, now: Long, accent: androidx.compose.ui.graphics.Color) {
    val total = (settings.focusUntil - settings.focusStartedAt).coerceAtLeast(1L)
    val left = (settings.focusUntil - now).coerceAtLeast(0L)
    val seconds = left / 1000
    Spacer(Modifier.height(24.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProgressRing(
            progress = 1f - left.toFloat() / total.toFloat(),
            center = "%d:%02d".format(seconds / 60, seconds % 60),
            label = "restantes",
            accent = accent,
            size = 220.dp
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = settings.focusTitle.ifBlank { "Travail profond" },
            style = MaterialTheme.typography.titleLarge
        )
        val endLabel = java.time.Instant.ofEpochMilli(settings.focusUntil)
            .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        Text(
            text = "Fin à %02d:%02d · une sonnerie le dira".format(endLabel.hour, endLabel.minute),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(
        text = "Une idée qui surgit ? Le bouton ＋ la note sans quitter la séance — " +
            "vous la traiterez après.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FinishedFocus(settings: AppSettings, now: Long) {
    val end = minOf(now, settings.focusUntil)
    val minutes = ((end - settings.focusStartedAt) / 60_000L).coerceAtLeast(0L)
    Spacer(Modifier.height(24.dp))
    EmptyState(
        emoji = "🌿",
        text = "$minutes min sur « ${settings.focusTitle.ifBlank { "travail profond" }} ».\n" +
            "Levez-vous, buvez un verre d'eau, regardez au loin : la pause fait partie de la séance."
    )
}

@Composable
private fun FocusSetup(
    vm: AppViewModel,
    settings: AppSettings,
    open: List<com.notresemaine.app.data.TaskEntity>,
    accent: androidx.compose.ui.graphics.Color
) {
    val priority = open.firstOrNull { it.isPriority }
    var selectedId by remember { mutableStateOf(priority?.id ?: open.firstOrNull()?.id) }
    var free by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(50) }

    Spacer(Modifier.height(12.dp))
    SectionLabel("SUR QUOI ?")
    if (open.isEmpty()) {
        Text(
            text = "Aucune tâche ouverte aujourd'hui : écrivez ce que vous allez faire.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }
    open.take(6).forEach { task ->
        val selected = selectedId == task.id && free.isBlank()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    selectedId = task.id
                    free = ""
                }
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (task.isPriority) "⭐" else "•",
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
    }
    VoiceField(
        value = free,
        onValueChange = { free = it },
        placeholder = if (open.isEmpty()) "Ce que je vais faire" else "…ou autre chose",
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )

    Spacer(Modifier.height(20.dp))
    SectionLabel("COMBIEN DE TEMPS ?")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        listOf(25 to "25 min", 50 to "50 min", 90 to "1 h 30").forEach { (value, label) ->
            ChoiceChip(
                label = label,
                selected = minutes == value,
                onClick = { minutes = value },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text(
        text = when (minutes) {
            25 -> "Pour se lancer quand on n'en a pas envie : 25 minutes, ça se tient toujours."
            50 -> "Le bon format pour avancer vraiment, suivi de 10 minutes de pause."
            else -> "Une vraie plongée, pour ce qui demande de penser en profondeur."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )

    val title = free.trim().ifBlank { open.firstOrNull { it.id == selectedId }?.title.orEmpty() }
    BigButton(
        text = "🎧 Commencer",
        enabled = title.isNotBlank(),
        onClick = { vm.startFocus(minutes, title) },
        modifier = Modifier.padding(top = 20.dp)
    )
    Text(
        text = "Posez le téléphone face contre la table. L'écran restera allumé.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** « 1 h 35 », « 50 min » — jamais « 95 min ». */
internal fun formatMinutes(total: Int): String = when {
    total < 60 -> "$total min"
    total % 60 == 0 -> "${total / 60} h"
    else -> "${total / 60} h ${"%02d".format(total % 60)}"
}
