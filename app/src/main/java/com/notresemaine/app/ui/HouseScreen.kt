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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.HouseItemEntity
import com.notresemaine.app.data.MenuIdeas
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

/**
 * « Maison » : tout ce qui se gère à deux — les repas, l'argent, les enfants,
 * et les tâches qu'on s'est confiées.
 *
 * Trois onglets plutôt que trois écrans : ce sont les mêmes gestes sur des sujets
 * différents, et on passe de l'un à l'autre sans perdre le fil.
 */
@Composable
fun HouseScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onMenus: (String) -> Unit,
    onShopping: (String) -> Unit
) {
    val today = Dates.todayIso()
    val weekStart = Dates.weekStartIso()
    val days = Dates.daysOfWeek(weekStart)
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)

    var tab by remember { mutableStateOf("menus") }
    var editing by remember { mutableStateOf<HouseItemEntity?>(null) }
    var creating by remember { mutableStateOf<String?>(null) }
    var reworking by remember { mutableStateOf<String?>(null) }

    val meals by remember(weekStart) { vm.repo.db.meals().between(days.first(), days.last()) }
        .collectAsState(initial = emptyList())
    val shopping by remember(weekStart) { vm.repo.db.shopping().forWeek(weekStart) }
        .collectAsState(initial = emptyList())
    val weekTasks by remember(weekStart) { vm.repo.db.tasks().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val profiles by remember { vm.repo.db.profiles().all() }
        .collectAsState(initial = emptyList())
    val finance by remember { vm.repo.db.houseItems().bySection("finance") }
        .collectAsState(initial = emptyList())
    val kids by remember { vm.repo.db.houseItems().bySection("enfants") }
        .collectAsState(initial = emptyList())

    val partner = profiles.firstOrNull { it.id != myId }
    val shared = weekTasks.filter { it.assignedBy.isNotBlank() }

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
            ScreenHeader(title = "Maison", subtitle = Dates.longLabel(today))

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip("🍽️ Repas", tab == "menus", { tab = "menus" }, Modifier.weight(1f))
                TabChip("💶 Finance", tab == "finance", { tab = "finance" }, Modifier.weight(1f))
                TabChip("🧒 Enfants", tab == "enfants", { tab = "enfants" }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            when (tab) {
                "menus" -> {
                    SectionLabel("AU MENU AUJOURD'HUI")
                    val todayMeals = meals.filter { it.date == today }
                    val eaten = todayMeals.sumOf { it.calories }
                    MenuIdeas.slots.forEach { (slot, label) ->
                        val meal = meals.firstOrNull { it.date == today && it.slot == slot }
                        MealCard(
                            slot = slot,
                            label = label,
                            title = meal?.title.orEmpty(),
                            quantities = meal?.quantities.orEmpty(),
                            calories = meal?.calories ?: 0,
                            accent = accent,
                            aiReady = settings.aiEnabled && settings.aiApiKey.isNotBlank(),
                            onAi = { reworking = slot }
                        )
                    }
                    if (eaten > 0 && settings.dailyCalories > 0) {
                        Spacer(Modifier.height(10.dp))
                        Gauge(
                            value = eaten.toFloat(),
                            max = settings.dailyCalories.toFloat(),
                            accent = accent,
                            caption = "$eaten kcal prévues par personne sur " +
                                "${settings.dailyCalories} — pour ${settings.householdSize} couvert" +
                                (if (settings.householdSize > 1) "s" else "")
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    ShortcutTile(
                        emoji = "🍽️",
                        title = "Menus de la semaine",
                        subtitle = meals.count { it.title.isNotBlank() }
                            .let { if (it == 0) "Rien de décidé" else "$it repas prévus sur 21" },
                        onClick = { onMenus(weekStart) }
                    )
                    ShortcutTile(
                        emoji = "🛒",
                        title = "Liste de courses",
                        subtitle = if (shopping.isEmpty()) "À générer depuis les menus"
                        else "${shopping.count { !it.checked }} article(s) à prendre",
                        onClick = { onShopping(weekStart) }
                    )
                }

                "finance" -> {
                    val objectives = finance.filter { !it.done }
                    val total = objectives.sumOf { it.amount }
                    if (total != 0.0) {
                        KpiTile(
                            value = "%,.0f €".format(total),
                            label = "total des postes en cours",
                            accent = accent,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    SectionLabel("OBJECTIFS ET POSTES")
                    if (finance.isEmpty()) {
                        EmptyState(
                            emoji = "💶",
                            text = "Rien pour l'instant. Notez un objectif d'épargne, une " +
                                "échéance, un budget à surveiller.",
                            actionLabel = "Ajouter",
                            onAction = { creating = "finance" }
                        )
                    } else {
                        finance.forEach { item ->
                            HouseItemRow(item, accent, { vm.toggleHouseItem(item.id) }, { editing = item })
                        }
                    }
                }

                else -> {
                    SectionLabel("CE QU'IL Y A À GÉRER")
                    if (kids.isEmpty()) {
                        EmptyState(
                            emoji = "🧒",
                            text = "Rien pour l'instant. Rendez-vous, affaires à préparer, " +
                                "inscriptions, anniversaires.",
                            actionLabel = "Ajouter",
                            onAction = { creating = "enfants" }
                        )
                    } else {
                        kids.forEach { item ->
                            HouseItemRow(item, accent, { vm.toggleHouseItem(item.id) }, { editing = item })
                        }
                    }
                }
            }

            // ----- Ce qu'on s'est confié, visible quel que soit l'onglet -----
            if (shared.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionLabel("TÂCHES CONFIÉES")
                shared.forEach { task ->
                    val forMe = task.userId == myId
                    val otherName = partner?.name ?: "l'autre"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable(enabled = forMe) { if (forMe) vm.toggleDone(task.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (if (task.done) "✓ " else "· ") + task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (task.done) NeutralGray else MaterialTheme.colorScheme.onBackground,
                                textDecoration = if (task.done) TextDecoration.LineThrough else null
                            )
                            Text(
                                text = if (forMe) "↗ confiée par $otherName"
                                else "↘ confiée à $otherName" +
                                    (task.date?.let { " · ${Dates.shortLabel(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = when (tab) {
                "menus" -> "Remplir les menus"
                "finance" -> "+ Ajouter un poste"
                else -> "+ Ajouter pour les enfants"
            },
            onClick = {
                when (tab) {
                    "menus" -> onMenus(weekStart)
                    else -> creating = tab
                }
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    val section = creating
    val edited = editing
    if (section != null || edited != null) {
        HouseItemDialog(
            section = section ?: edited!!.section,
            existing = edited,
            onSave = { title, detail, amount, due ->
                vm.saveHouseItem(edited?.id, section ?: edited!!.section, title, detail, amount, due)
                creating = null
                editing = null
            },
            onDelete = edited?.let {
                {
                    vm.deleteHouseItem(it.id)
                    editing = null
                }
            },
            onDismiss = {
                creating = null
                editing = null
            }
        )
    }

    val reworkSlot = reworking
    if (reworkSlot != null) {
        val aiBusy by vm.aiBusy.collectAsState()
        MealRework(
            slot = reworkSlot,
            label = MenuIdeas.slotLabel(reworkSlot),
            busy = aiBusy,
            onDismiss = { reworking = null },
            onSend = { instruction ->
                vm.reworkMealWithAi(today, reworkSlot, instruction)
                reworking = null
            }
        )
    }
}

/** Onglet interne : un tap, un état visible, jamais de balayage. */
@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HouseItemRow(
    item: HouseItemEntity,
    accent: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp)
        ) {
            Text(
                text = (if (item.done) "✓ " else "") + item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.done) NeutralGray else MaterialTheme.colorScheme.onBackground,
                textDecoration = if (item.done) TextDecoration.LineThrough else null
            )
            val sub = buildList {
                if (item.amount != 0.0) add("%,.0f €".format(item.amount))
                item.dueDate?.let { add(Dates.shortLabel(it)) }
                if (item.detail.isNotBlank()) add(item.detail)
            }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.done) NeutralGray else accent
                )
            }
        }
        TextButton(onClick = onEdit) { Text("✏️") }
    }
}

@Composable
private fun HouseItemDialog(
    section: String,
    existing: HouseItemEntity?,
    onSave: (String, String, Double, String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var detail by remember { mutableStateOf(existing?.detail ?: "") }
    var amount by remember {
        mutableStateOf(if ((existing?.amount ?: 0.0) != 0.0) existing!!.amount.toInt().toString() else "")
    }
    var due by remember { mutableStateOf(existing?.dueDate ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (section == "finance") "💶 Poste financier" else "🧒 Enfants") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Quoi ?") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Précision (facultatif)") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (section == "finance") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Montant en € (facultatif)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it },
                    label = { Text("Échéance AAAA-MM-JJ (facultatif)") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                    onSave(title, detail, amount.toDoubleOrNull() ?: 0.0, due.ifBlank { null })
                },
                enabled = title.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

/** Emoji du créneau : un repère visuel vaut mieux qu'un mot répété trois fois. */
private fun slotEmoji(slot: String): String = when (slot) {
    "matin" -> "🥐"
    "midi" -> "🍽️"
    else -> "🌙"
}

/**
 * Un repas de la journée : ce qu'on mange, ce qu'il y a dans l'assiette de
 * chacun, et ce que ça pèse en calories. Les quantités par personne sont là
 * pour qu'on serve sans peser au hasard — pas pour compter les grammes.
 */
@Composable
private fun MealCard(
    slot: String,
    label: String,
    title: String,
    quantities: String,
    calories: Int,
    accent: androidx.compose.ui.graphics.Color,
    aiReady: Boolean,
    onAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slotEmoji(slot), style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title.ifBlank { "Rien de prévu" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (title.isBlank()) NeutralGray else MaterialTheme.colorScheme.onSurface
                )
            }
            if (calories > 0) {
                Text(
                    text = "$calories\nkcal",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
        if (quantities.isNotBlank()) {
            Text(
                text = "Par personne : $quantities",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (aiReady) {
            TextButton(onClick = onAi, modifier = Modifier.padding(top = 4.dp)) {
                Text("✨ Modifier ce repas")
            }
        }
    }
}

/**
 * « Modifier ce repas » : une consigne en français, et l'assistant réécrit le
 * plat avec ses quantités et ses calories. On propose des consignes toutes
 * faites, parce qu'un champ vide est une question à laquelle personne n'a envie
 * de répondre.
 */
@Composable
private fun MealRework(
    slot: String,
    label: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var instruction by remember { mutableStateOf("") }
    val shortcuts = listOf(
        "Plus léger", "Plus rapide (20 min)", "Végétarien",
        "Avec ce qu'il me reste", "Pour des invités"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slotEmoji(slot)} $label") },
        text = {
            Column {
                Text(
                    text = "Dites ce que vous voulez changer.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(10.dp))
                shortcuts.forEach { s ->
                    TextButton(onClick = { instruction = s }) { Text(s) }
                }
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    placeholder = { Text("Ex. : il me reste du poulet et des courgettes") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(instruction) }, enabled = !busy) {
                Text(if (busy) "…" else "Proposer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
