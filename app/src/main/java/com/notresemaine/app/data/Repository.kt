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

    suspend fun deleteTask(taskId: String) {
        val t = db.tasks().byId(taskId) ?: return
        db.tasks().upsert(t.copy(deleted = true, updatedAt = now()))
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

    suspend fun saveMyProfile(userId: String, name: String, color: String) {
        db.profiles().upsert(ProfileEntity(id = userId, name = name.trim(), color = color, updatedAt = now()))
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

    suspend fun saveMeal(userId: String, date: String, slot: String, title: String, ingredients: String) {
        db.meals().upsert(
            MealEntity(
                id = "$date:$slot",
                userId = userId,
                date = date,
                slot = slot,
                title = title.trim(),
                ingredients = ingredients.trim(),
                deleted = title.isBlank() && ingredients.isBlank(),
                updatedAt = now()
            )
        )
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

    /** Quand on se connecte à la synchro, l'identifiant local devient l'identifiant du compte. */
    suspend fun migrateUserId(oldId: String, newId: String) {
        if (oldId == newId || oldId.isBlank()) return
        val t = now()
        db.tasks().migrateUser(oldId, newId, t)
        db.dayPlans().migrateUser(oldId, newId, t)
        db.weekPlans().migrateUser(oldId, newId, t)
        db.goals().migrateUser(oldId, newId, t)
        val profile = db.profiles().byId(oldId)
        if (profile != null) {
            db.profiles().delete(oldId)
            db.profiles().upsert(profile.copy(id = newId, updatedAt = t))
        }
    }
}
