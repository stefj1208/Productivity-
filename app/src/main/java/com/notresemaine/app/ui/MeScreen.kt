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
import com.notresemaine.app.ai.Ai
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates

/**
 * « Moi » : tout ce qui me concerne, visible d'un coup d'œil.
 *
 * Avant, ces fonctions vivaient derrière une roue dentée, empilées dans un seul
 * long écran de réglages — donc introuvables. Chacune a maintenant sa tuile, et
 * chaque tuile dit où elle en est : un réglage dont on voit l'état est un réglage
 * qu'on ouvre.
 */
@Composable
fun MeScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onRitual: () -> Unit,
    onScreenTime: () -> Unit,
    onHealth: () -> Unit,
    onPerformance: () -> Unit,
    onWeight: () -> Unit,
    onHabits: () -> Unit,
    onSport: () -> Unit,
    onAsk: () -> Unit,
    onMealLog: () -> Unit,
    onCalendar: () -> Unit,
    onAssistant: () -> Unit,
    onReminders: () -> Unit,
    onMethod: () -> Unit,
    onSync: () -> Unit,
    onProfile: () -> Unit
) {
    val myId = settings.myUserId
    val weekStart = Dates.weekStartIso()
    val days = Dates.daysOfWeek(weekStart)

    val ritualSteps by remember(myId) { vm.repo.db.ritual().steps(myId) }
        .collectAsState(initial = emptyList())
    val ritualLogs by remember { vm.repo.db.ritual().logs() }
        .collectAsState(initial = emptyList())
    val healthDays by remember(weekStart) { vm.repo.db.health().since(days.first()) }
        .collectAsState(initial = emptyList())
    val usageDays by remember { vm.repo.db.usage().since(Dates.todayIso()) }
        .collectAsState(initial = emptyList())
    val weights by remember { vm.repo.db.weights().all() }
        .collectAsState(initial = emptyList())
    val lastWeight = weights.filter { it.userId == myId }.maxByOrNull { it.date }
    val habits by remember { vm.repo.db.habits().all() }
        .collectAsState(initial = emptyList())
    val habitCount = habits.count { it.userId == myId && it.enabled }
    val today = Dates.todayIso()
    val mealLogs by remember(today) { vm.repo.db.mealLogs().between(today, today) }
        .collectAsState(initial = emptyList())
    val loggedToday = mealLogs.count { it.userId == myId }

    val streak = vm.repo.ritualStreak(ritualLogs, myId)
    val activeSteps = ritualSteps.count { it.enabled }

    val myNights = healthDays.filter { it.userId == myId && it.sleepMinutes > 0 }
    val avgSleep = if (myNights.isEmpty()) 0 else myNights.sumOf { it.sleepMinutes } / myNights.size
    val socialToday = usageDays.firstOrNull { it.userId == myId }?.socialMinutes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "Moi",
            subtitle = settings.myName.ifBlank { "Profil incomplet" }
        )

        Spacer(Modifier.height(8.dp))
        SectionLabel("MES HABITUDES")
        ShortcutTile(
            emoji = "🌅",
            title = "Mon rituel du matin",
            subtitle = when {
                activeSteps == 0 -> "Pas encore configuré"
                streak > 0 -> "$activeSteps étapes · série de $streak jour" + (if (streak > 1) "s" else "")
                else -> "$activeSteps étapes · à reprendre"
            },
            onClick = onRitual
        )
        ShortcutTile(
            emoji = "📵",
            title = "Mon pacte d'écran",
            subtitle = when {
                !settings.pacteEnabled -> "Aucun engagement pour l'instant"
                socialToday != null -> "Limite ${settings.dailyLimitMinutes} min · $socialToday min aujourd'hui"
                else -> "Limite ${settings.dailyLimitMinutes} min/jour"
            },
            onClick = onScreenTime,
            highlight = settings.pacteEnabled
        )
        ShortcutTile(
            emoji = "😴",
            title = "Sommeil & sport",
            subtitle = if (avgSleep > 0) "${avgSleep / 60} h ${avgSleep % 60} en moyenne cette semaine"
            else "Aucune mesure cette semaine",
            onClick = onHealth
        )

        ShortcutTile(
            emoji = "🔁",
            title = "Mes habitudes",
            subtitle = if (habitCount == 0) "Aucune — rappels au hasard dans la journée"
            else "$habitCount active(s)",
            onClick = onHabits,
            highlight = habitCount > 0
        )
        ShortcutTile(
            emoji = "🏃",
            title = "Sport",
            subtitle = "Séances de la semaine et programme sur mesure",
            onClick = onSport
        )
        ShortcutTile(
            emoji = "⚖️",
            title = "Mon poids",
            subtitle = when {
                lastWeight == null -> "Aucune pesée"
                settings.weightTarget > 0 -> String.format(
                    java.util.Locale.FRANCE, "%.1f kg · objectif %.1f kg",
                    lastWeight.kilos, settings.weightTarget
                )
                else -> String.format(java.util.Locale.FRANCE, "%.1f kg", lastWeight.kilos)
            },
            onClick = onWeight
        )

        ShortcutTile(
            emoji = "📷",
            title = "Ce que j'ai mangé",
            subtitle = if (loggedToday == 0) "Le réel, en face du menu prévu"
            else "$loggedToday repas noté(s) aujourd'hui",
            onClick = onMealLog,
            highlight = loggedToday > 0
        )

        ShortcutTile(
            emoji = "📈",
            title = "Mes performances",
            subtitle = "Tâches, séances, sommeil, écran — les chiffres",
            onClick = onPerformance
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("L'APPLICATION")
        ShortcutTile(
            emoji = "📅",
            title = "Agenda",
            subtitle = if (settings.calendarEnabled && settings.calendarName.isNotBlank())
                "Créneaux envoyés vers ${settings.calendarName}"
            else "Relier l'agenda du téléphone (donc Google Agenda)",
            onClick = onCalendar,
            highlight = settings.calendarEnabled
        )
        ShortcutTile(
            emoji = "💬",
            title = "Chat avec l'assistant",
            subtitle = "Une question sur votre semaine, réponse d'après vos données",
            onClick = onAsk
        )
        ShortcutTile(
            emoji = "✨",
            title = "Assistant",
            subtitle = if (settings.aiEnabled && settings.aiApiKey.isNotBlank())
                "Activé · ${Ai.providerLabel(settings.aiApiKey)}"
            else "Éteint — aucun bouton ✨ n'apparaît",
            onClick = onAssistant,
            highlight = settings.aiEnabled && settings.aiApiKey.isNotBlank()
        )
        ShortcutTile(
            emoji = "🔔",
            title = "Rappels",
            subtitle = if (settings.alertsEnabled) "Alarme plein écran au moment d'agir"
            else "Deux notifications discrètes par jour",
            onClick = onReminders
        )
        ShortcutTile(
            emoji = "☁️",
            title = "Synchronisation",
            subtitle = when {
                settings.supabaseUrl.isBlank() -> "Non configurée — l'app marche sans"
                settings.refreshToken.isBlank() -> "À terminer : créer votre compte"
                settings.coupleCode.isBlank() -> "À terminer : relier vos deux téléphones"
                else -> "Reliée ✓ · ${settings.authEmail}"
            },
            onClick = onSync,
            highlight = settings.coupleCode.isNotBlank()
        )
        ShortcutTile(
            emoji = "📖",
            title = "La méthode",
            subtitle = "Ce que l'application applique des 6 livres",
            onClick = onMethod
        )
        ShortcutTile(
            emoji = "👤",
            title = "Profil & apparence",
            subtitle = settings.myName.ifBlank { "Sans prénom" } + " · " +
                when (settings.themeMode) {
                    "clair" -> "thème clair"
                    "auto" -> "thème auto"
                    else -> "thème sombre"
                },
            onClick = onProfile
        )

        Spacer(Modifier.height(32.dp))
    }
}
