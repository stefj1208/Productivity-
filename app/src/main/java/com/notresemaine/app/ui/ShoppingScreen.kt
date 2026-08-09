package com.notresemaine.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
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
    val items by remember(weekStart) { vm.repo.db.shopping().forWeek(weekStart) }
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
            Spacer(Modifier.height(20.dp))
            Text("🛒 Courses", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${Dates.weekRangeLabel(weekStart)} · $remaining article(s) à prendre",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Les articles que l'application ne reconnaît pas atterrissent dans « Divers ».
            val unsorted = items.count { it.aisle == "Divers" }
            if (settings.aiEnabled && settings.aiApiKey.isNotBlank() && unsorted > 0) {
                val aiBusy by vm.aiBusy.collectAsState()
                AiButton(
                    text = "Ranger les $unsorted articles de « Divers »",
                    busy = aiBusy,
                    onClick = { vm.sortShoppingWithAi(weekStart) },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    placeholder = { Text("Ajouter un article") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    vm.addShoppingItem(weekStart, newItem)
                    newItem = ""
                }) { Text("Ajouter") }
            }

            if (items.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Liste vide. Renseigne les menus de la semaine, " +
                        "puis touche « Générer la liste de courses ».",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            items.groupBy { it.aisle }.forEach { (aisle, aisleItems) ->
                Spacer(Modifier.height(16.dp))
                SectionLabel(aisle.uppercase())
                aisleItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
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
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Row {
            TextButton(onClick = onBack) { Text("Retour") }
            if (items.any { it.checked }) {
                TextButton(onClick = { vm.clearCheckedShopping(weekStart) }) {
                    Text("Retirer les articles pris")
                }
            }
        }
        BigButton(
            text = "Régénérer depuis les menus",
            onClick = { vm.generateShoppingList(weekStart) },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
