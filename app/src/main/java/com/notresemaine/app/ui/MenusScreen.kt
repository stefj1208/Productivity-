package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates

/**
 * Menus de la semaine, partagés par le couple.
 * Les ingrédients saisis ici alimentent la liste de courses.
 */
@Composable
fun MenusScreen(
    vm: AppViewModel,
    settings: AppSettings,
    weekStart: String,
    onShopping: () -> Unit,
    onBack: () -> Unit
) {
    val days = Dates.daysOfWeek(weekStart)
    val meals by remember(weekStart) { vm.repo.db.meals().between(days.first(), days.last()) }
        .collectAsState(initial = emptyList())

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
            Text("🍽️ Menus", style = MaterialTheme.typography.titleLarge)
            Text(
                text = Dates.weekRangeLabel(weekStart),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            TipCard(com.notresemaine.app.data.Tips.review(6))

            days.forEach { dayIso ->
                Spacer(Modifier.height(20.dp))
                Text(
                    text = Dates.shortLabel(dayIso),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dayIso == Dates.todayIso()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf("midi" to "Midi", "soir" to "Soir").forEach { (slot, label) ->
                    val meal = meals.firstOrNull { it.date == dayIso && it.slot == slot }
                    MealEditor(
                        label = label,
                        title = meal?.title ?: "",
                        ingredients = meal?.ingredients ?: "",
                        onSave = { title, ingredients ->
                            vm.saveMeal(dayIso, slot, title, ingredients)
                        }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        TextButton(onClick = onBack) { Text("Retour") }
        BigButton(
            text = "Générer la liste de courses",
            onClick = {
                vm.generateShoppingList(weekStart)
                onShopping()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

/** Un repas : le plat, puis les ingrédients séparés par des virgules. */
@Composable
private fun MealEditor(
    label: String,
    title: String,
    ingredients: String,
    onSave: (String, String) -> Unit
) {
    var titleText by androidx.compose.runtime.remember(title) {
        androidx.compose.runtime.mutableStateOf(title)
    }
    var ingredientsText by androidx.compose.runtime.remember(ingredients) {
        androidx.compose.runtime.mutableStateOf(ingredients)
    }
    val dirty = titleText != title || ingredientsText != ingredients

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (dirty) {
                TextButton(onClick = { onSave(titleText, ingredientsText) }) { Text("Enregistrer") }
            }
        }
        OutlinedTextField(
            value = titleText,
            onValueChange = { titleText = it },
            placeholder = { Text("Ex. : pâtes bolognaise") },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (titleText.isNotBlank()) {
            OutlinedTextField(
                value = ingredientsText,
                onValueChange = { ingredientsText = it },
                placeholder = { Text("400 g pâtes, 500 g bœuf haché, 1 oignon") },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}
