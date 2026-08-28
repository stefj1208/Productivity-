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
import com.notresemaine.app.data.ProfileEntity
import com.notresemaine.app.data.TaskEntity
import com.notresemaine.app.data.WeekPlanEntity
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

/**
 * « Nous deux » : ce qui n'a de sens qu'à deux.
 *
 * L'écran répond à quatre questions, dans cet ordre — de la plus urgente à la
 * plus contemplative :
 *
 *  1. **Est-ce qu'on attend quelque chose de moi ?** Une demande de pause, une
 *     tâche qu'on m'a confiée. Ce qui bloque l'autre passe avant tout le reste.
 *  2. **Où en est chacun ?** La priorité de la semaine, celle du jour, et
 *     l'avancement — côte à côte, jamais l'un au-dessus de l'autre : deux
 *     colonnes disent « à égalité », une liste dirait « d'abord toi ».
 *  3. **Qu'est-ce qu'on s'est confié ?** Les tâches passées d'une main à l'autre,
 *     dans les deux sens.
 *  4. **Qu'est-ce qu'on se dit ?** Les mots envoyés dans la journée.
 *
 * Aucun classement, aucun score comparé : voir où en est l'autre sert à s'aider,
 * pas à savoir qui gagne.
 */
@Composable
fun UsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onGoToSettings: () -> Unit,
    onScreenTime: () -> Unit,
    onHealth: () -> Unit
) {
    val myId = settings.myUserId
    val weekStart = Dates.weekStartIso()
    val today = Dates.todayIso()
    val days = Dates.daysOfWeek(weekStart)

    val profiles by remember { vm.repo.db.profiles().all() }
        .collectAsState(initial = emptyList())
    val weekPlans by remember(weekStart) { vm.repo.db.weekPlans().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val weekTasks by remember(weekStart) { vm.repo.db.tasks().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val usageDays by remember(today) { vm.repo.db.usage().since(today) }
        .collectAsState(initial = emptyList())
    val graces by remember(today) { vm.repo.db.grace().forDate(today) }
        .collectAsState(initial = emptyList())
    val ritualLogs by remember { vm.repo.db.ritual().logs() }
        .collectAsState(initial = emptyList())
    val notes by remember(weekStart) { vm.repo.db.encouragements().between(days.first(), days.last()) }
        .collectAsState(initial = emptyList())
    val meals by remember(today) { vm.repo.db.meals().between(today, today) }
        .collectAsState(initial = emptyList())

    val me = profiles.firstOrNull { it.id == myId }
        ?: ProfileEntity(id = myId, name = settings.myName, color = settings.myColor, updatedAt = 0)
    val partner = profiles.firstOrNull { it.id != myId }

    var writing by remember { mutableStateOf(false) }

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
                title = "Nous deux",
                subtitle = Dates.weekRangeLabel(weekStart)
            )

            if (partner == null) {
                Spacer(Modifier.height(16.dp))
                EmptyState(
                    emoji = "💞",
                    text = "L'autre moitié n'est pas encore connectée. Reliez vos deux " +
                        "téléphones : sa semaine apparaîtra ici, à côté de la vôtre, et vous " +
                        "verrez chacun le pacte d'écran de l'autre.",
                    actionLabel = "Relier nos téléphones",
                    onAction = onGoToSettings
                )
                Spacer(Modifier.height(24.dp))
            } else {
                // ----- 1. Ce qui attend une réponse de ma part -----
                //
                // Une demande de pause laisse quelqu'un devant un écran de blocage.
                // Elle passe donc avant tout, y compris avant le récapitulatif.
                val pendingForMe = graces.filter { it.toUser == myId && it.status == "pending" }
                pendingForMe.forEach { request ->
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "⏸ ${partner.name} demande ${request.minutes} min",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Sa limite d'écran est atteinte. Vous êtes le seul à pouvoir " +
                                "ouvrir une pause — ou à dire non, c'était le pacte.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { vm.answerGrace(request.id, true) },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("Accorder ${request.minutes} min") }
                            TextButton(
                                onClick = { vm.answerGrace(request.id, false) },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("Pas ce soir") }
                        }
                    }
                }

                // ----- 2. Où en est chacun -----
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    listOf(me, partner).forEach { person ->
                        val theirTasks = weekTasks.filter { it.userId == person.id && !it.deleted }
                        PersonColumn(
                            profile = person,
                            weekPlan = weekPlans.firstOrNull { it.userId == person.id },
                            todayTasks = theirTasks.filter { it.date == today },
                            weekDone = theirTasks.count { it.done },
                            weekTotal = theirTasks.size,
                            ritualStreak = vm.repo.ritualStreak(ritualLogs, person.id),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ----- 3. Ce qu'on s'est confié -----
                //
                // Confier une tâche est un acte à deux : il mérite d'être visible
                // des deux côtés, avec le sens du transfert et son état. Sinon on
                // ne sait jamais si l'autre s'en est occupé.
                val shared = weekTasks.filter { it.assignedBy.isNotBlank() && !it.deleted }
                Spacer(Modifier.height(28.dp))
                SectionLabel("CE QU'ON S'EST CONFIÉ")
                if (shared.isEmpty()) {
                    Text(
                        text = "Rien pour l'instant. Dans le planning, touchez le crayon d'une " +
                            "tâche puis « Confier à ${partner.name} ».",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                shared.sortedBy { it.done }.forEach { task ->
                    val fromMe = task.assignedBy == myId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (task.done) "✓" else if (fromMe) "↗" else "↘",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (task.done) NeutralGray else accentFor(
                                if (fromMe) partner.color else me.color
                            )
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (task.done) NeutralGray
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append(if (fromMe) "Confiée à ${partner.name}" else "Confiée par ${partner.name}")
                                    task.date?.let { append(" · ${Dates.shortLabel(it)}") }
                                    if (task.startTime.isNotBlank()) append(" · ${task.startTime}")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // On ne coche que ce qui nous revient : cocher à la place
                        // de l'autre ne dit rien de vrai.
                        if (!fromMe && !task.done) {
                            TextButton(onClick = { vm.toggleDone(task.id) }) { Text("Fait") }
                        }
                    }
                }

                // ----- 4. Ce qu'on se dit -----
                Spacer(Modifier.height(28.dp))
                SectionLabel("NOS MOTS DE LA SEMAINE")
                if (notes.isEmpty()) {
                    Text(
                        text = "Aucun mot échangé. Le bouton du bas en envoie un en deux tapes.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                notes.take(6).forEach { note ->
                    val fromMe = note.fromUser == myId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = (if (fromMe) me.name else partner.name).take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = accentFor(if (fromMe) me.color else partner.color)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(note.message, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = (if (fromMe) "à ${partner.name}" else "de ${partner.name}") +
                                    " · ${Dates.shortLabel(note.date)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ----- Ce soir, on mange quoi -----
                //
                // Le dîner est la seule chose de la journée que le couple fait
                // vraiment ensemble : sa place est ici, pas seulement dans Maison.
                val dinner = meals.firstOrNull { it.slot == "soir" && it.title.isNotBlank() }
                if (dinner != null) {
                    Spacer(Modifier.height(28.dp))
                    SectionLabel("CE SOIR")
                    Text(
                        text = "🌙 ${dinner.title}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (dinner.quantities.isNotBlank()) {
                        Text(
                            text = "Par personne : ${dinner.quantities}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ----- Le Pacte d'écran, visible en permanence par les deux -----
                Spacer(Modifier.height(28.dp))
                SectionLabel("NOTRE PACTE D'ÉCRAN")
                listOf(me, partner).forEach { person ->
                    val usage = usageDays.firstOrNull { it.userId == person.id }
                    PacteRow(profile = person, socialMinutes = usage?.socialMinutes)
                }
                Text(
                    text = "Le durcir prend effet tout de suite, l'assouplir attend le lendemain. " +
                        "Vous voyez tous les deux les réglages de l'autre — c'est là qu'est le " +
                        "contrôle mutuel.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            ShortcutTile(
                emoji = "📵",
                title = "Mon pacte d'écran",
                subtitle = if (settings.pacteEnabled) "Limite ${settings.dailyLimitMinutes} min/jour" +
                    (if (settings.curfewEnabled) " · couvre-feu ${settings.curfewStart}" else "")
                else "Pas encore d'engagement",
                onClick = onScreenTime
            )
            ShortcutTile(
                emoji = "😴",
                title = "Sommeil & sport",
                subtitle = "Mesures automatiques ou saisie en 10 secondes",
                onClick = onHealth
            )
            Spacer(Modifier.height(20.dp))
        }

        if (partner == null) {
            BigButton(
                text = "Configurer la synchronisation",
                onClick = onGoToSettings,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            BigButton(
                text = "💬 Écrire un mot à ${partner.name}",
                onClick = { writing = true },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    if (writing && partner != null) {
        NoteDialog(
            partnerName = partner.name,
            onSend = { message ->
                vm.sendBravo(partner.id, message)
                writing = false
            },
            onDismiss = { writing = false }
        )
    }
}

/**
 * Écrire un mot, en deux tapes si l'on veut.
 *
 * Le bouton envoyait auparavant « Bravo ! » et rien d'autre. Un message toujours
 * identique cesse très vite de vouloir dire quelque chose ; une phrase précise,
 * elle, se relit. D'où les formules toutes faites — pour ceux qui n'ont pas le
 * temps — et le champ libre juste en dessous, dictable.
 */
@Composable
private fun NoteDialog(
    partnerName: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    val ready = listOf(
        "👏 Bravo pour aujourd'hui",
        "🙏 Merci d'avoir pris le relais",
        "💪 Tu tiens bon, ça se voit",
        "❤️ Je pense à toi",
        "🌙 Repose-toi ce soir"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Un mot à $partnerName") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ready.forEach { phrase ->
                    TextButton(
                        onClick = { message = phrase },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = phrase,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                VoiceField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = "Ou écrivez le vôtre",
                    prompt = "Dictez votre message",
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(message) }, enabled = message.isNotBlank()) {
                Text("Envoyer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** L'engagement d'une personne et où elle en est aujourd'hui, en une ligne lisible. */
@Composable
private fun PacteRow(profile: ProfileEntity, socialMinutes: Int?) {
    val accent = accentFor(profile.color)
    val overLimit = socialMinutes != null && profile.dailyLimitMinutes > 0 &&
        socialMinutes >= profile.dailyLimitMinutes
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            Text(
                text = " ${profile.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (profile.pacteEnabled) "Pacte actif" else "Pacte inactif",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (!profile.pacteEnabled) "Aucun engagement pour l'instant."
            else buildString {
                append("Limite ${profile.dailyLimitMinutes} min/jour")
                if (profile.curfewEnabled) {
                    append(" · couvre-feu ${profile.curfewStart}–${profile.curfewEnd}")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = when {
                socialMinutes == null -> "Aujourd'hui : pas encore de relevé."
                overLimit -> "Aujourd'hui : $socialMinutes min — limite atteinte."
                else -> "Aujourd'hui : $socialMinutes min."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (overLimit) NeutralGray else accent,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun PersonColumn(
    profile: ProfileEntity,
    weekPlan: WeekPlanEntity?,
    todayTasks: List<TaskEntity>,
    weekDone: Int,
    weekTotal: Int,
    ritualStreak: Int,
    modifier: Modifier = Modifier
) {
    val accent = accentFor(profile.color)
    Column(modifier = modifier) {
        PersonBadge(name = profile.name, colorRole = profile.color)
        Spacer(Modifier.height(16.dp))

        SectionLabel("PRIORITÉ DE LA SEMAINE")
        Text(
            text = weekPlan?.priority?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = if (!weekPlan?.priority.isNullOrBlank()) accent else NeutralGray
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel("AUJOURD'HUI")
        val priority = todayTasks.firstOrNull { it.isPriority }
        Text(
            text = priority?.let { (if (it.done) "✓ " else "· ") + it.title } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                priority == null -> NeutralGray
                priority.done -> NeutralGray
                else -> MaterialTheme.colorScheme.onBackground
            }
        )
        if (todayTasks.isNotEmpty()) {
            Text(
                text = "${todayTasks.count { it.done }}/${todayTasks.size} fait" +
                    if (todayTasks.count { it.done } > 1) "s" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // L'avancement de la semaine entière, et la série du rituel : deux
        // chiffres qui manquaient, et qui disent bien plus que la seule journée.
        Spacer(Modifier.height(16.dp))
        SectionLabel("SA SEMAINE")
        Text(
            text = if (weekTotal == 0) "Rien de planifié" else "$weekDone/$weekTotal tâches",
            style = MaterialTheme.typography.bodyLarge,
            color = if (weekTotal == 0) NeutralGray else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (ritualStreak == 0) "Rituel : pas de série en cours"
            else "🌅 Rituel : $ritualStreak jour" + if (ritualStreak > 1) "s" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
