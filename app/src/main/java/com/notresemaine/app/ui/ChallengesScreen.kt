package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.ChallengeEntity
import com.notresemaine.app.data.Challenges
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

/**
 * « Défis » : un engagement borné, un geste par jour.
 *
 * L'écran se lit de haut en bas comme une journée : ce qu'il y a à faire
 * aujourd'hui (la carte, un gros bouton), puis la grille des jours — coché,
 * passé sans rien, à venir — puis le catalogue pour en lancer un autre.
 *
 * Ce qu'on ne verra pas ici : de flamme, de série, de « tu as raté ». Un jour
 * manqué reste gris. Le défi ne juge pas, il compte.
 */
@Composable
fun ChallengesScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onReminders: () -> Unit,
    onBack: (() -> Unit)?
) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val today = Dates.today()

    val all by remember { vm.repo.db.challenges().all() }.collectAsState(initial = emptyList())
    val profiles by remember { vm.repo.db.profiles().all() }.collectAsState(initial = emptyList())
    val partner = profiles.firstOrNull { it.id != myId }

    val mine = all.filter { it.userId == myId }
    val active = mine.filter { it.status == "actif" }
    val past = mine.filter { it.status != "actif" }
    val theirs = if (settings.solo) emptyList() else all.filter { it.userId != myId && it.shared }

    var creating by remember { mutableStateOf(false) }
    var confirmTemplate by remember { mutableStateOf<Challenges.Template?>(null) }
    var editing by remember { mutableStateOf<ChallengeEntity?>(null) }

    val aiIdea by vm.aiChallenge.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "🏁 Défis",
            subtitle = if (active.isEmpty()) "Un engagement, un geste par jour"
            else "${active.size} en cours",
            onBack = onBack
        )

        // ----- Aujourd'hui -----
        if (active.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            EmptyState(
                emoji = "🏁",
                text = "Aucun défi en cours. Un défi, c'est une seule chose, un nombre de jours " +
                    "fixé d'avance, et une case à cocher chaque soir. Choisissez-en un ci-dessous, " +
                    "ou inventez le vôtre."
            )
        }
        active.forEach { c ->
            Spacer(Modifier.height(12.dp))
            ChallengeCard(
                challenge = c,
                accent = accent,
                today = today,
                onToggle = { day -> vm.toggleChallengeDay(c.id, day) },
                onEdit = { editing = c }
            )
        }

        // ----- Le binôme -----
        if (theirs.isNotEmpty() && partner != null) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("CE QUE ${partner.name.uppercase()} S'EST LANCÉ")
            theirs.filter { it.status == "actif" }.forEach { c ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(c.emoji, style = MaterialTheme.typography.titleLarge)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(c.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = Challenges.todayLine(c) + " · ${Challenges.doneDays(c).size} jour(s) cochés",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = "On voit, on ne note pas. Un mot d'encouragement se dit dans « Nous ».",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ----- Le catalogue -----
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("EN LANCER UN", modifier = Modifier.weight(1f))
            TextButton(
                onClick = { creating = true },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) { Text("✏️ Le mien") }
        }
        val runningIds = active.map { it.templateId }.toSet()
        Challenges.CATALOG.forEach { t ->
            val running = t.id in runningIds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .clickable(enabled = !running) { confirmTemplate = t }
                    .defaultMinSize(minHeight = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(t.emoji, style = MaterialTheme.typography.titleLarge)
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        text = t.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (running) NeutralGray else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (running) "En cours" else "${t.days} jours · " + when (t.rule) {
                            "croissant" -> "de plus en plus chaque jour"
                            "constant" -> "${t.amount} ${t.unit} par jour"
                            else -> "une case par jour"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!running) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = "Un nouveau type de défi s'ajoute au catalogue en une entrée : pas d'écran " +
                "à refaire. Dites-moi lequel vous manque.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        // ----- Rappel -----
        Spacer(Modifier.height(20.dp))
        ShortcutTile(
            emoji = "🔔",
            title = "Rappel du défi",
            subtitle = if (settings.challengeRemindersEnabled) "Chaque jour à ${settings.challengeReminder}"
            else "Éteint — à vous d'y penser",
            onClick = onReminders
        )

        // ----- Les défis passés -----
        if (past.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("TERMINÉS")
            past.forEach { c ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = c }
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(vertical = 6.dp)
                ) {
                    Text(c.emoji, style = MaterialTheme.typography.titleMedium)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(c.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${Challenges.doneDays(c).size} jour(s) sur ${c.days}" +
                                (if (c.status == "arrete") " · arrêté" else "") +
                                " · depuis le ${Dates.shortLabel(c.startDate)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    // ----- Lancer un défi du catalogue -----
    confirmTemplate?.let { t ->
        var shared by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmTemplate = null },
            title = { Text("${t.emoji} ${t.title}") },
            text = {
                Column {
                    Text(t.description, style = MaterialTheme.typography.bodyLarge)
                    if (t.source.isNotBlank()) {
                        Text(
                            text = t.source,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (t.rule == "croissant") {
                        Text(
                            text = "Au total : ${t.amount * t.days * (t.days + 1) / 2} ${t.unit} en ${t.days} jours.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (!settings.solo) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                "Visible par mon binôme",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = shared, onCheckedChange = { shared = it })
                        }
                    }
                    Text(
                        text = "Jour 1, c'est aujourd'hui.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.startChallenge(t, shared)
                    confirmTemplate = null
                }) { Text("Je me lance") }
            },
            dismissButton = { TextButton(onClick = { confirmTemplate = null }) { Text("Pas maintenant") } }
        )
    }

    // ----- Inventer le sien -----
    if (creating) {
        CustomChallengeDialog(
            vm = vm,
            settings = settings,
            idea = aiIdea,
            onDismiss = {
                creating = false
                vm.aiChallenge.value = null
            }
        )
    }

    // ----- Modifier / arrêter / supprimer -----
    editing?.let { c ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${c.emoji} ${c.title}") },
            text = {
                Column {
                    Text(
                        text = "Commencé le ${Dates.shortLabel(c.startDate)} · " +
                            "${Challenges.doneDays(c).size} jour(s) sur ${c.days}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (c.description.isNotBlank()) {
                        Text(
                            text = c.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (!settings.solo) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                "Visible par mon binôme",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = c.shared, onCheckedChange = { vm.setChallengeShared(c.id, it) })
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        if (c.status == "actif") {
                            OutlinedButton(
                                onClick = { vm.setChallengeStatus(c.id, "arrete"); editing = null },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("Arrêter") }
                        } else if (!Challenges.isOver(c)) {
                            OutlinedButton(
                                onClick = { vm.setChallengeStatus(c.id, "actif"); editing = null },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("Reprendre") }
                        }
                        TextButton(
                            onClick = { vm.deleteChallenge(c.id); editing = null },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Supprimer") }
                    }
                    Text(
                        text = "Arrêter n'efface rien : le défi reste dans « Terminés », avec ce qui a été fait.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Fermer") } }
        )
    }
}

/**
 * La carte d'un défi en cours : ce qu'il y a à faire aujourd'hui, le bouton pour
 * le cocher, et la grille de tous les jours.
 */
@Composable
private fun ChallengeCard(
    challenge: ChallengeEntity,
    accent: Color,
    today: java.time.LocalDate,
    onToggle: (Int) -> Unit,
    onEdit: () -> Unit
) {
    val day = Challenges.dayNumber(challenge, today)
    val done = Challenges.doneDays(challenge)
    val over = day > challenge.days
    val todayDone = day in done

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(challenge.emoji, style = MaterialTheme.typography.displaySmall)
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(challenge.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = Challenges.todayLine(challenge, today),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (over) MaterialTheme.colorScheme.onSurfaceVariant else accent
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) { Text("✏️", style = MaterialTheme.typography.bodyLarge) }
        }

        if (!over && day >= 1) {
            if (todayDone) {
                OutlinedButton(
                    onClick = { onToggle(day) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(52.dp)
                ) { Text("✅ Fait aujourd'hui — toucher pour annuler") }
            } else {
                BigButton(
                    text = "C'est fait pour aujourd'hui",
                    onClick = { onToggle(day) },
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }

        // Le total accompli, dans l'unité du défi. « 28 objets sur 465 » dit
        // plus qu'un pourcentage — et n'a rien d'un score : c'est un inventaire.
        val total = Challenges.total(challenge)
        val achieved = Challenges.achieved(challenge)
        Text(
            text = when (challenge.rule) {
                "libre" -> "$achieved jour(s) sur ${challenge.days}"
                else -> "$achieved ${challenge.unit} sur $total"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )

        DayGrid(
            challenge = challenge,
            today = day,
            done = done,
            accent = accent,
            onTap = onToggle
        )
    }
}

/**
 * Tous les jours du défi, sept par ligne. Coché en couleur, aujourd'hui cerclé,
 * les jours passés sans case restent gris : rien de plus.
 */
@Composable
private fun DayGrid(
    challenge: ChallengeEntity,
    today: Int,
    done: Set<Int>,
    accent: Color,
    onTap: (Int) -> Unit
) {
    val rows = (challenge.days + 6) / 7
    Column(modifier = Modifier.padding(top = 8.dp)) {
        repeat(rows) { r ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                repeat(7) { col ->
                    val n = r * 7 + col + 1
                    if (n > challenge.days) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val isDone = n in done
                        val isToday = n == today
                        val future = n > today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                                .background(
                                    when {
                                        isDone -> accent
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(enabled = !future) { onTap(n) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isToday && !isDone) "•$n" else "$n",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                color = when {
                                    isDone -> MaterialTheme.colorScheme.onPrimary
                                    future -> NeutralGray
                                    isToday -> accent
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Inventer son défi — à la main, ou en demandant une idée à l'assistant. */
@Composable
private fun CustomChallengeDialog(
    vm: AppViewModel,
    settings: AppSettings,
    idea: com.notresemaine.app.ai.Assistant.ChallengeIdea?,
    onDismiss: () -> Unit
) {
    var wish by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🏁") }
    var days by remember { mutableIntStateOf(21) }
    var rule by remember { mutableStateOf("libre") }
    var unit by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("1") }
    var shared by remember { mutableStateOf(false) }
    val aiBusy by vm.aiBusy.collectAsState()
    val aiOn = settings.aiEnabled && settings.aiApiKey.isNotBlank()

    // L'idée de l'assistant remplit le formulaire : on la relit, on la corrige, on valide.
    androidx.compose.runtime.LaunchedEffect(idea) {
        if (idea != null) {
            title = idea.title
            emoji = idea.emoji
            days = idea.days
            rule = idea.rule
            unit = idea.unit
            amount = idea.amount.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mon défi") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (aiOn) {
                    VoiceField(
                        value = wish,
                        onValueChange = { wish = it },
                        placeholder = "Ce que je veux changer (ex. : moins de désordre)",
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AiButton(
                        text = "Me proposer un défi",
                        busy = aiBusy,
                        enabled = wish.isNotBlank(),
                        onClick = { vm.suggestChallenge(wish) },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (idea != null && idea.why.isNotBlank()) {
                        AiNote(text = idea.why, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceField(
                        value = emoji,
                        onValueChange = { emoji = it.take(4) },
                        label = "Emoji",
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                    VoiceField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Titre",
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                SectionLabel("COMBIEN DE JOURS")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf(7, 14, 21, 30).forEach { d ->
                        ChoiceChip(label = "$d", selected = days == d, onClick = { days = d }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                SectionLabel("CHAQUE JOUR")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    ChoiceChip(label = "Une case", selected = rule == "libre", onClick = { rule = "libre" }, modifier = Modifier.weight(1f))
                    ChoiceChip(label = "Une quantité", selected = rule == "constant", onClick = { rule = "constant" }, modifier = Modifier.weight(1f))
                    ChoiceChip(label = "De plus en plus", selected = rule == "croissant", onClick = { rule = "croissant" }, modifier = Modifier.weight(1f))
                }
                if (rule != "libre") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        VoiceField(
                            value = amount,
                            onValueChange = { amount = it.filter { c -> c.isDigit() }.take(5) },
                            label = if (rule == "croissant") "Le jour 1" else "Par jour",
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        VoiceField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = "Unité (objet(s), page(s)…)",
                            singleLine = true,
                            modifier = Modifier.weight(1.6f)
                        )
                    }
                    if (rule == "croissant") {
                        Text(
                            text = "Jour 2 : deux fois, jour 3 : trois fois… jusqu'au jour $days.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (!settings.solo) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Text("Visible par mon binôme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = shared, onCheckedChange = { shared = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    vm.startCustomChallenge(
                        title = title, emoji = emoji, days = days, rule = rule, unit = unit,
                        amount = amount.toIntOrNull() ?: 1, shared = shared
                    )
                    onDismiss()
                }
            ) { Text("Je me lance") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
