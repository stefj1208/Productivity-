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
    val aiBusy by vm.aiBusy.collectAsState()

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
                title = "🍽️ Menus",
                subtitle = Dates.weekRangeLabel(weekStart),
                onBack = onBack
            )

            // Sans savoir pour combien de couverts, ni les quantités ni les
            // calories ne veulent dire quoi que ce soit : c'est donc la première
            // question, et elle ne se pose qu'une fois.
            Spacer(Modifier.height(8.dp))
            SectionLabel("POUR QUI ON CUISINE")
            var people by remember(settings.householdSize) { mutableStateOf(settings.householdSize.toString()) }
            var kcal by remember(settings.dailyCalories) { mutableStateOf(settings.dailyCalories.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = people,
                    onValueChange = { people = it },
                    label = { Text("Couverts") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.padding(horizontal = 5.dp))
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = { Text("kcal/jour") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.padding(horizontal = 5.dp))
                OutlinedButton(
                    onClick = {
                        vm.saveHousehold(people.toIntOrNull() ?: 2, kcal.toIntOrNull() ?: 2000)
                    },
                    modifier = Modifier.height(48.dp)
                ) { Text("OK") }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("PAS D'INSPIRATION ?")
            OutlinedButton(
                onClick = { vm.fillMenusFromBank(weekStart) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) { Text("💡 Remplir les créneaux vides") }

            if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                var constraints by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = constraints,
                    onValueChange = { constraints = it },
                    placeholder = { Text("Contraintes : végétarien, rapide le soir…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                AiButton(
                    text = "Demander à l'assistant",
                    busy = aiBusy,
                    onClick = { vm.suggestMenusWithAi(weekStart, constraints) },
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Text(
                    text = "✨ Menus sur mesure : activez l'assistant dans Moi.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            days.forEach { dayIso ->
                Spacer(Modifier.height(20.dp))
                Text(
                    text = Dates.shortLabel(dayIso),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dayIso == Dates.todayIso()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                com.notresemaine.app.data.MenuIdeas.slots.forEach { (slot, label) ->
                    val meal = meals.firstOrNull { it.date == dayIso && it.slot == slot }
                    MealEditor(
                        label = label,
                        title = meal?.title ?: "",
                        ingredients = meal?.ingredients ?: "",
                        quantities = meal?.quantities ?: "",
                        calories = meal?.calories ?: 0,
                        onSave = { title, ingredients, quantities, calories ->
                            vm.saveMeal(dayIso, slot, title, ingredients, quantities, calories)
                        }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

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
    quantities: String,
    calories: Int,
    onSave: (String, String, String, Int) -> Unit
) {
    var titleText by remember(title) { mutableStateOf(title) }
    var ingredientsText by remember(ingredients) { mutableStateOf(ingredients) }
    var quantitiesText by remember(quantities) { mutableStateOf(quantities) }
    var caloriesText by remember(calories) { mutableStateOf(if (calories > 0) calories.toString() else "") }
    val dirty = titleText != title || ingredientsText != ingredients ||
        quantitiesText != quantities || caloriesText != (if (calories > 0) calories.toString() else "")

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (dirty) {
                TextButton(onClick = {
                    onSave(titleText, ingredientsText, quantitiesText, caloriesText.toIntOrNull() ?: 0)
                }) { Text("Enregistrer") }
            }
            if (title.isNotBlank() && !dirty) {
                TextButton(onClick = { onSave("", "", "", 0) }) {
                    Text(
                        text = "🗑",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        OutlinedTextField(
            value = titleText,
            onValueChange = { titleText = it },
            placeholder = { Text("Ex. : pâtes bolognaise") },
            textStyle = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        if (titleText.isNotBlank()) {
            OutlinedTextField(
                value = ingredientsText,
                onValueChange = { ingredientsText = it },
                placeholder = { Text("Courses : 400 g pâtes, 500 g bœuf haché, 1 oignon") },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = quantitiesText,
                    onValueChange = { quantitiesText = it },
                    placeholder = { Text("Par personne : 120 g pâtes…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(2f)
                        .padding(top = 6.dp)
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it },
                    label = { Text("kcal") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 6.dp)
                )
            }
        }
    }
}
