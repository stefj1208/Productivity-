package com.notresemaine.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.MealLogEntity
import com.notresemaine.app.data.Photo
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

/** Le menu prévoit trois moments ; la vraie vie en a un quatrième. */
private val SLOTS = listOf(
    "matin" to "Petit-déjeuner",
    "midi" to "Déjeuner",
    "soir" to "Dîner",
    "encas" to "En-cas"
)

private fun slotLabel(slot: String): String =
    SLOTS.firstOrNull { it.first == slot }?.second ?: slot

private fun slotEmoji(slot: String): String = when (slot) {
    "matin" -> "🥐"
    "midi" -> "🍽️"
    "soir" -> "🌙"
    else -> "🍎"
}

/**
 * « Ce que j'ai mangé » : le journal du réel, en face du menu théorique.
 *
 * L'écran ne note rien de lui-même. Il photographie, montre ce que l'assistant a
 * cru voir, et attend qu'on dise oui — parce qu'un chiffre lu sur une photo est
 * une estimation, et qu'une estimation se corrige avant d'être enregistrée.
 *
 * Il n'y a ici ni score, ni couleur d'alerte, ni série de jours réussis : compter
 * ce qu'on mange sert à voir l'écart avec ce qui était prévu, pas à se noter.
 */
@Composable
fun MealLogScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val today = Dates.todayIso()
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val aiReady = settings.aiEnabled && settings.aiApiKey.isNotBlank()
    val photoReady = aiReady && settings.mealPhotoEnabled

    val hour = java.time.LocalTime.now().hour
    var slot by remember {
        mutableStateOf(
            when {
                hour < 11 -> "matin"
                hour < 15 -> "midi"
                hour < 22 -> "soir"
                else -> "encas"
            }
        )
    }
    var note by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val busy by vm.aiBusy.collectAsState()
    val proposal by vm.aiPhotoMeal.collectAsState()

    val logs by remember { vm.repo.db.mealLogs().between(today, today) }
        .collectAsState(initial = emptyList())
    val meals by remember { vm.repo.db.meals().between(today, today) }
        .collectAsState(initial = emptyList())

    val mine = logs.filter { it.userId == myId }
    val eaten = mine.sumOf { it.calories }
    val planned = meals.filter { it.title.isNotBlank() }.sumOf { it.calories }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) vm.analyzeMealPhoto(uri, today, slot, note)
        else Photo.cleanUp(context)
        pendingUri = null
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.analyzeMealPhoto(uri, today, slot, note) }

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
                title = "Ce que j'ai mangé",
                subtitle = Dates.longLabel(today),
                onBack = onBack
            )

            // ----- Prévu contre réel -----
            if (planned > 0 || eaten > 0) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("AUJOURD'HUI")
                if (planned > 0) {
                    Gauge(
                        value = planned.toFloat(),
                        max = maxOf(settings.dailyCalories, 1).toFloat(),
                        accent = NeutralGray,
                        caption = "$planned kcal prévues au menu"
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Gauge(
                    value = eaten.toFloat(),
                    max = maxOf(settings.dailyCalories, 1).toFloat(),
                    accent = accent,
                    caption = if (eaten == 0) "Rien de noté pour l'instant"
                    else "≈ $eaten kcal notées sur ${settings.dailyCalories} — estimation"
                )
            }

            // ----- Le chemin court : cocher ce qui était prévu -----
            //
            // La plupart des jours, on mange ce qui était au menu. Un appui suffit
            // alors, et la photo ne sert plus qu'aux jours où l'assiette s'écarte
            // du plan. Mettre ceci avant l'appareil photo, c'est mettre le geste
            // le plus fréquent en premier.
            val plannedMeals = meals.filter { it.title.isNotBlank() }
            if (plannedMeals.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("J'AI MANGÉ CE QUI ÉTAIT PRÉVU")
                plannedMeals.forEach { meal ->
                    val ticked = mine.any { it.slot == meal.slot && it.source == "menu" }
                    PlannedMealRow(
                        emoji = slotEmoji(meal.slot),
                        label = slotLabel(meal.slot),
                        title = meal.title,
                        calories = meal.calories,
                        checked = ticked,
                        accent = accent,
                        onToggle = { vm.toggleMenuEaten(today, meal.slot) }
                    )
                }
                Text(
                    text = "Décochez si finalement ce n'était pas ça — vous pourrez le " +
                        "remplacer par une photo.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ----- Le créneau -----
            Spacer(Modifier.height(24.dp))
            SectionLabel("SINON, QUEL REPAS ?")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SLOTS.forEach { (key, label) ->
                    SlotChip(
                        label = "${slotEmoji(key)}\n${label.take(9)}",
                        selected = slot == key,
                        onClick = { slot = key },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val plannedHere = meals.firstOrNull { it.slot == slot }?.title.orEmpty()
            if (plannedHere.isNotBlank()) {
                Text(
                    text = "Au menu : $plannedHere",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (photoReady) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Précision (facultatif)") },
                    placeholder = { Text("Ex. : il y avait aussi du pain et un verre de vin") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Astuce : posez une fourchette ou votre main à côté de l'assiette. " +
                        "Sans repère de taille, l'estimation des quantités est beaucoup plus vague.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 10.dp)
                ) { Text("🖼️ Choisir une photo déjà prise") }
            } else {
                Spacer(Modifier.height(16.dp))
                EmptyState(
                    emoji = "📷",
                    text = if (!aiReady)
                        "L'assistant est éteint. Activez-le dans Moi → Assistant pour analyser " +
                            "une photo — vous pouvez déjà noter vos repas à la main."
                    else "L'analyse photo est éteinte. Activez-la dans Moi → Assistant : " +
                        "c'est la seule fonction qui envoie une image hors du téléphone."
                )
            }

            // ----- Ce qui est déjà noté -----
            Spacer(Modifier.height(24.dp))
            SectionLabel("NOTÉ AUJOURD'HUI (${mine.size})")
            if (mine.isEmpty()) {
                Text(
                    text = "Rien encore. Le menu dit ce qui était prévu ; ceci dira ce qui a " +
                        "vraiment été mangé.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            mine.forEach { log ->
                MealLogRow(log, accent) { vm.deleteMealLog(log.id) }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { manual = true }, modifier = Modifier.height(48.dp)) {
                Text("✍️ Noter un repas à la main")
            }
            Spacer(Modifier.height(24.dp))
        }

        BigButton(
            text = if (busy) "L'assistant regarde la photo…" else "📷 Photographier mon repas",
            enabled = photoReady && !busy,
            onClick = {
                val (_, uri) = Photo.newCaptureTarget(context)
                pendingUri = uri
                runCatching { camera.launch(uri) }.onFailure {
                    pendingUri = null
                    vm.messages.tryEmit("Aucun appareil photo disponible sur ce téléphone.")
                }
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    // ----- La proposition, avant tout enregistrement -----
    proposal?.let { p ->
        PhotoMealDialog(
            proposal = p,
            accent = accent,
            onSave = { title, detail, low, high ->
                vm.logMeal(p.date, p.slot, title, detail, low, high, "photo")
                note = ""
            },
            onDismiss = { vm.clearPhotoMeal() }
        )
    }

    if (manual) {
        ManualMealDialog(
            slot = slot,
            onDismiss = { manual = false },
            onSave = { title, kcal ->
                vm.logMeal(today, slot, title, "", kcal, kcal, "manuel")
                manual = false
            }
        )
    }
}

/**
 * Ce que l'assistant a cru voir — modifiable ligne par ligne.
 *
 * Tous les champs sont ouverts, y compris la fourchette de calories : le modèle
 * propose, il ne décide pas. C'est la même règle que partout dans l'application,
 * et elle compte double ici, où le chiffre est par nature approximatif.
 */
@Composable
private fun PhotoMealDialog(
    proposal: AppViewModel.PhotoMealProposal,
    accent: androidx.compose.ui.graphics.Color,
    onSave: (String, String, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val r = proposal.reading
    var title by remember { mutableStateOf(r.title) }
    var detail by remember { mutableStateOf(r.items) }
    var low by remember { mutableStateOf(r.caloriesLow.toString()) }
    var high by remember { mutableStateOf(r.caloriesHigh.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slotEmoji(proposal.slot)} ${slotLabel(proposal.slot)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Voici ce que l'assistant a reconnu. Corrigez ce qui est faux — " +
                        "rien n'est enregistré avant.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (proposal.plannedTitle.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "ÉTAIT PRÉVU",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = proposal.plannedTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NeutralGray
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Le plat") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Ce qu'il y avait") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "ENTRE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = low,
                        onValueChange = { low = it.filter { c -> c.isDigit() } },
                        label = { Text("kcal min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = high,
                        onValueChange = { high = it.filter { c -> c.isDigit() } },
                        label = { Text("kcal max") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "${r.confidenceLabel}. Une photo ne donne pas l'échelle de " +
                        "l'assiette : prenez ces chiffres comme un ordre de grandeur, jamais " +
                        "comme un compte exact.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "La photo a déjà été effacée du téléphone.",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title, detail,
                        low.toIntOrNull() ?: 0,
                        high.toIntOrNull() ?: (low.toIntOrNull() ?: 0)
                    )
                },
                enabled = title.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun ManualMealDialog(
    slot: String,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slotEmoji(slot)} ${slotLabel(slot)}") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Qu'avez-vous mangé ?") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it.filter { c -> c.isDigit() } },
                    label = { Text("Calories environ (facultatif)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, kcal.toIntOrNull() ?: 0) },
                enabled = title.isNotBlank()
            ) { Text("Noter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/**
 * Un repas du menu, à cocher.
 *
 * La ligne entière est la cible du tap, pas seulement la case : viser une case de
 * 20 dp au bout d'une ligne est une petite épreuve, alors que le geste voulu est
 * évident. La case suit, elle ne commande pas.
 */
@Composable
private fun PlannedMealRow(
    emoji: String,
    label: String,
    title: String,
    calories: Int,
    checked: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) accent else MaterialTheme.colorScheme.onSurface
            )
            if (calories > 0) {
                Text(
                    text = "$calories kcal par personne",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun MealLogRow(
    log: MealLogEntity,
    accent: androidx.compose.ui.graphics.Color,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = slotEmoji(log.slot),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 14.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(log.title, style = MaterialTheme.typography.bodyLarge)
            val line = buildList {
                add(slotLabel(log.slot))
                // Le chiffre du menu vient d'une recette : il n'a pas de fourchette,
                // et l'afficher en « 507–507 » ferait passer une valeur simple pour
                // une estimation bancale.
                when {
                    log.caloriesHigh <= 0 -> Unit
                    log.caloriesLow == log.caloriesHigh -> add("${log.calories} kcal")
                    else -> add("≈ ${log.caloriesLow}–${log.caloriesHigh} kcal")
                }
                when (log.source) {
                    "photo" -> add("📷")
                    "menu" -> add("🍽️ au menu")
                }
            }
            Text(
                text = line.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
            if (log.detail.isNotBlank()) {
                Text(
                    text = log.detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) { Text("🗑", style = MaterialTheme.typography.bodyLarge) }
    }
}

/** Un créneau : quatre cases de même largeur, un tap, jamais de menu déroulant. */
@Composable
private fun SlotChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
