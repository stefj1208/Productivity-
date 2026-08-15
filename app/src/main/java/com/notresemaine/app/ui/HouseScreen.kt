package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedButton
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
import com.notresemaine.app.data.Categories
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.HouseItemEntity
import com.notresemaine.app.data.MenuIdeas
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

/**
 * « Catégories » : tout ce qui se gère à deux, rangé par sujet.
 *
 * Une catégorie n'existe que si elle contient quelque chose — pas de rangement
 * vide à administrer. « Repas » est la seule à avoir un écran à part, parce que
 * c'est la seule qui produit une liste de courses ; les autres partagent les
 * mêmes gestes : noter, décrire, en faire un plan d'action.
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
    val aiReady = settings.aiEnabled && settings.aiApiKey.isNotBlank()

    var tab by remember { mutableStateOf("repas") }
    var editing by remember { mutableStateOf<HouseItemEntity?>(null) }
    var creating by remember { mutableStateOf<String?>(null) }
    var reworking by remember { mutableStateOf<String?>(null) }
    var newCategory by remember { mutableStateOf(false) }

    val meals by remember(weekStart) { vm.repo.db.meals().between(days.first(), days.last()) }
        .collectAsState(initial = emptyList())
    val shopping by remember(weekStart) { vm.repo.db.shopping().forWeek(weekStart) }
        .collectAsState(initial = emptyList())
    val weekTasks by remember(weekStart) { vm.repo.db.tasks().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val profiles by remember { vm.repo.db.profiles().all() }
        .collectAsState(initial = emptyList())
    val items by remember { vm.repo.db.houseItems().all() }
        .collectAsState(initial = emptyList())

    val partner = profiles.firstOrNull { it.id != myId }
    val sharedTasks = weekTasks.filter { it.assignedBy.isNotBlank() }
    val categories = Categories.all(items.map { it.section })
    val current = items.filter { it.section == tab }

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
            ScreenHeader(title = "Catégories", subtitle = Dates.longLabel(today))

            // Rangée de catégories qui défile : en ajouter une ne coûte rien.
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                categories.forEach { cat ->
                    val count = items.count { it.section == cat.key && !it.done }
                    TabChip(
                        label = "${cat.emoji} ${cat.label}" + if (count > 0) " ($count)" else "",
                        selected = tab == cat.key,
                        onClick = { tab = cat.key }
                    )
                }
                TabChip(label = "＋", selected = false, onClick = { newCategory = true })
            }

            Spacer(Modifier.height(20.dp))
            if (tab == "repas") {
                MealsTab(
                    vm = vm,
                    settings = settings,
                    today = today,
                    weekStart = weekStart,
                    meals = meals,
                    shopping = shopping,
                    accent = accent,
                    aiReady = aiReady,
                    onMenus = onMenus,
                    onShopping = onShopping,
                    onRework = { reworking = it }
                )
            } else {
                CategoryTab(
                    vm = vm,
                    categoryKey = tab,
                    items = current,
                    accent = accent,
                    onEdit = { editing = it }
                )
            }

            // ----- Ce qu'on s'est confié -----
            if (sharedTasks.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("TÂCHES CONFIÉES")
                sharedTasks.forEach { task ->
                    val fromMe = task.assignedBy == myId
                    TaskRow(
                        task = task,
                        accent = accent,
                        onToggle = { if (!fromMe) vm.toggleDone(task.id) },
                        note = if (fromMe) "↗ confiée à ${partner?.name ?: "l'autre"}"
                        else "↘ confiée par ${partner?.name ?: "l'autre"}"
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        BigButton(
            text = if (tab == "repas") "Remplir les menus"
            else "Ajouter dans ${Categories.labelOf(tab)}",
            onClick = {
                if (tab == "repas") onMenus(weekStart) else creating = tab
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    // ----- Boîtes de dialogue -----

    val section = creating
    val edited = editing
    if (section != null || edited != null) {
        CategoryItemDialog(
            vm = vm,
            categoryKey = section ?: edited!!.section,
            existing = edited,
            aiReady = aiReady,
            partnerName = partner?.name,
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
                vm.clearAiPlan()
            }
        )
    }

    if (newCategory) {
        NewCategoryDialog(
            onDismiss = { newCategory = false },
            onCreate = { label ->
                val key = Categories.keyOf(label)
                if (key.isNotBlank()) {
                    tab = key
                    creating = key
                }
                newCategory = false
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

// ---------------------------------------------------------------- Repas

@Composable
private fun MealsTab(
    vm: AppViewModel,
    settings: AppSettings,
    today: String,
    weekStart: String,
    meals: List<com.notresemaine.app.data.MealEntity>,
    shopping: List<com.notresemaine.app.data.ShoppingItemEntity>,
    accent: androidx.compose.ui.graphics.Color,
    aiReady: Boolean,
    onMenus: (String) -> Unit,
    onShopping: (String) -> Unit,
    onRework: (String) -> Unit
) {
    SectionLabel("AU MENU AUJOURD'HUI")
    val todayMeals = meals.filter { it.date == today }
    val eaten = todayMeals.sumOf { it.calories }
    MenuIdeas.slots.forEach { (slot, label) ->
        val meal = todayMeals.firstOrNull { it.slot == slot }
        MealCard(
            slot = slot,
            label = label,
            title = meal?.title.orEmpty(),
            quantities = meal?.quantities.orEmpty(),
            calories = meal?.calories ?: 0,
            accent = accent,
            aiReady = aiReady,
            onAi = { onRework(slot) }
        )
    }
    if (eaten > 0 && settings.dailyCalories > 0) {
        Spacer(Modifier.height(10.dp))
        Gauge(
            value = eaten.toFloat(),
            max = settings.dailyCalories.toFloat(),
            accent = accent,
            caption = "$eaten kcal prévues par personne sur ${settings.dailyCalories} — " +
                "pour ${settings.householdSize} couvert" +
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

// ---------------------------------------------------------- Autres catégories

@Composable
private fun CategoryTab(
    vm: AppViewModel,
    categoryKey: String,
    items: List<HouseItemEntity>,
    accent: androidx.compose.ui.graphics.Color,
    onEdit: (HouseItemEntity) -> Unit
) {
    val open = items.filter { !it.done }
    val total = open.sumOf { it.amount }
    if (total != 0.0) {
        KpiTile(
            value = "%,.0f €".format(total),
            label = "total des postes en cours",
            accent = accent,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
    }

    SectionLabel("EN COURS (${open.size})")
    if (items.isEmpty()) {
        EmptyState(
            emoji = Categories.emojiOf(categoryKey),
            text = "Rien dans ${Categories.labelOf(categoryKey)}. Notez l'intention en une " +
                "ligne — l'assistant en fait un plan d'action."
        )
    }
    open.forEach { item ->
        HouseItemRow(item, accent, { vm.toggleHouseItem(item.id) }, { onEdit(item) })
    }

    val done = items.filter { it.done }
    if (done.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionLabel("RÉGLÉ (${done.size})")
        done.forEach { item ->
            HouseItemRow(item, accent, { vm.toggleHouseItem(item.id) }, { onEdit(item) })
        }
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
            .padding(vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = (if (item.done) "✓ " else "") + item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.done) NeutralGray else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.done) TextDecoration.LineThrough else null
            )
            val details = buildList {
                if (item.amount != 0.0) add("%,.0f €".format(item.amount))
                if (!item.dueDate.isNullOrBlank()) add(Dates.shortLabel(item.dueDate!!))
                if (item.detail.isNotBlank()) add(item.detail)
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.done) NeutralGray else accent
                )
            }
        }
        TextButton(onClick = onEdit) { Text("✏️") }
    }
}

// ------------------------------------------------------------- Dialogues

/**
 * Ajouter ou modifier un élément — et surtout : le transformer en plan d'action.
 *
 * Une intention notée (« refaire les papiers de la voiture ») ne bouge jamais
 * toute seule. Le bouton ✨ la découpe en actions faisables, dit si c'est une
 * routine ou un coup unique, et chaque action part chez soi ou chez l'autre.
 */
