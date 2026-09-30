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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.accentFor

/**
 * « Notre semaine en une page » — la lettre du dimanche.
 *
 * Les chiffres de la semaine étaient tous là, mais éparpillés entre cinq
 * écrans ; personne ne les rassemble le dimanche soir. Cette page le fait, pour
 * chacun des deux, côte à côte, en phrases plutôt qu'en tableaux — et se copie
 * ou s'envoie d'un tap, pour la relire ensemble ou la garder.
 *
 * Ce qu'elle ne fait pas, volontairement : comparer, noter, classer. Une ligne
 * absente veut dire « pas mesuré », jamais « raté ». Le poids et les repas
 * n'y figurent pas : ils restent dans la sphère de chacun.
 */
@Composable
fun WeekLetterScreen(
    vm: AppViewModel,
    settings: AppSettings,
    initialWeekStart: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = accentFor(settings.myColor)
    val myId = settings.myUserId

    var offset by remember(initialWeekStart) { mutableIntStateOf(Dates.weekOffsetOf(initialWeekStart)) }
    val weekStart = Dates.weekStartIsoOffset(offset)
    val nextWeek = Dates.weekStartIsoOffset(offset + 1)
    val days = Dates.daysOfWeek(weekStart)

    val profiles by remember { vm.repo.db.profiles().all() }.collectAsState(initial = emptyList())
    val tasks by remember(weekStart) { vm.repo.db.tasks().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val plans by remember(weekStart) { vm.repo.db.weekPlans().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val nextPlans by remember(nextWeek) { vm.repo.db.weekPlans().byWeekAllUsers(nextWeek) }
        .collectAsState(initial = emptyList())
    val ritualLogs by remember { vm.repo.db.ritual().logs() }.collectAsState(initial = emptyList())
    val health by remember(weekStart) { vm.repo.db.health().since(days.first()) }
        .collectAsState(initial = emptyList())
    val usage by remember(weekStart) { vm.repo.db.usage().since(days.first()) }
        .collectAsState(initial = emptyList())
    val notes by remember(weekStart) { vm.repo.db.encouragements().between(days.first(), days.last()) }
        .collectAsState(initial = emptyList())
    val meals by remember(weekStart) { vm.repo.db.meals().between(days.first(), days.last()) }
        .collectAsState(initial = emptyList())

    val me = profiles.firstOrNull { it.id == myId }
    val partner = profiles.firstOrNull { it.id != myId }
    val people = listOfNotNull(
        myId to (me?.name ?: settings.myName.ifBlank { "Moi" }),
        partner?.let { it.id to it.name }
    )

    /** Ce qui s'est passé pour une personne, en phrases. Rien n'est inventé : pas de mesure, pas de ligne. */
    fun linesFor(userId: String): List<String> = buildList {
        val theirTasks = tasks.filter { it.userId == userId && !it.deleted }
        plans.firstOrNull { it.userId == userId }?.priority?.takeIf { it.isNotBlank() }?.let {
            add("⭐ Priorité : $it")
        }
        if (theirTasks.isNotEmpty()) {
            add("✅ ${theirTasks.count { it.done }} tâche(s) menée(s) à bout sur ${theirTasks.size}")
        }
        val sessions = theirTasks.filter { it.goalId != null }
        if (sessions.isNotEmpty()) {
            add("🎯 ${sessions.count { it.done }} séance(s) d'objectif sur ${sessions.size}")
        }
        val mornings = ritualLogs.count { it.userId == userId && !it.deleted && it.date in days }
        if (mornings > 0) add("🌅 Rituel du matin : $mornings matin(s) sur 7")

        val theirHealth = health.filter { it.userId == userId && it.date in days && !it.deleted }
        val nights = theirHealth.filter { it.sleepMinutes > 0 }
        if (nights.isNotEmpty()) {
            val avg = nights.sumOf { it.sleepMinutes } / nights.size
            add("😴 ${avg / 60} h ${"%02d".format(avg % 60)} de sommeil par nuit en moyenne")
        }
        val stepDays = theirHealth.filter { it.steps > 0 }
        if (stepDays.isNotEmpty()) {
            add("👟 ${"%,d".format(stepDays.sumOf { it.steps } / stepDays.size).replace(',', ' ')} pas par jour")
        }
        val sport = theirHealth.sumOf { it.exerciseMinutes }
        if (sport > 0) add("🏃 ${formatMinutes(sport)} de sport")

        val theirUsage = usage.filter { it.userId == userId && it.date in days && it.socialMinutes > 0 }
        if (theirUsage.isNotEmpty()) {
            add("📵 ${theirUsage.sumOf { it.socialMinutes } / theirUsage.size} min de réseaux par jour")
        }
        // La concentration n'existe que sur ce téléphone : c'est un moment à soi.
        if (userId == myId) {
            val focus = days.sumOf { settings.focusMinutesByDay()[it] ?: 0 }
            if (focus > 0) add("🎧 ${formatMinutes(focus)} de concentration")
        }
        if (isEmpty()) add("Rien de mesuré cette semaine — ce n'est pas un reproche, juste un blanc.")
    }

    val togetherLines = buildList {
        val given = tasks.count { it.assignedBy.isNotBlank() && it.assignedBy != it.userId && !it.deleted }
        if (given > 0) add("🤝 $given tâche(s) confiée(s) l'un à l'autre")
        val words = notes.filter { !it.deleted }
        if (words.isNotEmpty()) add("💬 ${words.size} mot(s) échangé(s)")
        val planned = meals.count { !it.deleted && it.title.isNotBlank() }
        if (planned > 0) add("🍽️ $planned repas prévus ensemble")
        words.maxByOrNull { it.updatedAt }?.let { add("Le dernier : « ${it.message} »") }
    }

    val nextLines = people.mapNotNull { (id, name) ->
        nextPlans.firstOrNull { it.userId == id }?.priority?.takeIf { it.isNotBlank() }?.let { "$name : $it" }
    }

    val letter = buildString {
        appendLine("📜 Notre semaine · ${Dates.weekRangeLabel(weekStart)}")
        people.forEach { (id, name) ->
            appendLine()
            appendLine(name.uppercase())
            linesFor(id).forEach { appendLine(it) }
        }
        if (togetherLines.isNotEmpty()) {
            appendLine()
            appendLine("À DEUX")
            togetherLines.forEach { appendLine(it) }
        }
        if (nextLines.isNotEmpty()) {
            appendLine()
            appendLine("LA SEMAINE PROCHAINE")
            nextLines.forEach { appendLine(it) }
        }
    }.trimEnd()

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
                title = "📜 Notre semaine",
                subtitle = "En une page, à relire ensemble",
                onBack = onBack
            )
            WeekNavigator(
                weekStartIso = weekStart,
                onOffsetChange = { delta -> offset = (offset + delta).coerceAtMost(0) }
            )

            people.forEach { (id, name) ->
                Spacer(Modifier.height(16.dp))
                LetterCard(title = name, lines = linesFor(id), accent = accent)
            }
            if (partner == null) {
                Text(
                    text = "Une fois vos deux téléphones reliés, la semaine de l'autre apparaîtra ici, à côté de la vôtre.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (togetherLines.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LetterCard(title = "À deux", lines = togetherLines, accent = accent)
            }
            Spacer(Modifier.height(16.dp))
            LetterCard(
                title = "La semaine prochaine",
                lines = nextLines.ifEmpty {
                    listOf("Aucune priorité choisie encore. C'est le bon moment : le bilan du dimanche est fait pour ça.")
                },
                accent = accent
            )
            Text(
                text = "Ni score ni classement : une ligne absente veut dire « pas mesuré ». " +
                    "Poids et repas n'apparaissent pas ici.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            Spacer(Modifier.height(24.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            .setPrimaryClip(android.content.ClipData.newPlainText("Notre semaine", letter))
                    }
                    vm.messages.tryEmit("Page copiée ✓")
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) { Text("📋 Copier") }
            OutlinedButton(
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, letter)
                    runCatching {
                        context.startActivity(android.content.Intent.createChooser(send, "Envoyer notre semaine"))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) { Text("📤 Envoyer") }
        }
    }
}

@Composable
private fun LetterCard(title: String, lines: List<String>, accent: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = accent)
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
