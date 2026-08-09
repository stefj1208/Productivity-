package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.MenuIdeas
import com.notresemaine.app.ui.theme.NeutralGray

/**
 * « Maison » : ce qu'on mange et ce qu'il faut acheter.
 *
 * Ces deux fonctions vivaient au fond de l'onglet Semaine, derrière deux petits
 * boutons — donc invisibles. Elles ont maintenant leur propre onglet, et la
 * question du jour (« on mange quoi ce soir ? ») a la réponse en haut de l'écran.
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

    val meals by remember(weekStart) { vm.repo.db.meals().between(days.first(), days.last()) }
        .collectAsState(initial = emptyList())
    val shopping by remember(weekStart) { vm.repo.db.shopping().forWeek(weekStart) }
        .collectAsState(initial = emptyList())

    val todayMeals = meals.filter { it.date == today }
    val remaining = shopping.count { !it.checked }
    val plannedThisWeek = meals.count { it.title.isNotBlank() }

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
            Text("Maison", style = MaterialTheme.typography.titleLarge)
            Text(
                text = Dates.longLabel(today),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("AU MENU AUJOURD'HUI")
            MenuIdeas.slots.forEach { (slot, label) ->
                val meal = todayMeals.firstOrNull { it.slot == slot }
                Text(
                    text = "$label · ${meal?.title?.ifBlank { null } ?: "—"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (meal?.title.isNullOrBlank()) NeutralGray
                    else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            ShortcutTile(
                emoji = "🍽️",
                title = "Menus de la semaine",
                subtitle = if (plannedThisWeek == 0) "Rien de décidé pour l'instant"
                else "$plannedThisWeek repas prévus sur 21",
                onClick = { onMenus(weekStart) }
            )
            ShortcutTile(
                emoji = "🛒",
                title = "Liste de courses",
                subtitle = if (shopping.isEmpty()) "À générer depuis les menus"
                else "$remaining article(s) à prendre",
                onClick = { onShopping(weekStart) }
            )

            Spacer(Modifier.height(20.dp))
            TipCard(com.notresemaine.app.data.Tips.review(6))
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = "Remplir les menus",
            onClick = { onMenus(weekStart) },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
