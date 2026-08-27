package com.notresemaine.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.notresemaine.app.data.Fasting
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
fun MealLogScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onMenus: (String) -> Unit,
    onBack: () -> Unit
) {
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

    val logs by remember(today) { vm.repo.db.mealLogs().between(today, today) }
        .collectAsState(initial = emptyList())
    val meals by remember(today) { vm.repo.db.meals().between(today, today) }
        .collectAsState(initial = emptyList())
    // Un jeûne enjambe la nuit : le calculer sur la seule journée d'aujourd'hui
    // afficherait « 8 h » à midi pour quelqu'un qui n'a rien mangé depuis la veille.
    // Trois jours en arrière suffisent, et couvrent un jeûne de deux jours pleins.
    val recentLogs by remember(today) {
        vm.repo.db.mealLogs().between(java.time.LocalDate.now().minusDays(3).toString(), today)
    }.collectAsState(initial = emptyList())

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

            // ----- Le jeûne en cours -----
            //
            // Personne ne déclare « je commence mon jeûne » : on note ses repas, et
            // l'écart se lit tout seul. C'est pour ça que ce chiffre apparaît sans
            // qu'on ait rien eu à démarrer.
            val fastingMinutes = Fasting.currentMinutes(recentLogs.filter { it.userId == myId })
            if (fastingMinutes != null && fastingMinutes >= 4 * 60) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "⏳ ${Fasting.label(fastingMinutes)} sans manger",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
                Text(
                    text = "Depuis le dernier repas noté. Notez le suivant et le compteur repart.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ----- Le chemin court : cocher ce qui était prévu -----
            //
            // La plupart des jours, on mange ce qui était au menu. Un appui suffit
            // alors, et la photo ne sert plus qu'aux jours où l'assiette s'écarte
            // du plan. Mettre ceci avant l'appareil photo, c'est mettre le geste
            // le plus fréquent en premier.
            //
            // La section reste visible même sans menu. Quand elle disparaissait,
            // on croyait la fonction perdue alors qu'il manquait simplement le
            // menu du jour — exactement le genre de silence qui fait douter de
            // l'application plutôt que de ses données.
            val plannedMeals = meals.filter { it.title.isNotBlank() }
            Spacer(Modifier.height(24.dp))
            SectionLabel("J'AI MANGÉ CE QUI ÉTAIT PRÉVU")
            if (plannedMeals.isEmpty()) {
                Text(
                    text = "Rien au menu pour ${Dates.longLabel(today)}. Remplissez-le et " +
                        "chaque repas s'affichera ici, à cocher en un appui.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { onMenus(Dates.weekStartIso()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 10.dp)
                ) { Text("🍽️ Remplir les menus de la semaine") }
            } else {
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
                        accent = accent,
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

            // ----- Les trois façons de noter ce repas -----
            //
            // Elles sont maintenant côte à côte, juste sous le créneau choisi.
            // « Noter à la main » vivait tout en bas, en petit texte, après la
            // liste : c'est pourtant le seul moyen qui marche sans clé, sans
            // réseau et sans photo. Il passe donc devant, en vrai bouton.
            Spacer(Modifier.height(14.dp))
            BigButton(
                text = "✍️ Noter ce repas à la main",
                onClick = { manual = true }
            )

            // Sans cette touche, sauter un repas et oublier de le noter laissent
            // exactement la même trace : rien. C'est elle, et elle seule, qui
            // permet ensuite de parler de jeûne plutôt que de trou dans le journal.
            val fasted = mine.any { it.slot == slot && it.source == "jeune" }
            OutlinedButton(
                onClick = { vm.toggleFasted(today, slot) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 10.dp)
            ) {
                Text(
                    if (fasted) "✓ ${slotLabel(slot)} jeûné — annuler"
                    else "🚫 J'ai jeûné ce repas (0 kcal)"
                )
            }

            if (photoReady) {
                Spacer(Modifier.height(12.dp))
                VoiceField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Précision (facultatif)",
                    placeholder = "Ex. : il y avait aussi du pain et un verre de vin",
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
            mine.sortedBy { it.time }.forEach { log ->
                MealLogRow(log, accent) { vm.deleteMealLog(log.id) }
            }

            // ----- Ce qu'il y avait dedans -----
            val todayNutrition = com.notresemaine.app.data.Nutrition.summarize(mine)
            if (todayNutrition.hasData) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("CE QU'IL Y AVAIT DEDANS")
                MacroBar(todayNutrition, accent)
            }

            // ----- Partager, ou pas -----
            //
            // Le menu se décide à deux ; l'assiette réelle non. Sauter un repas,
            // se resservir, grignoter à 23 h : c'est du même ordre que le poids.
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Partager avec mon binôme",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (settings.mealLogShared)
                            "Vos repas notés partent vers l'espace commun."
                        else "Vos repas restent sur ce téléphone. Le menu, lui, reste " +
                            "partagé — c'est ce qui est vraiment mangé qui ne l'est pas.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.mealLogShared,
                    onCheckedChange = { vm.saveMealLogShared(it) }
                )
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
            onSave = { title, detail, low, high, time ->
                val r = p.reading
                vm.logMeal(
                    p.date, p.slot, title, detail, low, high, "photo", time,
                    r.protein, r.carbs, r.fat, r.fiber
                )
                note = ""
            },
            onDismiss = { vm.clearPhotoMeal() }
        )
    }

    if (manual) {
        ManualMealDialog(
            vm = vm,
            slot = slot,
            aiReady = aiReady,
            accent = accent,
            onDismiss = { manual = false },
            onSave = { title, kcal, time, macros ->
                vm.logMeal(
                    today, slot, title, "", kcal, kcal, "manuel", time,
                    macros[0], macros[1], macros[2], macros[3]
                )
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
    onSave: (String, String, Int, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val r = proposal.reading
    var title by remember { mutableStateOf(r.title) }
    var detail by remember { mutableStateOf(r.items) }
    var low by remember { mutableStateOf(r.caloriesLow.toString()) }
    var high by remember { mutableStateOf(r.caloriesHigh.toString()) }
    var time by remember { mutableStateOf(Fasting.defaultTime(proposal.slot)) }

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
                VoiceField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Le plat",
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                VoiceField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = "Ce qu'il y avait",
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
                if (r.protein > 0 || r.carbs > 0 || r.fat > 0) {
                    Text(
                        text = "🥗 ${r.protein} g protéines · ${r.carbs} g glucides · " +
                            "${r.fat} g lipides · ${r.fiber} g fibres",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                // L'heure n'est pas un détail administratif : c'est elle qui rend
                // le jeûne mesurable. Pré-remplie à l'heure habituelle du créneau.
                TimeField(label = "À QUELLE HEURE ?", value = time, onChange = { time = it })

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
                        high.toIntOrNull() ?: (low.toIntOrNull() ?: 0),
                        time
                    )
                },
                enabled = title.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/**
 * Noter un repas en l'écrivant — avec les calories estimées au fil de la frappe.
 *
 * Taper « pâtes bolognaise et un verre de vin » puis devoir chercher soi-même
 * combien ça fait, c'est demander à l'utilisateur le travail que la machine sait
 * faire. L'estimation part donc toute seule une seconde après la dernière lettre,
 * remplit le champ, et **s'efface de la route dès qu'on y touche** : à partir de
 * là, le chiffre est le vôtre et plus rien ne le réécrit.
 *
 * Aucune image ici : c'est du texte, comme les autres boutons ✨. Cela marche donc
 * même quand l'option « analyse photo » est éteinte.
 */
@Composable
private fun ManualMealDialog(
    vm: AppViewModel,
    slot: String,
    aiReady: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, List<Int>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(Fasting.defaultTime(slot)) }
    // Dès que la personne écrit un chiffre, l'assistant se tait définitivement.
    var kcalIsMine by remember { mutableStateOf(false) }

    val estimate by vm.aiMealEstimate.collectAsState()
    val estimating by vm.aiMealEstimating.collectAsState()

    // Une seconde de silence au clavier vaut « j'ai fini d'écrire ». Sans cette
    // pause, chaque lettre déclencherait une requête.
    androidx.compose.runtime.LaunchedEffect(title, aiReady) {
        if (!aiReady || kcalIsMine) return@LaunchedEffect
        kotlinx.coroutines.delay(1000)
        vm.estimateMealCalories(title)
    }
    androidx.compose.runtime.LaunchedEffect(estimate) {
        val e = estimate ?: return@LaunchedEffect
        if (!kcalIsMine && e.calories > 0) kcal = e.calories.toString()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.clearMealEstimate() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slotEmoji(slot)} ${slotLabel(slot)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                VoiceField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Qu'avez-vous mangé ?",
                    placeholder = "Ex. : pâtes bolognaise et un verre de vin",
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = kcal,
                    onValueChange = {
                        kcal = it.filter { c -> c.isDigit() }
                        kcalIsMine = true
                    },
                    label = { Text("Calories environ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val e = estimate
                Text(
                    text = when {
                        !aiReady -> "Assistant éteint : tapez le chiffre vous-même si vous " +
                            "le connaissez, sinon laissez vide."
                        estimating -> "L'assistant estime…"
                        kcalIsMine -> "Votre chiffre — l'assistant ne le remplacera plus."
                        e != null && e.caloriesHigh > 0 ->
                            "Estimé entre ${e.caloriesLow} et ${e.caloriesHigh} kcal. " +
                                "${e.confidenceLabel}. Corrigez si besoin."
                        title.isBlank() -> "Décrivez le repas : les calories s'estiment toutes seules."
                        else -> "Décrivez les quantités pour affiner (« 150 g de pâtes »)."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (e != null && !kcalIsMine) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (e != null && e.items.isNotBlank() && !kcalIsMine) {
                    Text(
                        text = e.items,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (e != null && (e.protein > 0 || e.carbs > 0 || e.fat > 0)) {
                    Text(
                        text = "🥗 ${e.protein} g protéines · ${e.carbs} g glucides · " +
                            "${e.fat} g lipides · ${e.fiber} g fibres",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                TimeField(label = "À QUELLE HEURE ?", value = time, onChange = { time = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val e = estimate
                    // Les nutriments viennent de la même estimation que les calories.
                    // Si la personne a corrigé le chiffre à la main, on les garde
                    // quand même : elle a ajusté la quantité, pas la nature du plat.
                    val macros = if (e == null) listOf(0, 0, 0, 0)
                    else listOf(e.protein, e.carbs, e.fat, e.fiber)
                    onSave(title, kcal.toIntOrNull() ?: 0, time, macros)
                },
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
                add(if (log.time.isBlank()) slotLabel(log.slot) else "${log.time} · ${slotLabel(log.slot)}")
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
                    "jeune" -> add("🚫 sauté")
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

/**
 * Un créneau : quatre cases de même largeur, un tap, jamais de menu déroulant.
 *
 * La sélection ne repose plus sur une nuance de gris. Deux fonds sombres voisins
 * ne se distinguent pas sur un écran OLED en plein jour — c'est exactement ce
 * qu'on nous a signalé. Elle passe donc par trois signaux à la fois : un contour
 * à la couleur de la personne, le texte dans cette même couleur, et un fond plus
 * clair. Un seul suffirait à la rigueur ; trois se voient à coup sûr.
 */
@Composable
private fun SlotChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
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
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
