package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.accentFor
import java.time.LocalDate

/**
 * « Mes performances » : les chiffres, en gros, sans commentaire.
 *
 * Les mesures étaient dispersées — un peu sur l'accueil, un peu dans Sommeil.
 * Elles sont réunies ici : d'abord ce que je constate cette semaine, puis
 * l'évolution sur quatre semaines. Aucun badge, aucune félicitation : on regarde,
 * on décide, on referme.
 */
@Composable
fun PerformanceScreen(
    vm: AppViewModel,
    settings: AppSettings,
    // Nullable : « Progrès » est devenue une destination principale, et une
    // destination principale ne porte pas de flèche de retour.
    onBack: (() -> Unit)? = null
) {
    val myId = settings.myUserId
    val accent = accentFor(settings.myColor)
    val weekStart = Dates.weekStartIso()

    val weekTasks by remember(weekStart) { vm.repo.db.tasks().byWeekAllUsers(weekStart) }
        .collectAsState(initial = emptyList())
    val goals by remember { vm.repo.db.goals().all() }
        .collectAsState(initial = emptyList())
    val ritualLogs by remember { vm.repo.db.ritual().logs() }
        .collectAsState(initial = emptyList())
    val healthDays by remember { vm.repo.db.health().since(Dates.weekStartIsoOffset(-3)) }
        .collectAsState(initial = emptyList())
    val usageDays by remember { vm.repo.db.usage().since(Dates.weekStartIsoOffset(-3)) }
        .collectAsState(initial = emptyList())
    val profiles by remember { vm.repo.db.profiles().all() }
        .collectAsState(initial = emptyList())
    val mealLogs by remember(weekStart) {
        vm.repo.db.mealLogs().between(Dates.weekStartIsoOffset(-3), Dates.todayIso())
    }.collectAsState(initial = emptyList())
    val weights by remember { vm.repo.db.weights().all() }
        .collectAsState(initial = emptyList())

    val partner = profiles.firstOrNull { it.id != myId }
    val days = Dates.daysOfWeek(weekStart)

    // Une tâche appartient à la semaine si elle y est rangée OU si elle est posée
    // sur l'un de ses jours. Ne regarder que la colonne « semaine » laissait de
    // côté les tâches datées dont ce rangement n'avait pas suivi — elles
    // existaient, mais le compteur affichait « — ».
    val myTasks = weekTasks.filter {
        it.userId == myId && !it.deleted &&
            (it.weekStart == weekStart || (it.date != null && it.date in days))
    }
    val doneTasks = myTasks.count { it.done }

    val myGoals = goals.filter { it.userId == myId && it.active }
    val plannedSessions = myGoals.sumOf { it.sessionsPerWeek }
    val doneSessions = myTasks.count { it.goalId != null && it.done }

    val streak = vm.repo.ritualStreak(ritualLogs, myId)

    val myHealth = healthDays.filter { it.userId == myId }
    val thisWeekHealth = myHealth.filter { it.date in days }
    val nights = thisWeekHealth.filter { it.sleepMinutes > 0 }
    val avgSleep = if (nights.isEmpty()) 0 else nights.sumOf { it.sleepMinutes } / nights.size
    // Les séances de sport, et RIEN d'autre : marcher toute la journée ne fait
    // pas une séance, et une séance de 30 minutes ne fait pas 8 000 pas. Les
    // deux ont donc chacun leur case, plus bas.
    val sportMinutes = thisWeekHealth.sumOf { it.exerciseMinutes }
    val stepDays = thisWeekHealth.filter { it.steps > 0 }
    val avgSteps = if (stepDays.isEmpty()) 0 else stepDays.sumOf { it.steps } / stepDays.size

    val myUsage = usageDays.filter { it.userId == myId }
    // Une journée sans mesure n'est pas une journée à zéro : on ne divise que
    // par les jours réellement mesurés, sinon la moyenne s'effondre dès qu'on
    // installe l'application en milieu de semaine.
    val thisWeekUsage = myUsage.filter { it.date in days && it.socialMinutes > 0 }
    val avgSocial = if (thisWeekUsage.isEmpty()) 0
    else thisWeekUsage.sumOf { it.socialMinutes } / thisWeekUsage.size

    val myWeights = weights.filter { it.userId == myId && !it.deleted }.sortedBy { it.date }

    // Tâches échangées : ce que j'ai confié, ce que j'ai reçu.
    val given = if (partner == null) 0
    else weekTasks.count { it.userId == partner.id && it.assignedBy == myId }
    val received = if (partner == null) 0
    else weekTasks.count { it.userId == myId && it.assignedBy == partner.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "📈 Mes performances",
            subtitle = Dates.weekRangeLabel(weekStart),
            onBack = onBack
        )

        Spacer(Modifier.height(12.dp))
        SectionLabel("CETTE SEMAINE")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile(
                value = if (myTasks.isEmpty()) "—" else "$doneTasks/${myTasks.size}",
                label = "tâches faites",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                value = if (plannedSessions == 0) "—" else "$doneSessions/$plannedSessions",
                label = "séances d'objectifs",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            KpiTile(
                value = if (streak == 0) "—" else "$streak j",
                label = "série du rituel",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                value = if (avgSleep == 0) "—" else "${avgSleep / 60} h ${avgSleep % 60}",
                label = "sommeil / nuit",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            KpiTile(
                value = if (sportMinutes == 0) "—" else "$sportMinutes min",
                label = "séances de sport",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                value = if (avgSteps == 0) "—" else "%,d".format(avgSteps),
                label = "pas / jour",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            KpiTile(
                value = if (avgSocial == 0) "—" else "$avgSocial min",
                label = "réseaux / jour",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                value = if (myWeights.isEmpty()) "—"
                else String.format(java.util.Locale.FRANCE, "%.1f kg", myWeights.last().kilos),
                label = "dernière pesée",
                accent = accent,
                modifier = Modifier.weight(1f)
            )
        }

        // ----- Pourquoi tel chiffre manque -----
        //
        // Un tiret ne dit pas s'il n'y a rien à mesurer ou si la mesure n'a pas
        // eu lieu. Ces lignes-là ne s'affichent que pour les cases vides, et
        // nomment à chaque fois l'endroit où l'on peut y remédier.
        val missing = buildList {
            if (myTasks.isEmpty()) add("Tâches : aucune tâche rangée dans cette semaine (Planning).")
            if (plannedSessions == 0) add("Séances : aucun objectif actif (Objectifs).")
            if (avgSleep == 0 || avgSteps == 0) {
                add("Sommeil et pas : viennent de Health Connect (Moi → Sommeil & sport).")
            }
            if (sportMinutes == 0) {
                add("Séances de sport : ce sont les séances enregistrées, pas la marche " +
                    "quotidienne — celle-ci est comptée dans les pas.")
            }
            if (avgSocial == 0) {
                add(
                    if (!settings.pacteEnabled)
                        "Réseaux : la mesure ne tourne que si le Pacte d'écran est activé (Moi → Pacte)."
                    else "Réseaux : aucune mesure cette semaine — l'autorisation « Accès aux " +
                        "données d'utilisation » a peut-être été retirée (Moi → Pacte)."
                )
            }
            if (myWeights.isEmpty()) add("Poids : aucune pesée enregistrée (Moi → Mon poids).")
        }
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            missing.forEach { line ->
                Text(
                    text = "· $line",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        // ----- Ce qu'on a vraiment mangé -----
        //
        // Les calories arrivent avant le jeûne : c'est le chiffre qu'on vient
        // chercher. La courbe répond à « ça monte ou ça descend », que quatre
        // tuiles de chiffres ne diraient jamais.
        val myMeals = mealLogs.filter { it.userId == myId }
        val weekMeals = myMeals.filter { it.date >= days.first() && it.date <= days.last() }
        val eatenDays = weekMeals.map { it.date }.distinct()
        val avgKcal = if (eatenDays.isEmpty()) 0
        else weekMeals.sumOf { it.calories } / eatenDays.size
        val plannedKcal = settings.dailyCalories

        if (myMeals.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("CE QUE J'AI MANGÉ")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiTile(
                    value = if (avgKcal == 0) "—" else "$avgKcal",
                    label = "kcal / jour noté",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                KpiTile(
                    value = "${eatenDays.size}/7",
                    label = "jours notés",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quatre semaines de calories : c'est la tendance qui parle, pas le
            // chiffre d'un jour. Les jours sans rien de noté restent à zéro et
            // sont ignorés par la courbe, qui ne relie que les vraies mesures.
            val range = (0..27).map { java.time.LocalDate.now().minusDays((27 - it).toLong()).toString() }
            val serie = com.notresemaine.app.data.Nutrition.caloriesPerDay(myMeals, range)
            Spacer(Modifier.height(12.dp))
            LineChart(
                values = serie,
                labels = listOf(Dates.shortLabel(range.first()), Dates.shortLabel(range.last())),
                accent = accent,
                valueLabel = { "${it.toInt()} kcal" },
                target = if (plannedKcal > 0) plannedKcal.toFloat() else null
            )
            Text(
                text = "Le trait horizontal est votre besoin quotidien ($plannedKcal kcal). " +
                    "Un jour sans rien de noté n'est pas un jour à zéro : il est simplement absent.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            val nutrition = com.notresemaine.app.data.Nutrition.summarize(weekMeals)
            if (nutrition.hasData) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("CE QU'IL Y AVAIT DEDANS")
                MacroBar(nutrition, accent)
            }
        }

        // ----- Le poids -----
        //
        // La section reste visible même sans courbe traçable : invisible, elle
        // donnait l'impression que le suivi du poids n'existait pas.
        Spacer(Modifier.height(24.dp))
        SectionLabel("MON POIDS")
        if (myWeights.size < 2) {
            Text(
                text = if (myWeights.isEmpty())
                    "Aucune pesée. La courbe apparaît dès la deuxième — allez dans " +
                        "Moi → Mon poids pour la première."
                else "Une seule pesée (${String.format(java.util.Locale.FRANCE, "%.1f kg", myWeights.first().kilos)}). " +
                    "Il en faut deux pour tracer une tendance.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LineChart(
                values = myWeights.map { it.kilos.toFloat() },
                labels = listOf(
                    Dates.shortLabel(myWeights.first().date),
                    Dates.shortLabel(myWeights.last().date)
                ),
                accent = accent,
                valueLabel = { String.format(java.util.Locale.FRANCE, "%.1f kg", it) },
                target = settings.weightTarget.takeIf { it > 0 }?.toFloat()
            )
            val delta = myWeights.last().kilos - myWeights.first().kilos
            Text(
                text = String.format(
                    java.util.Locale.FRANCE,
                    "%+.1f kg depuis la première pesée, sur %d mesures.",
                    delta, myWeights.size
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // ----- Le jeûne -----
        //
        // Rien n'est saisi à la main : ces chiffres se déduisent des heures de
        // repas notées. La section n'apparaît donc que si l'on a commencé à noter,
        // et jamais comme un objectif à tenir — c'est un constat, pas une cible.
        val weekLogs = myMeals.filter { it.date >= days.first() && it.date <= days.last() }
        val longestFast = com.notresemaine.app.data.Fasting.longest(myMeals)
        val currentFast = com.notresemaine.app.data.Fasting.currentMinutes(myMeals)
        val fastDays = com.notresemaine.app.data.Fasting.fullDays(weekLogs, days).size
        val skipped = weekLogs.count { it.source == com.notresemaine.app.data.Fasting.SOURCE }

        if (myMeals.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("JEÛNE")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiTile(
                    value = currentFast?.let { com.notresemaine.app.data.Fasting.label(it) } ?: "—",
                    label = "sans manger, là",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                KpiTile(
                    value = longestFast?.label ?: "—",
                    label = "le plus long",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                KpiTile(
                    value = if (skipped == 0) "—" else "$skipped",
                    label = "repas sautés",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                KpiTile(
                    value = if (fastDays == 0) "—" else "$fastDays",
                    label = "jours complets",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Calculé à partir des heures de repas notées — une fenêtre ne compte " +
                    "qu'au-delà de 12 heures. Aucun objectif, aucun palier : seulement ce " +
                    "qui s'est passé.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("CE QU'ON SE CONFIE")
        if (partner == null) {
            Text(
                text = "Personne n'est encore relié à vous. Une fois la synchronisation " +
                    "en place, vous pourrez vous confier des tâches.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiTile(
                    value = "$given",
                    label = "confiées à ${partner.name}",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                KpiTile(
                    value = "$received",
                    label = "reçues de ${partner.name}",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Pour confier une tâche : touchez le crayon ✏️ sur la ligne, " +
                    "puis « Confier à ${partner.name} ».",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("4 DERNIÈRES SEMAINES")

        val weekKeys = (-3..0).map { Dates.weekStartIsoOffset(it) }
        val weekLabels = weekKeys.map { key ->
            if (Dates.weekOffsetOf(key) == 0) "cette sem." else "S${LocalDate.parse(key).dayOfMonth}"
        }

        fun weekOf(dateIso: String) = Dates.weekStartIso(LocalDate.parse(dateIso))

        val sleepPerWeek = weekKeys.map { key ->
            val n = myHealth.filter { weekOf(it.date) == key && it.sleepMinutes > 0 }
            if (n.isEmpty()) 0f else n.sumOf { it.sleepMinutes }.toFloat() / n.size / 60f
        }
        val stepsPerWeek = weekKeys.map { key ->
            val d = myHealth.filter { weekOf(it.date) == key && it.steps > 0 }
            if (d.isEmpty()) 0f else d.sumOf { it.steps }.toFloat() / d.size
        }
        val sportPerWeek = weekKeys.map { key ->
            myHealth.filter { weekOf(it.date) == key }.sumOf { it.exerciseMinutes }.toFloat()
        }
        val screenPerWeek = weekKeys.map { key ->
            val d = myUsage.filter { weekOf(it.date) == key }
            if (d.isEmpty()) 0f else d.sumOf { it.socialMinutes }.toFloat() / d.size
        }
        // Les calories par semaine se calculent sur les jours réellement notés :
        // diviser par sept ferait passer une semaine notée trois jours pour un
        // régime, alors que ce n'est qu'un oubli de saisie.
        val kcalPerWeek = weekKeys.map { key ->
            val ofWeek = myMeals.filter { weekOf(it.date) == key }
            val noted = ofWeek.map { it.date }.distinct()
            if (noted.isEmpty()) 0f else ofWeek.sumOf { it.calories }.toFloat() / noted.size
        }
        val tasksPerWeek = weekKeys.map { key ->
            weekTasks.filter { it.userId == myId && it.weekStart == key && it.done }.size.toFloat()
        }

        Spacer(Modifier.height(8.dp))
        Text("✅ Tâches faites", style = MaterialTheme.typography.bodyLarge)
        MiniBarChart(
            values = tasksPerWeek, labels = weekLabels, accent = accent,
            valueLabel = { "${it.toInt()}" },
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(20.dp))
        Text("😴 Sommeil — moyenne par nuit", style = MaterialTheme.typography.bodyLarge)
        MiniBarChart(
            values = sleepPerWeek, labels = weekLabels, accent = accent,
            valueLabel = { "%.1f h".format(it) },
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(20.dp))
        Text("👟 Pas — moyenne par jour", style = MaterialTheme.typography.bodyLarge)
        MiniBarChart(
            values = stepsPerWeek, labels = weekLabels, accent = accent,
            valueLabel = { "%,d".format(it.toInt()) },
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(20.dp))
        Text("🏃 Sport — séances de la semaine", style = MaterialTheme.typography.bodyLarge)
        MiniBarChart(
            values = sportPerWeek, labels = weekLabels, accent = accent,
            valueLabel = { "${it.toInt()} min" },
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(20.dp))
        Text("🍽️ Calories — moyenne par jour noté", style = MaterialTheme.typography.bodyLarge)
        MiniBarChart(
            values = kcalPerWeek, labels = weekLabels, accent = accent,
            valueLabel = { "${it.toInt()}" },
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(20.dp))
        Text("📱 Réseaux — moyenne par jour", style = MaterialTheme.typography.bodyLarge)
        MiniBarChart(
            values = screenPerWeek, labels = weekLabels, accent = accent,
            valueLabel = { "${it.toInt()} min" },
            modifier = Modifier.padding(top = 6.dp)
        )

        if (myHealth.isEmpty() && myUsage.isEmpty()) {
            Text(
                text = "Aucune mesure pour l'instant. Activez Health Connect ou saisissez " +
                    "votre sommeil en 10 secondes depuis Sommeil & sport.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
