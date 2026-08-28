package com.notresemaine.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

/** Liste de courses générée depuis les menus, regroupée par rayon, cochable en un tap. */
@Composable
fun ShoppingScreen(
    vm: AppViewModel,
    settings: AppSettings,
    weekStart: String,
    onBack: () -> Unit
) {
    val accent = accentFor(settings.myColor)
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(android.content.ClipboardManager::class.java)
    }
    // Les courses suivent les menus : si l'on prépare la semaine prochaine, la
    // liste doit pouvoir se lire pour la semaine prochaine.
    var offset by remember(weekStart) {
        androidx.compose.runtime.mutableIntStateOf(Dates.weekOffsetOf(weekStart))
    }
    val week = Dates.weekStartIsoOffset(offset)
    val items by remember(week) { vm.repo.db.shopping().forWeek(week) }
        .collectAsState(initial = emptyList())
    var newItem by remember { mutableStateOf("") }

    val remaining = items.count { !it.checked }

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
                title = "🛒 Courses",
                subtitle = "${Dates.weekRangeLabel(week)} · $remaining article(s)",
                onBack = onBack
            )

            Spacer(Modifier.height(8.dp))
            WeekNavigator(
                weekStartIso = week,
                onOffsetChange = { delta -> offset += delta }
            )

            // Les articles que l'application ne reconnaît pas atterrissent dans « Divers ».
            val unsorted = items.count { it.aisle == "Divers" }
            if (settings.aiEnabled && settings.aiApiKey.isNotBlank() && unsorted > 0) {
                val aiBusy by vm.aiBusy.collectAsState()
                AiButton(
                    text = "Ranger les $unsorted articles de « Divers »",
                    busy = aiBusy,
                    onClick = { vm.sortShoppingWithAi(week) },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    placeholder = "Ajouter un article",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    vm.addShoppingItem(week, newItem)
                    newItem = ""
                }) { Text("Ajouter") }
            }

            if (items.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                EmptyState(
                    emoji = "🛒",
                    text = "Liste vide. Elle se remplit toute seule à partir des menus."
                )
            }

            items.groupBy { it.aisle }.forEach { (aisle, aisleItems) ->
                Spacer(Modifier.height(16.dp))
                SectionLabel(aisle.uppercase())
                aisleItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { vm.toggleShoppingItem(item.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = if (item.checked) "Pris" else "À prendre",
                                tint = if (item.checked) NeutralGray else accent,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (item.checked) NeutralGray else MaterialTheme.colorScheme.onBackground,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        // Retirer un article de la liste, sans avoir à le cocher d'abord.
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { vm.deleteShoppingItem(item.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ----- Sortir la liste de l'application -----
        //
        // Une liste de courses se lit souvent ailleurs : dans un message envoyé
        // à l'autre, ou collée dans une note quand on fait les courses à deux
        // depuis deux rayons différents. Copier et partager sont donc deux
        // gestes distincts, et tous deux à un tap.
        val toBuy = items.filter { !it.checked }
        if (toBuy.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Courses", shoppingText(toBuy, week)
                            )
                        )
                        vm.messages.tryEmit("${'$'}{toBuy.size} articles copiés ✓")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) { Text("📋 Copier") }
                OutlinedButton(
                    onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, shoppingText(toBuy, week))
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(send, "Envoyer la liste")
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) { Text("📤 Envoyer") }
            }
        }
        if (items.any { it.checked }) {
            TextButton(onClick = { vm.clearCheckedShopping(week) }) {
                Text("Retirer les articles pris")
            }
        }
        BigButton(
            text = "Régénérer depuis les menus",
            onClick = { vm.generateShoppingList(week) },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}


/**
 * La liste sous forme de texte, rangée par rayon.
 *
 * Les rayons sont conservés : c'est ce qui rend une liste utilisable dans un
 * magasin, et ce qui la rend lisible dans un message. Seuls les articles non
 * cochés partent — envoyer ce qu'on a déjà pris n'aiderait personne.
 */
private fun shoppingText(
    items: List<com.notresemaine.app.data.ShoppingItemEntity>,
    week: String
): String = buildString {
    appendLine("🛒 Courses — ${Dates.weekRangeLabel(week)}")
    items.groupBy { it.aisle }.forEach { (aisle, ofAisle) ->
        appendLine()
        appendLine(aisle.uppercase())
        ofAisle.forEach { appendLine("- ${it.label}") }
    }
}