@Composable
private fun CategoryItemDialog(
    vm: AppViewModel,
    categoryKey: String,
    existing: HouseItemEntity?,
    aiReady: Boolean,
    partnerName: String?,
    onSave: (String, String, Double, String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var detail by remember { mutableStateOf(existing?.detail ?: "") }
    var amount by remember { mutableStateOf(if ((existing?.amount ?: 0.0) != 0.0) existing!!.amount.toString() else "") }
    var due by remember { mutableStateOf(existing?.dueDate ?: "") }

    val aiBusy by vm.aiBusy.collectAsState()
    val plan by vm.aiPlan.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${Categories.emojiOf(categoryKey)} ${Categories.labelOf(categoryKey)}")
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                    label = { Text("En deux mots (facultatif)") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (aiReady) {
                    AiButton(
                        text = "Faire le plan d'action",
                        busy = aiBusy,
                        enabled = title.isNotBlank(),
                        onClick = { vm.planActionWithAi(categoryKey, title, detail) },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                val p = plan
                if (p != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (p.kind == "routine") "🔁 Ça revient régulièrement"
                        else "✅ Ça se règle une fois",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = p.note,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    p.steps.forEachIndexed { i, stepText ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("${i + 1}. $stepText", style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { vm.addTask(stepText, null, Dates.weekStartIso()) }) {
                                    Text("Pour moi")
                                }
                                if (partnerName != null) {
                                    TextButton(onClick = { vm.addTaskForPartner(stepText) }) {
                                        Text("Pour $partnerName")
                                    }
                                }
                            }
                        }
                    }
                    if (p.kind == "routine") {
                        OutlinedButton(
                            onClick = {
                                vm.makeRoutineFromPlan(title, categoryKey)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(top = 6.dp)
                        ) { Text("🔁 En faire une routine hebdomadaire") }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Montant en € (facultatif)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                onClick = { onSave(title, detail, amount.toDoubleOrNull() ?: 0.0, due.ifBlank { null }) },
                enabled = title.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun NewCategoryDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle catégorie") },
        text = {
            Column {
                Text(
                    text = "Elle apparaîtra dès que vous y mettrez quelque chose.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("Ex. : voiture, voyages, animaux") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(label) }, enabled = label.isNotBlank()) {
                Text("Créer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
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
