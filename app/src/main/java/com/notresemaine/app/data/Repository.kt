package com.notresemaine.app.data

import android.content.Context
import java.util.UUID

/** Résultat d'un ajout de tâche : refusé si la limite du jour est atteinte. */
sealed class AddResult {
    data object Ok : AddResult()
    data object DayFull : AddResult()
}

class Repository private constructor(context: Context) {

    val db = AppDb.get(context)
    val settings = SettingsStore(context)

    companion object {
        const val MAX_TASKS_PER_DAY = 3

        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }

    private fun now() = System.currentTimeMillis()

    // ----- Tâches -----

    suspend fun addTask(
        userId: String,
        title: String,
        date: String?,
        weekStart: String?,
        isSport: Boolean = false
    ): AddResult {
        if (!isSport && date != null && db.tasks().countForDay(userId, date) >= MAX_TASKS_PER_DAY) {
            return AddResult.DayFull
        }
        db.tasks().upsert(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title.trim(),
                date = date,
                weekStart = weekStart ?: date?.let { Dates.weekStartIso(java.time.LocalDate.parse(it)) },
                isSport = isSport,
                updatedAt = now()
            )
        )
        return AddResult.Ok
    }

    suspend fun toggleDone(taskId: String) {
        val t = db.tasks().byId(taskId) ?: return
        db.tasks().upsert(t.copy(done = !t.done, updatedAt = now()))
    }

    /** Renomme une tâche existante sans en créer une nouvelle. */
    suspend fun renameTask(taskId: String, title: String) {
        val t = db.tasks().byId(taskId) ?: return
        if (title.isBlank()) return
        db.tasks().upsert(t.copy(title = title.trim(), updatedAt = now()))
    }

    suspend fun deleteTask(taskId: String) {
        val t = db.tasks().byId(taskId) ?: return
        db.tasks().upsert(t.copy(deleted = true, updatedAt = now()))
    }

    /**
     * Confie une tâche à l'autre : elle change de propriétaire et garde la trace
     * de qui l'a donnée, pour qu'elle n'apparaisse jamais comme tombée du ciel.
     */
    suspend fun giveTaskToPartner(taskId: String, partnerId: String, fromUserId: String) {
        val t = db.tasks().byId(taskId) ?: return
        if (partnerId.isBlank() || partnerId == t.userId) return
        db.tasks().upsert(
            t.copy(
                userId = partnerId,
                assignedBy = fromUserId,
                isPriority = false, // la priorité du jour reste le choix de celui qui la reçoit
                updatedAt = now()
            )
        )
    }

    /** Réserve un créneau : une tâche sans heure reste une intention, pas un rendez-vous. */
    suspend fun setTaskSlot(taskId: String, startTime: String, durationMinutes: Int) {
        val t = db.tasks().byId(taskId) ?: return
        val clean = if (Dates.isValidTime(startTime)) startTime else ""
        db.tasks().upsert(
            t.copy(
                startTime = clean,
                durationMinutes = if (clean.isBlank()) 0 else durationMinutes.coerceIn(5, 480),
                updatedAt = now()
            )
        )
    }

    // ----- Finance et enfants : ce qui se gère à deux -----

    suspend fun saveHouseItem(
        id: String?, section: String, title: String,
        detail: String, amount: Double, dueDate: String?
    ) {
        if (title.isBlank()) return
        val existing = id?.let { db.houseItems().byId(it) }
        db.houseItems().upsert(
            HouseItemEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                section = section,
                title = title.trim(),
                detail = detail.trim(),
                amount = amount,
                // Une échéance qu'on ne sait pas relire n'est pas une échéance :
                // on la refuse à l'entrée plutôt que de planter à l'affichage.
                dueDate = dueDate?.let { Dates.normalizeDate(it) },
                done = existing?.done ?: false,
                updatedAt = now()
            )
        )
    }

    suspend fun toggleHouseItem(id: String) {
        val item = db.houseItems().byId(id) ?: return
        db.houseItems().upsert(item.copy(done = !item.done, updatedAt = now()))
    }

    suspend fun deleteHouseItem(id: String) {
        val item = db.houseItems().byId(id) ?: return
        db.houseItems().upsert(item.copy(deleted = true, updatedAt = now()))
    }

    suspend fun assignTaskToDay(taskId: String, date: String?) {
        val t = db.tasks().byId(taskId) ?: return
        if (date != null && !t.isSport && db.tasks().countForDay(t.userId, date) >= MAX_TASKS_PER_DAY) return
        val ws = date?.let { Dates.weekStartIso(java.time.LocalDate.parse(it)) } ?: t.weekStart
        db.tasks().upsert(t.copy(date = date, weekStart = ws, updatedAt = now()))
    }

    suspend fun moveTaskToWeek(taskId: String, weekStart: String) {
        val t = db.tasks().byId(taskId) ?: return
        db.tasks().upsert(t.copy(date = null, weekStart = weekStart, done = false, updatedAt = now()))
    }

    /** Définit LA priorité d'un jour (remplace l'existante s'il y en a une). */
    suspend fun setDayPriority(userId: String, date: String, title: String) {
        val existing = db.tasks().priorityOfDay(userId, date)
        if (existing != null) {
            db.tasks().upsert(existing.copy(title = title.trim(), updatedAt = now()))
        } else {
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title.trim(),
                    date = date,
                    weekStart = Dates.weekStartIso(java.time.LocalDate.parse(date)),
                    isPriority = true,
                    updatedAt = now()
                )
            )
        }
    }

    /**
     * Aligne les tâches secondaires d'un jour sur la liste saisie :
     * les titres retirés sont supprimés, les nouveaux ajoutés (pas de doublon).
     */
    suspend fun reconcileSecondary(userId: String, date: String, titles: List<String>) {
        val wanted = titles.map { it.trim() }.filter { it.isNotBlank() }
        val current = db.tasks().byDateOnce(userId, date).filter { !it.isPriority && !it.isSport }
        current.filter { it.title !in wanted }.forEach {
            db.tasks().upsert(it.copy(deleted = true, updatedAt = now()))
        }
        val existingTitles = current.map { it.title }
        wanted.filter { it !in existingTitles }.forEach { title ->
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    date = date,
                    weekStart = Dates.weekStartIso(java.time.LocalDate.parse(date)),
                    updatedAt = now()
                )
            )
        }
    }

    /**
     * Étale les tâches sans date sur les jours les moins chargés de la semaine.
     * Respecte la limite de 3 tâches par jour : ce qui ne rentre pas reste
     * sans date plutôt que d'être entassé — c'est le signal qu'il y en a trop.
     */
    suspend fun spreadTasksOverWeek(userId: String, weekStart: String): Int {
        val days = Dates.daysOfWeek(weekStart)
        val today = Dates.todayIso()
        // On ne remplit pas le passé : une tâche posée hier est déjà en retard.
        val usable = days.filter { it >= today }.ifEmpty { days }
        val load = usable.associateWith { db.tasks().countForDay(userId, it) }.toMutableMap()
        val pending = db.tasks().byWeekOnce(userId, weekStart)
            .filter { it.date == null && !it.deleted && !it.isSport && it.goalId == null }
        var placed = 0
        pending.forEach { task ->
            val target = load.entries.filter { it.value < MAX_TASKS_PER_DAY }.minByOrNull { it.value }
                ?: return@forEach
            db.tasks().upsert(task.copy(date = target.key, updatedAt = now()))
            load[target.key] = target.value + 1
            placed++
        }
        return placed
    }

    // ----- Plan du jour (réveil, blocs de concentration) -----

    suspend fun saveDayPlan(userId: String, date: String, wakeTime: String?, focusBlocks: String?) {
        db.dayPlans().upsert(
            DayPlanEntity(
                id = "$userId:$date",
                userId = userId,
                date = date,
                wakeTime = wakeTime?.ifBlank { null },
                focusBlocks = focusBlocks?.ifBlank { null },
                updatedAt = now()
            )
        )
    }

    // ----- Plan de la semaine -----

    suspend fun saveWeekPlan(userId: String, weekStart: String, priority: String?, abandon: String?, validate: Boolean) {
        val existing = db.weekPlans().byWeekOnce(userId, weekStart)
        db.weekPlans().upsert(
            WeekPlanEntity(
                id = "$userId:$weekStart",
                userId = userId,
                weekStart = weekStart,
                priority = (priority ?: existing?.priority)?.ifBlank { null },
                abandon = (abandon ?: existing?.abandon)?.ifBlank { null },
                validatedAt = if (validate) now() else existing?.validatedAt,
                updatedAt = now()
            )
        )
    }

    // ----- Profils -----

    /** Le profil porte aussi l'engagement du Pacte, pour que le partenaire le voie. */
    suspend fun saveMyProfile(userId: String, name: String, color: String) {
        val s = settings.current()
        db.profiles().upsert(
            ProfileEntity(
                id = userId, name = name.trim(), color = color,
                pacteEnabled = s.pacteEnabled,
                dailyLimitMinutes = s.dailyLimitMinutes,
                curfewEnabled = s.curfewEnabled,
                curfewStart = s.curfewStart,
                curfewEnd = s.curfewEnd,
                updatedAt = now()
            )
        )
    }

    /**
     * Applique un assouplissement mis en attente une fois la date atteinte.
     * Serrer le pacte est immédiat ; le relâcher attend le lendemain.
     */
    suspend fun applyPendingPacteIfDue() {
        val s = settings.current()
        if (s.pendingFromDate.isBlank() || s.pendingFromDate > Dates.todayIso()) return
        settings.setPacte(
            enabled = s.pacteEnabled,
            socialApps = s.socialApps,
            limitMinutes = if (s.pendingLimitMinutes > 0) s.pendingLimitMinutes else s.dailyLimitMinutes
        )
        if (s.pendingCurfewStart.isNotBlank() && s.pendingCurfewEnd.isNotBlank()) {
            settings.setCurfew(s.curfewEnabled, s.pendingCurfewStart, s.pendingCurfewEnd, s.curfewStrict)
        }
        settings.clearPending()
        if (s.myUserId.isNotBlank()) saveMyProfile(s.myUserId, s.myName, s.myColor)
    }

    // ----- Encouragements -----

    suspend fun sendBravo(fromUser: String, toUser: String, message: String) {
        db.encouragements().upsert(
            EncouragementEntity(
                id = UUID.randomUUID().toString(),
                fromUser = fromUser,
                toUser = toUser,
                date = Dates.todayIso(),
                message = message,
                updatedAt = now()
            )
        )
    }

    // ----- Objectifs « clé en main » -----

    /** Crée un objectif ; refuse au-delà de la limite (Essentialisme : moins mais mieux). */
    suspend fun addGoal(
        userId: String, title: String, domain: String,
        sessionsPerWeek: Int, minutesPerSession: Int,
        preferredTime: String, preferredDays: List<Int>,
        nextAction: String, isPrivate: Boolean
    ): Boolean {
        if (db.goals().countActive(userId) >= GoalTemplates.MAX_ACTIVE_GOALS) return false
        db.goals().upsert(
            GoalEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title.trim(),
                domain = domain,
                sessionsPerWeek = sessionsPerWeek.coerceIn(1, 7),
                minutesPerSession = minutesPerSession.coerceIn(5, 180),
                preferredTime = preferredTime,
                preferredDays = preferredDays.joinToString(","),
                nextAction = nextAction.trim(),
                isPrivate = isPrivate,
                updatedAt = now()
            )
        )
        return true
    }

    /** Modifie un objectif existant : le rythme change, l'historique reste. */
    suspend fun updateGoal(
        goalId: String, title: String, sessionsPerWeek: Int, minutesPerSession: Int,
        preferredTime: String, preferredDays: List<Int>, nextAction: String, isPrivate: Boolean
    ) {
        val g = db.goals().byId(goalId) ?: return
        db.goals().upsert(
            g.copy(
                title = title.trim(),
                sessionsPerWeek = sessionsPerWeek.coerceIn(1, 7),
                minutesPerSession = minutesPerSession.coerceIn(5, 180),
                preferredTime = preferredTime,
                preferredDays = preferredDays.joinToString(","),
                nextAction = nextAction.trim(),
                isPrivate = isPrivate,
                updatedAt = now()
            )
        )
    }

    suspend fun setGoalActive(goalId: String, active: Boolean) {
        val g = db.goals().byId(goalId) ?: return
        db.goals().upsert(g.copy(active = active, updatedAt = now()))
    }

    suspend fun deleteGoal(goalId: String) {
        val g = db.goals().byId(goalId) ?: return
        db.goals().upsert(g.copy(deleted = true, active = false, updatedAt = now()))
    }

    /**
     * Génère les séances de la semaine pour chaque objectif actif :
     * placées sur les jours préférés, sans doublon si on relance.
     * Retourne le nombre de séances créées.
     */
    suspend fun planGoalSessions(userId: String, weekStart: String): Int {
        val goals = db.goals().activeOnce(userId)
        if (goals.isEmpty()) return 0
        val existing = db.tasks().byWeekOnce(userId, weekStart)
        val days = Dates.daysOfWeek(weekStart)
        var created = 0
        goals.forEach { goal ->
            val already = existing.count { it.goalId == goal.id }
            val preferred = goal.preferredDays.split(",").mapNotNull { it.trim().toIntOrNull() }
            // Jours préférés d'abord, puis les autres si l'objectif demande plus de séances.
            val ordered = (preferred + (1..7).filter { it !in preferred }).map { days[it - 1] }
            val moment = when (goal.preferredTime) {
                "matin" -> "le matin"
                "midi" -> "le midi"
                else -> "le soir"
            }
            var toCreate = goal.sessionsPerWeek - already
            for (day in ordered) {
                if (toCreate <= 0) break
                if (existing.any { it.goalId == goal.id && it.date == day }) continue
                db.tasks().upsert(
                    TaskEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        title = "${goal.title} · ${goal.minutesPerSession} min $moment",
                        date = day,
                        weekStart = weekStart,
                        isSport = goal.domain == "sante",
                        goalId = goal.id,
                        updatedAt = now()
                    )
                )
                created++
                toCreate--
            }
        }
        return created
    }

    // ----- Rituel du matin -----

    /** Séquence S.A.V.E.R.S. par défaut (Miracle Morning), créée au premier passage. */
    suspend fun ensureRitualSteps(userId: String) {
        if (db.ritual().stepsOnce(userId).isNotEmpty()) return
        listOf(
            "Silence / méditation" to 5,
            "Affirmations" to 2,
            "Visualisation" to 3,
            "Exercice" to 7,
            "Lecture" to 10,
            "Écriture / journal" to 3
        ).forEachIndexed { index, (name, minutes) ->
            db.ritual().upsertStep(
                RitualStepEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    name = name,
                    minutes = minutes,
                    position = index,
                    updatedAt = now()
                )
            )
        }
    }

    suspend fun saveRitualStep(step: RitualStepEntity) {
        db.ritual().upsertStep(step.copy(updatedAt = now()))
    }

    suspend fun completeRitual(userId: String, minutes: Int) {
        val date = Dates.todayIso()
        db.ritual().upsertLog(
            RitualLogEntity(id = "$userId:$date", userId = userId, date = date, minutes = minutes, updatedAt = now())
        )
    }

    /** Série de jours consécutifs (aujourd'hui ou hier compris). */
    fun ritualStreak(logs: List<RitualLogEntity>, userId: String): Int {
        val dates = logs.filter { it.userId == userId && !it.deleted }.map { it.date }.toSet()
        var day = java.time.LocalDate.now()
        if (day.format(Dates.ISO) !in dates) day = day.minusDays(1)
        var streak = 0
        while (day.format(Dates.ISO) in dates) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    // ----- Capture rapide (GTD) -----

    /** Capture une note : datée si une date est reconnue, sinon boîte de réception. */
    suspend fun capture(userId: String, text: String): String {
        val parsed = CaptureParser.parse(text)
        if (parsed.title.isBlank()) return ""
        return if (parsed.date != null &&
            db.tasks().countForDay(userId, parsed.date) < MAX_TASKS_PER_DAY
        ) {
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = parsed.title,
                    date = parsed.date,
                    weekStart = Dates.weekStartIso(java.time.LocalDate.parse(parsed.date)),
                    updatedAt = now()
                )
            )
            "Noté pour ${Dates.shortLabel(parsed.date)} ✓"
        } else {
            db.inbox().upsert(
                InboxItemEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    text = parsed.title,
                    updatedAt = now()
                )
            )
            "Dans la boîte de réception ✓"
        }
    }

    suspend fun resolveInbox(itemId: String, action: String, weekStart: String) {
        val item = db.inbox().byId(itemId) ?: return
        when (action) {
            "planifier" -> {
                db.tasks().upsert(
                    TaskEntity(
                        id = UUID.randomUUID().toString(),
                        userId = item.userId,
                        title = item.text,
                        date = null,
                        weekStart = weekStart,
                        updatedAt = now()
                    )
                )
                db.inbox().upsert(item.copy(processed = true, updatedAt = now()))
            }
            "fait" -> db.inbox().upsert(item.copy(processed = true, updatedAt = now()))
            else -> db.inbox().upsert(item.copy(deleted = true, updatedAt = now()))
        }
    }

    suspend fun reopenInbox(itemId: String) {
        val item = db.inbox().byId(itemId) ?: return
        db.inbox().upsert(item.copy(processed = false, updatedAt = now()))
    }

    // ----- Pacte d'écran -----

    suspend fun saveUsageDay(userId: String, date: String, totalMin: Int, socialMin: Int, unlocks: Int) {
        db.usage().upsert(
            UsageDayEntity(
                id = "$userId:$date", userId = userId, date = date,
                totalMinutes = totalMin, socialMinutes = socialMin, unlocks = unlocks,
                updatedAt = now()
            )
        )
    }

    suspend fun requestGrace(fromUser: String, toUser: String, minutes: Int) {
        db.grace().upsert(
            GraceRequestEntity(
                id = UUID.randomUUID().toString(),
                fromUser = fromUser, toUser = toUser,
                date = Dates.todayIso(), minutes = minutes,
                status = "pending", updatedAt = now()
            )
        )
    }

    suspend fun answerGrace(requestId: String, granted: Boolean) {
        val r = db.grace().byId(requestId) ?: return
        db.grace().upsert(r.copy(status = if (granted) "granted" else "denied", updatedAt = now()))
    }

    // ----- Menus & liste de courses -----

    suspend fun saveMeal(
        userId: String,
        date: String,
        slot: String,
        title: String,
        ingredients: String,
        quantities: String = "",
        calories: Int = 0
    ) {
        db.meals().upsert(
            MealEntity(
                id = "$date:$slot",
                userId = userId,
                date = date,
                slot = slot,
                title = title.trim(),
                ingredients = ingredients.trim(),
                quantities = quantities.trim(),
                calories = calories.coerceAtLeast(0),
                deleted = title.isBlank() && ingredients.isBlank(),
                updatedAt = now()
            )
        )
    }

    /** Remplace un repas par la proposition de l'assistant, quantités et calories comprises. */
    suspend fun replaceMeal(
        userId: String,
        date: String,
        slot: String,
        suggestion: com.notresemaine.app.ai.Assistant.MealSuggestion
    ) {
        saveMeal(
            userId, date, slot,
            suggestion.title, suggestion.ingredients,
            suggestion.quantities, suggestion.calories
        )
    }

    /** Remplit les créneaux vides de la semaine depuis la banque d'idées hors ligne. */
    suspend fun fillWeekMenusFromBank(userId: String, weekStart: String): Int {
        val days = Dates.daysOfWeek(weekStart)
        val existing = db.meals().betweenOnce(days.first(), days.last())
            .filter { it.title.isNotBlank() && !it.deleted }
            .map { it.id }
            .toSet()
        val seed = java.time.LocalDate.parse(weekStart).dayOfYear
        var filled = 0
        MenuIdeas.weekPlan(seed).forEach { (key, idea) ->
            val (dayIndex, slot) = key
            val date = days[dayIndex]
            if ("$date:$slot" in existing) return@forEach
            saveMeal(userId, date, slot, idea.title, idea.ingredients)
            filled++
        }
        return filled
    }

    /** Applique des suggestions de l'assistant, sans écraser ce qui est déjà décidé. */
    suspend fun applyMenuSuggestions(
        userId: String,
        weekStart: String,
        suggestions: List<com.notresemaine.app.ai.Assistant.MealSuggestion>
    ): Int {
        val days = Dates.daysOfWeek(weekStart)
        val existing = db.meals().betweenOnce(days.first(), days.last())
            .filter { it.title.isNotBlank() && !it.deleted }
            .map { it.id }
            .toSet()
        var applied = 0
        suggestions.forEach { s ->
            val date = days.getOrNull(s.dayIndex) ?: return@forEach
            if ("$date:${s.slot}" in existing) return@forEach
            saveMeal(userId, date, s.slot, s.title, s.ingredients, s.quantities, s.calories)
            applied++
        }
        return applied
    }

    /**
     * (Re)génère la liste de courses depuis les menus de la semaine.
     * Les lignes ajoutées à la main et les cases déjà cochées sont conservées.
     */
    suspend fun generateShoppingList(userId: String, weekStart: String): Int {
        val days = Dates.daysOfWeek(weekStart)
        val meals = db.meals().betweenOnce(days.first(), days.last())
        val items = Ingredients.aggregate(meals.map { it.ingredients }.filter { it.isNotBlank() })

        val existing = db.shopping().forWeekOnce(weekStart)
        val existingById = existing.associateBy { it.id }
        val generatedIds = mutableSetOf<String>()

        items.forEach { item ->
            val id = "$weekStart:${item.aisle}:${item.label.lowercase()}"
            generatedIds += id
            val previous = existingById[id]
            db.shopping().upsert(
                ShoppingItemEntity(
                    id = id,
                    userId = userId,
                    weekStart = weekStart,
                    label = item.label,
                    aisle = item.aisle,
                    checked = previous?.checked ?: false,
                    manual = false,
                    updatedAt = now()
                )
            )
        }

        // Retire les lignes générées précédemment qui ne correspondent plus aux menus.
        existing.filter { !it.manual && it.id !in generatedIds }.forEach {
            db.shopping().upsert(it.copy(deleted = true, updatedAt = now()))
        }
        return items.size
    }

    suspend fun addShoppingItem(userId: String, weekStart: String, label: String) {
        if (label.isBlank()) return
        val aisle = Ingredients.aisleFor(label)
        db.shopping().upsert(
            ShoppingItemEntity(
                id = "$weekStart:$aisle:${label.lowercase().trim()}",
                userId = userId,
                weekStart = weekStart,
                label = label.trim(),
                aisle = aisle,
                manual = true,
                updatedAt = now()
            )
        )
    }

    suspend fun deleteShoppingItem(itemId: String) {
        val item = db.shopping().byId(itemId) ?: return
        db.shopping().upsert(item.copy(deleted = true, updatedAt = now()))
    }

    suspend fun toggleShoppingItem(itemId: String) {
        val item = db.shopping().byId(itemId) ?: return
        db.shopping().upsert(item.copy(checked = !item.checked, updatedAt = now()))
    }

    suspend fun clearCheckedShopping(weekStart: String) {
        db.shopping().forWeekOnce(weekStart).filter { it.checked }.forEach {
            db.shopping().upsert(it.copy(deleted = true, updatedAt = now()))
        }
    }

    // ----- Sommeil, pas, sport -----

    suspend fun saveHealthDay(
        userId: String, date: String,
        sleepMinutes: Int, steps: Int, exerciseMinutes: Int, source: String
    ) {
        val id = "$userId:$date"
        val existing = db.health().byId(id)
        // Une mesure automatique ne doit jamais écraser une saisie manuelle du même jour.
        if (existing?.source == "manuel" && source == "health_connect") return
        db.health().upsert(
            HealthDayEntity(
                id = id, userId = userId, date = date,
                sleepMinutes = sleepMinutes, steps = steps,
                exerciseMinutes = exerciseMinutes, source = source,
                updatedAt = now()
            )
        )
    }

    /** Saisie de secours en moins de 10 secondes : heure de coucher + heure de lever. */
    suspend fun saveSleepManually(userId: String, date: String, bedTime: String, wakeTime: String) {
        val bed = runCatching { java.time.LocalTime.parse(bedTime) }.getOrNull() ?: return
        val wake = runCatching { java.time.LocalTime.parse(wakeTime) }.getOrNull() ?: return
        var minutes = java.time.Duration.between(bed, wake).toMinutes()
        if (minutes <= 0) minutes += 24 * 60 // le coucher est la veille
        val existing = db.health().byId("$userId:$date")
        saveHealthDay(
            userId, date, minutes.toInt(),
            existing?.steps ?: 0, existing?.exerciseMinutes ?: 0, "manuel"
        )
    }

    /** Bouton « j'ai fait une séance » : ajoute la durée au sport du jour. */
    suspend fun addExerciseManually(userId: String, date: String, minutes: Int) {
        val existing = db.health().byId("$userId:$date")
        saveHealthDay(
            userId, date,
            existing?.sleepMinutes ?: 0, existing?.steps ?: 0,
            (existing?.exerciseMinutes ?: 0) + minutes, "manuel"
        )
    }

    /**
     * Crée une tâche directement chez le binôme. Différent de [giveTaskToPartner],
     * qui déplace une tâche existante : ici on en fabrique une pour lui.
     */
    suspend fun addTaskAssigned(toUserId: String, fromUserId: String, title: String, weekStart: String) {
        if (title.isBlank() || toUserId.isBlank()) return
        db.tasks().upsert(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                userId = toUserId,
                title = title.trim(),
                date = null,
                weekStart = weekStart,
                assignedBy = fromUserId,
                updatedAt = now()
            )
        )
    }

    /**
     * Applique les actions proposées pour la boîte de réception.
     * Une note qui reste « inbox » n'est pas traitée : l'assistant a le droit
     * de dire « ça demande encore réflexion », et on ne force pas.
     */
    suspend fun applyInboxActions(
        userId: String,
        notes: List<InboxItemEntity>,
        actions: List<com.notresemaine.app.ai.Assistant.InboxAction>,
        weekStart: String
    ): Int {
        var applied = 0
        actions.forEach { action ->
            val note = notes.getOrNull(action.index) ?: return@forEach
            if (action.whenLabel == "inbox") return@forEach
            val date = when (action.whenLabel) {
                "aujourdhui" -> Dates.todayIso()
                "demain" -> Dates.tomorrowIso()
                else -> null
            }
            // La limite de 3 tâches par jour tient aussi ici : sinon la journée
            // se remplit toute seule et la règle ne veut plus rien dire.
            val target = if (date != null && db.tasks().countForDay(userId, date) >= MAX_TASKS_PER_DAY) null else date
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = action.action.trim(),
                    date = target,
                    weekStart = target?.let { Dates.weekStartIso(java.time.LocalDate.parse(it)) } ?: weekStart,
                    updatedAt = now()
                )
            )
            db.inbox().upsert(note.copy(processed = true, updatedAt = now()))
            applied++
        }
        return applied
    }

    // ----- Habitudes -----

    suspend fun saveHabit(
        id: String?, userId: String, title: String, source: String,
        fromHour: Int, toHour: Int, perDay: Int
    ) {
        if (title.isBlank()) return
        val existing = id?.let { db.habits().byId(it) }
        db.habits().upsert(
            HabitEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                userId = existing?.userId ?: userId,
                title = title.trim(),
                source = source.trim(),
                enabled = existing?.enabled ?: true,
                fromHour = fromHour.coerceIn(0, 23),
                toHour = toHour.coerceIn(1, 23),
                perDay = perDay.coerceIn(1, 8),
                updatedAt = now()
            )
        )
    }

    suspend fun toggleHabit(id: String) {
        val h = db.habits().byId(id) ?: return
        db.habits().upsert(h.copy(enabled = !h.enabled, updatedAt = now()))
    }

    suspend fun deleteHabit(id: String) {
        val h = db.habits().byId(id) ?: return
        db.habits().upsert(h.copy(deleted = true, updatedAt = now()))
    }

    // ----- Sport -----

    /** Pose le programme sur la semaine, en séances de sport datées. */
    suspend fun applySportProgram(
        userId: String,
        weekStart: String,
        sessions: List<com.notresemaine.app.ai.Assistant.SportSession>
    ): Int {
        val days = Dates.daysOfWeek(weekStart)
        var placed = 0
        sessions.forEach { session ->
            val date = days.getOrNull(session.dayIndex) ?: return@forEach
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = "${session.title} · ${session.minutes} min",
                    date = date,
                    weekStart = weekStart,
                    // Une séance ne compte pas dans la limite de trois tâches :
                    // le corps n'est pas une tâche de plus dans la journée.
                    isSport = true,
                    durationMinutes = session.minutes,
                    updatedAt = now()
                )
            )
            placed++
        }
        return placed
    }

    // ----- Poids -----

    /** Une pesée par jour : se repeser le soir remplace la pesée du matin. */
    suspend fun saveWeight(userId: String, date: String, kilos: Double, note: String = "") {
        if (kilos <= 0.0 || kilos > 400.0) return
        db.weights().upsert(
            WeightEntity(
                id = "$userId:$date", userId = userId, date = date,
                kilos = kilos, note = note.trim(), updatedAt = now()
            )
        )
    }

    suspend fun deleteWeight(id: String) {
        val existing = db.weights().byId(id) ?: return
        db.weights().upsert(existing.copy(deleted = true, updatedAt = now()))
    }

    /**
     * Ce que voit l'assistant : des kilos et rien d'autre — pas de date, pas de nom,
     * et uniquement les miens.
     */
    suspend fun myWeightSeries(userId: String, count: Int = 12): List<Double> =
        db.weights().lastOnce(userId, count).reversed().map { it.kilos }

    // ----- Agenda du téléphone -----

    /**
     * Envoie dans l'agenda les tâches de la journée qui ont un créneau.
     * Idempotent : réécrire la journée remplace nos événements, jamais les autres.
     */
    suspend fun pushDayToCalendar(context: android.content.Context, userId: String, date: String): Int {
        val s = settings.current()
        if (!s.calendarEnabled || s.calendarId <= 0) return 0
        val day = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return 0
        val slots = db.tasks().byDateOnce(userId, date)
            .filter { it.startTime.isNotBlank() && !it.deleted }
            .mapNotNull { task ->
                val time = runCatching { java.time.LocalTime.parse(task.startTime) }.getOrNull()
                    ?: return@mapNotNull null
                com.notresemaine.app.calendar.PhoneCalendar.Slot(
                    title = task.title,
                    startMinutes = time.hour * 60 + time.minute,
                    durationMinutes = if (task.durationMinutes > 0) task.durationMinutes else 30
                )
            }
        return com.notresemaine.app.calendar.PhoneCalendar.writeDay(context, s.calendarId, day, slots)
    }

    /** Toute la semaine d'un coup, après la préparation du dimanche. */
    suspend fun pushWeekToCalendar(context: android.content.Context, userId: String, weekStart: String): Int {
        var total = 0
        Dates.daysOfWeek(weekStart).forEach { total += pushDayToCalendar(context, userId, it) }
        return total
    }

    // ----- Ce que l'assistant a le droit de voir -----
    //
    // Ces méthodes sont le seul chemin par lequel des données partent vers l'assistant.
    // Elles filtrent ici, dans le code, ce que la page des réglages promet en français :
    // jamais un objectif privé, jamais les données de l'autre.

    /** Objectifs actifs, les privés exclus : ils ne quittent pas le téléphone. */
    suspend fun goalTitlesForAi(userId: String): List<String> =
        db.goals().activeOnce(userId).filter { !it.isPrivate }.map { it.title }

    suspend fun inboxTextsForAi(userId: String): List<String> =
        db.inbox().pendingOnce(userId).map { it.text }

    /** Tâches de la semaine qui n'ont pas encore de jour. */
    suspend fun backlogTitlesForAi(userId: String, weekStart: String): List<String> =
        db.tasks().byWeekOnce(userId, weekStart)
            .filter { it.date == null && !it.done && it.goalId == null }
            .map { it.title }

    suspend fun weekPriorityOf(userId: String, weekStart: String): String =
        db.weekPlans().byWeekOnce(userId, weekStart)?.priority.orEmpty()

    data class UsageAverages(val socialMinutes: Int, val unlocks: Int, val days: Int)

    /** Moyennes des 7 derniers jours, pour proposer un pacte fondé sur le réel. */
    suspend fun usageAverages(userId: String): UsageAverages {
        val days = db.usage().sinceOnce(userId, Dates.daysAgoIso(7))
        if (days.isEmpty()) return UsageAverages(0, 0, 0)
        return UsageAverages(
            socialMinutes = days.sumOf { it.socialMinutes } / days.size,
            unlocks = days.sumOf { it.unlocks } / days.size,
            days = days.size
        )
    }

    data class HealthAverages(
        val sleepMinutes: Int,
        val steps: Int,
        val exerciseMinutes: Int,
        val days: Int
    )

    /** Moyennes de la semaine, uniquement les vôtres. */
    suspend fun healthAverages(userId: String, weekStart: String): HealthAverages {
        val days = Dates.daysOfWeek(weekStart)
        val rows = db.health().betweenOnce(userId, days.first(), days.last())
        val withSleep = rows.filter { it.sleepMinutes > 0 }
        val withSteps = rows.filter { it.steps > 0 }
        return HealthAverages(
            sleepMinutes = if (withSleep.isEmpty()) 0 else withSleep.sumOf { it.sleepMinutes } / withSleep.size,
            steps = if (withSteps.isEmpty()) 0 else withSteps.sumOf { it.steps } / withSteps.size,
            exerciseMinutes = rows.sumOf { it.exerciseMinutes },
            days = rows.size
        )
    }

    // ----- Appliquer ce que l'assistant propose -----

    /** Range une note clarifiée à l'endroit proposé ; jamais au-delà de 3 tâches par jour. */
    suspend fun applyClarifiedCapture(userId: String, action: String, whenLabel: String): String {
        val title = action.trim()
        if (title.isBlank()) return ""
        val date = when (whenLabel) {
            "aujourdhui" -> Dates.todayIso()
            "demain" -> Dates.tomorrowIso()
            else -> null
        }
        if (date != null && db.tasks().countForDay(userId, date) < MAX_TASKS_PER_DAY) {
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    date = date,
                    weekStart = Dates.weekStartIso(java.time.LocalDate.parse(date)),
                    updatedAt = now()
                )
            )
            return "Noté pour ${Dates.shortLabel(date)} ✓"
        }
        if (whenLabel != "inbox") {
            // Journée pleine, ou action prévue « dans la semaine » : elle attend un jour libre.
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    date = null,
                    weekStart = Dates.weekStartIso(),
                    updatedAt = now()
                )
            )
            return "Ajouté à la semaine ✓"
        }
        db.inbox().upsert(
            InboxItemEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                text = title,
                updatedAt = now()
            )
        )
        return "Dans la boîte de réception ✓"
    }

    /** Articles que le classement hors ligne n'a pas su ranger. */
    suspend fun unsortedShoppingLabels(weekStart: String): List<String> =
        db.shopping().forWeekOnce(weekStart).filter { it.aisle == "Divers" }.map { it.label }

    /** Applique les rayons proposés ; ne touche qu'aux articles restés dans « Divers ». */
    suspend fun applyAisles(weekStart: String, aisles: Map<String, String>): Int {
        if (aisles.isEmpty()) return 0
        val byLabel = aisles.mapKeys { it.key.trim().lowercase() }
        var changed = 0
        db.shopping().forWeekOnce(weekStart).filter { it.aisle == "Divers" }.forEach { item ->
            val aisle = byLabel[item.label.trim().lowercase()] ?: return@forEach
            if (aisle == item.aisle) return@forEach
            db.shopping().upsert(item.copy(aisle = aisle, updatedAt = now()))
            changed++
        }
        return changed
    }

    /** Quand on se connecte à la synchro, l'identifiant local devient l'identifiant du compte. */
    suspend fun migrateUserId(oldId: String, newId: String) {
        if (oldId == newId || oldId.isBlank()) return
        val t = now()
        db.tasks().migrateUser(oldId, newId, t)
        db.dayPlans().migrateUser(oldId, newId, t)
        db.weekPlans().migrateUser(oldId, newId, t)
        db.goals().migrateUser(oldId, newId, t)
        db.weights().migrateUser(oldId, newId, t)
        db.habits().migrateUser(oldId, newId, t)
        val profile = db.profiles().byId(oldId)
        if (profile != null) {
            db.profiles().delete(oldId)
            db.profiles().upsert(profile.copy(id = newId, updatedAt = t))
        }
    }
}
